# FraudLens — Product Specification

> **What**: Full product definition — data model, API contract, acceptance criteria, and future improvements.
> For implementation rules and technical decisions, see [CLAUDE.md](./CLAUDE.md).

---

## 1. Overview

FraudLens is a full-stack application that manages and analyzes user session data collected by a network appliance. Each session is enriched with navigation events. The system exposes a secured REST API consumed by a React frontend and includes a rule-based local risk scoring engine plus an AI-powered natural language risk assessment.

---

## 2. Data Model

### 2.1 Session

| Field       | Type                              | Constraints                                      |
|-------------|-----------------------------------|--------------------------------------------------|
| `id`        | String                            | Primary key, server-generated (UUID v4 string)   |
| `userId`    | String                            | Not null                                         |
| `ip`        | String                            | Not null                                         |
| `country`   | String                            | Not null                                         |
| `device`    | String                            | Not null                                         |
| `timestamp` | String (ISO 8601 datetime)        | Not null, stored as VARCHAR(50)                  |
| `status`    | Enum: `SAFE`, `SUSPICIOUS`, `DANGEROUS` | Not null, defaults to `SAFE`            |
| `riskScore` | Integer (0–100)                   | **Not in original spec entity.** Computed enrichment from §4, not persisted — derived on read |

> **Design note on `id`**: Session and Event IDs are stored as `String` (VARCHAR(255)) with a server-generated UUID v4 value. This keeps path variables as `@PathVariable String id`, avoids 400 errors on non-UUID input, and matches the flexible ID style implied by the PDF examples (`"abc-123"`, `"evt_001"`). The `User` entity uses a DB-generated `BIGINT` identity instead — it is never exposed in API path variables.

### 2.2 Event

| Field        | Type                                         | Constraints                              |
|--------------|----------------------------------------------|------------------------------------------|
| `id`         | String                                       | Primary key, server-generated (UUID v4 string) |
| `sessionId`  | String                                       | Not null, FK → `sessions.id`. **Must appear as a top-level field in all Event API responses** (the PDF model explicitly includes it) |
| `type`       | Enum: `PAGE_VISIT`, `FORM_SUBMIT`, `LOGIN_ATTEMPT` | Not null                           |
| `url`        | String                                       | Not null                                 |
| `durationMs` | Long                                         | Not null                                 |
| `metadata`   | String (JSON text)                           | Nullable                                 |

### 2.3 User (Auth)

> **Design decision — not in the PDF spec.** The PDF only requires "a simple form that authenticates the user". A `users` table is our design choice to support proper JWT-backed authentication without hardcoded credentials. It is seeded at startup via an `ApplicationRunner` (see CLAUDE.md §3.13).

| Field      | Type    | Constraints                                      |
|------------|---------|--------------------------------------------------|
| `id`       | Long    | Primary key, DB-generated (`BIGINT IDENTITY`)    |
| `username` | String  | Not null, unique                                 |
| `password` | String  | Not null, BCrypt hashed                          |
| `role`     | String  | Not null (e.g. `ADMIN`, `USER`)                  |

### 2.4 Referential Integrity Constraints

- **Database level**: `events.session_id` has a `FOREIGN KEY` referencing `sessions.id` with `ON DELETE CASCADE`. Enforced regardless of how data is deleted (direct SQL, ORM bypass, admin tooling).
- **ORM level**: `Session` entity declares `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)`. Enforced at the application layer.
- **Rationale**: Both layers are intentional. The DB constraint is the safety net; the ORM cascade is the application-layer convenience.

---

## 3. API Endpoints

All endpoints except `POST /auth/login` and `GET /actuator/health` require a valid JWT `Authorization: Bearer <token>` header.

### 3.1 Authentication

| Method | Path           | Description                     | Request Body                         | Response         |
|--------|----------------|---------------------------------|--------------------------------------|------------------|
| POST   | `/auth/login`  | Authenticate and receive JWT    | `{ username, password }`             | `200 { token }`  |

### 3.2 Sessions

| Method | Path                   | Description                                | Request Body              | Response              |
|--------|------------------------|--------------------------------------------|---------------------------|-----------------------|
| GET    | `/sessions`            | List all sessions (with `riskScore`)       | —                         | `200 Session[]`       |
| GET    | `/sessions/{id}`       | Single session with its events and score   | —                         | `200 Session+Events`  |
| POST   | `/sessions`            | Create a new session                       | Session fields (no id)    | `201 Session`         |
| PUT    | `/sessions/{id}`       | Update an existing session                 | Updatable session fields  | `200 Session`         |
| DELETE | `/sessions/{id}`       | Delete session and all its events          | —                         | `204 No Content`      |
| POST   | `/sessions/search`     | Filter and sort sessions                   | See §3.5 SearchRequest    | `200 Session[]`       |

