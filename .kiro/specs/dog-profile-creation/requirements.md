# Requirements Document

## Project Description (Input)

Create an API endpoint to create a new dog profile given the name, breed, size, age, gender, and short bio

## Introduction

This specification covers the API endpoint that allows a caller to create a dog profile on the Tinder4Dogs platform. A dog profile is the core entity enabling discovery, matching, and compatibility assessment. It must capture all attributes necessary for meaningful pairing: identity fields (name, breed, size, age, gender) and a short bio. No authentication is required for this endpoint. Photo upload is out of scope and will be handled by a separate endpoint.

---

## Requirements

### Requirement 1: Dog Profile Creation Endpoint

**Objective:
** As an owner, I want to submit my dog's details via an API so that my dog's profile is created and becomes eligible for matching.

#### Acceptance Criteria

1. When a POST request is received at the dog profile endpoint with all required fields (name, breed, size, age, gender), the Dog Profile Service shall create a new dog profile and return HTTP 201 with the created profile representation.
2. The Dog Profile Service shall accept the following fields: `name` (string), `breed` (free-text string),
   `size` (enum: SMALL, MEDIUM, LARGE, EXTRA_LARGE), `age` (integer, full years), `gender` (enum: MALE, FEMALE),
   `bio` (optional string, short free-text description).
3. When a dog profile is created, the Dog Profile Service shall persist the profile to the database and return a unique profile identifier in the response.

---

### Requirement 2: Input Validation

**Objective:
** As a platform, I want all incoming profile data to be validated before persistence so that only valid and complete dog profiles are stored.

#### Acceptance Criteria

1. If a required field (name, breed, size, age, gender) is missing from the request, the Dog Profile Service shall return HTTP 400 with a descriptive error message identifying the missing field(s).
2. If `age` is outside the range 0–30, the Dog Profile Service shall return HTTP 400 with an error indicating that age must be between 0 and 30.
3. If
   `size` contains a value outside the allowed enum set (SMALL, MEDIUM, LARGE, EXTRA_LARGE), the Dog Profile Service shall return HTTP 400 with an error listing the valid values.
4. If
   `gender` contains a value outside the allowed enum set (MALE, FEMALE), the Dog Profile Service shall return HTTP 400 with an error listing the valid values.
5. If `name` or
   `breed` exceeds 100 characters, the Dog Profile Service shall return HTTP 400 with an error indicating the maximum allowed length.
6. If
   `bio` exceeds 500 characters, the Dog Profile Service shall return HTTP 400 with an error indicating the maximum allowed length.
7. The `bio` field shall be optional; when omitted, the Dog Profile Service shall store a null value without error.

---

### Requirement 3: Response Contract

**Objective:
** As an API consumer, I want a consistent and informative response so that I can correctly handle success and error scenarios.

#### Acceptance Criteria

1. When a profile is successfully created, the Dog Profile Service shall return HTTP 201 with a JSON body containing:
   `id`, `name`, `breed`, `size`, `age`, `gender`, `bio`, and `createdAt`.
2. When a request fails validation, the Dog Profile Service shall return a JSON error body containing a machine-readable
   `code` and a human-readable `message`.
3. The Dog Profile Service shall return `Content-Type: application/json` for all responses.

---

## Non-Functional Requirements

### Performance

| NFR ID  | Description                             | Threshold                     | Priority | Source Req   |
|---------|-----------------------------------------|-------------------------------|----------|--------------|
| PERF-01 | Profile creation endpoint response time | p95 < 300ms under normal load | P1       | NFR-04 (PRD) |

### Security

| NFR ID | Description                                                    | Threshold | Priority | Source Req       |
|--------|----------------------------------------------------------------|-----------|----------|------------------|
| —      | No authentication required for this endpoint in this iteration | N/A       | —        | Scoping decision |

### Scalability

| NFR ID  | Description                                                            | Threshold                                              | Priority | Source Req   |
|---------|------------------------------------------------------------------------|--------------------------------------------------------|----------|--------------|
| SCAL-01 | Endpoint must function without degradation under platform load targets | Supports up to 10,000 DAU without architectural change | P1       | NFR-05 (PRD) |

### Reliability

| NFR ID | Description                                                         | Threshold                                       | Priority | Source Req |
|--------|---------------------------------------------------------------------|-------------------------------------------------|----------|------------|
| REL-01 | Failed profile creation must not leave partial data in the database | Operation is atomic; no partial writes on error | P0       | Req 1      |

### Observability

| NFR ID | Description                            | Threshold                                                                    | Priority | Source Req   |
|--------|----------------------------------------|------------------------------------------------------------------------------|----------|--------------|
| OBS-01 | Profile creation events must be logged | Each creation attempt (success/failure) logged with timestamp and profile ID | P1       | NFR-08 (PRD) |

### Usability

| NFR ID | Description                                  | Threshold                                                            | Priority | Source Req |
|--------|----------------------------------------------|----------------------------------------------------------------------|----------|------------|
| USA-01 | Validation error messages must be actionable | Each error message identifies the field and the reason for rejection | P1       | Req 2      |

### Constraints

| NFR ID | Description                                                                           | Threshold                                                                                                    | Priority | Source Req             |
|--------|---------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|----------|------------------------|
| CON-01 | Implementation must follow Kotlin/Spring Boot stack and project structure conventions | Controller in `presentation/`, Service in `service/`, Model in `model/` under a `dogprofile/` feature module | P0       | tech.md / structure.md |

---

## Open Questions

| # | Question                                                                     | Impact                                                                                                   | Owner          | Status   |
|---|------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|----------------|----------|
| 1 | Should `breed` be validated against a predefined list in a future iteration? | Affects downstream filtering for F-07; free-text accepted for now                                        | Product        | Deferred |
| 2 | Should the creation endpoint also accept photo upload in a future iteration? | PRD F-02 mandates at least one photo for a complete profile; photo upload is a separate endpoint for now | Product / Tech | Deferred |
