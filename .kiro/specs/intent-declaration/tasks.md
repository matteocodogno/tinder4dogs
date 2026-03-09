# Implementation Plan

- [ ] 1. Set up project dependencies and configuration <!-- #56 -->
- [ ] 1.1 Add Liquibase, OpenTelemetry, and Actuator dependencies to pom.xml <!-- #57 -->
  - Add `spring-boot-starter-liquibase` with no explicit version (managed by Spring Boot parent BOM)
  - Add `micrometer-tracing-bridge-otel` (Spring Boot BOM)
  - Add `opentelemetry-exporter-otlp` (Spring Boot BOM)
  - Add `spring-boot-starter-actuator` (Spring Boot BOM)
  - _Requirements: 6.1, 6.2, 6.3_

- [ ] 1.2 Update application configuration for Liquibase and OpenTelemetry <!-- #58 -->
  - Change `spring.jpa.hibernate.ddl-auto` from `update` to `validate`
  - Add `spring.liquibase.change-log` pointing to `classpath:/db/changelog/db.changelog-master.yaml`
  - Add `management.tracing.sampling.probability: 1.0` (tune per environment)
  - Add `management.otlp.tracing.endpoint` and `management.otlp.metrics.export.url` driven by `OTEL_EXPORTER_OTLP_ENDPOINT` env var (default: `http://localhost:4318`)
  - Enable p95 percentiles histogram for `intent.session.validate` metric
  - Set `management.tracing.enabled: false` in the `test` profile to prevent OTEL Collector coupling during tests
  - _Requirements: 6.1, 6.2, 6.3_

- [ ] 2. Create the database migration <!-- #59 -->
  - Create `src/main/resources/db/changelog/db.changelog-master.yaml` using `includeAll` to scan the `changes/` directory
  - Create `src/main/resources/db/changelog/changes/001-create-intent-sessions.sql` using Liquibase formatted SQL syntax
  - Define the `intent_sessions` table with columns: `id` (BIGSERIAL PK), `session_id` (UUID UNIQUE NOT NULL), `token` (UUID UNIQUE NOT NULL), `owner_id` (BIGINT NOT NULL), `intent` (VARCHAR 20 NOT NULL), `auto_sex_filter` (VARCHAR 10 nullable), `status` (VARCHAR 10 NOT NULL DEFAULT 'ACTIVE'), `swipe_count` (INT NOT NULL DEFAULT 0), `activated` (BOOLEAN NOT NULL DEFAULT FALSE), `created_at` (TIMESTAMPTZ NOT NULL), `last_activity_at` (TIMESTAMPTZ), `expires_at` (TIMESTAMPTZ NOT NULL)
  - Add index on `(token, expires_at)` for the swipe feed validation hot path
  - Add index on `(status, expires_at)` for the cleanup job query
  - Add a separate changeset (scoped `dbms:postgresql`) for the partial unique index on `owner_id WHERE status = 'ACTIVE'`
  - Provide a `--rollback` directive for every changeset
  - _Requirements: 4.1, 4.2, 4.3_

- [ ] 3. Implement the domain model and repository <!-- #60 -->
- [ ] 3.1 (P) Define domain enums and value objects <!-- #61 -->
  - Create `SearchIntent` enum: `PLAYMATE`, `BREEDING`
  - Create `DogSex` enum: `MALE`, `FEMALE`
  - Create `SessionStatus` enum: `ACTIVE`, `CLOSED`, `EXPIRED`
  - Create `FilterType` enum with PLAYMATE-scoped values (`BREED`, `SIZE`, `AGE`, `ENERGY_LEVEL`, `TEMPERAMENT`) and BREEDING-scoped values (`PEDIGREE`, `HEALTH_CRITERIA`); omit `SEX` (auto-applied for Breeding — Req 3.4)
  - Create `IntentSessionContext` as a read-only data class carrying `sessionId`, `ownerId`, `intent`, `autoSexFilter` (nullable), and a derived `availableFilters: Set<FilterType>` set
  - _Requirements: 2.3, 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 3.2 (P) Implement the IntentSession JPA entity <!-- #62 -->
  - Map `IntentSession` to the `intent_sessions` table with all columns from the migration
  - Annotate `intent`, `autoSexFilter`, and `status` to store as their string names (not ordinals)
  - Ensure column names exactly match the SQL schema (snake_case to camelCase)
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 3.3 Implement the session repository <!-- #63 -->
  - Create `IntentSessionRepository` extending `JpaRepository<IntentSession, Long>`
  - Add a derived query to find a session by token where `expires_at` is after a given instant
  - Add a derived query to find a session by `ownerId` and `status`
  - Add a `@Modifying` JPQL query to bulk-delete sessions matching a status list and an expiry cutoff for the cleanup job
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [ ] 4. Implement session service business logic <!-- #64 -->
- [ ] 4.1 Implement intent declaration with session invalidation <!-- #65 -->
  - Find any existing `ACTIVE` session for the requesting owner and set its status to `CLOSED` before proceeding
  - For `BREEDING` intent, derive `autoSexFilter` as the opposite of `ownerDogSex`; for `PLAYMATE`, `autoSexFilter` is null
  - Reject `BREEDING` requests where `ownerDogSex` is null with an `IllegalArgumentException` before any persistence
  - Create a new session with a fresh UUID for both `sessionId` and `token`, `status = ACTIVE`, and `expiresAt = now + 2 hours`
  - Return a response containing `sessionId`, `token`, `intent`, `autoSexFilter`, and `expiresAt`
  - _Requirements: 1.1, 1.2, 1.4, 1.6, 2.1, 2.2, 2.3, 4.1, 4.5_