### 3.3 Events

| Method | Path                                  | Description                   | Request Body       | Response        |
|--------|---------------------------------------|-------------------------------|--------------------|-----------------|
| GET    | `/sessions/{id}/events`               | List events for a session     | —                  | `200 Event[]`   |
| POST   | `/sessions/{id}/events`               | Add an event to a session     | Event fields       | `201 Event`     |
| DELETE | `/sessions/{id}/events/{eventId}`     | Delete a single event         | —                  | `204 No Content`|

### 3.4 AI Risk Summary (Bonus)

| Method | Path                           | Description                                | Response                    |
|--------|--------------------------------|--------------------------------------------|-----------------------------|
| POST   | `/sessions/{id}/risk-summary`  | Generate AI natural language risk summary  | `200 { summary: string }`   |

### 3.5 SearchRequest Body (`POST /sessions/search`)

```json
{
  "status":   "DANGEROUS",        // optional: SAFE | SUSPICIOUS | DANGEROUS
  "country":  "IT",               // optional
  "userId":   "usr_001",          // optional
  "ip":       "1.2.3.4",          // optional
  "sortBy":   "timestamp",        // optional: "timestamp" | "id" — default: "timestamp"
  "sortDir":  "desc"              // optional: "asc" | "desc" — default: "desc"
}
```

All fields are optional. An empty body `{}` returns all sessions sorted by timestamp descending.

> **Scope note**: The PDF says *"filtering by any field"*. The fields above cover the primary use cases (`status`, `country`, `userId`, `ip`). Fields such as `device` and timestamp range filtering are not included in this implementation. This is a deliberate scope reduction documented as a trade-off in the README.

### 3.6 Standard Error Response

All errors return a consistent JSON body:

```json
{
  "timestamp": "2028-11-02T10:20:11Z",
  "status":    404,
  "error":     "Not Found",
  "message":   "Session not found: abc-123",
  "path":      "/sessions/abc-123"
}
```

No stack traces, internal class names, or sensitive data are ever included in error responses.

### 3.7 Health

| Method | Path               | Description                          | Auth Required |
|--------|--------------------|--------------------------------------|---------------|
| GET    | `/actuator/health` | Application and DB connectivity check | No            |

---

## 4. Local Risk Score Rules

> **Status: To Be Defined**

The risk scoring algorithm will be documented here once the rules are finalized. The score is an integer between 0 and 100, computed server-side from the session's events and attributes on every read request. It is never stored in the database.

Placeholder categories:
- Country-based signals
- Event sequence patterns (e.g. LOGIN_ATTEMPT → FORM_SUBMIT timing)
- Sensitive metadata in form submissions
- Multiple failed login attempts
- Anomalously short interaction durations

---

## 5. Acceptance Criteria

### 5.1 Authentication

| # | Given | When | Then | Done |
|---|-------|------|------|------|
| AC-AUTH-01 | Valid credentials (`username`, `password`) | `POST /auth/login` | `200 OK` + JWT token in response body | ✅ |
| AC-AUTH-02 | Invalid credentials | `POST /auth/login` | `401 Unauthorized` | ✅ |
| AC-AUTH-03 | No `Authorization` header | Any protected endpoint is called | `401 Unauthorized` | ✅ |
| AC-AUTH-04 | Expired JWT | Any protected endpoint is called | `401 Unauthorized` | ✅ |
| AC-AUTH-05 | Malformed JWT | Any protected endpoint is called | `401 Unauthorized` | ✅ |

### 5.2 Sessions — CRUD

