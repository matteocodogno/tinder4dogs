# Design Document — Intent Declaration

## Overview

The Intent Declaration feature introduces a mandatory session-scoped intent selection step that must precede every swipe session on Tinder4Dogs. An owner declares a goal — **Playmate** or **Breeding** — which is bound to an ephemeral session token (2-hour idle TTL). The token gates access to the swipe feed and carries context used by the Matching Service to filter the candidate pool and adapt available search filters.

**Purpose**: Contextualise every search session by the owner's stated goal, ensuring match quality and 100% intent attribution in analytics.
**Users**: All registered owners (Personas: Marco — playmate seeker; Giulia — breeding owner) interact with this feature at the start of every session.
**Impact**: Introduces the `intent` domain module and the `intent_sessions` persistence table; adds a required header (`X-Intent-Session`) to the swipe feed API contract.

### Goals

- Enforce explicit intent declaration before each swipe session (Req 1, 5)
- Filter the profile candidate pool by declared intent; auto-apply opposite-sex filter for Breeding (Req 2)
- Adapt available search filters to the declared intent (Req 3)
- Ensure sessions are ephemeral and never stored on the permanent owner profile (Req 4)
- Track `session.started`, `session.activated`, `session.ended` analytics events (Req 6)

### Non-Goals

- Mid-session intent change (owner must close session and start new one)
- Permanent intent preference storage
- Push notification on session expiry
- Premium session features (multi-intent, extended TTL) — out of scope v1

---

## Architecture

### Existing Architecture Analysis

The codebase is a Spring Boot 4 monolith (Kotlin 2.2, Java 24) with a `service/` domain core and feature modules following a vertical slice pattern (`<feature>/presentation/service/model/`). No authentication or session management infrastructure exists. Persistence is PostgreSQL via Spring Data JPA. **This feature introduces Liquibase for schema management**; `spring.jpa.hibernate.ddl-auto` is changed from `update` to `validate` as part of this work. See `research.md` for full discovery notes.

**Constraints**:
- No Spring Security, no JWT infrastructure — owner identity is a placeholder (`X-Owner-Id` header) until an auth feature is implemented.
- No event bus — analytics events are emitted as structured log entries.
- Schema changes are managed exclusively via Liquibase changesets; Hibernate DDL auto-generation is disabled.

### Architecture Pattern & Boundary Map

```mermaid
graph TB
    subgraph Presentation
        IntentController[IntentController]
    end

    subgraph IntentModule
        IntentSessionService[IntentSessionService]
        IntentSessionRepository[IntentSessionRepository]
        IntentSessionCleanupJob[IntentSessionCleanupJob]
    end

    subgraph Domain
        DogMatcherService[DogMatcherService]
    end

    subgraph Persistence
        IntentSessionsTable[(intent_sessions)]
    end

    subgraph Observability
        ObservationRegistry[ObservationRegistry]
        MeterRegistry[MeterRegistry]
        OtelCollector[OTEL Collector]
        StructuredLogger[Structured Logger]
    end

    IntentController --> IntentSessionService
    IntentSessionService --> IntentSessionRepository
    IntentSessionRepository --> IntentSessionsTable
    IntentSessionCleanupJob --> IntentSessionRepository
    IntentSessionService --> ObservationRegistry
    IntentSessionService --> MeterRegistry
    IntentSessionService --> StructuredLogger
    ObservationRegistry --> OtelCollector
    MeterRegistry --> OtelCollector
    DogMatcherService -.->|consumes session context| IntentSessionService
```

**Architecture Integration**:
- Selected pattern: Vertical slice module `intent/presentation/service/model/` — consistent with `support/` and `ai/finetuning/` modules.
- Domain boundary: `intent` module owns session lifecycle; `DogMatcherService` (and future `SwipeFeedService`) consumes it as a dependency, not the reverse.
- Existing patterns preserved: constructor injection, `@RestController`/`@Service` stereotypes, Kotlin `data class` DTOs, `suspend` coroutine functions in controllers.
- New components: `IntentController`, `IntentSessionService`, `IntentSessionRepository` (Spring Data JPA), `IntentSessionCleanupJob` (`@Scheduled`), `IntentSession` (JPA entity).
- Steering compliance: no field injection, `presentation → service` dependency only, AI isolation respected.

### Technology Stack

| Layer | Choice / Version | Role in Feature | Notes |
|-------|------------------|-----------------|-------|
| Backend | Spring Boot 4.0.2 / Kotlin 2.2 | REST controllers, service layer, scheduling | Existing — no change |
| Data | PostgreSQL + Spring Data JPA | Persist `intent_sessions` table | Existing — new entity only |
| Migration | `spring-boot-starter-liquibase` (Spring Boot BOM) | Version-controlled schema migration for `intent_sessions` | **New dependency** — version managed by Spring Boot parent BOM; `ddl-auto` set to `validate` |
| Scheduling | Spring `@Scheduled` | Cleanup expired sessions every 30 min | Built-in — no new dependency |
| Observability — Tracing | `micrometer-tracing-bridge-otel` (Spring Boot BOM) | Bridges Micrometer `ObservationRegistry` to OTEL SDK; instruments service methods as spans | **New dependency** |
| Observability — Export | `opentelemetry-exporter-otlp` (Spring Boot BOM) | Exports spans and metrics to an OTEL Collector via OTLP/gRPC | **New dependency** |
| Observability — Actuator | `spring-boot-starter-actuator` (Spring Boot BOM) | Activates Micrometer auto-configuration (`ObservationRegistry`, `MeterRegistry`) | **New dependency** |
| Observability — Logging | kotlin-logging-jvm | Structured log complement; WARN/ERROR signals only | Existing |
| Auth (placeholder) | `X-Owner-Id` header | Owner identity until JWT auth is added | Replaced by JWT in future auth feature |

