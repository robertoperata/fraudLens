# FraudLens — Remaining Spec Ambiguities

Issues that diverge from the PDF but are kept as deliberate design decisions.
Each must be documented as a trade-off in the README.

| # | Location in SPEC | What We Wrote | What the PDF Says | Decision / Rationale |
|---|---|---|---|---|
| 1 | §2.1 Session — `riskScore` | Included in the Session data model table as a computed field | Section 1 (data model) does not define `riskScore`. It is introduced in Section 4 as a separate feature. | Kept — placing it in the response alongside session fields is the most practical design. Marked clearly as "not persisted, derived from §4". README must note it is our addition. |
| 2 | §2.3 User entity | Full `users` table with `id`, `username`, `password` | PDF only says "a simple form that authenticates the user". No `User` entity is defined. | Kept — required to support proper JWT auth without hardcoded credentials. Noted in §2.3 as a design decision. README must explain the choice. |
| 3 | §3.5 `SessionSearchRequestDTO` fields | Filters: `status`, `country`, `userId`, `ip` | *"filtering by **any** field"* — `device`, timestamp range, and others are omitted | Kept as subset — scope reduction is pragmatic for a demo. Note added in §3.5. README must list which fields are excluded and why. |
| 4 | §2.1 `timestamp` — "defaults to creation time" | Implied default when field is omitted on `POST /sessions` | PDF shows a timestamp value in examples but does not specify server-side defaulting | Open assumption — reasonable for UX but not spec'd. If kept, the API contract should document it clearly. |
| 5 | §2.2 `durationMs` — "Not null" | Treated as required | PDF shows it in examples but does not state nullability or a minimum value | Open assumption — a `POST /sessions/:id/events` without `durationMs` would return 400. Consider making it optional if real clients may omit it. |
