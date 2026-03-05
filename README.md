# FraudLens

Full-stack fraud session analysis application — Spring Boot backend, React frontend.

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