- [ ] 4.2 Implement token validation and activity tracking <!-- #66 -->
  - Query the repository by token where `expires_at > now`; throw `SessionNotFoundException` if no row is returned
  - Update `lastActivityAt` on each valid call to reset the 2-hour idle window
  - Derive `availableFilters` from the session's intent (PLAYMATE set vs BREEDING set, no SEX filter for BREEDING)
  - Throw `SessionExpiredException` if a token is found but its TTL has elapsed (defensive guard beyond the DB query)
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 4.4, 5.1, 5.4_

- [ ] 4.3 Implement first-swipe recording <!-- #67 -->
  - Accept a token; find the session and check the `activated` flag
  - If already `true`, return immediately without side effects (idempotent)
  - On first call, set `activated = true` and `swipeCount = 1`
  - _Requirements: 6.2, 6.3_

- [ ] 4.4 Implement explicit session close <!-- #68 -->
  - Look up the session by `sessionId` and `ownerId`; throw `SessionNotFoundException` if not found
  - Set `status = CLOSED` and persist
  - _Requirements: 1.6, 4.2, 4.5_

- [ ] 5. (P) Implement the session cleanup scheduler <!-- #69 -->
  - Create `IntentSessionCleanupJob` as a Spring-managed component with scheduling enabled
  - Run every 30 minutes via `@Scheduled(fixedDelay = 1800000)`
  - Delete rows where `status IN (EXPIRED, CLOSED)` and `expires_at` is older than 1 hour (grace period) using the bulk-delete repository query
  - Log the count of deleted rows at INFO level; emit a WARN when the deleted count exceeds 1 000 as a capacity signal
  - Depends on Task 3 (entity and repository); can run in parallel with Task 4
  - _Requirements: 4.2, 4.3_

- [ ] 6. Integrate OpenTelemetry observability into the service <!-- #70 -->
- [ ] 6.1 Instrument service methods as OTEL spans <!-- #71 -->
  - Inject `ObservationRegistry` into `IntentSessionService`
  - Wrap `declareIntent` in an `Observation` named `intent.session.declare` with low-cardinality attribute `intent`
  - Wrap `getValidSession` in an `Observation` named `intent.session.validate`
  - Wrap `closeSession` in an `Observation` named `intent.session.close` with attribute `reason`
  - Wrap the cleanup job execution in an `Observation` named `intent.session.cleanup` with attribute `deleted.count`
  - _Requirements: 6.1, 6.4_

- [ ] 6.2 Emit session lifecycle span events <!-- #72 -->
  - Within `intent.session.declare`, emit a `session.started` event with attributes `sessionId`, `intent`, `autoSexFilter` (nullable), `expiresAt`
  - Within `intent.session.validate` on the first-swipe path (after `recordFirstSwipe`), emit `session.activated` with `sessionId` and `intent`
  - Within `intent.session.close` and `intent.session.cleanup`, emit `session.ended` with `sessionId`, `intent`, `swipeCount`, and `reason` (`OWNER_CLOSED` or `EXPIRED`)
  - Keep `sessionId` and `ownerId` as span event attributes only — never as top-level span attributes to avoid cardinality explosion
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 6.3 Implement session aggregate metrics <!-- #73 -->
  - Inject `MeterRegistry` into `IntentSessionService`
  - Increment `intent.session.started.total` Counter (tag: `intent`) on each successful `declareIntent`
  - Increment `intent.session.activated.total` Counter (tag: `intent`) on the first call to `recordFirstSwipe`
  - Increment `intent.session.ended.total` Counter (tags: `intent`, `reason`) on `closeSession` and cleanup
  - Register an `intent.session.active` Gauge querying the live count of `ACTIVE` rows from the repository
  - Increment `intent.session.validation.errors.total` Counter (tag: `error_type`: `NOT_FOUND` or `EXPIRED`) on each exception thrown by `getValidSession`
  - _Requirements: 5.3, 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 7. Implement the REST controller <!-- #74 -->