---

## System Flows

### Intent Declaration & Swipe Feed Access

```mermaid
sequenceDiagram
    participant Owner
    participant IntentController
    participant IntentSessionService
    participant DB as intent_sessions
    participant SwipeFeed as SwipeFeedService

    Owner->>IntentController: POST /api/v1/intent-sessions (intent, X-Owner-Id)
    IntentController->>IntentSessionService: declareIntent(ownerId, intent, dogSex)
    IntentSessionService->>DB: invalidate previous ACTIVE session for ownerId
    IntentSessionService->>DB: insert new session (token, intent, autoSexFilter, expiresAt)
    IntentSessionService-->>IntentController: IntentSessionResponse(token, expiresAt)
    IntentController-->>Owner: 201 Created (token, expiresAt)

    Owner->>SwipeFeed: GET /api/v1/swipe/feed (X-Intent-Session: token)
    SwipeFeed->>IntentSessionService: getValidSession(token)
    IntentSessionService->>DB: SELECT WHERE token = ? AND expires_at > NOW()
    alt valid session
        IntentSessionService-->>SwipeFeed: IntentSessionContext(intent, autoSexFilter, ownerId)
        SwipeFeed-->>Owner: 200 OK (filtered profiles)
    else missing or expired
        IntentSessionService-->>SwipeFeed: SessionNotFoundException
        SwipeFeed-->>Owner: 403 Forbidden (redirect to intent selection)
    end
```

### Session Expiry & Cleanup

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: POST intent-sessions
    ACTIVE --> EXPIRED: idle > 2 hours
    ACTIVE --> CLOSED: DELETE intent-sessions/sessionId
    EXPIRED --> [*]: cleanup job removes row
    CLOSED --> [*]: cleanup job removes row
```

**Key Decisions**: A new `declareIntent` call automatically invalidates the owner's previous `ACTIVE` session before creating a new one (prevents orphaned active sessions). The cleanup job runs every 30 minutes on `status IN (EXPIRED, CLOSED)` rows older than 1 hour.

---

## Requirements Traceability

| Requirement | Summary | Components | Interfaces | Flows |
|-------------|---------|------------|------------|-------|
| 1.1–1.6 | Intent selection UI & session creation | IntentController, IntentSessionService | `POST /api/v1/intent-sessions`, `declareIntent()` | Declaration flow |
| 2.1–2.5 | Profile pool filtered by intent + auto sex filter | IntentSessionService, DogMatcherService | `getValidSession()`, `IntentSessionContext` | Feed access flow |
| 3.1–3.5 | Filter options adapt to intent | IntentSessionService | `getValidSession()` → `availableFilters` | Feed access flow |
| 4.1–4.6 | Session ephemeral, 2h TTL, no persistence to profile | IntentSessionService, IntentSessionCleanupJob | `closeSession()`, `@Scheduled` cleanup | Expiry flow |
| 5.1–5.4 | 100% swipe feed requests require valid token | IntentSessionService | `getValidSession()` (throws on invalid) | Feed access flow |
| 6.1–6.5 | Analytics: started / activated / ended events | IntentSessionService | Structured log events | All flows |

---

## Components and Interfaces

### Component Summary

| Component | Layer | Intent | Req Coverage | Key Dependencies | Contracts |
|-----------|-------|--------|--------------|-----------------|-----------|
| IntentController | Presentation | REST entry point for session lifecycle | 1.1–1.6, 4.6 | IntentSessionService (P0) | API |
| IntentSessionService | Service | Core session lifecycle: create, validate, close, expire | All | IntentSessionRepository (P0), Logger (P1) | Service, Event |
| IntentSessionRepository | Persistence | JPA CRUD + queries on `intent_sessions` | 4.1–4.6 | PostgreSQL (P0) | — |
| IntentSessionCleanupJob | Scheduler | Removes expired/closed sessions every 30 min | 4.2–4.3 | IntentSessionRepository (P0) | Batch |
| IntentSession | Domain Model | JPA entity representing one session | All | — | State |

---

### Presentation Layer

#### IntentController

| Field | Detail |
|-------|--------|
| Intent | Accept intent declaration, return session token; accept session close request |
| Requirements | 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 4.6 |

**Responsibilities & Constraints**
- Accepts `POST /api/v1/intent-sessions` to declare intent; returns opaque session token.
- Accepts `DELETE /api/v1/intent-sessions/{sessionId}` to explicitly close a session.
- Reads `X-Owner-Id` header (Long) as owner identity placeholder — replace with JWT extraction when auth is implemented.
- Delegates all business logic to `IntentSessionService`; no direct repository access.

**Dependencies**
- Outbound: `IntentSessionService` — session lifecycle (P0)

**Contracts**: API [x]

##### API Contract

| Method | Endpoint | Request | Response | Errors |
|--------|----------|---------|----------|--------|
| POST | `/api/v1/intent-sessions` | `DeclareIntentRequest` | `IntentSessionResponse` (201) | 400 (invalid intent), 422 (dog sex missing for Breeding) |
| DELETE | `/api/v1/intent-sessions/{sessionId}` | — | 204 No Content | 404 (session not found or already closed) |

```kotlin
// Request / Response DTOs (Kotlin data classes)

