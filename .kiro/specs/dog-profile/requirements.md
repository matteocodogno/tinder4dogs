# Requirements Document

## Introduction

The **Dog Profile** feature enables each registered owner on PawMatch to create and manage a single profile for their dog. The profile is the central data object of the platform: it drives compatibility scoring, powers the matching feed, and is the prerequisite for any interaction. This document captures the functional and non-functional requirements for creating, reading, updating, and deleting a dog profile, including photo management and temperament-tag selection.

---

## Requirements

### Requirement 1: Dog Profile Creation

**Objective:** As an owner, I want to create a dog profile with all relevant information so that other users can evaluate compatibility before a match is made.

#### Acceptance Criteria

1. When an authenticated owner submits a new dog profile with all mandatory fields (name, breed, age, sex, purpose), the Dog Profile Service shall persist the profile and return a `201 Created` response containing the assigned profile ID.
2. When an owner who already has a dog profile attempts to create a second one, the Dog Profile Service shall reject the request with a `409 Conflict` response.
3. If any mandatory field (name, breed, age, sex, purpose) is absent or fails validation, the Dog Profile Service shall return a `400 Bad Request` response that includes field-level error messages.
4. The Dog Profile Service shall associate every created dog profile with exactly one owner account.
5. When a dog profile is created or updated with a valid breed value, the Dog Profile Service shall automatically derive and store the dog's size category (Small / Medium / Large / XL) from the breed reference data.
6. When an owner includes optional health/breeding fields (e.g. pedigree number, health-certificate reference) during creation, the Dog Profile Service shall store those values and include them in all subsequent profile responses.

---

### Requirement 2: Photo Management

**Objective:** As an owner, I want to upload and manage photos of my dog so that other users can see my dog and I can satisfy the profile-completion requirement.

#### Acceptance Criteria

1. When an authenticated owner uploads a photo for their dog profile, the Dog Profile Service shall store the image in object storage, associate it with the profile, and return the public URL of the stored image.
2. When an owner uploads a photo, the Dog Profile Service shall validate that the file is an accepted image format (JPEG, PNG, or WebP) and does not exceed 5 MB; If validation fails, the Dog Profile Service shall return a `400 Bad Request` with a descriptive error.
3. The Dog Profile Service shall enforce a minimum of 1 and a maximum of 5 photos per dog profile.
4. If an owner attempts to upload a photo when the profile already has 5 photos, the Dog Profile Service shall return a `422 Unprocessable Entity` response.
5. When an owner deletes a photo, the Dog Profile Service shall remove the image from object storage and update the profile's photo list atomically; If the object-storage deletion fails, the Dog Profile Service shall leave the database record unchanged and return a `502 Bad Gateway`.
6. While the owner has not uploaded at least one photo, the Dog Profile Service shall include `"photo"` in the list of incomplete fields returned by the profile-status endpoint.

---

### Requirement 3: Temperament Tag Management

**Objective:** As an owner, I want to select temperament tags for my dog so that the matching algorithm can account for personality compatibility.

#### Acceptance Criteria

1. The Dog Profile Service shall maintain a fixed vocabulary of temperament tags; the v1 vocabulary is: `playful`, `calm`, `energetic`, `shy`, `sociable`, `protective`, `gentle`, `stubborn`.
2. When an authenticated user requests the temperament tag vocabulary, the Dog Profile Service shall return the full list of valid tags.
3. When an owner selects one or more temperament tags during profile creation or update, the Dog Profile Service shall associate only those tags with the dog profile.
4. If an owner submits a tag value that is not in the approved vocabulary, the Dog Profile Service shall reject the request with a `400 Bad Request` that lists the invalid tag(s).
5. The Dog Profile Service shall allow an owner to replace the temperament tags of their dog profile at any time; the update shall be atomic (full replacement, not partial merge).

---

### Requirement 4: Profile Completion Gate

**Objective:** As the platform, I want to enforce a fully completed dog profile before granting access to the matching feed so that match quality is maintained.

