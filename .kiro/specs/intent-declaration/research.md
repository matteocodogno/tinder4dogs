# Research & Design Decisions

---

## Summary
- **Feature**: `intent-declaration`
- **Discovery Scope**: Extension (new vertical slice in existing Spring Boot monolith)
- **Key Findings**:
  - No authentication or session management infrastructure exists yet; the design must define a session token mechanism and explicitly accommodate future JWT integration.
  - The existing codebase follows a strict vertical-slice pattern (`<feature>/presentation/service/model/`). The new `intent` module must conform to this pattern.
  - This feature introduces Liquibase (`liquibase-core`) as the first use of version-controlled schema migration; `ddl-auto` is changed from `update` to `validate`.
  - Analytics events are integrated with OpenTelemetry via the Micrometer Observation API bridge; session lifecycle events become OTEL span events and aggregate counts become OTEL metrics exported via OTLP.

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

### Session Storage Options

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| DB-backed session table | `intent_sessions` in PostgreSQL, UUID token | No new infra, consistent with stack, transactional | Requires periodic GC job for expired rows | **Selected** — see ADR-001 |
| Spring Session + Redis | Redis-backed HTTP session | Auto-expiry, fast lookups | Adds Redis as new infra dependency; violates MVP simplicity | Rejected |
| JWT-encoded intent token | Intent embedded in signed JWT | Stateless, no DB reads on validation | Requires JWT infra (not yet present); revocation impossible without store | Rejected for MVP |
| In-memory (ConcurrentHashMap) | Store sessions in JVM memory | Trivial implementation | Lost on restart; not cluster-safe | Rejected |

### Module Boundary Options

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| Vertical slice module (`intent/`) | New module inside monolith following existing pattern | Zero new infra; JVM-local calls; aligns with steering | Shared schema; scheduling contention possible | **Selected** |
| Domain-embedded (inside `DogMatcherService`) | Intent logic merged into existing service | Minimal files; single transaction scope | Violates SRP; guaranteed refactor debt when auth added; breaks `Presentation→Service` rule | Rejected |
| Separate microservice | Standalone HTTP service for session management | Independent scaling; clear ownership | Network hop per swipe feed request (consumes NFR-P2 budget); adds operational complexity disproportionate to MVP | Rejected |

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

### Decision: Liquibase for schema management (replaces ddl-auto: update)
- **Context**: `application.yaml` currently uses `spring.jpa.hibernate.ddl-auto: update`. This feature is the first to require a new table, making it the right time to introduce version-controlled schema migration.
- **Alternatives Considered**:
  1. Keep `ddl-auto: update` — lets Hibernate auto-create the table
  2. `ddl-auto: create-drop` — destructive, test-only
  3. Flyway — alternative migration tool
  4. Liquibase via Spring Boot Liquibase starter (`liquibase-core` on classpath)
- **Selected Approach**: `spring-boot-starter-liquibase` added to `pom.xml` with no explicit version (managed by the Spring Boot parent BOM at 4.0.2); Spring Boot auto-configures Liquibase. `ddl-auto` changed to `validate`. Changesets written in **Liquibase formatted SQL** (`.sql` files); master changelog at `src/main/resources/db/changelog/db.changelog-master.yaml` uses `includeAll` to pick up files from `changes/`.
- **Rationale**: `ddl-auto: update` is unsafe for production — it can silently drop columns or fail on non-trivial changes. Liquibase provides reproducible, reviewable, rollback-capable migrations. Spring Boot BOM manages the Liquibase version, so no explicit version pin is needed in pom.xml.
- **Trade-offs**: Adds a migration file to maintain per schema change; worth it for production safety.
- **Follow-up**: Tag the DB state before first deploy (`liquibase tag pre-intent`) to enable `rollback --tag` if needed.