data class DeclareIntentRequest(
    val intent: SearchIntent,      // enum: PLAYMATE | BREEDING
    val ownerDogSex: DogSex?       // required when intent == BREEDING; null allowed for PLAYMATE
)

data class IntentSessionResponse(
    val sessionId: String,         // UUID
    val token: String,             // UUID opaque token for X-Intent-Session header
    val intent: SearchIntent,
    val autoSexFilter: DogSex?,    // non-null only when intent == BREEDING
    val expiresAt: java.time.Instant
)
```

**Implementation Notes**
- Validation: `DeclareIntentRequest` validated with JSR-303 (`@NotNull intent`); service validates `ownerDogSex` for BREEDING intent.
- Integration: Add `@Valid` on `@RequestBody`; return `ResponseEntity<IntentSessionResponse>` with status 201.
- Risks: Without real auth, any caller can impersonate an owner via `X-Owner-Id`. Acceptable in MVP development phase only.

---

### Service Layer

#### IntentSessionService

| Field | Detail |
|-------|--------|
| Intent | Orchestrate session lifecycle: create, validate (token gate), close, and emit analytics events |
| Requirements | 1.1–1.6, 2.3, 3.1–3.5, 4.1–4.6, 5.1–5.4, 6.1–6.5 |

**Responsibilities & Constraints**
- Creates a new `IntentSession`, invalidating any previous `ACTIVE` session for the same `ownerId`.
- Validates a token on every swipe feed call: returns `IntentSessionContext` or throws `SessionNotFoundException` / `SessionExpiredException`.
- Derives `autoSexFilter` (opposite sex) automatically when intent is BREEDING.
- Wraps each public method in a Micrometer `Observation` (becomes an OTEL span via the tracing bridge); emits `session.started`, `session.activated`, `session.ended` as **OTEL span events** on the active span.
- Increments Micrometer counters/gauges for session aggregate metrics (see Event Contract).
- Does not touch the owner's permanent profile — no write to any owner/dog table.
- Transaction boundary: each public method is a single transaction (`@Transactional`).

**Dependencies**
- Outbound: `IntentSessionRepository` — persistence (P0)
- Outbound: `ObservationRegistry` (Micrometer) — span creation and span event emission (P0)
- Outbound: `MeterRegistry` (Micrometer) — session aggregate metrics (P1)
- Outbound: `KotlinLogging.logger {}` — WARN/ERROR log complement (P2)

**Contracts**: Service [x] / Event [x]

##### Service Interface

```kotlin
interface IntentSessionService {

    /**
     * Declare intent for a new search session.
     * Invalidates the previous ACTIVE session for this owner (if any).
     * Preconditions: ownerId must be a known owner; intent must be non-null;
     *   ownerDogSex must be non-null when intent == BREEDING.
     * Postconditions: returns a new ACTIVE session with a fresh token and expiresAt = now + 2h.
     */
    fun declareIntent(
        ownerId: Long,
        intent: SearchIntent,
        ownerDogSex: DogSex?
    ): IntentSessionResponse

    /**
     * Validate a session token and return the session context.
     * Preconditions: token is non-blank.
     * Postconditions: returns IntentSessionContext for a valid, non-expired session.
     * Throws: SessionNotFoundException if token unknown; SessionExpiredException if TTL elapsed.
     */
    fun getValidSession(token: String): IntentSessionContext

    /**
     * Record the first swipe in a session (triggers session.activated event).
     * Idempotent: subsequent calls for already-activated sessions are no-ops.
     */
    fun recordFirstSwipe(token: String)

    /**
     * Explicitly close a session (owner-initiated).
     * Postconditions: session status set to CLOSED; session.ended event emitted.
     * Throws: SessionNotFoundException if sessionId not found for ownerId.
     */
    fun closeSession(ownerId: Long, sessionId: String)
}
```

**Preconditions**:
- `declareIntent`: `ownerId > 0`; `intent` non-null; `ownerDogSex` non-null when `intent == BREEDING`
- `getValidSession`: `token` non-blank

**Postconditions**:
- After `declareIntent`: previous ACTIVE session for `ownerId` is CLOSED; new session row exists with `status = ACTIVE` and `expiresAt = Instant.now() + 2h`
- After `getValidSession`: session `lastActivityAt` updated (resets idle window)

**Invariants**:
- At most one `ACTIVE` session per `ownerId` at any time
- `autoSexFilter` is always null for PLAYMATE sessions and always non-null for BREEDING sessions

##### Event Contract

Analytics signals are emitted via the Micrometer Observation API, exported to the OTEL Collector via OTLP. Three categories of signals:

**OTEL Spans** — one span per service operation, carrying low-cardinality attributes:

| Span name | Triggered by | Attributes |
|-----------|-------------|------------|
| `intent.session.declare` | `declareIntent()` | `intent`, `owner.id` |
| `intent.session.validate` | `getValidSession()` | `session.id` |
| `intent.session.close` | `closeSession()` | `session.id`, `reason` |
| `intent.session.cleanup` | `IntentSessionCleanupJob` | `deleted.count` |

**OTEL Span Events** — attached to the active span, carry high-cardinality context:

| Event name | Attached to span | Attributes |
|------------|-----------------|------------|
| `session.started` | `intent.session.declare` | `session.id`, `intent`, `auto_sex_filter` (nullable), `expires_at` |
| `session.activated` | `intent.session.validate` (first swipe) | `session.id`, `intent` |
| `session.ended` | `intent.session.close` or `intent.session.cleanup` | `session.id`, `intent`, `swipe_count`, `reason` |

**Micrometer Metrics** (exported as OTEL metrics):

| Metric name | Type | Labels | Description |
|-------------|------|--------|-------------|
| `intent.session.started.total` | Counter | `intent` | Sessions declared |
| `intent.session.activated.total` | Counter | `intent` | Sessions with ≥ 1 swipe |
| `intent.session.ended.total` | Counter | `intent`, `reason` | Sessions closed or expired |
| `intent.session.active` | Gauge | — | Current count of `ACTIVE` sessions (queried from DB) |
| `intent.session.validation.errors.total` | Counter | `error_type` (`NOT_FOUND`, `EXPIRED`) | Token validation failures |

- Delivery: synchronous in-process; exported asynchronously to OTEL Collector via OTLP/gRPC (non-blocking).
- Attribute cardinality: `session.id` and `owner.id` are span event attributes only — never span-level attributes (avoid high-cardinality span key explosion).

**Implementation Notes**
- Integration: `DogMatcherService` / `SwipeFeedService` calls `getValidSession(token)` and uses `IntentSessionContext.intent` and `IntentSessionContext.autoSexFilter` to build the candidate query.
- Validation: Reject BREEDING sessions where `ownerDogSex` is null with `IllegalArgumentException` before persisting.
- Risks: `getValidSession` is called on every swipe feed request — ensure `(token, expires_at)` index exists on `intent_sessions`.

---

### Persistence Layer

#### IntentSessionRepository

Spring Data JPA repository — no custom logic beyond standard CRUD and two named queries:

```kotlin
interface IntentSessionRepository : JpaRepository<IntentSession, Long> {