| # | Given | When | Then | Done |
|---|-------|------|------|------|
| AC-SES-01 | Valid JWT | `GET /sessions` | `200 OK` + array of sessions each containing a `riskScore` field | ✅ |
| AC-SES-02 | Valid JWT + valid body | `POST /sessions` | `201 Created` + created session object with generated `id` | ✅ |
| AC-SES-03 | Valid JWT + missing required field | `POST /sessions` | `400 Bad Request` + field-level validation errors | ✅ |
| AC-SES-04 | Valid JWT + invalid status value | `POST /sessions` | `400 Bad Request` | ✅ |
| AC-SES-05 | Valid JWT + existing session id | `GET /sessions/{id}` | `200 OK` + session object with nested `events` and `riskScore` | ✅ |
| AC-SES-06 | Valid JWT + non-existing id | `GET /sessions/{id}` | `404 Not Found` | ✅ |
| AC-SES-07 | Valid JWT + existing id + valid body | `PUT /sessions/{id}` | `200 OK` + updated session | ✅ |
| AC-SES-08 | Valid JWT + existing id | `DELETE /sessions/{id}` | `204 No Content` | ✅ |
| AC-SES-09 | Valid JWT + non-existing id | `DELETE /sessions/{id}` | `404 Not Found` | ✅ |

### 5.3 Cascade Delete

| # | Given | When | Then | Done |
|---|-------|------|------|------|
| AC-CAS-01 | Session S1 has events E1, E2, E3 | `DELETE /sessions/S1` | `204 No Content` and subsequent `GET /sessions/S1/events` returns `404` or empty list | ⏳ Integration test (requires DB) |
| AC-CAS-02 | Session S1 has events E1, E2, E3 | `DELETE /sessions/S1` (verified via direct DB query) | Events table contains no rows with `session_id = S1` | ⏳ Integration test (requires DB) |

### 5.4 Sessions — Search

| # | Given | When | Then | Done |
|---|-------|------|------|------|
| AC-SRCH-01 | Sessions with mixed statuses exist | `POST /sessions/search` with `{ "status": "DANGEROUS" }` | `200 OK` + only `DANGEROUS` sessions | ✅ |
| AC-SRCH-02 | Sessions from multiple countries exist | `POST /sessions/search` with `{ "country": "IT" }` | `200 OK` + only sessions with `country = "IT"` | ✅ |
| AC-SRCH-03 | Multiple sessions exist | `POST /sessions/search` with `{ "sortBy": "timestamp", "sortDir": "desc" }` | Sessions ordered newest-first | ✅ |
| AC-SRCH-04 | — | `POST /sessions/search` with empty body `{}` | `200 OK` + all sessions, default sort applied | ✅ |
| AC-SRCH-05 | — | `POST /sessions/search` with multiple filters (e.g. `status` + `country`) | Only sessions matching **all** conditions returned | ✅ (unit-tested in SessionServiceTest) |

### 5.5 Events

| # | Given | When | Then | Done |
|---|-------|------|------|------|
| AC-EVT-01 | Valid JWT + existing session | `GET /sessions/{id}/events` | `200 OK` + list of events ordered by insertion time | ✅ |
| AC-EVT-02 | Valid JWT + existing session + valid body | `POST /sessions/{id}/events` | `201 Created` + event object with generated `id` | ✅ |
| AC-EVT-03 | Valid JWT + non-existing session | `POST /sessions/{id}/events` | `404 Not Found` | ✅ |
| AC-EVT-04 | Valid JWT + existing event | `DELETE /sessions/{id}/events/{eventId}` | `204 No Content` | ✅ |
| AC-EVT-05 | Valid JWT + non-existing eventId | `DELETE /sessions/{id}/events/{eventId}` | `404 Not Found` | ✅ |
| AC-EVT-06 | Valid JWT + eventId belonging to a different session | `DELETE /sessions/{id}/events/{eventId}` | `404 Not Found` | ✅ |

### 5.6 Risk Score

| # | Given | When | Then | Done |
|---|-------|------|------|------|
| AC-RISK-01 | Session with no events | `GET /sessions/{id}` | `riskScore` is `0` | ✅ |
| AC-RISK-02 | Session with a `LOGIN_ATTEMPT` followed by `FORM_SUBMIT` within 5 seconds | `GET /sessions/{id}` | `riskScore` increases by the defined rule weight | ✅ |
| AC-RISK-03 | Session with `FORM_SUBMIT` containing `card_number` or `cvv` in `metadata.formFields` | `GET /sessions/{id}` | `riskScore` increases by the defined rule weight | ✅ |
| AC-RISK-04 | Any session | `GET /sessions/{id}` | `riskScore` is always between `0` and `100` inclusive | ✅ |
| AC-RISK-05 | Session with maximum signals active simultaneously | `GET /sessions/{id}` | `riskScore` is capped at `100` | ✅ (max computed score is 95; `Math.min` cap verified) |

