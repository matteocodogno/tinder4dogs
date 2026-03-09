# Requirements Document

## Introduction

The **Dog Profile** feature (F-02) is the core entity of the Tinder4Dogs platform. It enables registered owners to create and manage a rich profile for their dog — including breed, age, sex, size, temperament tags, energy level, pedigree status, and up to five photos. The profile is the primary input to the matching and swipe experience. MVP scope: one dog per owner account.

---

## Requirements

### Requirement 1: Dog Profile Creation

**Objective:** As an owner, I want to create a profile for my dog with name, breed, age, sex, and at least one photo so that other users can assess compatibility.

#### Acceptance Criteria

1. When an authenticated owner submits a valid dog profile creation request, the Dog Profile Service shall persist the profile and return the created profile with a generated ID.
2. If the submitted profile is missing any mandatory field (name, breed, age, sex, or at least one photo), the Dog Profile Service shall reject the request with HTTP 422 and a field-level error message.
3. If the owner already has a dog profile (MVP: one dog per account), the Dog Profile Service shall reject the creation request with HTTP 409 and an error message indicating the limit has been reached.
4. When a dog profile is successfully created, the Dog Profile Service shall associate the profile with the authenticated owner's account.
5. The Dog Profile Service shall accept the following mandatory fields: `name` (string, 1–50 chars), `breed` (non-empty string), `age` (integer months, 1–240), `sex` (enum: MALE | FEMALE).
6. The Dog Profile Service shall accept the following optional fields: `size` (enum: SMALL | MEDIUM | LARGE | EXTRA_LARGE), `temperamentTags` (list of strings, max 10 tags), `energyLevel` (enum: LOW | MEDIUM | HIGH), `pedigree` (boolean, default false).

---

### Requirement 2: Photo Management

**Objective:** As an owner, I want to upload, view, and delete photos for my dog's profile so that other users can see what my dog looks like.

#### Acceptance Criteria

1. When an owner uploads a photo to their dog profile, the Dog Profile Service shall store the photo and associate it with the profile, returning the photo URL.
2. If an owner attempts to upload a photo when the profile already has 5 photos, the Dog Profile Service shall reject the request with HTTP 422 and an error indicating the maximum limit of 5 photos has been reached.
3. If an owner attempts to create a dog profile without at least one photo, the Dog Profile Service shall reject the request with HTTP 422 indicating a photo is required.
4. When an owner deletes a photo and the profile has more than one photo, the Dog Profile Service shall remove the photo and return the updated profile.
5. If an owner attempts to delete the last remaining photo from a profile, the Dog Profile Service shall reject the request with HTTP 422 indicating at least one photo must remain.
6. The Dog Profile Service shall accept image files in JPEG and PNG formats only; if an unsupported format is uploaded, it shall return HTTP 415.
7. The Dog Profile Service shall enforce a maximum file size of 10 MB per photo; if exceeded, it shall return HTTP 413.

---

### Requirement 3: Profile Retrieval

**Objective:** As an owner, I want to view my dog's profile and as a user of the swipe feature I want to view other dogs' profiles so that I can assess compatibility.

#### Acceptance Criteria

1. When an authenticated owner requests their own dog profile, the Dog Profile Service shall return the full profile including all fields, photos, and metadata.
2. When an authenticated user requests another owner's dog profile (e.g. during swipe), the Dog Profile Service shall return only the public-facing fields: name, breed, age, sex, size, temperamentTags, energyLevel, pedigree, photos, and approximate distance — never exact coordinates.
3. If a requested dog profile does not exist, the Dog Profile Service shall return HTTP 404.
4. The Dog Profile Service shall never include the owner's exact geolocation coordinates in any profile response visible to other users.
5. While an owner's account is deactivated, the Dog Profile Service shall not return that owner's dog profile in any public listing or swipe deck.

---

### Requirement 4: Profile Update

**Objective:** As an owner, I want to update my dog's profile attributes so that the information remains accurate over time.

#### Acceptance Criteria

1. When an authenticated owner submits a valid partial or full update to their dog profile, the Dog Profile Service shall persist the changes and return the updated profile.
2. If an update request would leave a mandatory field empty or null (name, breed, age, sex), the Dog Profile Service shall reject the request with HTTP 422 and a field-level error.
3. If an unauthenticated user or a user who does not own the profile attempts to update it, the Dog Profile Service shall return HTTP 403.
4. When a profile is updated, the Dog Profile Service shall record an `updatedAt` timestamp.

---

