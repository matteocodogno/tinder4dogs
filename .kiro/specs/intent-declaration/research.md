# Research & Design Decisions

---

## Summary
- **Feature**: `intent-declaration`
- **Discovery Scope**: Extension (new vertical slice in existing Spring Boot monolith)
- **Key Findings**:
  - No authentication or session management infrastructure exists yet; the design must define a session token mechanism and explicitly accommodate future JWT integration.
  - The existing codebase follows a strict vertical-slice pattern (`<feature>/presentation/service/model/`). The new `intent` module must conform to this pattern.
  - No external dependency additions are required: the feature is backed entirely by PostgreSQL (already in stack) and Spring Data JPA (already in use).

---

## Research Log

### Existing Authentication Infrastructure
- **Context**: Requirements assume an "authenticated owner" but no JWT/Spring Security exists.
- **Sources Consulted**: Codebase scan — `application.yaml`, all `*.kt` files.
- **Findings**:
  - No Spring Security dependency. No JWT filter chain. No `@PreAuthorize`.
  - Controllers currently accept all requests without identity enforcement.
  - Owner identity has no stable representation in the codebase (no `Owner` entity, no `User` entity).
- **Implications**:
  - Intent Declaration cannot bind sessions to a real authenticated principal yet.
  - Design decision: use an `X-Owner-Id: Long` request header as a placeholder, replaced by JWT extraction once auth is implemented. Enforce this via a note in the interface contract.

### Session Storage Options
- **Context**: Intent is ephemeral (2-hour TTL, not stored in permanent profile). Need a storage strategy.
- **Sources Consulted**: Codebase — `application.yaml` (PostgreSQL + JPA), `compose.yaml` pattern, steering/tech.md.
- **Findings**:
  - PostgreSQL is the only persistence layer available. No Redis, no in-memory cache.
  - `ddl-auto: update` — new tables are auto-created by Hibernate. Safe for new entities.
  - A scheduled cleanup job (Spring `@Scheduled`) can handle expired session GC.
- **Implications**:
  - Store intent sessions in a new `intent_sessions` table with an `expires_at` column.
  - No new infrastructure. Consistent with project constraint of no added complexity for MVP.

### Vertical Slice Module Pattern
- **Context**: Must follow the existing feature module convention.
- **Sources Consulted**: `support/` module, `ai/finetuning/` module, steering/structure.md.
- **Findings**:
  - Pattern: `com.ai4dev.tinderfordogs.<feature>/presentation/`, `/service/`, `/model/`
  - Controllers: `@RestController`, `@RequestMapping("/api/v1/...")`, `suspend` functions (coroutines), constructor injection.
  - DTOs: Kotlin `data class`.
  - Services: `@Service`, no field injection.
- **Implications**:
  - New module: `com.ai4dev.tinderfordogs.intent/`
  - Sub-packages: `presentation/`, `service/`, `model/`

### Analytics Event Strategy
- **Context**: Req 6 requires `session.started`, `session.activated`, `session.ended` events.
- **Sources Consulted**: Codebase — no event bus, no Kafka, no RabbitMQ; `kotlin-logging-jvm` is the only observability tool besides Langfuse.
- **Findings**:
  - No event bus is in the stack.
  - Structured logging via `KotlinLogging.logger {}` is used consistently.
  - Langfuse is used for AI prompt tracing only; not appropriate for domain events.
- **Implications**:
  - Analytics events are emitted as structured log entries (JSON key-value pairs) in MVP.
  - Session fields (`intent`, `ownerId`, `sessionId`, `swipeCount`) included in each log entry.
  - Clearly marked as `event.type = session.started | session.activated | session.ended`.
  - Future: replace with proper event bus without changing service contracts.

### Opposite-Sex Auto-Filter for Breeding
- **Context**: Q4 resolved — Breeding sessions automatically restrict to opposite-sex dogs.
- **Sources Consulted**: `DogMatcherService.kt` — `Dog.gender: String`.
- **Findings**:
  - `Dog` model has a `gender` field (String). No enum yet.
  - Matching logic currently in `DogMatcherService.calculateCompatibility` — no filter pipeline exists.