    fun findByTokenAndExpiresAtAfter(token: String, now: Instant): IntentSession?

    fun findByOwnerIdAndStatus(ownerId: Long, status: SessionStatus): IntentSession?

    @Modifying
    @Query("DELETE FROM IntentSession s WHERE s.status IN :statuses AND s.expiresAt < :cutoff")
    fun deleteExpired(statuses: List<SessionStatus>, cutoff: Instant): Int
}
```

---

### Scheduler

#### IntentSessionCleanupJob

| Field | Detail |
|-------|--------|
| Intent | Periodically remove expired and closed session rows from `intent_sessions` |
| Requirements | 4.2, 4.3 |

**Contracts**: Batch [x]

##### Batch / Job Contract
- **Trigger**: `@Scheduled(fixedDelay = 1800000)` — every 30 minutes
- **Input / validation**: Queries rows where `status IN (EXPIRED, CLOSED)` AND `expires_at < NOW() - 1 hour` (1-hour grace period)
- **Output / destination**: Deleted rows; count logged at INFO level
- **Idempotency & recovery**: Deletion is idempotent; safe to run multiple times

---

## Data Models

### Domain Model

**Aggregates**:
- `IntentSession` — root aggregate. Owns the session lifecycle. No sub-entities. Invariant: one `ACTIVE` session per owner.

**Value Objects**:
- `SearchIntent` — enum: `PLAYMATE`, `BREEDING`
- `DogSex` — enum: `MALE`, `FEMALE`
- `SessionStatus` — enum: `ACTIVE`, `CLOSED`, `EXPIRED`
- `IntentSessionContext` — read-only value object returned to consumers; carries `intent`, `autoSexFilter`, `ownerId`

**Domain Events** (emitted as structured log entries):
- `SessionStarted`, `SessionActivated`, `SessionEnded`

### Logical Data Model

**`IntentSession` entity**:

| Field | Type | Notes |
|-------|------|-------|
| `id` | Long (PK, auto) | Internal surrogate key |
| `sessionId` | UUID (unique, not null) | Stable public identifier |
| `token` | UUID (unique, not null) | Opaque access token for header |
| `ownerId` | Long (not null) | Foreign key placeholder — no FK constraint until Owner entity exists |
| `intent` | VARCHAR(20) (not null) | `PLAYMATE` or `BREEDING` |
| `autoSexFilter` | VARCHAR(10) (nullable) | `MALE`, `FEMALE`, or null |
| `status` | VARCHAR(10) (not null) | `ACTIVE`, `CLOSED`, `EXPIRED` |
| `swipeCount` | INT (not null, default 0) | Incremented on `recordFirstSwipe` (capped at 1 for activation tracking) |
| `activated` | BOOLEAN (not null, default false) | True after first swipe |
| `createdAt` | TIMESTAMP WITH TIME ZONE | Session creation time |
| `lastActivityAt` | TIMESTAMP WITH TIME ZONE | Updated on `getValidSession` to reset idle window |
| `expiresAt` | TIMESTAMP WITH TIME ZONE | `createdAt + 2h`; refreshed on activity |

**Consistency & Integrity**:
- Unique constraint on `(owner_id, status = 'ACTIVE')` — enforced at service layer via invalidation before insert (application-level enforcement; DB-level partial unique index for PostgreSQL: `CREATE UNIQUE INDEX ON intent_sessions (owner_id) WHERE status = 'ACTIVE'`).
- No FK to owner table until `Owner` entity is created.

### Physical Data Model

Schema is managed exclusively via Liquibase. The changelog master file is `src/main/resources/db/changelog/db.changelog-master.yaml`, which includes changesets from `changes/`.

Changesets are written in **Liquibase formatted SQL** (`.sql` files with special comment headers). The master changelog (`db.changelog-master.yaml`) includes them by directory scan.

**`src/main/resources/db/changelog/db.changelog-master.yaml`**:
```yaml
databaseChangeLog:
  - includeAll:
      path: db/changelog/changes/
