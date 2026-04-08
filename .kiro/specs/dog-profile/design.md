# Technical Design — Dog Profile

---

## Overview

The **Dog Profile** feature introduces the `dogprofile` domain to PawMatch — a greenfield domain within the existing Spring Boot monolith. It provides the persistent aggregate that drives compatibility scoring, the matching feed, and post-match communication. Without a complete dog profile an owner cannot access any matchmaking functionality.

**Purpose**: Enable owners to create, manage, and delete a single dog profile including breed, age, sex, purpose, temperament tags, and photos. Size is derived automatically from breed via a controlled enum.

**Users**: All authenticated owners (casual owners, breeders).

**Impact**: Introduces a new `dogprofile/` domain package, two JPA entities (`DogProfileEntity`, `DogPhotoEntity`), MinIO S3 photo storage integration, and domain event publication for downstream compatibility-score recalculation.

### Goals

- Full CRUD lifecycle for a single dog profile per owner account
- Photo upload/delete against local MinIO (S3-compatible), max 5 photos
- Profile-completion gate enforced before matching feed access
- Async `DogProfileUpdatedEvent` / `DogProfileDeletedEvent` for downstream scoring

### Non-Goals

- Multi-dog profiles per account (v2)
- Auth/JWT implementation (prerequisite feature, separate domain)
- Matching feed, chat, or compatibility scoring (consume events from this domain)
- Liquibase migration strategy (follow-up task; current `ddl-auto: update` stays for dev)

---

## Architecture

### Existing Architecture Context

The codebase follows a domain-driven layered monolith: `<domain>/model/ → service/ → presentation/`. No dog-profile persistence exists today — `match.model.Dog` is a transient in-memory data class and will be superseded by this domain. Spring Security and AWS SDK v2 are absent from `pom.xml` and must be added.

### Architecture Pattern & Boundary Map

Selected pattern: **Layered domain module** — consistent with existing `match/` and `support/` domains. The one architectural exception is the `PhotoStoragePort` interface (a single outbound port) to keep photo storage swappable without touching service logic.

```mermaid
graph TB
    Client --> DogProfileController
    Client --> DogPhotoController
    DogProfileController --> DogProfileService
    DogPhotoController --> DogProfileService
    DogPhotoController --> PhotoStoragePort
    DogProfileService --> DogProfileRepository
    DogProfileService --> DogPhotoRepository
    DogProfileService --> BreedReferenceService
    DogProfileService --> EventPublisher
    PhotoStoragePort --> MinioAdapter
    MinioAdapter --> MinIO
    DogProfileRepository --> PostgreSQL
    DogPhotoRepository --> PostgreSQL
    EventPublisher --> MatchingDomain
    PhotoPurgeScheduler --> DogProfileRepository
    PhotoPurgeScheduler --> PhotoStoragePort
```

Key decisions captured in `research.md`:
- `PhotoStoragePort` interface decouples MinIO from the service; allows unit testing without storage infrastructure.
- `ApplicationEventPublisher` + `@Async` delivers domain events with zero additional broker infrastructure.
- `Breed` Kotlin enum with embedded `sizeCategory` provides compile-time breed validation and size derivation in one construct.

### Technology Stack

| Layer | Choice / Version | Role in Feature |
|-------|-----------------|-----------------|
| Backend | Spring Boot 4.0.4, Kotlin 2.2.21 | REST controllers, service logic, JPA persistence |
| Persistence | Spring Data JPA + Hibernate (PostgreSQL) | `DogProfileEntity`, `DogPhotoEntity` CRUD + soft delete |
| Object Storage | AWS SDK v2 `S3Client` (new dep) | Photo upload/delete against local MinIO |
| Async Events | Spring `ApplicationEventPublisher` + `@Async` | Domain events for score recalculation |
| Validation | Spring Boot Validation (Bean Validation) | Request validation at controller boundary |
| Auth | Spring Security (new dep — prerequisite) | JWT principal injection (`ownerId` from `sub` claim) |
| Scheduling | Spring `@Scheduled` | 30-day hard photo purge after soft delete |

