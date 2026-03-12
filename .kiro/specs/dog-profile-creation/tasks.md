# Implementation Plan

- [x] 1. Domain enums
- [x] 1.1 Create `DogSize` enum (SMALL, MEDIUM, LARGE, EXTRA_LARGE) (P)
  - `src/main/kotlin/com/ai4dev/tinderfordogs/dogprofile/model/DogSize.kt`
  - _Requirements: 1.2_
- [x] 1.2 Create `DogGender` enum (MALE, FEMALE) (P)
  - `src/main/kotlin/com/ai4dev/tinderfordogs/dogprofile/model/DogGender.kt`
  - _Requirements: 1.2_

- [x] 2. Data models
- [x] 2.1 Create `DogProfile` JPA entity (P)
  - `@Entity`, table `dog_profiles`; fields: id, name, breed, size, age, gender, bio, createdAt
  - `@PrePersist` sets `createdAt`
  - `src/main/kotlin/com/ai4dev/tinderfordogs/dogprofile/model/DogProfile.kt`
  - _Requirements: 1.2, 1.3_
- [x] 2.2 Create `CreateDogProfileRequest` DTO with Bean Validation (P)
  - `@field:NotBlank`, `@field:NotNull`, `@field:Min(0)`, `@field:Max(30)`, `@field:Size`
  - `src/main/kotlin/com/ai4dev/tinderfordogs/dogprofile/model/CreateDogProfileRequest.kt`
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_
- [x] 2.3 Create `DogProfileResponse` DTO (P)
  - `src/main/kotlin/com/ai4dev/tinderfordogs/dogprofile/model/DogProfileResponse.kt`
  - _Requirements: 3.1_
- [x] 2.4 Create `ErrorResponse` DTO (P)
  - `src/main/kotlin/com/ai4dev/tinderfordogs/dogprofile/model/ErrorResponse.kt`
  - _Requirements: 3.2_

- [x] 3. Persistence layer
- [x] 3.1 Create `DogProfileRepository`
  - `interface DogProfileRepository : JpaRepository<DogProfile, Long>`
  - `src/main/kotlin/com/ai4dev/tinderfordogs/dogprofile/service/DogProfileRepository.kt`
  - _Requirements: 1.3_

- [x] 4. Service layer
- [x] 4.1 Create `DogProfileService`
  - `@Transactional fun create(request): DogProfileResponse`
  - Maps request → entity → save → response; logs creation event
  - `src/main/kotlin/com/ai4dev/tinderfordogs/dogprofile/service/DogProfileService.kt`
  - _Requirements: 1.1, 1.2, 1.3_

- [x] 5. Presentation layer
- [x] 5.1 Create `DogProfileController`
  - `POST /api/v1/dogs` → HTTP 201 with `DogProfileResponse`
  - `@ExceptionHandler(MethodArgumentNotValidException)` → HTTP 400 `ErrorResponse`
  - `src/main/kotlin/com/ai4dev/tinderfordogs/dogprofile/presentation/DogProfileController.kt`
  - _Requirements: 1.1, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 3.1, 3.2, 3.3_

- [x] 6. Tests
- [x] 6.1 `DogProfileServiceTest` — unit tests (P)
  - Happy path field mapping; null bio stored correctly
  - `src/test/kotlin/com/ai4dev/tinderfordogs/dogprofile/service/DogProfileServiceTest.kt`
  - _Requirements: 1.1, 1.2, 1.3_
- [x] 6.2 `DogProfileControllerTest` — `@WebMvcTest` tests (P)
  - 201 with all fields; 201 without optional bio; 400 missing field; 400 invalid enum; 400 age > 30; 400 age < 0
  - `src/test/kotlin/com/ai4dev/tinderfordogs/dogprofile/presentation/DogProfileControllerTest.kt`
  - _Requirements: 1.1, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 3.1, 3.2, 3.3_

- [x] 7. Database migration
- [x] 7.1 Add `spring-boot-starter-liquibase` dependency and set `ddl-auto: none`
    - `pom.xml`, `src/main/resources/application.yaml`
    - _Requirements: 1.3_
- [x] 7.2 Create Liquibase master changelog
    - `src/main/resources/db/changelog/db.changelog-master.yaml`
    - _Requirements: 1.3_
- [x] 7.3 Create migration `001-create-dog-profiles` (PostgreSQL)
    - Liquibase formatted SQL; `BIGSERIAL` PK, VARCHAR columns, `TIMESTAMPTZ created_at`; `dbms: postgresql`
    - `src/main/resources/db/changelog/migrations/001-create-dog-profiles.sql`
    - _Requirements: 1.3_
