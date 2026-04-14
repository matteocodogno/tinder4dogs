# Implementation Plan

- [ ] 1. Set up project infrastructure for the dog profile feature
- [x] 1.1 Add new Maven dependencies and enable async event dispatching <!-- gh:#2 -->
  - Add `spring-boot-starter-security` and AWS SDK v2 `s3` artifact to `pom.xml`
  - Add an `AsyncConfig` class annotated `@EnableAsync` so Spring can dispatch domain events off the calling thread
  - Verify the project still compiles and all existing tests pass after the dependency additions
  - _Requirements: NFR-S01, NFR-C03_

- [ ] 1.2 Add MinIO configuration properties and local dev service <!-- gh:#3 -->
  - Declare `minio.url`, `minio.access-key`, `minio.secret-key`, and `minio.bucket` placeholders in `application.yaml` using environment-variable substitution (consistent with existing secret management)
  - Add a MinIO service entry to the Docker Compose file used for local development so the bucket is available immediately on `just dev`
  - _Requirements: NFR-C03_

- [ ] 2. Build the domain value types and error handling foundation
- [ ] 2.1 Implement the controlled breed vocabulary with automatic size derivation <!-- gh:#4 -->
  - Create the `Breed` enum whose entries each carry a `sizeCategory` property, covering ~25-30 representative breeds across all four size groups (Small / Medium / Large / XL)
  - Create `SizeCategory`, `Sex`, `Purpose`, and `ProfileStatus` enums
  - Define the `DogProfileUpdatedEvent` and `DogProfileDeletedEvent` domain event data classes (profileId, ownerId, changedFields / correlationId)
  - _Requirements: 1.1, 1.3, 1.5, 6.3, 7.2_

- [ ] 2.2 Implement the temperament tag vocabulary enum <!-- gh:#5 -->
  - Create the `TemperamentTag` enum with the eight confirmed v1 values: `PLAYFUL`, `CALM`, `ENERGETIC`, `SHY`, `SOCIABLE`, `PROTECTIVE`, `GENTLE`, `STUBBORN`
  - Verify that JSON deserialisation rejects any value outside this set at the framework level (enum deserialization)
  - _Requirements: 3.1, 3.4_

- [ ] 2.3 Implement the domain error hierarchy and global HTTP error handler <!-- gh:#6 -->
  - Create the `DogProfileError` sealed class with subtypes covering all error cases: not found, duplicate profile, forbidden, photo limit exceeded, invalid breed, invalid temperament tags, storage failure
  - Implement a `@ControllerAdvice` that maps each subtype to the HTTP status and response body defined in the design (400, 403, 404, 409, 422, 502)
  - _Requirements: 1.2, 1.3, 2.2, 2.4, 5.2, 6.2, 7.3_

- [ ] 3. Build the persistence layer
- [ ] 3.1 (P) Create the dog profile database entity <!-- gh:#7 -->
  - Define a JPA entity for `dog_profiles` with all columns from the physical data model: id, owner_id (unique), name, breed, size, age, sex, purpose, temperament tags (stored as a PostgreSQL text array), optional pedigree and health-cert fields, completion status, and soft-delete timestamp
  - Apply `@SQLRestriction("deleted_at IS NULL")` so all repository queries automatically exclude soft-deleted rows (requirement 7.4)
  - _Requirements: 1.1, 1.4, 1.5, 1.6, 4.1, 7.4_

- [ ] 3.2 (P) Create the dog photo database entity <!-- gh:#8 -->
  - Define a JPA entity for `dog_photos` with id, foreign key to the dog profile, storage key (unique), public URL, display position, and creation timestamp
  - _Requirements: 2.1, 2.3_

- [ ] 3.3 (P) Create the profile and photo repositories with necessary queries <!-- gh:#9 -->
  - Create `DogProfileRepository` with a finder by `ownerId` and a query that returns profiles whose `deletedAt` is before a given cutoff (for the purge scheduler)
  - Create `DogPhotoRepository` with a photo count query by profile ID and a bulk-delete by profile ID
  - Add a unique database constraint on `dog_profiles.owner_id` to enforce one-profile-per-owner at the storage level
  - _Requirements: 1.2, 2.3, 7.1, 7.4_