New Maven dependencies required (details in implementation task):
- `software.amazon.awssdk:s3` (AWS SDK v2)
- `org.springframework.boot:spring-boot-starter-security`

---

## System Flows

### Photo Upload

```mermaid
sequenceDiagram
    participant Client
    participant DogPhotoController
    participant DogProfileService
    participant PhotoStoragePort
    participant MinIO
    participant DB as PostgreSQL

    Client->>DogPhotoController: POST /api/v1/dogs/me/photos
    DogPhotoController->>DogProfileService: validatePhotoLimit(ownerId)
    DogProfileService->>DB: SELECT COUNT FROM dog_photos WHERE profile_id
    DB-->>DogProfileService: count
    DogPhotoController->>PhotoStoragePort: upload(profileId, file)
    PhotoStoragePort->>MinIO: putObject(bucket, key, bytes)
    MinIO-->>PhotoStoragePort: ETag confirmed
    PhotoStoragePort-->>DogPhotoController: StoredPhoto(key, url)
    DogPhotoController->>DogProfileService: addPhoto(profileId, key, url)
    DogProfileService->>DB: INSERT dog_photos; UPDATE completion_status
    DogProfileService-->>DogPhotoController: DogProfileResponse
    DogPhotoController-->>Client: 201 Created
```

If MinIO fails after `putObject` succeeds but before DB insert: `PhotoStoragePort` rolls back the object and returns `StorageFailure`; no DB record is created (2.5, NFR-R01).

### Profile Update with Async Event

```mermaid
sequenceDiagram
    participant Client
    participant DogProfileController
    participant DogProfileService
    participant DB as PostgreSQL
    participant EventPublisher
    participant MatchingDomain

    Client->>DogProfileController: PATCH /api/v1/dogs/me
    DogProfileController->>DogProfileService: updateProfile(ownerId, request)
    DogProfileService->>DB: UPDATE dog_profiles SET ..., updated_at = NOW()
    DB-->>DogProfileService: committed
    DogProfileService-->>DogProfileController: DogProfileResponse
    DogProfileController-->>Client: 200 OK (immediate)
    DogProfileService-)EventPublisher: publishEvent(DogProfileUpdatedEvent) @Async
    EventPublisher-)MatchingDomain: onDogProfileUpdated(event)
```

### Profile Lifecycle States

```mermaid
stateDiagram-v2
    [*] --> INCOMPLETE: createProfile
    INCOMPLETE --> COMPLETE: all mandatory fields present AND photo uploaded
    COMPLETE --> INCOMPLETE: mandatory field cleared on update
    COMPLETE --> DELETED: deleteProfile
    INCOMPLETE --> DELETED: deleteProfile
    DELETED --> [*]
```

---

## Requirements Traceability

| Requirement | Summary | Components | Key Interface |
|-------------|---------|------------|---------------|
| 1.1 | Create profile — mandatory fields | `DogProfileController`, `DogProfileService`, `DogProfileRepository` | `DogProfileService.createProfile` |
| 1.2 | Reject duplicate profile | `DogProfileService`, `DogProfileRepository` | DB UNIQUE on `owner_id`; `DuplicateProfile` error |
| 1.3 | Field validation | `DogProfileController` (Bean Validation) | `CreateDogProfileRequest` constraints |
| 1.4 | Associate with one owner | `DogProfileService` | `owner_id` stored on entity |
| 1.5 | Derive size from breed | `BreedReferenceService`, `DogProfileService` | `BreedReferenceService.deriveSize` |
| 1.6 | Optional health/breeding fields | `DogProfileService`, `DogProfileRepository` | nullable columns in entity |
| 2.1 | Upload photo to MinIO | `DogPhotoController`, `PhotoStoragePort`, `DogProfileService` | `PhotoStoragePort.upload` |
| 2.2 | Validate format + size | `DogPhotoController` | `MultipartFile` validation |
| 2.3–2.4 | Enforce 1–5 photo limit | `DogProfileService` | `DogProfileService.addPhoto` |
| 2.5 | Atomic photo delete | `DogProfileService`, `PhotoStoragePort` | `DogProfileService.deletePhoto` |
| 2.6 | Incomplete flag without photo | `DogProfileService` | `ProfileStatusResponse.missingFields` |
| 3.1–3.4 | Fixed tag vocabulary + validation | `TemperamentTag` enum, `DogProfileController` | enum deserialization rejects unknowns |
| 3.5 | Full-replacement tag update | `DogProfileService` | `updateProfile` replaces tag set atomically |
| 4.1–4.4 | Completion gate + status endpoint | `DogProfileService`, `DogProfileController` | `DogProfileService.getProfileStatus` |
| 5.1–5.3 | Profile retrieval | `DogProfileController`, `DogProfileService` | `DogProfileService.getProfile` |
| 6.1–6.2 | Update + ownership check | `DogProfileService` | `updateProfile`; `Forbidden` error |
| 6.3 | Async domain event on update | `DogProfileService`, `EventPublisher` | `DogProfileUpdatedEvent` |
| 6.4 | Revert to INCOMPLETE | `DogProfileService` (internal) | `evaluateCompletionStatus()` |
| 7.1 | Soft delete + schedule purge | `DogProfileService`, `PhotoPurgeScheduler` | `deleteProfile`; `@Scheduled` |
| 7.2 | Cascade match invalidation | `EventPublisher` → Matching domain | `DogProfileDeletedEvent` |
| 7.3 | Forbid cross-owner delete | `DogProfileService` | `Forbidden` error |
| 7.4 | 404 after soft delete | `DogProfileRepository` | `@SQLRestriction("deleted_at IS NULL")` |