```

**`src/main/resources/db/changelog/changes/001-create-intent-sessions.sql`**:

```sql
--liquibase formatted sql

--changeset tinder4dogs:001-create-intent-sessions
CREATE TABLE intent_sessions
(
    id               BIGSERIAL PRIMARY KEY,
    session_id       UUID        NOT NULL UNIQUE,
    token            UUID        NOT NULL UNIQUE,
    owner_id         BIGINT      NOT NULL,
    intent           VARCHAR(20) NOT NULL,
    auto_sex_filter  VARCHAR(10),
    status           VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    swipe_count      INT         NOT NULL DEFAULT 0,
    activated        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ NOT NULL
);

-- Primary lookup on swipe feed validation (NFR-P2: p95 < 50ms)
CREATE INDEX idx_intent_sessions_token_expires
    ON intent_sessions (token, expires_at);

-- Cleanup job query
CREATE INDEX idx_intent_sessions_status_expires
    ON intent_sessions (status, expires_at);

--rollback DROP TABLE intent_sessions;

--changeset tinder4dogs:001b-partial-unique-index-owner-active dbms:postgresql
-- One ACTIVE session per owner enforced at DB level
CREATE UNIQUE INDEX idx_intent_sessions_owner_active
    ON intent_sessions (owner_id)
    WHERE status = 'ACTIVE';

--rollback DROP INDEX IF EXISTS idx_intent_sessions_owner_active;
```

> The partial unique index uses a `WHERE` clause which requires a raw SQL changeset in any Liquibase format. The `dbms:postgresql` runAttribute scopes it to PostgreSQL only.

**`application.yaml` changes required by this feature**:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate       # changed from 'update' — Liquibase owns schema
  liquibase:
    change-log: classpath:/db/changelog/db.changelog-master.yaml
    enabled: true

management:
  tracing:
    sampling:
      probability: 1.0         # 100% sampling in dev/test; tune for production
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
    metrics:
      export:
        url: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/metrics}
  metrics:
    distribution:
      percentiles-histogram:
        intent.session.validate: true   # enables p95 histogram for NFR-P2
```

> `OTEL_EXPORTER_OTLP_ENDPOINT` is an environment variable; the default points to a local OTEL Collector. Override per environment. The `test` profile should set `management.tracing.enabled: false` to avoid coupling tests to a collector.

### Data Contracts & Integration

**`IntentSessionContext`** — read-only DTO consumed by `DogMatcherService` / `SwipeFeedService`:

```kotlin
data class IntentSessionContext(
    val sessionId: String,
    val ownerId: Long,
    val intent: SearchIntent,
    val autoSexFilter: DogSex?,          // null for PLAYMATE; opposite sex for BREEDING
    val availableFilters: Set<FilterType> // derived from intent; see Req 3
)

enum class FilterType {
    BREED, SIZE, AGE, ENERGY_LEVEL, TEMPERAMENT,   // PLAYMATE
    PEDIGREE, HEALTH_CRITERIA                        // BREEDING only
    // SEX is intentionally omitted — auto-applied for BREEDING
}
```

---

## Error Handling

### Error Strategy

Validate at the boundary (controller + service entry points). Fail fast with actionable HTTP responses. No silent degradation for session validation failures.

### Error Categories and Responses

| Scenario | HTTP Status | Response Body | Req |
|----------|-------------|---------------|-----|
| Missing or blank intent in request | 400 Bad Request | `{"error": "intent is required"}` | 1.4 |
| Invalid intent value | 400 Bad Request | `{"error": "intent must be PLAYMATE or BREEDING"}` | 1.4 |
| BREEDING declared without ownerDogSex | 422 Unprocessable Entity | `{"error": "ownerDogSex is required for BREEDING intent"}` | 2.3 |
| Swipe feed accessed without session token | 403 Forbidden | `{"error": "intent session required", "redirectTo": "/intent-sessions/new"}` | 5.1, 5.4 |
| Session token not found | 403 Forbidden | Same as above | 5.1 |
| Session token expired (2h TTL) | 403 Forbidden | Same as above | 4.4, 5.4 |
| Session not found on close | 404 Not Found | `{"error": "session not found"}` | — |

### Monitoring

- **Traces**: Every service method produces an OTEL span exported to the Collector. Trace IDs are propagated via W3C `traceparent` header on inbound HTTP requests.
- **Metrics**: `intent.session.active` gauge and `intent.session.validation.errors.total` counter are the primary alerting signals (NFR-O1, NFR-O2).
- **Logs (complement only)**: WARN on expired token presented; WARN when cleanup deletes > 1000 rows; ERROR on unexpected exceptions. Structured logs include `trace_id` and `span_id` fields (injected by Micrometer Tracing bridge) for correlation with spans.

---

## Testing Strategy

### Unit Tests