- [ ] 4. Build the MinIO photo storage adapter
- [ ] 4.1 (P) Define the photo storage contract and wire the MinIO client bean <!-- gh:#10 -->
  - Declare the `PhotoStoragePort` interface with `upload` (returns a stored-photo value with key and public URL) and `delete` (by storage key) operations
  - Create `MinioConfig` as a Spring `@Configuration` that builds an `S3Client` bean using the `minio.*` properties, path-style access enabled, and static credentials — consistent with the `HttpClientConfig` pattern already in the project
  - _Requirements: 2.1, 2.5_

- [ ] 4.2 (P) Implement the MinIO storage adapter with upload, delete, and failure handling <!-- gh:#11 -->
  - Implement `MinioPhotoStorageAdapter` using `S3Client.putObject` and `S3Client.deleteObject`
  - Use the storage key convention `dog-profiles/{profileId}/photos/{UUID}.{ext}` for all objects
  - If the `putObject` call fails, throw `StorageFailure` without persisting anything; if `deleteObject` fails after the DB record has been updated, throw `StorageFailure` so the caller can roll back (requirement 2.5)
  - _Requirements: 2.1, 2.5_

- [ ] 5. (P) Build the breed reference service <!-- gh:#12 -->
  - Implement `BreedReferenceService` with a `resolve(breedName: String)` method that performs a case-insensitive lookup against the `Breed` enum and throws `InvalidBreed` for unknown values
  - Expose a `listAll()` method that returns all valid breeds with their derived size categories, to back the reference data endpoint
  - Requires Task 2.1 (Breed enum) to be complete
  - _Requirements: 1.3, 1.5_

- [ ] 6. Build the core dog profile service
- [ ] 6.1 Implement profile creation <!-- gh:#13 -->
  - Validate that the requesting owner does not already have a profile; throw `DuplicateProfile` if one exists
  - Resolve and validate the breed via `BreedReferenceService`; derive and store the size category automatically
  - Persist all mandatory and optional fields; set initial `completionStatus` to `INCOMPLETE` (photo not yet uploaded)
  - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6_

- [ ] 6.2 Implement profile retrieval <!-- gh:#14 -->
  - Return the full profile (including derived size, photos, completion status) when fetched by profile ID or by owner ID
  - Throw `NotFound` for profiles that do not exist or have been soft-deleted (transparent to callers)
  - Allow any authenticated user to read any profile (no ownership restriction on reads)
  - _Requirements: 5.1, 5.2, 5.3_

- [ ] 6.3 Implement profile update with async domain event emission <!-- gh:#15 -->
  - Apply partial field updates; reject attempts to modify another owner's profile with `Forbidden`
  - Re-derive and store the size category whenever the breed changes
  - Replace the temperament tag set atomically (full replacement, not merge)
  - After persisting changes, publish `DogProfileUpdatedEvent` asynchronously when breed or temperament tags changed; the update response must be returned to the caller without waiting for event processing
  - Re-evaluate and persist completion status after every update (revert to `INCOMPLETE` if a mandatory field is removed)
  - _Requirements: 3.3, 3.5, 6.1, 6.2, 6.3, 6.4_

- [ ] 6.4 Implement photo attachment and detachment within the profile <!-- gh:#16 -->
  - Before delegating the upload to `PhotoStoragePort`, check that the profile has fewer than five photos; throw `PhotoLimitExceeded` if at the limit
  - After a successful upload, persist the photo record and re-evaluate completion status (first photo triggers transition to `COMPLETE` if other fields are present)
  - For photo deletion, call `PhotoStoragePort.delete` first; only remove the database record after storage confirms deletion; propagate `StorageFailure` and leave the DB record untouched if storage fails
  - _Requirements: 2.1, 2.3, 2.4, 2.5, 2.6_

- [ ] 6.5 Implement profile soft deletion with domain event and ownership check <!-- gh:#17 -->
  - Reject deletion attempts by non-owners with `Forbidden`
  - Record `deletedAt` timestamp on the profile entity; do not physically delete the row
  - Publish `DogProfileDeletedEvent` asynchronously after the soft delete is committed
  - All subsequent reads of the deleted profile must return `NotFound` (enforced by the `@SQLRestriction` applied in task 3.1)
  - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [ ] 6.6 Implement profile completion evaluation and status reporting <!-- gh:#18 -->
  - Encapsulate the completion rule: a profile is `COMPLETE` if and only if name, breed, age, sex, and purpose are all present and at least one photo is attached
  - Expose the result as a `ProfileStatusResponse` carrying the current status and the list of missing required field names (empty list when complete)
  - Call this evaluation after every mutation (create, update, add/remove photo) and persist the computed status
  - _Requirements: 4.1, 4.3, 4.4, 2.6_

