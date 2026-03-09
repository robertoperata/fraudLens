# FraudLens

Full-stack fraud session analysis — Spring Boot backend, React frontend, PostgreSQL.

---

## Table of Contents

1. [Running the Application](#1-running-the-application)
2. [Architecture & Design Decisions](#2-architecture--design-decisions)
3. [Risk Score Rules](#3-risk-score-rules)
4. [Security Considerations](#4-security-considerations)
5. [API Documentation](#5-api-documentation)
6. [What I Would Improve With More Time](#6-what-i-would-improve-with-more-time)

---

## 1. Running the Application

### Option 1 — Docker Compose (recommended)

The entire stack (PostgreSQL, Spring Boot backend, React frontend) starts with a single command.

**Prerequisites:** Docker Desktop (or Docker Engine + Compose plugin) installed and running.

**Step 1 — Create your environment file**

```bash
cp .env.example .env
```

**Step 2 — Fill in the required values**

Open `.env` and set at minimum:

| Variable | Required | Notes |
|---|---|---|
| `ANTHROPIC_API_KEY` | For AI Risk Summary | Get one at console.anthropic.com. Without it all other features still work; only `POST /sessions/{id}/risk-summary` returns 503. |
| `JWT_SECRET` | For production | Must be ≥ 32 characters. The default dev value is acceptable for local use only. |
| All others | No | Defaults in `.env.example` are correct for the Docker Compose setup. |

Example minimal `.env`:
```
ANTHROPIC_API_KEY=sk-ant-api03-...
JWT_SECRET=change-me-to-a-secure-256-bit-secret-at-least-32-chars
```

**Step 3 — Build and start**

```bash
docker compose up --build
```

Docker Compose automatically reads the `.env` file from the project root and injects the variables into the containers. No `export` or CLI flags needed.

**Step 4 — Open the app**

| Service | URL |
|---|---|
| Frontend (React) | http://localhost:3000 |
| Backend API (Swagger UI) | http://localhost:8080/swagger-ui.html |
| Health check | http://localhost:8080/actuator/health |

Default login credentials (from `.env.example`):
```
username: admin
password: admin123
```

**Stopping the stack**

```bash
docker compose down          # stop containers, keep DB data
docker compose down -v       # stop containers and delete DB volume
```

---

### Option 2 — Local Development (no Docker for backend/frontend)

Use this if you want live-reload for backend or frontend development.

**Prerequisites:** Java 21+, Maven 3.9+, Node.js 22+, a running PostgreSQL 16 instance.

**Step 1 — Start only the database**

```bash
docker compose up db -d
```

**Step 2 — Start the backend**

```bash
cd backend

export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fraudlens
export SPRING_DATASOURCE_USERNAME=fraudlens
export SPRING_DATASOURCE_PASSWORD=fraudlens
export JWT_SECRET=dev-secret-change-in-production-min-32chars
export ANTHROPIC_API_KEY=sk-ant-...        # optional
export CORS_ALLOWED_ORIGIN=http://localhost:5173
export APP_ADMIN_USERNAME=admin
export APP_ADMIN_PASSWORD=admin123

mvn spring-boot:run
```

Liquibase runs automatically on startup and creates the schema.

**Step 3 — Start the frontend**

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server starts on **http://localhost:5173** with hot module replacement.

| Service | URL |
|---|---|
| Frontend (Vite dev) | http://localhost:5173 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |

---

## 2. Architecture & Design Decisions

### Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3.6, Spring Security 6, Spring Data JPA |
| Database | PostgreSQL 16, Liquibase migrations |
| Mapping | MapStruct 1.6.3 |
| Auth | JJWT 0.12.x (HMAC-SHA256), BCrypt |
| AI | Spring AI 1.0.x, Anthropic Claude Haiku |
| Rate limiting | Bucket4j 8.x |
| Frontend | React 18, Vite, TypeScript, TanStack Query, Axios, Tailwind CSS |
| Containers | Docker Compose, nginx (frontend serving + API reverse proxy) |

---

### Data Layer

**PostgreSQL with Liquibase** — schema is owned entirely by Liquibase. `spring.jpa.hibernate.ddl-auto=validate` means Spring fails fast on startup if the entity model drifts from the database schema. This makes schema drift visible immediately rather than silently at runtime.

**Cascade delete enforced at two layers** — deleting a session removes all its events via both a JPA `CascadeType.ALL` + `orphanRemoval = true` relationship and a database-level `FOREIGN KEY ... ON DELETE CASCADE`. The ORM cascade is the application-layer convenience; the DB constraint is the safety net that holds even if someone bypasses the ORM (e.g. via a migration script or admin tool).

**Risk score is never persisted** — it is computed on every read from the session's attributes and events. This keeps the data model clean and avoids stale cached values, at the cost of a small computation per request. For this application's throughput the cost is negligible; at scale it could be memoised in Redis.

**JPA Specifications for search** — `POST /sessions/search` uses composable `Specification<Session>` predicates rather than hand-written JPQL. Each filter (status, country, userId, ip) is a null-safe predicate combined with `Specification.and()`. This avoids string concatenation in queries and is trivially extensible with new filters.

**Primary key type: `String`, value: UUID v4** — the `id` field on both `Session` and `Event` is declared as `String` in the JPA entity, not as `java.util.UUID`. This is a deliberate choice: keeping it `String` means path variables stay as `@PathVariable String id`, which accepts any string value without Spring throwing a `400 Bad Request` on non-UUID input. It also keeps the API contract flexible and matches the illustrative IDs (`"abc-123"`, `"evt_001"`) used in the spec examples.

The *value* assigned to that `String` field is a UUID v4 generated server-side via `@PrePersist` (e.g. `"550e8400-e29b-41d4-a716-446655440000"`). UUID v4 is preferred over sequential or short IDs because it is globally unique without coordination and resists enumeration-based IDOR attacks. A native `java.util.UUID` field type would have been the cleaner choice if we were certain IDs would always be UUIDs — `String` trades type safety for flexibility.

*Performance trade-off:* UUID v4 stored as `VARCHAR(36)` causes B-tree index fragmentation (random inserts rather than append) and carries more storage overhead than native `uuid` (36 bytes vs. 16) or `BIGINT` (8 bytes). At this application's scale this is acceptable. A production improvement would be UUIDv7 (time-ordered, monotonically increasing, negligible fragmentation) or a native `uuid` column with `gen_random_uuid()`.

---

### Backend Structure

**Layered architecture** — controllers delegate to services; services own business logic and transaction boundaries (`@Transactional` on service methods, never on controllers); repositories are plain Spring Data JPA interfaces.

**DTO separation with MapStruct** — JPA entities are never returned directly from controllers. All responses go through response DTOs (`SessionResponseDTO`, `EventResponseDTO`, etc.) mapped via MapStruct interfaces. This prevents accidental exposure of internal fields, makes the API contract explicit, and keeps entities free of serialization annotations.

**Global exception handler** — a single `@RestControllerAdvice` catches all exceptions and translates them to a consistent `ErrorResponseDTO` (timestamp, status, error, message, path). No raw exception messages, class names, or stack traces reach the client.

**Admin user seeding via `ApplicationRunner`** — credentials are read from environment variables at startup, BCrypt-encoded at runtime, and inserted only if the user does not already exist (idempotent). The password hash never appears in source control or migration files.

---

### AI Integration

The AI Risk Summary feature calls the **Anthropic Claude Haiku** model via **Spring AI 1.0.x** using the `ChatClient` fluent builder API. The prompt includes the session's country, device, status, computed risk score, and a chronological list of its events.

**Graceful degradation** — if the Anthropic API is unavailable or the key is not configured, the endpoint returns `503 Service Unavailable` with a meaningful message. The rest of the application is unaffected.

**Rate limiting** — the endpoint is limited to 10 requests per minute per authenticated user via Bucket4j, protecting both API quota and cost.

**Retry policy** — Spring AI's retry is scoped to transient errors only (`429 Too Many Requests`, `529 Overloaded`). Authentication failures (`401`) are not retried, preventing redundant calls with a bad key.

---

### Frontend Architecture

**TanStack Query** manages all server state — fetching, caching, and invalidation. After a mutation (create, update, delete) the relevant query cache is invalidated so the UI reflects the new state without a full page reload.

**JWT stored in React context (in-memory only)** — the token is held in React state and is lost on page refresh, requiring re-login. This is intentional: `localStorage` is vulnerable to XSS attacks. The UX trade-off is acceptable for this scope. In production, a `HttpOnly` refresh-token cookie would solve it.

**Axios interceptor** — a single request interceptor attaches the `Authorization: Bearer <token>` header to every outgoing call. A response interceptor catches `401` responses and dispatches a custom DOM event (`auth:unauthorized`) that `AuthProvider` listens for to clear the token and redirect to login.

**nginx reverse proxy** (Docker only) — in the Docker Compose setup, the frontend nginx container proxies all API paths (`/auth/`, `/sessions/`, `/actuator/`, etc.) to the backend service. This means `VITE_API_BASE_URL` is empty and Axios uses relative paths — the built image is portable and the browser never makes cross-origin requests, eliminating CORS complexity in production.

**Session timestamp — manual UTC entry** — the `timestamp` field is entered via a `datetime-local` picker and converted to ISO 8601 Zulu format before submission. In a production system this would be set server-side at session creation time. The known limitation is that the picker has no timezone awareness; operators must treat the value as UTC regardless of their browser locale.

---

## 3. Risk Score Rules

The risk score is a integer (0–100) computed server-side on every read request. It is never stored. The score starts at 0 and accumulates rule weights additively, capped at 100.

| Rule | Signal | Weight | Reasoning |
|---|---|---|---|
| **Unusual country** | Session `country` is outside the expected low-risk set | +15 | Anomalous origin is a common fraud signal; weighted lower than behavioral signals because VPNs make it unreliable alone |
| **Rapid login-then-submit** | `LOGIN_ATTEMPT` followed by `FORM_SUBMIT` within 5 seconds | +25 | Human users rarely read, fill, and submit a form this fast; highest single weight as it is a strong bot indicator |
| **Sensitive form fields** | `FORM_SUBMIT` event's `metadata.formFields` contains `card_number` or `cvv` | +20 | Indicates a high-value data exfiltration attempt regardless of other signals |
| **Multiple login attempts** | Session contains more than one `LOGIN_ATTEMPT` event | +15 | Credential stuffing or brute-force pattern |
| **Bot-speed submission** | Any event has `durationMs` < 500 ms | +10 | Interaction too fast to be human; lower weight because it is a supporting signal, not conclusive alone |
| **Dangerous status** | Session `status` is `DANGEROUS` | +10 | Analyst has confirmed this session as high-risk |
| **Suspicious status** | Session `status` is `SUSPICIOUS` | +5 | Analyst has flagged this session for review |

**Maximum score:** all rules firing simultaneously yields 15+25+20+15+10+10+5 = 100. In practice the `DANGEROUS` and `SUSPICIOUS` rules are mutually exclusive (a session has one status), so the realistic ceiling is 95. `Math.min(score, 100)` enforces the cap regardless.

**Design principles:** rules are stateless and deterministic — the same session and events always produce the same score. Every rule is a named constant in the implementation with a dedicated unit test. The algorithm is intentionally simple and auditable.

---

## 4. Security Considerations

### JWT Secret Management

**Concern:** A weak or hardcoded JWT signing secret allows an attacker to forge valid tokens offline.

**How addressed:**
- The secret is read exclusively from the `JWT_SECRET` environment variable. It is never hardcoded in source files or committed to version control.
- The application properties use a `${JWT_SECRET:dev-secret-change-in-production-min-32chars}` placeholder with a clearly labeled insecure default that is only acceptable for local development.
- `.env.example` documents the variable with no real value; `.env` is gitignored.
- Tokens are signed with HMAC-SHA256 (minimum 256-bit key) and expire after one hour.

---

### CORS — Restricted to Known Origin

**Concern:** An open CORS policy (`Access-Control-Allow-Origin: *`) allows any website to make authenticated requests on behalf of a logged-in user.

**How addressed:**
- The allowed origin is read from `CORS_ALLOWED_ORIGIN` and never wildcarded.
- In local development the default is `http://localhost:5173` (Vite dev server). In Docker Compose it is set to `http://localhost:3000` (nginx frontend).
- Only the methods and headers actually used by the frontend are permitted (`GET`, `POST`, `PUT`, `DELETE`, `OPTIONS` and `Authorization`, `Content-Type`).

---

### Input Validation and Error Sanitisation

**Concern:** Malformed request bodies can cause unexpected server behaviour, and raw exception messages can leak internal class names, stack traces, or database schema details.

**How addressed:**
- All request bodies are annotated with `@Valid` and validated by Spring's Bean Validation. Invalid requests return `400 Bad Request` with field-level error messages — no processing occurs.
- A single `@RestControllerAdvice` (`GlobalExceptionHandler`) catches every exception type. Raw exception messages and stack traces are logged internally but never serialised into the API response. The client receives only a structured `ErrorResponseDTO` with a generic message for unexpected errors.

---

### Rate Limiting on the AI Endpoint

**Concern:** The AI Risk Summary endpoint calls a paid external API. Without throttling, a single authenticated user could exhaust API quota or run up significant cost.

**How addressed:**
- Bucket4j enforces a limit of 10 requests per minute per authenticated user on `POST /sessions/{id}/risk-summary`.
- Requests exceeding the limit receive `429 Too Many Requests` before the AI service is called.
- Spring AI's retry policy is scoped to transient server errors (`429`, `529`) only, never to authentication failures, preventing redundant calls with a misconfigured key.

---

### Password Storage

**Concern:** Storing plaintext or weakly hashed passwords exposes all user credentials if the database is compromised.

**How addressed:**
- The admin password is read from an environment variable at startup and BCrypt-encoded (work factor 10) before being stored. The plaintext value never touches the database.
- The `users` table stores only the BCrypt hash. Even with full database read access, an attacker cannot recover the original password without an offline brute-force attack against a deliberately slow hash.

### Sensitive Data in Logs

**Concern:** `application.properties` sets `logging.level.com.fraudlens=DEBUG` for development convenience. At this level, application code in controllers and services can log request context that includes session attributes — IP addresses, user IDs, countries — and Spring's filter chain can surface `Authorization` header values containing the raw JWT token. If those logs are shipped to a centralised log aggregator without scrubbing, PII and bearer tokens are exposed to anyone with log read access.

**How addressed:**
- `spring.jpa.show-sql=false` prevents Hibernate from logging SQL statements with their bind parameter values (which would expose every field written to or read from the database).
- `GlobalExceptionHandler` logs the exception message and stack trace internally via `log.error(...)` but never writes request body content or user data to the log entry.
- The `DEBUG` level is intentional for local development only. In production the level must be raised by setting the environment variable `LOGGING_LEVEL_COM_FRAUDLENS=INFO`, which suppresses all application-level debug output without changing the JAR.

**Residual risk:** Spring Security's own filter-level logging at DEBUG can still print headers including `Authorization`. A production hardening step would be to add a `PatternLayout` or log filter that redacts `Authorization: Bearer .*` before log entries leave the process.

---

### No Rate Limiting on `POST /auth/login`

**Concern:** The login endpoint has no throttling. An attacker can submit unlimited password guesses against a known username without being blocked, making it vulnerable to online brute-force and credential-stuffing attacks.

**How addressed (partially):**
- Passwords are stored as BCrypt hashes with work factor 10. Each verification takes ~100 ms of deliberate computation, limiting an attacker to roughly 10 attempts per second per thread against the live endpoint — significantly slower than attacking a fast hash like MD5 or SHA-256.
- This is a deliberate scope reduction. Bucket4j is already in the project and rate-limits the AI endpoint; extending it to `/auth/login` (e.g. 5 attempts per minute per IP) is the immediate next step but was not implemented in this version.

**Residual risk:** BCrypt slows guessing but does not cap it. Without an IP-based or username-based attempt counter, a distributed attack from many IPs would not be detected or blocked. Account lockout and CAPTCHA are the production-grade mitigations.

---

## 5. API Documentation

Swagger UI is available at **http://localhost:8080/swagger-ui.html** once the backend is running. All endpoints are documented with `@Operation` and `@ApiResponse` annotations. JWT Bearer authentication can be configured directly in the Swagger UI to test protected endpoints.

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

### Testing
- **Performance tests**: If throughput or latency becomes a concern, add a JMeter test plan covering the main read paths (`GET /sessions`, `GET /sessions/{id}`) and the search endpoint (`POST /sessions/search`). Key metrics to baseline would be p95/p99 response time, requests per second at saturation, and error rate under load. This would also surface N+1 query issues and missing database indexes early.

### Infrastructure
- **Horizontal scaling**: Replace the single Docker Compose setup with a Kubernetes manifest or a cloud deployment template.
- **Secrets management**: Replace environment variable secrets with Vault or AWS Secrets Manager.
- **CI/CD pipeline**: Add a GitHub Actions workflow that builds, tests, and publishes Docker images on merge to `main`.