### Decision: OpenTelemetry via Micrometer Observation API (replaces structured-logging-only approach)
- **Context**: Req 6 requires distinct session lifecycle events traceable in an analytics dashboard (NFR-O1, NFR-O2). Initial design used structured logs only; requirement updated to integrate with OpenTelemetry.
- **Alternatives Considered**:
  1. Structured logging only (`KotlinLogging`) — not queryable as time-series; no distributed tracing
  2. Direct OTEL SDK (`io.opentelemetry:opentelemetry-api`) — bypasses Micrometer; breaks Spring Boot auto-configuration
  3. **Micrometer Observation API + OTEL bridge** (`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`) — selected
- **Selected Approach**: `ObservationRegistry` injected into `IntentSessionService`. Each service method wrapped in an `Observation` (→ OTEL span). Lifecycle events (`session.started`, `session.activated`, `session.ended`) emitted as `Observation.Event` (→ OTEL span events). Session aggregate counts tracked via `MeterRegistry` counters/gauges (→ OTEL metrics). All signals exported via OTLP to an OTEL Collector.
- **Rationale**: Spring Boot 4 natively auto-configures `ObservationRegistry` and `MeterRegistry` when `spring-boot-starter-actuator` is present. The Micrometer bridge decouples application code from the OTEL SDK — the service layer only depends on `io.micrometer` interfaces, which are vendor-neutral.
- **Trade-offs**: Adds 3 new dependencies (`spring-boot-starter-actuator`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`), all BOM-managed. Requires an OTEL Collector in the deployment environment. Tests must use `TestObservationRegistry`/`SimpleMeterRegistry` to avoid coupling to the collector.
- **Cardinality rule**: `session.id` and `owner.id` are span **event** attributes, never top-level span attributes — prevents cardinality explosion in the tracing backend.
- **Follow-up**: Add `OTEL_EXPORTER_OTLP_ENDPOINT` to the deployment environment config; set `management.tracing.enabled=false` in the `test` profile.

### Decision: Analytics via structured logging (complement only)
- **Context**: Structured logs remain useful for WARN/ERROR signals and log-based alerting pipelines.
- **Selected Approach**: `KotlinLogging.logger {}` emitting WARN/ERROR entries; logs include `trace_id` and `span_id` injected by the Micrometer Tracing bridge for correlation.
- **Rationale**: Logs and traces are complementary; logs remain the primary signal for error alerting while OTEL spans carry the analytics payload.
- **Trade-offs**: None beyond what is already in the codebase.

---

## Risks & Mitigations

- **Missing auth foundation** — `X-Owner-Id` header is unauthenticated. Mitigation: document explicitly as placeholder; enforce auth in a follow-up feature before production deployment.
- **Session table growth** — expired rows accumulate without GC. Mitigation: `@Scheduled` cleanup job runs every 30 minutes, deletes rows where `expires_at < NOW()`.
- **Concurrent session creation** — owner creates two sessions simultaneously. Mitigation: enforce unique constraint on `(owner_id, status = ACTIVE)` or invalidate the previous active session on new declaration.
- **Matching Service not yet built** — intent filtering contract is designed but consuming service is future work. Mitigation: design.md specifies the `IntentSessionService.getValidSession()` contract that the Matching Service will consume.
- **OTEL Collector unavailable at startup** — OTLP export failure should not crash the application. Mitigation: `opentelemetry-exporter-otlp` is non-blocking by default; export errors are logged but not propagated; verify with `management.tracing.enabled=false` in `test` profile.
- **High cardinality in spans** — using `session.id` or `owner.id` as top-level span attributes would explode the cardinality of the tracing backend index. Mitigation: design enforces these as span event attributes only (see cardinality rule in design decisions).

---

## References
- Steering: `.kiro/steering/tech.md` — Kotlin/Spring Boot patterns, no field injection, constructor injection preferred
- Steering: `.kiro/steering/structure.md` — vertical slice convention, `<feature>/presentation/service/model`
- PRD: `.kiro/steering/prd.md` — F-03 Intent Declaration, NFR-01 (location privacy), NFR-03 (GDPR session consent)
- Requirements: `.kiro/specs/intent-declaration/requirements.md` — all 6 requirements and resolved open questions