- `IntentSessionService.declareIntent`: verify session created with correct `intent`, `autoSexFilter`, `expiresAt`; verify previous ACTIVE session is invalidated.
- `IntentSessionService.declareIntent` — BREEDING without `ownerDogSex`: verify `IllegalArgumentException` thrown.
- `IntentSessionService.getValidSession` — expired token: verify `SessionExpiredException`.
- `IntentSessionService.getValidSession` — unknown token: verify `SessionNotFoundException`.
- `IntentSessionService.recordFirstSwipe` — idempotency: second call does not re-emit `session.activated` span event.
- `autoSexFilter` derivation: `MALE` dog owner → filter `FEMALE`; `FEMALE` dog owner → filter `MALE`.
- **OTEL**: use `TestObservationRegistry` (Micrometer test support) to assert that `intent.session.declare` span is created and `session.started` span event is present on `declareIntent`; assert `session.activated` span event on first `recordFirstSwipe`.
- **Metrics**: use `SimpleMeterRegistry` to assert `intent.session.started.total` counter increments by 1 on `declareIntent`; assert `intent.session.validation.errors.total{error_type=EXPIRED}` increments on expired token.

### Integration Tests

- `POST /api/v1/intent-sessions` → 201 with valid token and `expiresAt` (2h from now ±5s).
- `POST /api/v1/intent-sessions` twice for the same owner → second call succeeds; first session status becomes `CLOSED`.
- `GET /api/v1/swipe/feed` without `X-Intent-Session` header → 403.
- `GET /api/v1/swipe/feed` with expired token → 403.
- `DELETE /api/v1/intent-sessions/{sessionId}` → 204; subsequent `getValidSession` throws.
- `IntentSessionCleanupJob` removes only `EXPIRED`/`CLOSED` rows older than grace period; `ACTIVE` rows untouched.
- **OTEL**: `@SpringBootTest` with `management.tracing.enabled=false` to disable OTLP export; verify span names and metric counts via `TestObservationRegistry` and `SimpleMeterRegistry` beans.

### Performance Tests

- `getValidSession` under 1000 concurrent requests: p95 < 50ms (NFR-P2); requires `(token, expires_at)` index. Verify p95 histogram via `intent.session.validate` Micrometer distribution metric.
- `declareIntent` throughput: sustain 100 req/s without contention on the partial unique index.

---

## Security Considerations

- **Session isolation** (NFR-S1): `getValidSession` only returns data for the token presented; no cross-owner data exposure. Verified by integration test asserting token A cannot retrieve session B's context.
- **Server-side validation** (NFR-S2): token validated on every call to `getValidSession`; no client-side trust.
- **Intent data privacy**: `intent_sessions` table holds `ownerId` (Long) and intent; no PII beyond the owner identifier. Session rows deleted on close or expiry (Req 4.2) — supports GDPR right-to-erasure chain (NFR-03).
- **Auth placeholder risk**: `X-Owner-Id` is unauthenticated in MVP. Mitigation: deploy behind VPN/internal network only until the auth feature is implemented.

---

## Migration Strategy

This feature is the first to introduce Liquibase. The migration scope is greenfield (no pre-existing `intent_sessions` rows), so no data migration is needed — only schema creation.

```mermaid
flowchart LR
    A[Add liquibase-core to pom.xml] --> B[Set ddl-auto to validate]
    B --> C[Create db.changelog-master.yaml]
    C --> D[Create changeset 001-create-intent-sessions]
    D --> E[Run mvn test — Liquibase applies changelog on test DB]
    E --> F{Schema valid?}
    F -- yes --> G[Deploy]
    F -- no --> H[Fix changeset and re-run]
```

**Rollback trigger**: If startup validation fails (`ddl-auto: validate` throws `SchemaManagementException`), the rollback is the `rollback` block in changeset `001` (`DROP TABLE intent_sessions`). Run `liquibase rollback --tag=pre-intent` after tagging before deploy.

**Changeset authoring rules** (to be followed by all future features):
- Use **Liquibase formatted SQL** (`.sql` files with `--liquibase formatted sql` header).
- One logical change per `--changeset` block; never modify a deployed changeset.
- Always provide a `--rollback` line immediately after the changeset body.
- Use `dbms:postgresql` on the `--changeset` line for PostgreSQL-specific DDL (partial indexes, `BIGSERIAL`, etc.).
- File naming: `NNN-description.sql` under `src/main/resources/db/changelog/changes/`; master includes via `includeAll`.

---

## Performance & Scalability

- **Target**: `getValidSession` p95 < 50ms; intent selection screen render < 200ms (NFR-P1, NFR-P2).
- **Index**: `(token, expires_at)` covering index is the critical optimization — eliminates full table scans on the hot path.
- **Scale**: At 10k DAU (NFR-SC1), peak concurrent active sessions ≈ 2k–5k rows. PostgreSQL handles this comfortably without partitioning.
- **Cleanup**: 30-minute cleanup job prevents unbounded table growth; row count stays bounded to `2h_window × peak_session_rate`.

---

## Corner Cases

### Input boundary cases

| Scenario | Expected Behaviour | Requirement |
|----------|--------------------|-------------|
| `intent` field missing in `POST /api/v1/intent-sessions` | 400 Bad Request: `"intent is required"` | 1.4 |
| `intent` value outside `{PLAYMATE, BREEDING}` | 400 Bad Request: `"intent must be PLAYMATE or BREEDING"` | 1.4 |
| `ownerDogSex` null when `intent = BREEDING` | 422 Unprocessable Entity | 2.3 |
| `X-Owner-Id` header missing | 400 Bad Request — controller cannot determine owner | 1.2 |
| `X-Owner-Id` = 0 or negative | `IllegalArgumentException` in service → 400 Bad Request | 1.2 |
| `X-Intent-Session` header present but empty string | 403 Forbidden — treated as missing token | 5.1 |
| `sessionId` path variable in `DELETE` not a valid UUID | 404 Not Found — no match in DB | 4.1 |
| Concurrent `POST /api/v1/intent-sessions` from same owner (race condition) | One succeeds; the other hits partial unique index violation (`23505`) → `DataIntegrityViolationException` → 409 Conflict | 1.2 |