---

## Components and Interfaces

### Summary Table

| Component | Layer | Intent | Req Coverage | Key Dependencies | Contracts |
|-----------|-------|--------|--------------|-----------------|-----------|
| `DogProfileController` | Presentation | Profile CRUD + tag/breed reference endpoints | 1, 4, 5, 6, 7 | `DogProfileService` (P0) | API |
| `DogPhotoController` | Presentation | Photo upload/delete endpoints | 2 | `DogProfileService`, `PhotoStoragePort` (P0) | API |
| `DogProfileService` | Domain | Aggregate root logic; completion, events | All | `DogProfileRepository`, `BreedReferenceService`, `EventPublisher` (P0) | Service, Event |
| `PhotoStoragePort` | Domain (port) | Abstraction over object storage | 2, 7 | `MinioPhotoStorageAdapter` (P0) | Service |
| `MinioPhotoStorageAdapter` | Infrastructure | MinIO S3 write/delete | 2, 7 | `S3Client` (P0) | — |
| `BreedReferenceService` | Domain | Breed validation + size derivation | 1.5, 3.x | `Breed` enum (P0) | Service |
| `DogProfileRepository` | Persistence | JPA access to `dog_profiles` | All | PostgreSQL (P0) | — |
| `DogPhotoRepository` | Persistence | JPA access to `dog_photos` | 2, 7 | PostgreSQL (P0) | — |
| `PhotoPurgeScheduler` | Background | Hard-delete photos 30 days after soft delete | 7.1 | `DogProfileRepository`, `PhotoStoragePort` (P1) | Batch |
| `MinioConfig` | Config | `S3Client` Spring bean | 2, 7 | MinIO env vars (P0) | — |
| `AsyncConfig` | Config | Enables `@Async` dispatcher | 6.3 | — | — |

---

### Domain Layer

#### DogProfileService

| Field | Detail |
|-------|--------|
| Intent | Aggregate root service — owns profile lifecycle, completion evaluation, and domain event publication |
| Requirements | 1.1–1.6, 2.3–2.6, 3.3–3.5, 4.1–4.4, 5.1–5.3, 6.1–6.4, 7.1–7.4 |

**Responsibilities & Constraints**
- Single source of truth for profile state and `ProfileStatus` transitions
- Owns completion evaluation logic: `COMPLETE` iff mandatory fields present AND `photos.size >= 1`
- Publishes domain events **after** DB commit, never before
- Transaction boundary: each public method is `@Transactional`

**Dependencies**
- Outbound: `DogProfileRepository` — profile persistence (P0)
- Outbound: `DogPhotoRepository` — photo record persistence (P0)
- Outbound: `BreedReferenceService` — breed validation + size derivation (P0)
- Outbound: `ApplicationEventPublisher` — async domain events (P1)