### Requirement 5: Profile Deletion

**Objective:** As an owner, I want to delete my dog's profile so that my data is removed from the platform (GDPR right to erasure).

#### Acceptance Criteria

1. When an authenticated owner requests deletion of their dog profile, the Dog Profile Service shall soft-delete the profile and all associated photos and return HTTP 204.
2. When a dog profile is soft-deleted, the Dog Profile Service shall ensure the profile no longer appears in any swipe deck or public listing within 5 minutes.
3. If an unauthenticated user or a non-owner attempts to delete the profile, the Dog Profile Service shall return HTTP 403.
4. The Dog Profile Service shall complete hard deletion of owner data within 30 days of a deletion request, in compliance with GDPR.

---

### Requirement 6: Profile Validation & Data Integrity

**Objective:** As the system, I need to enforce data integrity rules so that profiles are consistent and usable for matching.

#### Acceptance Criteria

1. The Dog Profile Service shall validate that `age` is an integer between 1 and 240 (months); if out of range, it shall return HTTP 422 with a descriptive error.
2. The Dog Profile Service shall validate that `name` does not exceed 50 characters and is not blank.
3. The Dog Profile Service shall validate that `temperamentTags` contains no more than 10 items and each tag does not exceed 30 characters.
4. If any enum field (`sex`, `size`, `energyLevel`) is submitted with an invalid value, the Dog Profile Service shall return HTTP 422 with a descriptive error identifying the invalid field.
5. The Dog Profile Service shall persist all profile data in a transactional manner; if any part of the save operation fails, the entire operation shall be rolled back.

---

## Non-Functional Requirements

### Performance

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-P01 | Profile retrieval latency | GET /dog-profiles/{id} < 200ms at P95 | P1 | Req 3 |
| NFR-P02 | Profile creation latency | POST /dog-profiles < 500ms at P95 (excluding photo upload) | P1 | Req 1 |
| NFR-P03 | Photo upload latency | Single photo upload < 3s at P95 for files ≤ 10MB on a 4G connection | P1 | Req 2 |

### Security

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-S01 | Owner-scoped access | Profile write/delete operations require authenticated owner identity; enforced at service layer | P0 | Req 1, 4, 5 |
| NFR-S02 | Location privacy | Exact coordinates must never appear in any API response visible to third parties | P0 | Req 3 |
| NFR-S03 | Photo access control | Photo URLs must be scoped; unauthenticated access to private photos must return 403 | P1 | Req 2 |

### Scalability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-SC01 | Concurrent profile loads | Support 10,000 DAU with ≤ 500ms P95 profile load | P1 | Req 3 |

### Reliability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-R01 | Transactional writes | Profile creation/update/deletion must be ACID-compliant; no partial saves | P0 | Req 6 |

### Observability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-O01 | Structured logging | All create/update/delete operations shall emit structured log entries with ownerId, profileId, and operation type | P1 | Req 1, 4, 5 |
| NFR-O02 | Metrics | Profile creation rate and error rate tracked and accessible via monitoring dashboard | P1 | Req 1 |

### Usability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-U01 | Error messages | All HTTP 4xx responses must include a machine-readable `field` name and a human-readable `message` | P1 | Req 1, 2, 4, 6 |

### Constraints

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-C01 | Single dog per owner (MVP) | One dog profile per owner account enforced at service and DB level | P0 | Req 1 |
| NFR-C02 | Stack alignment | Implementation must use Kotlin data classes, Spring Boot JPA, PostgreSQL — no deviations | P0 | All |
| NFR-C03 | GDPR erasure | Hard deletion completed within 30 days of owner request | P0 | Req 5 |

---

## Open Questions

| # | Question | Impact | Owner | Status |
|---|----------|--------|-------|--------|
| 1 | Should temperament tags be a free-form list or selected from a controlled vocabulary? | Affects validation logic, UI, and matching algorithm input quality | Product | Open |
| 2 | Where are photos stored? (local filesystem, object storage like S3, DB blob) | Determines photo URL strategy and upload endpoint design | Tech | Open |
| 3 | Should breed be a free-form string or validated against an enumerated breed list? | Affects matching accuracy and search filter consistency | Product / Tech | Open |
| 4 | Is a "profile completeness score" or nudging mechanism needed to drive the >70% completion rate metric? | May require additional UI/API fields to track completion progress | Product | Open |
| 5 | How should deletion interact with existing active matches and chat threads? | Cascade behaviour must be defined to avoid orphaned match/chat data | Tech / Product | Open |
