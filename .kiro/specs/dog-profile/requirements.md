# Requirements Document

## Introduction

The **Dog Profile** feature (F-02) is the core entity of the Tinder4Dogs platform. It enables registered owners to create and manage a rich profile for their dog — including breed (from a controlled breed list), age, sex, size, temperament tags (from a controlled vocabulary), energy level, pedigree status, and up to five photos stored as DB blobs. The profile is the primary input to the matching and swipe experience. MVP scope: one dog per owner account.

---

## Reference: Controlled Vocabularies

### Temperament Tag Vocabulary

The following tags constitute the full controlled vocabulary for `temperamentTags`. A profile may carry up to 10 tags from this list.

| Tag | Description |
|-----|-------------|
| `FRIENDLY` | Sociable and approachable with people and dogs |
| `CALM` | Relaxed, low-reactivity temperament |
| `PLAYFUL` | Loves games and active interaction |
| `ENERGETIC` | High drive, needs regular vigorous exercise |
| `GENTLE` | Soft-natured, good with children and small animals |
| `CURIOUS` | Investigative, likes to explore new environments |
| `INDEPENDENT` | Self-sufficient, less prone to separation anxiety |
| `PROTECTIVE` | Alert and watchful; may be selective with strangers |
| `SOCIABLE` | Thrives in group settings with other dogs |
| `TIMID` | Shy or reserved; needs gentle handling |
| `STUBBORN` | Strong-willed; requires experienced handling |
| `AFFECTIONATE` | Highly bonded and physically demonstrative |

### Breed List

Breed is validated against a system-maintained enumerated breed list. The list is stored in the database (a `breeds` reference table) seeded at startup and is extensible without code changes. Examples: `LABRADOR_RETRIEVER`, `GOLDEN_RETRIEVER`, `GERMAN_SHEPHERD`, `BULLDOG`, `POODLE`, `BEAGLE`, `YORKSHIRE_TERRIER`, `MIXED` (catch-all for crossbreeds). The full seed list must cover all FCI-recognised groups.

---

## Requirements

### Requirement 1: Dog Profile Creation

**Objective:** As an owner, I want to create a profile for my dog with name, breed, age, sex, and at least one photo so that other users can assess compatibility.

#### Acceptance Criteria

1. When an authenticated owner submits a valid dog profile creation request, the Dog Profile Service shall persist the profile and return the created profile with a generated ID.
2. If the submitted profile is missing any mandatory field (name, breed, age, sex, or at least one photo), the Dog Profile Service shall reject the request with HTTP 422 and a field-level error message.
3. If the owner already has a dog profile (MVP: one dog per account), the Dog Profile Service shall reject the creation request with HTTP 409 and an error message indicating the limit has been reached.
4. When a dog profile is successfully created, the Dog Profile Service shall associate the profile with the authenticated owner's account.
5. The Dog Profile Service shall accept the following mandatory fields: `name` (string, 1–50 chars), `breed` (value from the system breed list), `age` (integer months, 1–240), `sex` (enum: MALE | FEMALE).
6. The Dog Profile Service shall accept the following optional fields: `size` (enum: SMALL | MEDIUM | LARGE | EXTRA_LARGE), `temperamentTags` (list of values from the controlled vocabulary, max 10), `energyLevel` (enum: LOW | MEDIUM | HIGH), `pedigree` (boolean, default false).
7. When a dog profile is created, the Dog Profile Service shall compute and store the initial `completenessScore` (see Requirement 7).

---

### Requirement 2: Photo Management

**Objective:** As an owner, I want to upload, view, and delete photos for my dog's profile so that other users can see what my dog looks like.

#### Acceptance Criteria

1. When an owner uploads a photo to their dog profile, the Dog Profile Service shall store the binary content as a DB blob, associate it with the profile, and return a retrieval URL for that photo.
2. If an owner attempts to upload a photo when the profile already has 5 photos, the Dog Profile Service shall reject the request with HTTP 422 and an error indicating the maximum limit of 5 photos has been reached.
3. If an owner attempts to create a dog profile without at least one photo, the Dog Profile Service shall reject the request with HTTP 422 indicating a photo is required.
4. When an owner deletes a photo and the profile has more than one photo, the Dog Profile Service shall remove the blob and its metadata and return the updated profile.
5. If an owner attempts to delete the last remaining photo from a profile, the Dog Profile Service shall reject the request with HTTP 422 indicating at least one photo must remain.
6. The Dog Profile Service shall accept image files in JPEG and PNG formats only; if an unsupported format is uploaded, it shall return HTTP 415.
7. The Dog Profile Service shall enforce a maximum file size of 10 MB per photo; if exceeded, it shall return HTTP 413.
8. When a dog profile is hard-deleted, the Dog Profile Service shall remove all associated photo blobs from the database in the same transaction.