**Contracts**: Service [x] / Event [x]

##### Service Interface

```kotlin
interface DogProfileService {
    fun createProfile(ownerId: UUID, request: CreateDogProfileRequest): DogProfileResponse
    fun getProfile(profileId: UUID): DogProfileResponse
    fun getOwnProfile(ownerId: UUID): DogProfileResponse
    fun updateProfile(ownerId: UUID, request: UpdateDogProfileRequest): DogProfileResponse
    fun deleteProfile(ownerId: UUID)
    fun getProfileStatus(ownerId: UUID): ProfileStatusResponse
    fun addPhoto(ownerId: UUID, storedPhoto: StoredPhoto): DogProfileResponse
    fun deletePhoto(ownerId: UUID, photoId: UUID): DogProfileResponse
}
```

- Preconditions: `ownerId` is a valid, authenticated principal UUID
- Postconditions: returned `DogProfileResponse` always reflects the current persisted state
- Invariants: `dog_profiles.owner_id` is unique; `dog_photos` count ≤ 5 per profile

##### Event Contract

```kotlin
data class DogProfileUpdatedEvent(
    val profileId: UUID,
    val ownerId: UUID,
    val changedFields: Set<String>,
    val correlationId: String
)

data class DogProfileDeletedEvent(
    val profileId: UUID,
    val ownerId: UUID,
    val correlationId: String
)
```

- Published: `DogProfileUpdatedEvent` on breed/temperament-tag changes (6.3); `DogProfileDeletedEvent` on soft delete (7.2)
- Delivery: Spring `ApplicationEventPublisher` + `@Async` listener — at-most-once, best-effort
- Ordering: no ordering guarantee; consumers must be idempotent

**Implementation Notes**
- Integration: emit `DogProfileUpdatedEvent` only when `changedFields` contains `"breed"` or `"temperamentTags"` to avoid unnecessary recalculations
- Validation: `evaluateCompletionStatus()` called after every mutation; never expose `DELETED` profiles via `getProfile` (throw `NotFound`)
- Risks: event lost on JVM crash — see research.md Decision 2 for mitigation rationale

---

#### BreedReferenceService

| Field | Detail |
|-------|--------|
| Intent | Validates breed names against the controlled `Breed` enum and derives size category |
| Requirements | 1.5, 1.3, 6.3 |

**Contracts**: Service [x]

##### Service Interface

```kotlin
interface BreedReferenceService {
    fun resolve(breedName: String): Breed
    fun listAll(): List<Breed>
}
```

- Preconditions: `breedName` is a non-blank string
- Postconditions: returns `Breed` enum with embedded `sizeCategory`
- Throws: `DogProfileError.InvalidBreed` if `breedName` does not match any `Breed` enum value (case-insensitive)

---

#### PhotoStoragePort

| Field | Detail |
|-------|--------|
| Intent | Outbound port abstracting object storage; implemented by `MinioPhotoStorageAdapter` |
| Requirements | 2.1, 2.5, 7.1 |

**Contracts**: Service [x]

##### Service Interface

```kotlin
interface PhotoStoragePort {
    fun upload(profileId: UUID, filename: String, contentType: String, bytes: ByteArray): StoredPhoto
    fun delete(storageKey: String)
}

data class StoredPhoto(val storageKey: String, val publicUrl: String)
```

- `upload`: if the `putObject` call to MinIO fails, throws `DogProfileError.StorageFailure`; caller (controller) does not persist any DB record in that case
- `delete`: if MinIO delete fails, throws `StorageFailure`; caller rolls back DB record update (2.5)

**Implementation Notes**
- `MinioPhotoStorageAdapter` uses `S3Client.putObject` / `deleteObject` with `pathStyleAccessEnabled(true)`
- Storage key convention: `dog-profiles/{profileId}/photos/{UUID}.{ext}`
- Risks: no rollback if DB commit succeeds but MinIO delete fails — see 2.5 error strategy

---

### Presentation Layer

#### DogProfileController

| Field | Detail |
|-------|--------|
| Intent | REST endpoints for profile CRUD, status, and reference data |
| Requirements | 1, 3.2, 4.4, 5, 6, 7 |

