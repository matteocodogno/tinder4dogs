# Research & Design Decisions — dog-profile

---
**Purpose**: Capture discovery findings, architectural investigations, and rationale that inform the technical design.

---

## Summary

- **Feature**: `dog-profile`
- **Discovery Scope**: New Feature — greenfield domain within existing Spring Boot monolith
- **Key Findings**:
  1. No dog-profile persistence exists yet. `match.model.Dog` is a transient in-memory data class (no JPA); the new `dogprofile/` domain creates proper JPA entities from scratch.
  2. MinIO (S3-compatible) is not yet integrated; AWS SDK v2 `S3Client` is the correct client and must be added as a new Maven dependency alongside a `MinioConfig` bean.
  3. Spring Security is absent from `pom.xml`; owner identity (`ownerId: UUID`) is a prerequisite contract that the dog-profile domain defines but does not implement — auth is a parallel/prerequisite feature.
  4. Spring `ApplicationEventPublisher` + `@Async` provides zero-broker async domain events sufficient for v1; no external message broker is required.

---

## Research Log

### Existing Codebase Analysis

- **Context**: Understand existing domain structure and patterns before designing the new domain.
- **Sources**: Codebase exploration (`src/main/kotlin/`, `pom.xml`, `application.yaml`)
- **Findings**:
  - Domains present: `match/` (transient `Dog` data class + `DogMatcherService`), `support/` (AI breed advice), `ai/` (LiteLLM, Langfuse, RAG, fine-tuning).
  - `match.model.Dog` is a plain `data class` with no JPA annotations; it will be superseded by the `DogProfileEntity` JPA entity in the new `dogprofile/` domain. `DogMatcherService` references it and will need updating in a subsequent task.
  - Controller pattern: `@RestController`, `@RequestMapping("/api/v1/...")`, `suspend` functions via coroutines (used in `SupportController`, `BreedCompatibilityController`).
  - HTTP client pattern: Spring 6 `@ImportHttpServices` + `RestClient.Builder` (see `HttpClientConfig`). MinIO does **not** use this pattern — `S3Client` is used directly as a Spring bean.
  - `application.yaml` uses `ddl-auto: update` — acceptable for local dev; Liquibase migration (per NFR-R08 in PRD) should be addressed in a follow-up.
  - `pom.xml`: No AWS SDK, no Spring Security, no Liquibase present. Both AWS SDK v2 and Spring Security must be added as new dependencies for this feature.
- **Implications**: New domain package `com.ai4dev.tinderfordogs.dogprofile` follows identical layered structure: `model/ → service/ → presentation/`; MinIO and async config go into `dogprofile/config/`.

---

### MinIO S3 Integration

- **Context**: NFR-C03 mandates local MinIO with S3-compatible API.
- **Sources**: AWS SDK v2 docs, MinIO Java Client docs, Spring Boot 4 dependency management
- **Findings**:
  - AWS SDK v2 (`software.amazon.awssdk:s3`) is the recommended approach for S3-compatible MinIO; Spring does not provide a built-in S3 bean.
  - Required `S3Client` configuration: `endpointOverride(URI)`, `credentialsProvider(StaticCredentialsProvider)`, `region("us-east-1")` (placeholder for MinIO), `pathStyleAccessEnabled(true)`.
  - `S3Client` (synchronous) is sufficient for v1 upload volumes; `S3AsyncClient` adds complexity without benefit.
  - Config keys needed in `application.yaml`: `minio.url`, `minio.access-key`, `minio.secret-key`, `minio.bucket`.
  - Storage key convention: `dog-profiles/{profileId}/photos/{photoId}.{ext}` — enables profile-scoped purge.
- **Implications**: Add `MinioConfig` bean in `dogprofile/config/`; add AWS SDK v2 dependency to `pom.xml`. MinIO service must be added to `docker-compose.yml` (or Docker Compose file) for local dev.

---

### Domain Event Strategy

- **Context**: 6.3 resolved as async (Q4 decision); event-driven score recalculation.
- **Sources**: Spring Framework `ApplicationEventPublisher` documentation
- **Findings**:
  - `ApplicationEventPublisher.publishEvent()` is synchronous by default; wrapping in `@Async` listener makes it non-blocking from the service perspective.
  - `@EnableAsync` on a config class + `@Async` on the listener method is sufficient.
  - Events are lost on JVM crash — acceptable for v1 since compatibility recalculation is best-effort; the matching feed can re-compute on next load.
  - `@RecordApplicationEvents` in Spring Boot Test makes event emission fully testable without infrastructure.
  - Alternative (Kafka/RabbitMQ): adds broker infrastructure and operational overhead — deferred to v2.
- **Implications**: Add `@EnableAsync` to `dogprofile/config/AsyncConfig`; event classes `DogProfileUpdatedEvent` and `DogProfileDeletedEvent` defined in `dogprofile/model/`; listener stubs placed in the future `match/` domain.

---

### Breed Enum and Size Derivation

- **Context**: Q1 (size derived from breed) + Q3 (breed is a controlled enum).
- **Findings**:
  - A Kotlin `enum class Breed(val sizeCategory: SizeCategory)` with ~25-30 representative breeds is compile-time safe, requires no DB join, and is trivially extensible.
  - Adding breeds later requires a code change + redeploy — acceptable at v1 scale and dog population.
  - `support.service.BreedCompatibilityService` currently takes free-text breed names. Once the `Breed` enum exists, its `.name.lowercase()` can be passed as-is.