- [ ] 7.1 (P) Implement the intent declaration endpoint <!-- #75 -->
  - Create `IntentController` at `POST /api/v1/intent-sessions`
  - Accept a `DeclareIntentRequest` body (`intent: SearchIntent`, `ownerDogSex: DogSex?`) validated with `@Valid`
  - Read `ownerId` (Long) from the `X-Owner-Id` request header; add an inline comment marking this as an auth placeholder
  - Delegate to `IntentSessionService.declareIntent`; return `201 Created` with `IntentSessionResponse`
  - Return `400` for an invalid intent value; return `422` for BREEDING intent without `ownerDogSex`
  - Do not expose a UI affordance to change intent within an active session — the controller accepts new sessions only (invalidation handled by the service)
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 5.1_

- [ ] 7.2 (P) Implement the session close endpoint <!-- #76 -->
  - Add `DELETE /api/v1/intent-sessions/{sessionId}` to `IntentController`
  - Read `ownerId` from `X-Owner-Id` header; delegate to `IntentSessionService.closeSession`
  - Return `204 No Content` on success; return `404 Not Found` when the session does not exist for that owner
  - _Requirements: 1.6, 4.2, 4.5, 4.6_

- [ ] 8. Unit test the service layer <!-- #77 -->
- [ ] 8.1 (P) Test session lifecycle business logic <!-- #78 -->
  - `declareIntent` creates an `ACTIVE` session with the correct `intent`, `autoSexFilter`, and `expiresAt` (now + 2h ± 5s)
  - Calling `declareIntent` when an `ACTIVE` session already exists closes the previous session before persisting the new one
  - `declareIntent` with `BREEDING` and null `ownerDogSex` throws `IllegalArgumentException` without persisting
  - `getValidSession` returns `IntentSessionContext` with correct `availableFilters` for PLAYMATE (no PEDIGREE, no SEX) and BREEDING (no SEX filter in available set)
  - `getValidSession` throws `SessionExpiredException` for an expired token
  - `getValidSession` throws `SessionNotFoundException` for an unknown token
  - `recordFirstSwipe` is idempotent — calling twice does not change `swipeCount` beyond 1 and does not emit a second `session.activated` event
  - `autoSexFilter` derivation: MALE owner → FEMALE filter; FEMALE owner → MALE filter
  - _Requirements: 1.4, 1.6, 2.3, 3.1, 3.2, 3.3, 3.4, 4.1, 4.4, 4.5, 5.1, 5.4_

- [ ] 8.2 (P) Test OpenTelemetry signals <!-- #79 -->
  - Use `TestObservationRegistry` to assert `intent.session.declare` span is created on `declareIntent`
  - Assert `session.started` span event is present with the correct `intent` and `sessionId` attributes
  - Assert `session.activated` span event is emitted on the first `recordFirstSwipe` but not on a repeated call
  - Assert `session.ended` span event is present on `closeSession` with `reason = OWNER_CLOSED`
  - Use `SimpleMeterRegistry` to assert `intent.session.started.total` increments on `declareIntent`
  - Assert `intent.session.validation.errors.total{error_type=EXPIRED}` increments when an expired token is validated
  - Assert `intent.session.active` gauge reflects the current number of `ACTIVE` rows
  - _Requirements: 5.3, 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 9. Integration test the full session lifecycle <!-- #80 -->
- [ ] 9.1 (P) Test controller endpoints end-to-end <!-- #81 -->
  - `POST /api/v1/intent-sessions` with valid PLAYMATE request → 201 with token and `expiresAt` approximately 2 hours ahead
  - `POST /api/v1/intent-sessions` with BREEDING and null `ownerDogSex` → 422
  - `POST /api/v1/intent-sessions` with an invalid intent string → 400
  - `POST /api/v1/intent-sessions` twice for the same owner → second call returns 201; verify first session `status = CLOSED` in the database
  - `DELETE /api/v1/intent-sessions/{sessionId}` → 204; subsequent `getValidSession` call throws
  - `DELETE /api/v1/intent-sessions/{unknownId}` → 404
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.3, 4.5, 4.6_

- [ ] 9.2 (P) Test session expiry and cleanup job <!-- #82 -->
  - Persist a session with `expiresAt` in the past; verify `getValidSession` throws `SessionExpiredException`
  - Run `IntentSessionCleanupJob` manually in the test; verify `EXPIRED` and `CLOSED` rows older than the grace period are deleted
  - Verify `ACTIVE` sessions are never touched by the cleanup job
  - _Requirements: 4.2, 4.3, 4.4, 5.4_

- [ ] 9.3 (P) Test cross-session isolation <!-- #83 -->
  - Create sessions for two different owners; verify that owner A's token cannot retrieve owner B's session context
  - Verify that no intent data appears in any API response beyond the session that produced it
  - _Requirements: 4.1, 4.6, 5.1, 5.2_