**Contracts**: API [x]

##### API Contract

| Method | Endpoint | Request | Response | Errors |
|--------|----------|---------|----------|--------|
| POST | `/api/v1/dogs` | `CreateDogProfileRequest` | `DogProfileResponse` 201 | 400, 409 |
| GET | `/api/v1/dogs/{id}` | — | `DogProfileResponse` 200 | 401, 404 |
| GET | `/api/v1/dogs/me` | — | `DogProfileResponse` 200 | 401, 404 |
| GET | `/api/v1/dogs/me/status` | — | `ProfileStatusResponse` 200 | 401 |
| PATCH | `/api/v1/dogs/me` | `UpdateDogProfileRequest` | `DogProfileResponse` 200 | 400, 401, 403 |
| DELETE | `/api/v1/dogs/me` | — | 204 No Content | 401, 403 |
| GET | `/api/v1/dogs/breeds` | — | `List<BreedResponse>` 200 | — |
| GET | `/api/v1/dogs/temperament-tags` | — | `List<String>` 200 | — |

---

#### DogPhotoController

| Field | Detail |
|-------|--------|
| Intent | REST endpoints for photo upload and deletion |
| Requirements | 2 |

**Contracts**: API [x]

##### API Contract

| Method | Endpoint | Request | Response | Errors |
|--------|----------|---------|----------|--------|
| POST | `/api/v1/dogs/me/photos` | `multipart/form-data` (file) | `DogProfileResponse` 201 | 400, 401, 422, 502 |
| DELETE | `/api/v1/dogs/me/photos/{photoId}` | — | `DogProfileResponse` 200 | 401, 403, 404, 502 |

Validation at controller boundary:
- Content-Type must be `image/jpeg`, `image/png`, or `image/webp`
- File size must not exceed 5 MB; reject with 400 before calling storage

---

### Background Jobs

#### PhotoPurgeScheduler

| Field | Detail |
|-------|--------|
| Intent | Hard-deletes MinIO objects and DB photo records for profiles soft-deleted > 30 days ago |
| Requirements | 7.1 |

**Contracts**: Batch [x]

##### Batch Contract

- **Trigger**: `@Scheduled(cron = "0 0 2 * * *")` — daily at 02:00
- **Input**: `DogProfileRepository.findSoftDeletedBefore(cutoff: Instant)` — returns profiles where `deleted_at < NOW() - 30 days`
- **Output**: `PhotoStoragePort.delete(storageKey)` for each photo; then `DogPhotoRepository.deleteByProfileId(profileId)`
- **Idempotency**: re-running after a partial failure safely re-attempts `delete` calls for any remaining photos

---

## Data Models

### Domain Model

`DogProfile` is the aggregate root. `DogPhoto` is a child entity owned by `DogProfile`. Breed and temperament are value types (enum). Domain events are published by `DogProfileService`, not by the entity.

```mermaid
erDiagram
    DogProfile ||--o{ DogPhoto : "has 0-5"
    DogProfile {
        UUID id PK
        UUID ownerId UK
        string name
        Breed breed
        SizeCategory size
        int age
        Sex sex
        Purpose purpose
        Set_TemperamentTag temperamentTags
        string pedigreeNumber
        string healthCertRef
        ProfileStatus completionStatus
        Instant createdAt
        Instant updatedAt
        Instant deletedAt
    }
    DogPhoto {
        UUID id PK
        UUID dogProfileId FK
        string storageKey UK
        string publicUrl
        int position
        Instant createdAt
    }
```

**Invariants**:
- `ownerId` uniqueness — one profile per owner
- `photos.size` ∈ [0, 5]
- `size` is always derived from `breed`, never set directly
- `completionStatus = COMPLETE` iff `name`, `breed`, `age`, `sex`, `purpose` non-null AND `photos.size >= 1`
- A profile with `deletedAt != null` is treated as non-existent by all read operations

### Physical Data Model