- **Implications**: Define `Breed` enum in `dogprofile/model/`; `BreedReferenceService.resolve(breedName: String): Breed` throws `InvalidBreed` on unknown input.

---

### Soft Delete and 30-Day Hard Purge

- **Context**: 7.1 — soft delete + schedule hard deletion of photos within 30 days.
- **Findings**:
  - JPA `@Where(clause = "deleted_at IS NULL")` (Hibernate 6 `@SoftDelete` or manual query filter) excludes soft-deleted rows transparently.
  - A Spring `@Scheduled` component scanning for `deleted_at < NOW() - 30 days` handles photo purge without blocking the delete API response.
  - Hard DB row deletion is separate from photo purge and can follow a longer retention window if legal requires audit trail.
- **Implications**: `PhotoPurgeScheduler` in `dogprofile/service/` runs daily; purges MinIO objects then nulls/removes the `DogPhotoEntity` rows.

---

### Auth Boundary

- **Context**: Spring Security absent; dog-profile APIs require authenticated owner identity.
- **Findings**:
  - The design defines `ownerId: UUID` extracted from the JWT `sub` claim via `@AuthenticationPrincipal`.
  - Controllers are designed auth-ready: they accept `Principal`/`Authentication` injection points.
  - Until auth is implemented, integration tests use a hardcoded `ownerId` header; controllers annotated with `// TODO: @PreAuthorize("isAuthenticated()")`.
- **Implications**: Auth feature is a prerequisite for production endpoints; dog-profile does not own the auth implementation.

---

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| Layered domain module (existing) | `model/ → service/ → presentation/` per domain | Consistent with codebase, low friction | Anemic service risk if logic leaks to controller | **Selected** — matches steering |
| Hexagonal (ports & adapters) | Domain core + inbound/outbound ports | Clean testable boundaries | Extra adapter layer overhead | `PhotoStoragePort` is the one port worth extracting; full hexagonal deferred |
| CQRS | Separate read/write models | Optimised read path for feed | Significant complexity for a small team | Defer to v2 if feed latency demands it |

---

## Design Decisions

### Decision: `PhotoStoragePort` abstraction

- **Context**: NFR-C03 mandates MinIO for v1; future migration to cloud S3 is plausible.
- **Alternatives**:
  1. Direct `S3Client` calls inside `DogProfileService`
  2. `PhotoStoragePort` interface with `MinioPhotoStorageAdapter` implementation
- **Selected**: Option 2 — `PhotoStoragePort` interface in domain, `MinioPhotoStorageAdapter` in infrastructure layer.
- **Rationale**: Keeps service layer unit-testable (mock the port); cloud S3 swap requires only a new adapter, no service changes.
- **Trade-offs**: One extra indirection; fully justified by testability requirement.

### Decision: Spring `ApplicationEventPublisher` + `@Async` for domain events

- **Context**: 6.3 — async score recalculation; no external broker in current stack.
- **Selected**: `ApplicationEventPublisher.publishEvent()` called after DB commit; listener annotated `@Async`.
- **Rationale**: Zero extra infrastructure for v1; both publisher and listener are in the same JVM.
- **Trade-offs**: Events lost on crash (best-effort); no delivery guarantee, no retry. Acceptable for compatibility recalculation use case.
- **Follow-up**: If match scoring reliability becomes a requirement, replace with a transactional outbox + broker in v2.

### Decision: `Breed` Kotlin enum with embedded `sizeCategory`

- **Context**: Q1 + Q3 decisions; size derived from breed, breed is a controlled enum.
- **Selected**: `enum class Breed(val sizeCategory: SizeCategory)` in `dogprofile/model/`.
- **Rationale**: Compile-time safety; no DB join needed to derive size; consistent with Kotlin idioms.
- **Trade-offs**: Adding a breed requires code change + redeploy. Acceptable at v1 scale.

### Decision: `ownerId` uniqueness constraint on `dog_profiles`

- **Context**: NFR-C01 — one dog profile per owner.
- **Selected**: `UNIQUE` constraint on `dog_profiles.owner_id` — enforced at DB level in addition to service-level check.
- **Rationale**: Dual enforcement prevents race conditions under concurrent profile creation requests.

---

## Risks & Mitigations

- **Auth prerequisite missing** — Mitigation: design API auth-ready; mark endpoints `TODO: @PreAuthorize`; unblock with owner-id header in dev/test.
- **MinIO not in local dev stack** — Mitigation: implementation task must add MinIO service to `docker-compose.yml`.
- **`ddl-auto: update` not production-safe** — Mitigation: implementation task converts to Liquibase changesets with tested rollbacks (per NFR-R08).
- **`match.model.Dog` becomes stale** — Mitigation: a follow-up task updates `DogMatcherService` to use `DogProfileEntity`; mark with `// TODO: replace with DogProfileService` comment.
- **PostgreSQL `TEXT[]` for temperament tags** — Mitigation: a `@ElementCollection` + join table is the JPA-idiomatic alternative; chosen array approach is simpler for v1 query patterns. If filtering by tag becomes a feed requirement, migrate to join table.

---

## References

- Spring Data JPA soft-delete with `@Where` / `@SQLRestriction` (Hibernate 6)
- AWS SDK for Java v2 — `S3Client` with MinIO path-style access
- Spring Framework `ApplicationEventPublisher` + `@Async` listener pattern
- Spring Boot Test `@RecordApplicationEvents` for event assertions
