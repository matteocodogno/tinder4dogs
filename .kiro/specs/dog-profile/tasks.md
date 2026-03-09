# Implementation Plan

## Task Summary
- 9 major tasks, 28 sub-tasks
- All 7 requirements covered (Req 1–7)
- Estimated size: 1–3 hours per sub-task

---

- [ ] 1. Project setup: add Liquibase and OTEL dependencies
- [ ] 1.1 Add `spring-boot-starter-liquibase` and OTEL dependencies to `pom.xml` <!-- #2 -->
  - Add `org.springframework.boot:spring-boot-starter-liquibase` (no version — managed by Spring Boot BOM)
  - Add `io.micrometer:micrometer-tracing-bridge-otel`
  - Add `io.opentelemetry:opentelemetry-exporter-otlp`
  - Verify `./mvnw dependency:resolve` completes without conflict
  - _Requirements: 1.1, 6.7_

- [ ] 1.2 Configure Liquibase and update application settings <!-- #3 -->
  - Change `spring.jpa.hibernate.ddl-auto` from `update` to `validate` in `application.yaml`
  - Set `spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml`
  - Create `src/main/resources/db/changelog/db.changelog-master.xml` as root include-list referencing the four SQL migration files
  - Set `spring.jpa.hibernate.ddl-auto=none` for the `test` profile to allow `@DataJpaTest` to manage schema via Liquibase
  - _Requirements: 6.7_

- [ ] 1.3 (P) Configure OTEL telemetry properties <!-- #4 -->
  - Add `management.otlp.tracing.endpoint` property to `application.yaml` (default: `http://localhost:4317`)
  - Set `management.tracing.sampling.probability=1.0` for dev/test profiles
  - Add OTEL collector service to `compose.yaml` for local observability (e.g., `otel/opentelemetry-collector`)
  - _Requirements: 1.1_

---

- [ ] 2. Define domain enums and JPA entities
- [ ] 2.1 (P) Define the four controlled-vocabulary enums <!-- #5 -->
  - Create `Sex` enum: `MALE`, `FEMALE`
  - Create `Size` enum: `SMALL`, `MEDIUM`, `LARGE`, `EXTRA_LARGE`
  - Create `EnergyLevel` enum: `LOW`, `MEDIUM`, `HIGH`
  - Create `TemperamentTag` enum with all 12 vocabulary values (`FRIENDLY`, `CALM`, `PLAYFUL`, `ENERGETIC`, `GENTLE`, `CURIOUS`, `INDEPENDENT`, `PROTECTIVE`, `SOCIABLE`, `TIMID`, `STUBBORN`, `AFFECTIONATE`)
  - _Requirements: 1.5, 1.6, 6.3, 6.4, 6.6_

- [ ] 2.2 (P) Implement Breed JPA entity <!-- #6 -->
  - Annotate as `@Entity @Table(name = "breeds")`
  - Fields: `id` (BIGINT PK), `name` (VARCHAR 100, unique, not null), `fciGroup` (INT nullable)
  - _Requirements: 1.5, 6.5_

- [ ] 2.3 Implement DogProfile JPA entity <!-- #7 -->
  - Annotate with `@Entity @Table(name = "dog_profiles")` and Hibernate `@Where(clause = "deleted_at IS NULL")` for automatic soft-delete filtering
  - Fields: `id` (UUID PK), `ownerId` (UUID, not null), `name`, `breed` (`@ManyToOne` to `Breed`), `ageMonths`, `sex`, `size`, `energyLevel`, `pedigree`, `completenessScore`
  - `temperamentTags` as `@ElementCollection @Enumerated(EnumType.STRING)` backed by `dog_profile_temperament_tags` join table
  - Temporal fields: `deletedAt` (nullable), `createdAt`, `updatedAt`
  - Depends on Task 2.1 (enums) and Task 2.2 (Breed entity)
  - _Requirements: 1.5, 1.6, 3.4, 5.1_