**Table `dog_profiles`**:

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | PK, default `gen_random_uuid()` |
| `owner_id` | `UUID` | NOT NULL, UNIQUE |
| `name` | `VARCHAR(100)` | NOT NULL |
| `breed` | `VARCHAR(100)` | NOT NULL |
| `size` | `VARCHAR(10)` | NOT NULL |
| `age` | `SMALLINT` | NOT NULL, CHECK >= 0 |
| `sex` | `VARCHAR(10)` | NOT NULL |
| `purpose` | `VARCHAR(20)` | NOT NULL |
| `temperament_tags` | `TEXT[]` | NOT NULL, DEFAULT `'{}'` |
| `pedigree_number` | `VARCHAR(100)` | nullable |
| `health_cert_ref` | `VARCHAR(200)` | nullable |
| `completion_status` | `VARCHAR(20)` | NOT NULL, DEFAULT `'INCOMPLETE'` |
| `created_at` | `TIMESTAMPTZ` | NOT NULL |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL |
| `deleted_at` | `TIMESTAMPTZ` | nullable |

Indexes: `UNIQUE (owner_id)`, `(deleted_at)` for purge query.

**Table `dog_photos`**:

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | PK |
| `dog_profile_id` | `UUID` | NOT NULL, FK → `dog_profiles(id)` |
| `storage_key` | `VARCHAR(500)` | NOT NULL, UNIQUE |
| `public_url` | `VARCHAR(1000)` | NOT NULL |
| `position` | `SMALLINT` | NOT NULL, DEFAULT 0 |
| `created_at` | `TIMESTAMPTZ` | NOT NULL |

Index: `(dog_profile_id)`.

### Data Contracts & Integration

**Request / Response types** (Kotlin):

```kotlin
// Value types
enum class SizeCategory { SMALL, MEDIUM, LARGE, XL }
enum class Sex { MALE, FEMALE }
enum class Purpose { SOCIALISATION, BREEDING, BOTH }
enum class TemperamentTag { PLAYFUL, CALM, ENERGETIC, SHY, SOCIABLE, PROTECTIVE, GENTLE, STUBBORN }
enum class ProfileStatus { INCOMPLETE, COMPLETE, DELETED }

// API requests
data class CreateDogProfileRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val breed: String,
    @field:Min(0) @field:Max(30) val age: Int,
    val sex: Sex,
    val purpose: Purpose,
    val temperamentTags: Set<TemperamentTag> = emptySet(),
    val pedigreeNumber: String? = null,
    val healthCertRef: String? = null
)

data class UpdateDogProfileRequest(
    val name: String? = null,
    val breed: String? = null,
    val age: Int? = null,
    val sex: Sex? = null,
    val purpose: Purpose? = null,
    val temperamentTags: Set<TemperamentTag>? = null,
    val pedigreeNumber: String? = null,
    val healthCertRef: String? = null
)

// API responses
data class DogProfileResponse(
    val id: UUID,
    val ownerId: UUID,
    val name: String,
    val breed: String,
    val size: SizeCategory,
    val age: Int,
    val sex: Sex,
    val purpose: Purpose,
    val temperamentTags: Set<TemperamentTag>,
    val photos: List<PhotoResponse>,
    val pedigreeNumber: String?,
    val healthCertRef: String?,
    val completionStatus: ProfileStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class PhotoResponse(val id: UUID, val publicUrl: String, val position: Int)

data class ProfileStatusResponse(
    val status: ProfileStatus,
    val missingFields: List<String>
)
```

---

## Error Handling

### Error Strategy

All domain errors are represented as a sealed class hierarchy; the `@ControllerAdvice` (global exception handler) maps each subtype to the appropriate HTTP status.

```kotlin
sealed class DogProfileError : RuntimeException() {
    data class NotFound(val profileId: UUID) : DogProfileError()
    data class ProfileNotFound(val ownerId: UUID) : DogProfileError()
    data class DuplicateProfile(val ownerId: UUID) : DogProfileError()
    data class Forbidden(val requesterId: UUID) : DogProfileError()
    data class PhotoLimitExceeded(val max: Int) : DogProfileError()
    data class InvalidBreed(val breed: String) : DogProfileError()
    data class InvalidTemperamentTags(val invalid: List<String>) : DogProfileError()
    data class StorageFailure(val cause: String) : DogProfileError()
}
```