#### Acceptance Criteria

1. The Dog Profile Service shall consider a profile **complete** when and only when: name, breed, size, age, sex, and purpose are present and valid, and at least one photo has been uploaded.
2. While a dog profile is incomplete, the Matching Service shall deny access to the matching feed with a `403 Forbidden` response that references the incomplete profile.
3. When an owner completes all mandatory fields and uploads at least one photo, the Dog Profile Service shall mark the profile status as `complete` and make the matching feed accessible.
4. When an authenticated owner requests their profile status, the Dog Profile Service shall return the current completion status and the list of missing required fields (empty list when complete).

---

### Requirement 5: Profile Retrieval

**Objective:** As an owner or internal service, I want to retrieve a dog profile by its ID so that profile data can be displayed in the feed, on match cards, and in chat headers.

#### Acceptance Criteria

1. When an authenticated user requests a dog profile by its ID, the Dog Profile Service shall return the full profile (name, breed, size, age, sex, purpose, temperament tags, photo URLs, optional health/breeding fields, completion status).
2. When any service or user requests a dog profile ID that does not exist, the Dog Profile Service shall return a `404 Not Found` response.
3. The Dog Profile Service shall allow any authenticated user to read any dog profile (owners see their own; other users see profiles surfaced by the feed or a match).

---

### Requirement 6: Profile Update

**Objective:** As an owner, I want to update my dog's profile so that I can keep information accurate as my dog grows or my goals change.

#### Acceptance Criteria

1. When an authenticated owner submits a partial or full update to their own dog profile, the Dog Profile Service shall persist the changes, update the `updatedAt` timestamp, and return the updated profile.
2. If an owner attempts to modify another owner's dog profile, the Dog Profile Service shall return a `403 Forbidden` response.
3. When an owner updates fields that affect compatibility scoring (breed or temperament tags), the Dog Profile Service shall emit a `dog-profile.updated` domain event asynchronously and return the updated profile immediately without waiting for compatibility score recalculation; If breed changes, the Dog Profile Service shall also re-derive and update the stored size category before returning.
4. If the submitted update leaves a previously complete profile incomplete (e.g. breed is removed), the Dog Profile Service shall revert the profile status to `incomplete`.

---

### Requirement 7: Profile Deletion & Right to Erasure

**Objective:** As an owner, I want to delete my dog's profile so that I can exercise my right to erasure in compliance with GDPR/nLPD.

#### Acceptance Criteria

1. When an authenticated owner requests deletion of their own dog profile, the Dog Profile Service shall soft-delete the profile record and schedule hard deletion of all associated photos from object storage within 30 days.
2. When a dog profile is deleted, the Dog Profile Service shall emit a `dog-profile.deleted` domain event; the Matching Service shall cascade-invalidate all active matches referencing that profile upon receiving this event.
3. If an owner attempts to delete another owner's dog profile, the Dog Profile Service shall return a `403 Forbidden` response.
4. When a deleted profile is requested by any caller, the Dog Profile Service shall return a `404 Not Found` (the soft-delete must not be visible externally).

---

## Non-Functional Requirements

### Performance

| NFR ID   | Description                                              | Threshold                     | Priority | Source Req |
|----------|----------------------------------------------------------|-------------------------------|----------|------------|
| NFR-P01  | GET /dogs/{id} response time                             | p95 < 200 ms under normal load | P0       | Req 5      |
| NFR-P02  | Photo upload (5 MB file) end-to-end response time        | p95 < 3 s under normal load   | P1       | Req 2      |

### Security

| NFR ID   | Description                                                                 | Threshold                                          | Priority | Source Req    |
|----------|-----------------------------------------------------------------------------|----------------------------------------------------|----------|---------------|
| NFR-S01  | All Dog Profile endpoints require a valid, non-expired JWT                  | 401 returned for missing/invalid token             | P0       | All           |
| NFR-S02  | Photo uploads validated server-side (format + size); no binary execution    | Malformed uploads rejected at ingress; no exec     | P0       | Req 2         |
| NFR-S03  | Owners may only mutate or delete their own dog profile                      | 403 returned on cross-owner write attempts         | P0       | Req 6, Req 7  |