### State & timing edge cases

| Scenario | Expected Behaviour | Requirement |
|----------|--------------------|-------------|
| `getValidSession` called at exact millisecond of `expires_at` | Session expired — query uses `expires_at > NOW()` (strict); returns 403 Forbidden | 4.3, 5.4 |
| `lastActivityAt` update fails after successful read (DB write error) | Session remains valid for its original TTL; idle window not reset; next request after 2h idle returns 403 | 4.3 |
| `declareIntent` and `IntentSessionCleanupJob` run simultaneously; cleanup deletes the previous ACTIVE session first | Invalidation finds 0 rows to update — proceeds normally; new session created. Idempotent. | 4.2 |
| Two concurrent `getValidSession` calls both update `lastActivityAt` | Both UPDATE statements execute; last-writer-wins. TTL extension is approximate (acceptable for 2h granularity). | 4.3 |
| Cleanup job delayed (e.g., JVM GC pause > 30 min) | EXPIRED/CLOSED rows accumulate until job resumes. Correctness unaffected — `expires_at` check is server-side at query time. | 4.2 |
| DST transition occurs while session is active | No impact — all timestamps stored as `TIMESTAMPTZ` (UTC offset-aware). | 4.3 |
| DB clock vs. app server clock skew > 1 second | Session may expire slightly earlier or later than the 2h TTL. Acceptable for 2h granularity; document in ops runbook. | 4.3, NFR-C3 |

### Integration failure modes

| Dependency | Failure Scenario | Expected Behaviour | Requirement |
|------------|------------------|--------------------|-------------|
| PostgreSQL | Unavailable on `declareIntent` | `DataAccessException` → 503 Service Unavailable | 1.2 |
| PostgreSQL | Unavailable on `getValidSession` | 503 Service Unavailable; swipe feed blocked until DB recovers | 5.1 |
| PostgreSQL | Slow query on `getValidSession` (p95 > 50ms) | NFR-P2 breached. Mitigation: `(token, expires_at)` covering index; DB statement timeout < 500ms | NFR-P2 |
| OTEL Collector | Unavailable | OTLP export fails silently (non-blocking); application continues; analytics events dropped — alert on `otel.export.errors` counter | NFR-O1 |
| OTEL Collector | Slow (export backpressure) | Micrometer buffers up to `maxBufferSize` records; on overflow, events dropped by design | NFR-O1 |
| Spring `@Scheduled` | Thread pool exhaustion | Cleanup job delayed or skipped; EXPIRED/CLOSED rows accumulate; correctness maintained; alert via OTEL span `error` flag on job execution | 4.2 |

### Security edge cases

| Scenario | Expected Behaviour | Requirement |
|----------|--------------------|-------------|
| `X-Owner-Id` header forged (no JWT in MVP) | Service cannot detect forgery; deploy behind VPN/internal network until auth is added | NFR-S1 |
| Token brute-force attempt | UUID v4 has 122 bits of entropy (~5.3 × 10³⁶ combinations); computationally infeasible | NFR-S2 |
| Token presented after session is CLOSED | `findByTokenAndExpiresAtAfter` returns null → `SessionNotFoundException` → 403 Forbidden | 5.1 |
| Token from owner A presented by owner B | Token is opaque UUID bound to `ownerId` in DB; `getValidSession` returns session context for the token's registered owner; cross-owner use requires token theft | NFR-S1 |
| SQL injection via `X-Intent-Session` header value | Spring Data JPA uses parameterized queries; injection vector mitigated | NFR-S2 |
| Session token value in access logs | Ensure access log format excludes `X-Intent-Session` header value to prevent token exposure | NFR-S1 |

### Data edge cases

| Scenario | Expected Behaviour | Requirement |
|----------|--------------------|-------------|
| Concurrent `POST` for same owner hits partial unique index | PostgreSQL raises `23505` → `DataIntegrityViolationException` → 409 Conflict; owner retries | 1.2 |
| `swipeCount` INT overflow (2,147,483,647) | Requires 2.1B swipes in one 2h session — practically impossible; no guard needed | 6.3 |
| 10× load (100k DAU, ~50k concurrent active sessions) | Table stays small; both indexes (`token+expires_at`, `status+expires_at`) remain efficient; no partitioning needed; cleanup job delete volume scales proportionally | NFR-SC1 |
| Liquibase changeset applied to production DB with pre-existing data | Greenfield table — no pre-existing `intent_sessions` rows; all changesets are additive (`CREATE TABLE`, `CREATE INDEX`); no data migration risk | — |

---

## Architecture Options Considered

### Option 1: Vertical Slice Module within Monolith (Selected)

New `intent/` module following the `<feature>/presentation/service/model/` pattern inside the existing Spring Boot monolith.

**Advantages**:
1. Zero infrastructure addition — runs in the existing JVM process using the existing PostgreSQL instance; no new containers or services required.
2. Transactional consistency — `declareIntent` and swipe feed validation share the same JDBC connection pool and transaction boundary, preventing partial state across service calls.
3. Sub-millisecond internal call latency — `DogMatcherService` calls `IntentSessionService.getValidSession()` via direct JVM method invocation, keeping additional filter overhead well within NFR-P2 (< 50ms).