### Error Categories and Responses

| Error | HTTP | Notes |
|-------|------|-------|
| `NotFound`, `ProfileNotFound` | 404 | Includes soft-deleted profiles (7.4) |
| `DuplicateProfile` | 409 | DB UNIQUE constraint also enforces this |
| `Forbidden` | 403 | Cross-owner write attempt |
| Bean Validation failure | 400 | Field-level messages from `BindingResult` |
| `InvalidBreed` | 400 | Lists valid breeds in error body |
| `InvalidTemperamentTags` | 400 | Lists invalid tag names |
| `PhotoLimitExceeded` | 422 | Includes current count and max (5) |
| `StorageFailure` | 502 | MinIO unreachable or rejected |

### Monitoring

- All 5xx responses logged with `traceId` via `kotlin-logging-jvm` (NFR-O01)
- Domain events logged at `INFO` level with `correlationId` before publication (NFR-O02)
- `PhotoPurgeScheduler` logs a summary (profiles purged, objects deleted, failures) on each run

---

## Testing Strategy

### Unit Tests

- `DogProfileService` — completion evaluation logic; ownership validation; event publication trigger (using `@RecordApplicationEvents`)
- `BreedReferenceService` — breed resolution (valid, invalid, case-insensitive); size derivation for all `SizeCategory` values
- `MinioPhotoStorageAdapter` — mock `S3Client`; verify `putObject` / `deleteObject` call parameters and rollback on failure
- `PhotoPurgeScheduler` — verify purge selects only profiles with `deleted_at < cutoff`; verify storage delete called before DB delete

### Integration Tests

- `DogProfileController` — `@WebMvcTest` slices for all endpoints: happy path + error cases (409 duplicate, 403 forbidden, 404 not found, 400 validation)
- `DogPhotoController` — photo upload with valid/invalid file; limit enforcement; atomic delete on storage failure
- `DogProfileRepository` — `@DataJpaTest` with TestContainers PostgreSQL: soft-delete filter, unique constraint, completion status transitions
- End-to-end profile lifecycle: create → upload photo → update breed → verify event published → soft delete → verify 404

### Performance Tests

- `GET /api/v1/dogs/{id}` — verify p95 < 200 ms under 100 concurrent reads (NFR-P01)
- `POST /api/v1/dogs/me/photos` with 5 MB file — verify p95 < 3 s (NFR-P02)

---

## Security Considerations

- All endpoints require a valid JWT; `ownerId` extracted from the `sub` claim via `@AuthenticationPrincipal`; endpoints annotated `@PreAuthorize("isAuthenticated()")` once Spring Security is wired (NFR-S01).
- Photo uploads: content-type and size validated at controller layer before bytes reach storage; no server-side execution of uploaded content (NFR-S02).
- Ownership enforcement: `DogProfileService` compares `ownerId` from JWT principal against `dogProfile.ownerId` on every mutating operation; throws `Forbidden` on mismatch (NFR-S03).
- `deleted_at` profiles are treated as non-existent externally — no leaked tombstone data.
- MinIO access credentials stored as environment variables via direnv/Infisical (consistent with existing secret management).

---

## Performance & Scalability

- `GET /api/v1/dogs/{id}` targets p95 < 200 ms (NFR-P01): profile + photos fetched via `@EntityGraph` to avoid N+1; `owner_id` UNIQUE index ensures O(1) "my profile" lookup.
- Photo upload targets p95 < 3 s (NFR-P02): `S3Client` (synchronous) is sufficient at v1 scale; if throughput grows, replace with `S3AsyncClient`.
- Stateless service layer (NFR-SC01): no in-memory session state; MinIO and PostgreSQL are the only stateful systems; horizontal scaling of the Spring Boot pod does not require sticky sessions.
- `PhotoPurgeScheduler` runs at 02:00 daily with a low-priority thread to avoid contention with serving traffic.

---

## Supporting References

See `.kiro/specs/dog-profile/research.md` for:
- Detailed MinIO / AWS SDK v2 configuration findings
- Full domain event strategy rationale
- Architecture pattern trade-off table
- Risk register and mitigations