- **Implications**:
  - The Intent Session token carries `intent` + derived `sexFilter` (opposite of owner's dog sex).
  - `DogMatcherService` (or a future `SwipeFeedService`) must consume the session context when building the candidate pool.
  - Design must specify the session context payload to include `autoSexFilter: DogSex?`.

---

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| DB-backed session table | `intent_sessions` in PostgreSQL, UUID token | No new infra, consistent with stack, transactional | Requires periodic GC job for expired rows | Selected |
| Spring Session + Redis | Redis-backed HTTP session | Auto-expiry, fast lookups | Adds Redis as new infra dependency; violates MVP simplicity | Rejected |
| JWT-encoded intent token | Intent embedded in signed JWT | Stateless, no DB reads on validation | Requires JWT infra (not yet present); revocation impossible without store | Rejected for MVP |
| In-memory (ConcurrentHashMap) | Store sessions in JVM memory | Trivial implementation | Lost on restart; not cluster-safe | Rejected |

---

## Design Decisions

### Decision: PostgreSQL-backed session with UUID opaque token
- **Context**: Need ephemeral, TTL-bound sessions without adding Redis or JWT infrastructure.
- **Alternatives Considered**:
  1. Redis — adds infra
  2. JWT — needs signing key infra + no revocation
  3. In-memory — not durable across restarts
- **Selected Approach**: `intent_sessions` table with UUID `token` column. Token passed as `X-Intent-Session` header. Expiry enforced by `expires_at` column; scheduled cleanup job removes stale rows.
- **Rationale**: Zero new infrastructure, transactional consistency, aligns with `ddl-auto: update` pattern.
- **Trade-offs**: DB read on every swipe feed validation (acceptable for MVP at ≤ 10k DAU); adds cleanup job complexity.
- **Follow-up**: Add index on `(token, expires_at)` for fast lookup.

### Decision: Owner identity via `X-Owner-Id` placeholder header
- **Context**: No authentication infrastructure exists; owner identity must be established.
- **Alternatives Considered**:
  1. JWT principal extraction — requires Spring Security (not yet in stack)
  2. Query parameter — less idiomatic, more error-prone
- **Selected Approach**: `X-Owner-Id: Long` request header. Documented as a placeholder in the interface contract.
- **Rationale**: Minimal friction, easy to replace with `SecurityContextHolder.getContext().authentication.principal` once auth is added.
- **Trade-offs**: Not secure (no authentication enforcement); acceptable for internal MVP development phase.
- **Follow-up**: Replace with JWT extraction in the auth feature implementation.

### Decision: Analytics via structured logging (not event bus)
- **Context**: No event bus available; Req 6 requires distinct session lifecycle events.
- **Selected Approach**: `KotlinLogging.logger {}` emitting structured log entries with `event.type` key.
- **Rationale**: Consistent with existing observability approach; replaceable without interface changes.
- **Trade-offs**: Not queryable as time-series events; dependent on log aggregator (ELK/Loki) for analysis.

---

## Risks & Mitigations

- **Missing auth foundation** — `X-Owner-Id` header is unauthenticated. Mitigation: document explicitly as placeholder; enforce auth in a follow-up feature before production deployment.
- **Session table growth** — expired rows accumulate without GC. Mitigation: `@Scheduled` cleanup job runs every 30 minutes, deletes rows where `expires_at < NOW()`.
- **Concurrent session creation** — owner creates two sessions simultaneously. Mitigation: enforce unique constraint on `(owner_id, status = ACTIVE)` or invalidate the previous active session on new declaration.
- **Matching Service not yet built** — intent filtering contract is designed but consuming service is future work. Mitigation: design.md specifies the `IntentSessionService.getValidSession()` contract that the Matching Service will consume.

---

## References
- Steering: `.kiro/steering/tech.md` — Kotlin/Spring Boot patterns, no field injection, constructor injection preferred
- Steering: `.kiro/steering/structure.md` — vertical slice convention, `<feature>/presentation/service/model`
- PRD: `.kiro/steering/prd.md` — F-03 Intent Declaration, NFR-01 (location privacy), NFR-03 (GDPR session consent)
- Requirements: `.kiro/specs/intent-declaration/requirements.md` — all 6 requirements and resolved open questions