**Disadvantages**:
1. Monolith coupling risk — if boundary discipline is not enforced, `DogMatcherService` ↔ `IntentSessionService` bidirectional dependencies can form; mitigated by the one-way dependency rule in `design.md`.
2. Scheduling contention — `IntentSessionCleanupJob` shares the Spring `@Scheduled` thread pool with any future scheduled tasks; pool exhaustion delays cleanup (bounded by 30-min cadence).
3. Shared schema contention — a long-running cleanup DELETE on `intent_sessions` may lock rows and affect connection pool availability for the hot `getValidSession` path under peak load.

### Option 2: Domain-Embedded Session Management (Integrated into DogMatcherService)

Intent session logic added directly to the existing `DogMatcherService` rather than as a separate module.

**Advantages**:
1. Minimal new files — no new module structure; intent state co-located with filtering logic reduces indirection for the initial implementation.
2. Single transaction scope — intent creation and feed filtering happen in the same service bean, eliminating any cross-service consistency concern.
3. Shorter initial call chain — no separate `IntentSessionService.getValidSession()` call required on feed access.

**Disadvantages**:
1. Violates Single Responsibility — `DogMatcherService` would own both dog-pair compatibility scoring and user session management; any TTL change requires touching the matching service.
2. Guaranteed refactor debt — when JWT-based auth is added, session management must be extracted anyway, creating at least 2 additional sprint tasks of rework.
3. Breaks steering boundary rule — `service/` domain core would depend on HTTP header logic (session token parsing), violating the `Presentation → Service` dependency constraint in `steering/structure.md`.

### Option 3: Separate Microservice for Session Management

Intent session lifecycle extracted into a standalone service communicating via HTTP.

**Advantages**:
1. Independent scaling — session validation (called on every swipe feed request) can be scaled independently of the matching engine above 10k DAU.
2. Clear ownership boundary — dedicated service owns all session concerns and can evolve independently.
3. Technology freedom — Redis or a key-value store can be adopted for storage without impacting the monolith's PostgreSQL schema.

**Disadvantages**:
1. Network hop per swipe feed request — cross-service HTTP call adds minimum 5–20ms at p99, consuming most of the NFR-P2 (< 50ms) budget with no safety margin.
2. Operational complexity disproportionate to MVP — requires service discovery, container orchestration, and inter-service authentication not yet present in the stack.
3. Distributed consistency gap — TOCTOU window between session deletion in the session service and token validation cached in the monolith can expose stale-valid tokens for up to one cache TTL.

**Recommendation**: Option 1 (Vertical Slice Module within Monolith) — satisfies NFR-P2 with zero network overhead, requires no new infrastructure, and aligns with the existing steering architecture. Domain boundary enforced via module isolation (`intent/` package), sufficient at the current scale.

---

## Architecture Decision Record

See: [docs/adr/ADR-001-in-db-session-storage.md](../../../../docs/adr/ADR-001-in-db-session-storage.md)

---

## Sequence Diagram

See: [docs/diagrams/intent-declaration-sequence.md](../../../../docs/diagrams/intent-declaration-sequence.md)

```mermaid
sequenceDiagram
    autonumber
    participant Owner
    participant IntentController
    participant IntentSessionService
    participant IntentSessionRepository
    participant DB as intent_sessions
    participant SwipeFeedService

    Owner->>IntentController: POST /api/v1/intent-sessions intent, X-Owner-Id
    IntentController->>IntentSessionService: declareIntent(ownerId, intent, dogSex)
    IntentSessionService->>IntentSessionRepository: findByOwnerIdAndStatus(ownerId, ACTIVE)
    IntentSessionRepository->>DB: SELECT WHERE owner_id AND status=ACTIVE
    DB-->>IntentSessionRepository: previous session or null
    IntentSessionService->>IntentSessionRepository: update previous session status=CLOSED
    IntentSessionRepository->>DB: UPDATE status=CLOSED
    IntentSessionService->>IntentSessionRepository: save new IntentSession(token, intent, expiresAt)
    IntentSessionRepository->>DB: INSERT intent_sessions row
    DB-->>IntentSessionRepository: saved entity
    IntentSessionService-->>IntentController: IntentSessionResponse(token, expiresAt)
    IntentController-->>Owner: 201 Created token, expiresAt

    Note over IntentSessionService,DB: async boundary - OTEL span event session.started emitted here

    Owner->>SwipeFeedService: GET /api/v1/swipe/feed X-Intent-Session token
    SwipeFeedService->>IntentSessionService: getValidSession(token)
    IntentSessionService->>IntentSessionRepository: findByTokenAndExpiresAtAfter(token, now)
    IntentSessionRepository->>DB: SELECT WHERE token AND expires_at > NOW()
    alt valid session
        DB-->>IntentSessionRepository: IntentSession row
        IntentSessionRepository-->>IntentSessionService: IntentSession
        IntentSessionService-->>SwipeFeedService: IntentSessionContext(intent, autoSexFilter, availableFilters)
        SwipeFeedService-->>Owner: 200 OK filtered profiles
    else missing or expired
        DB-->>IntentSessionRepository: null
        IntentSessionRepository-->>IntentSessionService: null
        IntentSessionService-->>SwipeFeedService: SessionNotFoundException
        SwipeFeedService-->>Owner: 403 Forbidden redirectTo intent-sessions/new
    end
```