- [ ] 7. Build the REST API controllers
- [ ] 7.1 Implement the dog profile controller <!-- gh:#19 -->
  - Wire all profile CRUD operations: create (`POST /api/v1/dogs`), retrieve by ID (`GET /api/v1/dogs/{id}`), retrieve own profile (`GET /api/v1/dogs/me`), update (`PATCH /api/v1/dogs/me`), delete (`DELETE /api/v1/dogs/me`)
  - Add the completion status endpoint (`GET /api/v1/dogs/me/status`) that returns `ProfileStatusResponse`
  - Add reference data endpoints: list all valid breeds (`GET /api/v1/dogs/breeds`) and list all temperament tags (`GET /api/v1/dogs/temperament-tags`)
  - Annotate all endpoints `// TODO: @PreAuthorize("isAuthenticated()")` and extract `ownerId` from the authenticated principal once Spring Security is fully wired
  - _Requirements: 1.1, 3.2, 4.2, 4.4, 5.1, 5.3, 6.1, 6.2, 7.3_

- [ ] 7.2 Implement the dog photo controller <!-- gh:#20 -->
  - Add photo upload endpoint (`POST /api/v1/dogs/me/photos`) accepting `multipart/form-data`; validate content-type (JPEG / PNG / WebP) and file size (≤ 5 MB) before delegating to service and storage
  - Add photo delete endpoint (`DELETE /api/v1/dogs/me/photos/{photoId}`)
  - Return the updated `DogProfileResponse` on both operations so callers see the current photo list and completion status
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [ ] 8. (P) Implement the photo purge background job <!-- gh:#21 -->
  - Create `PhotoPurgeScheduler` scheduled to run daily at 02:00 via `@Scheduled(cron = "0 0 2 * * *")`
  - Query for profiles where `deletedAt` is older than 30 days; for each, call `PhotoStoragePort.delete` for every associated photo, then remove the photo records from the database
  - Log a structured summary after each run (profiles processed, objects deleted, failures) to satisfy observability requirements
  - The job must be re-runnable without side effects: if a photo object is already absent from MinIO, treat it as successfully deleted and continue
  - Can run in parallel with Task 7 — operates on `PhotoPurgeScheduler` alone with no controller file overlap
  - _Requirements: 7.1_

- [ ] 9. Write tests
- [ ] 9.1 (P) Write unit tests for the core domain logic <!-- gh:#22 -->
  - Test `BreedReferenceService`: valid breed resolution (case-insensitive), unknown breed throws `InvalidBreed`, `listAll()` returns all enum values
  - Test `DogProfileService` completion evaluation: all mandatory fields + photo → `COMPLETE`; missing any single field → `INCOMPLETE`; removing a field from a complete profile reverts to `INCOMPLETE`
  - Test `MinioPhotoStorageAdapter` with a mocked `S3Client`: verify `putObject` parameters, storage key format, and `StorageFailure` thrown on S3 error
  - Test event publication: verify `DogProfileUpdatedEvent` is published when breed or tags change, but not on name-only updates; verify `DogProfileDeletedEvent` on soft delete
  - _Requirements: 1.5, 4.1, 4.3, 6.3, 7.2_

- [ ] 9.2 (P) Write integration tests for the profile lifecycle and photo management <!-- gh:#23 -->
  - Use `@DataJpaTest` with TestContainers PostgreSQL: create profile → verify unique constraint blocks duplicate; update breed → verify size re-derived; soft delete → verify `@SQLRestriction` hides the row; purge query → verify correct profiles returned
  - Test full photo lifecycle: upload records persist correctly; deletion removes record; count constraint enforced at service level
  - _Requirements: 1.2, 1.4, 1.5, 2.3, 2.5, 7.1, 7.4_

- [ ] 9.3 Write controller slice tests for all endpoints <!-- gh:#24 -->
  - Use `@WebMvcTest` slices for `DogProfileController` and `DogPhotoController`
  - Cover happy paths and error branches: 409 on duplicate create, 403 on cross-owner update/delete, 404 on missing profile, 400 on invalid breed / unknown tag, 422 on photo limit exceeded, 502 on storage failure
  - Verify the status endpoint returns `missingFields` as an empty list for a complete profile
  - _Requirements: 1.2, 2.2, 2.4, 4.4, 5.2, 6.2, 7.3_