---

### Requirement 3: Profile Retrieval

**Objective:** As an owner, I want to view my dog's profile and as a user of the swipe feature I want to view other dogs' profiles so that I can assess compatibility.

#### Acceptance Criteria

1. When an authenticated owner requests their own dog profile, the Dog Profile Service shall return the full profile including all fields, photos, metadata, and current `completenessScore`.
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
5. When a profile is updated, the Dog Profile Service shall recompute and persist the `completenessScore` (see Requirement 7).

---

### Requirement 5: Profile Deletion & Cascade

**Objective:** As an owner, I want to delete my dog's profile and have all my associated data removed from the platform (GDPR right to erasure).

#### Acceptance Criteria

1. When an authenticated owner requests deletion of their dog profile, the Dog Profile Service shall soft-delete the profile, all associated photos, all active matches, all swipe records, and all chat threads linked to that profile, then return HTTP 204.
2. When a dog profile is soft-deleted, the Dog Profile Service shall ensure the profile no longer appears in any swipe deck, public listing, or active match within 5 minutes.
3. If an unauthenticated user or a non-owner attempts to delete the profile, the Dog Profile Service shall return HTTP 403.
4. The Dog Profile Service shall complete hard deletion of all cascaded data (profile, photos, matches, swipe records, chat threads and messages) within 30 days of the deletion request, in compliance with GDPR.
5. When a profile is hard-deleted, the Dog Profile Service shall remove all associated photo blobs from the database in the same transaction (see Requirement 2, criterion 8).
6. If any step of the cascade soft-delete fails, the Dog Profile Service shall roll back the entire operation and return HTTP 500 with a structured error.

---

### Requirement 6: Profile Validation & Data Integrity

**Objective:** As the system, I need to enforce data integrity rules so that profiles are consistent and usable for matching.

#### Acceptance Criteria

1. The Dog Profile Service shall validate that `age` is an integer between 1 and 240 (months); if out of range, it shall return HTTP 422 with a descriptive error.
2. The Dog Profile Service shall validate that `name` does not exceed 50 characters and is not blank.
3. If `temperamentTags` contains a value not present in the controlled vocabulary, the Dog Profile Service shall return HTTP 422 identifying the invalid tag(s).
4. The Dog Profile Service shall validate that `temperamentTags` contains no more than 10 items.
5. If `breed` is not found in the system breed list, the Dog Profile Service shall return HTTP 422 with an error identifying the unrecognised breed value.
6. If any enum field (`sex`, `size`, `energyLevel`) is submitted with an invalid value, the Dog Profile Service shall return HTTP 422 with a descriptive error identifying the invalid field.
7. The Dog Profile Service shall persist all profile data in a transactional manner; if any part of the save operation fails, the entire operation shall be rolled back.

---

### Requirement 7: Profile Completeness & Nudging

**Objective:** As the product, I need to track how complete each dog profile is and surface nudges to owners so that ≥70% of profiles have all fields filled in.

#### Acceptance Criteria

1. The Dog Profile Service shall compute a `completenessScore` (integer 0–100) for every profile based on the proportion of optional fields that are filled: `size`, `temperamentTags` (≥1 tag), `energyLevel`, `pedigree`, and photos (≥3 photos for full score contribution).
2. The Dog Profile Service shall recalculate and persist `completenessScore` on every profile create and update operation.
3. When an owner retrieves their own profile and `completenessScore` is below 80, the Dog Profile Service shall include a `missingFields` list in the response, enumerating which optional fields are unfilled.
4. The Dog Profile Service shall expose a `GET /dog-profiles/me/completeness` endpoint that returns `completenessScore` and `missingFields` without fetching the full profile payload.
5. The Dog Profile Service shall never block profile creation or matching eligibility based on `completenessScore`; nudging is informational only.

