# FraudLens

Full-stack fraud session analysis application — Spring Boot backend, React frontend.

## API Documentation

Swagger UI is available at http://localhost:8080/swagger-ui/index.html once the backend is running.

---

## Design Decisions & Spec Deviations

### Session and Event IDs — UUID v4 instead of short identifiers

The PDF specification uses short, human-readable example IDs such as `"abc-123"` for sessions and `"evt_001"` for events. These are illustrative examples in the spec document, not a prescribed format.

This implementation generates **UUID v4 strings** (e.g. `"550e8400-e29b-41d4-a716-446655440000"`) for both `Session` and `Event` primary keys at the server side via `@PrePersist`.

**Rationale:**
- UUIDs are globally unique without coordination, making them safe for distributed environments and safe to expose in URLs.
- Short sequential-style IDs (`evt_001`) can be enumerated, enabling IDOR attacks if authorization checks are ever relaxed.
- The field type remains `String` in both the entity and the API, so path variables stay as `@PathVariable String id` — any valid string ID is accepted without a 400 error.

The behaviour visible to the client (a string ID in the response and path) is fully compatible with what the spec describes.

---

### JWT stored in memory (React context), not localStorage

The JWT is stored exclusively in React state and is lost on page refresh — the user must re-login. This is intentional: `localStorage` is vulnerable to XSS. In production a `HttpOnly` refresh-token cookie would solve the UX trade-off.

---

### Session timestamp — manual entry in UTC (Zulu time)

The `timestamp` field on a session represents when the session started and is required by the backend (`@NotBlank`). In a production system this would be set automatically by the server at session creation time.

In this implementation the timestamp is entered manually via the form using a `datetime-local` picker. The picker displays and accepts time in **UTC**, and the frontend converts the value to **ISO 8601 Zulu format** (e.g. `2028-11-02T10:20:11Z`) before sending it to the backend.

**Timezone note:** the `datetime-local` input has no timezone awareness — it stores a local wall-clock value. Because sessions originate from many countries, the entered time should always be treated as UTC regardless of the operator's browser locale. A production improvement would be to derive the expected UTC offset from the session's `country` field and display a converted local time alongside the UTC picker, so operators can cross-reference the session timestamp against the user's local time without manual conversion.