- [ ] 2.4 Implement DogPhoto JPA entity <!-- #8 -->
  - Annotate with `@Entity @Table(name = "dog_photos")`
  - Fields: `id` (UUID PK), `dogProfile` (`@ManyToOne` lazy), `data` (`ByteArray`, `@Basic(fetch = LAZY)`), `mimeType`, `fileSize`, `sortOrder`, `createdAt`
  - Depends on Task 2.3 (DogProfile entity)
  - _Requirements: 2.1, 2.8_

---

- [ ] 3. Write Liquibase SQL migrations
- [ ] 3.1 Write SQL migration for the `breeds` reference table <!-- #9 -->
  - Liquibase formatted SQL (`--liquibase formatted sql`, `--changeset`, `--rollback`)
  - File: `migrations/001-create-breeds.sql`
  - `CREATE TABLE breeds` with `id`, `name` (unique), `fci_group`
  - _Requirements: 6.5_

- [ ] 3.2 Write SQL migration for the `dog_profiles` table <!-- #10 -->
  - File: `migrations/002-create-dog-profiles.sql`
  - `CREATE TABLE dog_profiles` with all columns including `deleted_at`
  - Partial unique index: `CREATE UNIQUE INDEX ON dog_profiles(owner_id) WHERE deleted_at IS NULL`
  - Index on `deleted_at` to support the hard-deletion scheduler
  - FK to `breeds(id)`; include rollback block
  - Depends on migration 3.1 (breeds table must exist)
  - _Requirements: 1.3, 5.1, 6.7_

- [ ] 3.3 (P) Write SQL migration for the `dog_photos` table <!-- #11 -->
  - File: `migrations/003-create-dog-photos.sql`
  - `CREATE TABLE dog_photos` with `data BYTEA NOT NULL`, `mime_type`, `file_size`, `sort_order`
  - FK to `dog_profiles(id) ON DELETE CASCADE`; include rollback block
  - Can be written in parallel with Task 3.4 once Task 3.2 is complete
  - _Requirements: 2.1, 2.8_

- [ ] 3.4 (P) Write SQL migration for the temperament tags join table <!-- #12 -->
  - File: `migrations/004-create-dog-profile-temperament-tags.sql`
  - `CREATE TABLE dog_profile_temperament_tags` with composite PK `(dog_profile_id, tag)`
  - FK to `dog_profiles(id) ON DELETE CASCADE`; include rollback block
  - Can be written in parallel with Task 3.3 once Task 3.2 is complete
  - _Requirements: 1.6, 6.3_

---

- [ ] 4. Implement Spring Data repositories
- [ ] 4.1 (P) Implement DogProfileRepository <!-- #13 -->
  - Extend `JpaRepository<DogProfile, UUID>`
  - Custom queries: `existsByOwnerIdAndDeletedAtIsNull`, `findByOwnerIdAndDeletedAtIsNull`, `findByIdAndDeletedAtIsNull`
  - _Requirements: 1.3, 3.1, 4.1, 5.1_

- [ ] 4.2 (P) Implement DogPhotoRepository <!-- #14 -->
  - Extend `JpaRepository<DogPhoto, UUID>`
  - Custom queries: `countByDogProfileId`, `deleteAllByDogProfileId`, `findByIdAndDogProfileOwnerId`
  - _Requirements: 2.1, 2.4, 2.5, 5.1_

- [ ] 4.3 (P) Implement BreedRepository <!-- #15 -->
  - Extend `JpaRepository<Breed, Long>`
  - Custom query: `findByNameIgnoreCase`
  - _Requirements: 6.5_

---

- [ ] 5. Implement supporting services
- [ ] 5.1 (P) Implement the completeness calculator <!-- #16 -->
  - Pure stateless service computing `completenessScore` (0–100) from five optional factors, each worth 20 points: `size` filled, `temperamentTags ≥ 1`, `energyLevel` filled, `pedigree` explicitly provided, `photos.size ≥ 3`
  - Return `CompletenessResult` with `score` and `missingFields` (human-readable labels per NFR-U02)
  - No Spring dependencies; no I/O
  - _Requirements: 7.1, 7.2, 7.5_