---

## Non-Functional Requirements

### Performance

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-P01 | Profile retrieval latency | GET /dog-profiles/{id} < 200ms at P95 | P1 | Req 3 |
| NFR-P02 | Profile creation latency | POST /dog-profiles < 500ms at P95 (excluding photo upload) | P1 | Req 1 |
| NFR-P03 | Photo upload latency | Single photo upload < 3s at P95 for files ≤ 10MB on a 4G connection | P1 | Req 2 |
| NFR-P04 | Completeness endpoint latency | GET /dog-profiles/me/completeness < 100ms at P95 | P2 | Req 7 |

### Security

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-S01 | Owner-scoped access | Profile write/delete operations require authenticated owner identity; enforced at service layer | P0 | Req 1, 4, 5 |
| NFR-S02 | Location privacy | Exact coordinates must never appear in any API response visible to third parties | P0 | Req 3 |
| NFR-S03 | Photo access control | Photo blob retrieval endpoint must verify the requesting user is authenticated; unauthenticated access returns HTTP 401 | P1 | Req 2 |

### Scalability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-SC01 | Concurrent profile loads | Support 10,000 DAU with ≤ 500ms P95 profile load | P1 | Req 3 |
| NFR-SC02 | Photo blob storage growth | DB photo blob storage must be monitored; alert threshold at 80% of allocated storage | P2 | Req 2 |

### Reliability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-R01 | Transactional writes | Profile creation/update/deletion (including cascade) must be ACID-compliant; no partial saves | P0 | Req 5, 6 |

### Observability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-O01 | OTEL traces | All create/update/delete/photo operations produce OTEL spans with `owner_id`, `profile_id`, and `operation` attributes; exported via OTLP | P1 | Req 1, 4, 5 |
| NFR-O02 | OTEL metrics | `dog_profile.create/update/delete/photo` counters and `dog_profile.completeness_score` histogram exported via OTLP; dashboard lag ≤ 1 min | P1 | Req 1, 7 |
| NFR-O03 | Correlated logs | Structured log entries include OTEL `trace_id` for cross-signal correlation in any OTLP-compatible backend | P1 | Req 1, 4, 5 |

### Usability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-U01 | Error messages | All HTTP 4xx responses must include a machine-readable `field` name and a human-readable `message` | P1 | Req 1, 2, 4, 6 |
| NFR-U02 | Completeness nudge | `missingFields` in completeness response must use human-readable field labels, not raw enum names | P2 | Req 7 |

### Constraints

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-C01 | Single dog per owner (MVP) | One dog profile per owner account enforced at service and DB level | P0 | Req 1 |
| NFR-C02 | Stack alignment | Implementation must use Kotlin data classes, Spring Boot JPA, PostgreSQL — no deviations | P0 | All |
| NFR-C03 | GDPR erasure | Hard deletion of all cascaded data completed within 30 days of owner request | P0 | Req 5 |
| NFR-C04 | Photo storage | Photos stored as PostgreSQL bytea blobs; no external object storage (MVP) | P0 | Req 2 |
| NFR-C05 | Breed extensibility | Breed list stored in a `breeds` reference table; new breeds added via DB migration, not code changes | P1 | Req 6 |

---

## Open Questions

| # | Question | Impact | Owner | Status |
|---|----------|--------|-------|--------|
| 1 | Should temperament tags be a free-form list or selected from a controlled vocabulary? | Affects validation logic, UI, and matching algorithm input quality | Product | **Resolved**: controlled vocabulary defined above |
| 2 | Where are photos stored? | Determines photo URL strategy and upload endpoint design | Tech | **Resolved**: PostgreSQL bytea blobs (DB blob) |
| 3 | Should breed be a free-form string or validated against an enumerated breed list? | Affects matching accuracy and search filter consistency | Product / Tech | **Resolved**: validated against `breeds` reference table |
| 4 | Is a "profile completeness score" or nudging mechanism needed? | May require additional UI/API fields to track completion progress | Product | **Resolved**: Requirement 7 added |
| 5 | How should deletion interact with existing active matches and chat threads? | Cascade behaviour must be defined to avoid orphaned match/chat data | Tech / Product | **Resolved**: all matches, swipe records, and chat threads cascade-deleted with profile (Req 5) |