### Scalability

| NFR ID   | Description                                                           | Threshold                                      | Priority | Source Req |
|----------|-----------------------------------------------------------------------|------------------------------------------------|----------|------------|
| NFR-SC01 | Photo storage must use external object storage (not local filesystem) | Upload must succeed with ≥ 2 backend instances | P1       | Req 2      |

### Reliability

| NFR ID   | Description                                                                          | Threshold                                              | Priority | Source Req |
|----------|--------------------------------------------------------------------------------------|--------------------------------------------------------|----------|------------|
| NFR-R01  | Photo upload must be atomic: if object storage fails, no DB record is created        | Zero orphaned DB records on storage failure            | P0       | Req 2      |
| NFR-R02  | Profile updates must be transactional: partial field writes must not be persisted    | All-or-nothing: either full update or no change        | P0       | Req 6      |

### Observability

| NFR ID   | Description                                            | Threshold                                           | Priority | Source Req |
|----------|--------------------------------------------------------|-----------------------------------------------------|----------|------------|
| NFR-O01  | All 5xx errors on Dog Profile endpoints logged with trace ID | Structured log entry per error with `traceId` | P1       | All        |
| NFR-O02  | Domain events (`dog-profile.updated`, `dog-profile.deleted`) emitted with correlation ID | Events include `correlationId` field | P1 | Req 6, Req 7 |

### Usability

| NFR ID   | Description                                                              | Threshold                                     | Priority | Source Req |
|----------|--------------------------------------------------------------------------|-----------------------------------------------|----------|------------|
| NFR-U01  | Profile-status endpoint returns machine-readable list of missing fields  | Response includes `missingFields: string[]`   | P1       | Req 4      |

### Constraints

| NFR ID   | Description                                                                        | Threshold                        | Priority | Source Req |
|----------|------------------------------------------------------------------------------------|----------------------------------|----------|------------|
| NFR-C01  | One dog profile per owner account (multi-dog deferred to v2)                       | 409 on second-profile attempt    | P0       | Req 1      |
| NFR-C02  | Breed must reference a controlled enum vocabulary (not free text) to support AI scoring and size derivation | Free-text breed values rejected with 400 | P0 | Req 1 |
| NFR-C03  | Photo storage uses local MinIO (S3-compatible API); no local filesystem storage    | Upload/delete via MinIO S3 API   | P0       | Req 2      |

---

## Open Questions

| # | Question | Impact | Owner | Status |
|---|----------|--------|-------|--------|
| Q1 | Should `size` be a discrete enum (Small / Medium / Large / XL) or derived automatically from breed? | Affects profile validation, matching weights, and UI | Product | **Closed** — derived from breed; size is a computed field, not user input |
| Q2 | What is the definitive list of temperament tags for v1 (is the proposed 8-tag vocabulary final)? | Affects tag-validation logic and compatibility scoring model | Product | **Closed** — 8-tag vocabulary confirmed: `playful`, `calm`, `energetic`, `shy`, `sociable`, `protective`, `gentle`, `stubborn` |
| Q3 | Should `breed` be a free-text field or a controlled enum drawn from a reference list? | Free text reduces scoring accuracy; enum adds maintenance overhead | Product / Tech | **Closed** — controlled enum |
| Q4 | When compatibility-relevant fields change (breed, size, temperament), should score recalculation be synchronous (block the update response) or asynchronous (event-driven)? | Directly impacts API response time and architecture complexity | Tech | **Closed** — async via domain event |
| Q5 | What object-storage provider is used for photo storage in v1 (e.g. S3-compatible, local MinIO)? | Determines upload implementation and URL generation strategy | Tech | **Closed** — local MinIO (S3-compatible) |