- [ ] 5.2 (P) Implement the breed seeder <!-- #17 -->
  - `CommandLineRunner` that runs after Liquibase migrations complete
  - Idempotent: use `INSERT ... ON CONFLICT DO NOTHING` (or check-before-insert via repository)
  - Seed all FCI-recognised breed names plus `MIXED`; fciGroup populated where applicable
  - _Requirements: 6.5_

- [ ] 5.3 (P) Implement owner identity resolution <!-- #18 -->
  - Custom `@OwnerId` annotation to mark controller method parameters
  - `HandlerMethodArgumentResolver` that reads `X-Owner-Id` header, parses it as `UUID`
  - Throws `MissingOwnerIdException` (mapped to HTTP 401) when header is absent or malformed
  - Register resolver in Spring MVC configuration
  - _Requirements: 1.1, 4.3, 5.3_

---

- [ ] 6. Implement the dog profile service
- [ ] 6.1 Implement profile creation <!-- #19 -->
  - Reject duplicate profile for same owner (HTTP 409)
  - Validate breed against reference table (HTTP 422 on miss)
  - Validate `temperamentTags` against `TemperamentTag` enum vocabulary
  - Compute initial `completenessScore` via `CompletenessCalculator`
  - Persist profile in a single `@Transactional` operation
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

- [ ] 6.2 Implement profile retrieval (own view and public view) <!-- #20 -->
  - Owner view: return all fields including `completenessScore` and `missingFields`
  - Public view: return only safe public fields; exact coordinates must never be present
  - Return HTTP 404 when profile not found or soft-deleted
  - Exclude deactivated-owner profiles from public listing
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 6.3 Implement profile update <!-- #21 -->
  - Accept partial update (null fields = unchanged); protect mandatory fields from being nulled
  - Verify caller owns the profile (HTTP 403 on mismatch)
  - Recompute `completenessScore` after every successful update
  - Record `updatedAt` timestamp on every mutation
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [ ] 6.4 Implement profile deletion with cascade <!-- #22 -->
  - Soft-delete within a single `@Transactional` block: delete all photo records then set `deletedAt` on profile
  - Verify caller owns the profile (HTTP 403); roll back entire transaction on any failure (HTTP 500)
  - Profile disappears from public queries immediately after commit (`@Where` filter handles this)
  - Hard-deletion scheduling is out of scope for this feature; schema supports it via `deleted_at` index
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

- [ ] 6.5 Implement photo management <!-- #23 -->
  - Add photo: validate MIME type (`image/jpeg`, `image/png`) and file size (≤ 10 MB) before reading bytes; enforce 5-photo cap; store as `ByteArray` blob; recompute completeness
  - Delete photo: verify caller owns the profile; reject deletion of last remaining photo (HTTP 422)
  - Retrieve photo: return raw bytes with correct `Content-Type` header; require authenticated caller
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8_

- [ ] 6.6 Implement the completeness endpoint <!-- #24 -->
  - Fetch the active profile for the caller and delegate score and missing fields to `CompletenessCalculator`
  - Return lightweight `CompletenessResponse` without the full profile payload
  - Score below 80 triggers the `missingFields` list in both this endpoint and the owner profile view
  - _Requirements: 7.3, 7.4, 7.5_

---

- [ ] 7. Implement exception handling
- [ ] 7.1 (P) Define domain exception hierarchy <!-- #25 -->
  - Sealed class or open exceptions: `DogProfileAlreadyExistsException`, `DogProfileNotFoundException`, `InvalidBreedException`, `PhotoLimitExceededException`, `LastPhotoRemovalException`, `OwnerMismatchException`, `MissingOwnerIdException`
  - Each carries enough context (profileId, ownerId where applicable) for structured logging
  - _Requirements: 1.2, 1.3, 2.2, 2.5, 3.3, 4.3, 5.3_