### 5.7 Health & Observability

| # | Given | When | Then | Done |
|---|-------|------|------|------|
| AC-HLTH-01 | Application is running + DB is reachable | `GET /actuator/health` | `200 OK` + `{ "status": "UP" }` | ⏳ Integration test (requires DB) |
| AC-HLTH-02 | DB is unreachable | `GET /actuator/health` | `503 Service Unavailable` + `{ "status": "DOWN" }` | ⏳ Integration test (requires DB) |
| AC-HLTH-03 | — | `GET /actuator/env` or any non-exposed actuator path | `404 Not Found` | ⏳ Integration test (requires running app) |

### 5.8 AI Risk Summary (Bonus)

| # | Given | When | Then | Done |
|---|-------|------|------|------|
| AC-AI-01 | Valid JWT + existing session with events | `POST /sessions/{id}/risk-summary` | `200 OK` + `{ "summary": "<natural language text>" }` | ✅ |
| AC-AI-02 | Valid JWT + non-existing session | `POST /sessions/{id}/risk-summary` | `404 Not Found` | ✅ |
| AC-AI-03 | AI provider is unavailable | `POST /sessions/{id}/risk-summary` | `503 Service Unavailable` + meaningful error message (no raw API error leaked) | ✅ |

### 5.9 Frontend — UI Behavior

| # | Given | When | Then |
|---|-------|------|------|
| AC-UI-01 | User is not logged in | App loads | Login screen is shown; protected views are inaccessible |
| AC-UI-02 | Valid credentials are submitted | Login form is submitted | JWT is stored in memory (React state/context); user is redirected to sessions list |
| AC-UI-03 | Sessions list is displayed | — | Each session shows: userId, country, device, status badge (color-coded), riskScore badge |
| AC-UI-04 | User filters by status | Status filter is applied | Only matching sessions are displayed |
| AC-UI-04b | User filters by country | Country filter is applied | Only sessions from that country are displayed |
| AC-UI-05 | User clicks a session | — | Session detail view opens with metadata and chronological event timeline |
| AC-UI-06 | User clicks "Delete Session" | — | Confirmation modal appears; session is only deleted after confirmation |
| AC-UI-07 | User confirms deletion | — | Session is removed from the list without full page reload |
| AC-UI-08 | User clicks "Add Event" in detail view | Form is submitted | New event appears in the timeline |

---

## 6. What I Would Improve With More Time

### Data Layer
- **Pagination**: `GET /sessions` currently returns all records. Add `page` / `size` query parameters and return a `Page<SessionResponseDTO>` wrapper with total count.
- **Full-text search on metadata**: Use PostgreSQL's `JSONB` operators or a GIN index to allow filtering events by metadata content.
- **Audit log**: A separate `session_status_changes` table to track who changed a session's status and when.
- **Soft delete**: Replace hard deletes with a `deleted_at` timestamp to support recovery and audit trails.

### Backend
- **Real user management**: Replace the seeded demo user with a full `users` CRUD and proper registration/password reset flows.
- **Role-based access control**: Distinguish between `VIEWER` and `ANALYST` roles (e.g. only analysts can delete sessions).
- **Rate limiting on all endpoints**: Extend Bucket4j limiting beyond the AI endpoint to prevent scraping or brute-force attacks.
- **Distributed tracing**: Add OpenTelemetry instrumentation for request tracing across services.
- **Metrics**: Expose Micrometer/Prometheus metrics (request latency, error rates, risk score distributions).
- **Async AI calls**: Move AI risk summary generation to an async job with a polling or WebSocket notification pattern.

### Frontend
- **Real-time updates**: Use Server-Sent Events or WebSocket to push new sessions to the list without polling.
- **Refresh token flow**: Implement silent token refresh so the user is not forced to re-login on page refresh.
- **Accessibility**: Audit the UI against WCAG 2.1 AA — status badges, modals, and timelines need proper ARIA attributes.
- **Error boundary**: Wrap views in React error boundaries to prevent a single API failure from crashing the whole app.

### Infrastructure
- **Horizontal scaling**: Replace the single Docker Compose setup with a Kubernetes manifest or a cloud deployment template.
- **Secrets management**: Replace environment variable secrets with Vault or AWS Secrets Manager.
- **CI/CD pipeline**: Add a GitHub Actions workflow that builds, tests, and publishes Docker images on merge to `main`.