- [ ] 7.2 (P) Implement the global exception handler <!-- #26 -->
  - `@ControllerAdvice` mapping each domain exception to its HTTP status and `ApiError` response body
  - `ApiError` includes machine-readable `field`, `message`, and `code` fields (NFR-U01)
  - Handle `ConstraintViolationException` (Bean Validation) → HTTP 422 with per-field errors
  - Log all 4xx/5xx at appropriate level (`WARN`/`ERROR`) with `ownerId`, exception class, and OTEL `trace_id` via MDC
  - _Requirements: 1.2, 2.2, 2.6, 2.7, 4.2, 5.3, 6.1, 6.4, 6.5, 6.6_

---

- [ ] 8. Implement REST controllers and wire telemetry
- [ ] 8.1 Implement DogProfileController <!-- #27 -->
  - Map all owner-scoped endpoints: POST create, GET own, GET public `/{id}`, PATCH update, DELETE, GET completeness
  - Inject `ownerId` via `@OwnerId` resolver on all write and owner-read operations
  - Delegate entirely to `DogProfileService`; no business logic in the controller
  - _Requirements: 1.1, 3.1, 3.2, 4.1, 5.1, 7.4_

- [ ] 8.2 Implement DogPhotoController <!-- #28 -->
  - Map photo endpoints: POST upload (multipart), GET retrieve binary, DELETE remove
  - Validate MIME type and size at controller boundary before delegating to service
  - Return `ResponseEntity<ByteArray>` with `Content-Type` header on photo retrieval
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

- [ ] 8.3 Instrument service layer with OTEL traces and metrics <!-- #29 -->
  - Annotate or wrap key service methods (`createProfile`, `updateProfile`, `deleteProfile`, `addPhoto`, `deletePhoto`) with OTEL spans; set `dog_profile.owner_id`, `dog_profile.profile_id`, `dog_profile.operation` as span attributes
  - Register Micrometer counters for `dog_profile.create/update/delete/photo.total` (tagged `result=success|error`)
  - Register `dog_profile.completeness_score` histogram
  - Register `dog_profile.photo.size_bytes` histogram on upload
  - _Requirements: 1.1, 4.4, 5.1_

---

- [ ] 9. Tests
- [ ] 9.1 Unit-test the completeness calculator <!-- #30 -->
  - All combinations of filled/unfilled optional fields; boundary cases: 0, 3, 5 photos; 0 and 1 temperament tags
  - Verify `missingFields` uses human-readable labels
  - _Requirements: 7.1, 7.2, 7.5_

- [ ] 9.2 Unit-test DogProfileService <!-- #31 -->
  - Use MockK mocks for all repositories and `CompletenessCalculator`
  - Cover: duplicate-owner guard (1.3), breed-not-found (6.5), temperament tag validation (6.3), photo cap (2.2), last-photo guard (2.5), owner mismatch on update/delete (4.3, 5.3), cascade soft-delete rollback on failure (5.6)
  - _Requirements: 1.2, 1.3, 2.2, 2.5, 4.3, 5.3, 5.6, 6.3, 6.5_

- [ ] 9.3 Integration-test the REST controllers <!-- #32 -->
  - `@WebMvcTest` for `DogProfileController` and `DogPhotoController`
  - Verify HTTP status codes and `ApiError` response shape for all documented error scenarios
  - Verify `PublicDogProfileResponse` never contains exact coordinates
  - _Requirements: 1.2, 2.2, 2.6, 2.7, 3.2, 3.4, 4.2, 5.3_

- [ ] 9.4 (P) Integration-test the repository layer <!-- #33 -->
  - `@DataJpaTest` with Liquibase migrations applied
  - Verify partial unique index (one active profile per owner), soft-delete filter, breed FK constraint
  - Verify photo blob round-trip (write + read bytes equality)
  - _Requirements: 1.3, 2.1, 5.2, 6.7_

- [ ]* 9.5 Integration-test BreedSeeder idempotency <!-- #34 -->
  - Run seeder twice against a clean schema; assert no duplicate breed rows
  - Verify `findByNameIgnoreCase` returns correct entries post-seed
  - _Requirements: 6.5_
