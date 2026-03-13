# Requirements Document

## Project Description (Input)

Create a GET endpoint /api/v1/dogs/{dogId}/matches that returns the best-ranked matches for a given dog, with an optional limit parameter defaulting to 1.

## Introduction

This feature exposes a read endpoint that returns the top-ranked compatibility matches for a given dog profile. Ranking is based on the compatibility score computed by the existing
`DogMatcherService`. The endpoint is a discovery/ranking API — it returns scored candidates, not confirmed mutual matches. It integrates with the existing
`DogProfile` persistence layer and the core matching algorithm.

The `DogMatcherService` will be relocated into the same feature module as the new controller (away from the root
`service/` package), following the vertical slice pattern. In future iterations, additional attributes (temperament tags, energy level, preferences) will be fed into the compatibility algorithm; the design must not prevent that extension.

---

## Requirements

### Requirement 1: Match Retrieval Endpoint

**Objective:
** As a dog owner, I want to retrieve the best-ranked potential matches for my dog, so that I can discover compatible dogs to interact with.

#### Acceptance Criteria

1. When a GET request is received at
   `/api/v1/dogs/{dogId}/matches`, the Match API shall return an ordered list of dog profiles ranked by compatibility score descending.
2. When the `limit` query parameter is omitted, the Match API shall default to returning 1 result.
3. When the `limit` query parameter is provided, the Match API shall return at most `limit` results.
4. The Match API shall exclude the requested dog's own profile from the results.
5. The Match API shall include the compatibility score (0–100) alongside each matched dog profile in the response.
6. The Match API shall include the dog profile fields (id, name, breed, size, age, gender, bio) for each match in the response.

---

### Requirement 2: Input Validation

**Objective:
** As an API consumer, I want to receive clear errors for invalid inputs, so that I can correct my request without ambiguity.

#### Acceptance Criteria

1. If
   `dogId` does not correspond to an existing dog profile, the Match API shall return HTTP 404 with an error body containing
   `code: "DOG_NOT_FOUND"`.
2. If `limit` is provided but is less than 1, the Match API shall return HTTP 400 with an error body containing
   `code: "VALIDATION_ERROR"`.
3. If `limit` is provided but is greater than 10, the Match API shall return HTTP 400 with an error body containing
   `code: "VALIDATION_ERROR"`.
4. If
   `limit` is provided but is not a valid positive integer, the Match API shall return HTTP 400 with an error body containing
   `code: "VALIDATION_ERROR"`.

---

### Requirement 3: Compatibility Ranking

**Objective:
** As a dog owner, I want matches ordered by how compatible they are with my dog, so that the most relevant profiles appear first.

#### Acceptance Criteria

1. The Match API shall rank candidate profiles using the compatibility score from
   `DogMatcherService.calculateCompatibility`.
2. When multiple candidates have the same compatibility score, the Match API shall apply a deterministic secondary sort (by profile
   `id`) to ensure stable ordering.
3. When no candidate profiles exist (excluding the requested dog), the Match API shall return HTTP 200 with an empty list.
4. The Match API shall not exclude candidates based on prior swipe history at this time; all persisted dog profiles (except the requesting dog) are eligible.

---

### Requirement 4: Response Format

**Objective:** As an API consumer, I want a consistent response structure, so that I can reliably parse match results.

#### Acceptance Criteria

1. The Match API shall return HTTP 200 for all successful responses.
2. The Match API shall return results in a fixed wrapper: `{ "matches": [...] }`.
3. Each element in `matches` shall contain the full dog profile fields (id, name, breed, size, age, gender, bio) and a
   `compatibilityScore` field (integer 0–100).

---

### Requirement 5: Module Co-location

**Objective:
** As a developer, I want the matching logic to live in the same feature module as the endpoint, so that the vertical slice is self-contained.

#### Acceptance Criteria

1. The Match API shall host `DogMatcherService` within the dog-match feature module (not in the root
   `service/` package).
2. The Match API module shall follow the standard layer split: `presentation/`, `service/`, `model/`, with a
   `repository/` layer if direct persistence access is needed.

---

## Non-Functional Requirements

### Performance

| NFR ID | Description                   | Threshold                     | Priority | Source Req |
|--------|-------------------------------|-------------------------------|----------|------------|
| NFR-P1 | Match retrieval response time | p95 < 300ms under normal load | P1       | Req 1      |

### Security

| NFR ID | Description                        | Threshold                                             | Priority | Source Req |
|--------|------------------------------------|-------------------------------------------------------|----------|------------|
| NFR-S1 | No exact location data in response | Exact coordinates must never appear in match response | P0       | PRD NFR-01 |

### Observability

| NFR ID | Description             | Threshold                                                   | Priority | Source Req |
|--------|-------------------------|-------------------------------------------------------------|----------|------------|
| NFR-O1 | Match retrieval logging | Log each call with dogId, limit, result count, and duration | P1       | Req 1      |

### Constraints

| NFR ID | Description                | Threshold                                                                                                                                                | Priority | Source Req                     |
|--------|----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|----------|--------------------------------|
| NFR-C1 | No authentication required | Endpoint is public; no JWT/auth enforcement in this iteration                                                                                            | P0       | dog-profile-creation precedent |
| NFR-C2 | limit range                | `limit` must be between 1 and 10 inclusive; default is 1                                                                                                 | P0       | Req 2                          |
| NFR-C3 | Extensible ranking         | Compatibility algorithm must be structured to allow additional attributes (temperament, preferences, energy level) in future iterations without redesign | P1       | Req 3                          |

---

## Open Questions

| # | Question                                                                                           | Impact                           | Owner   | Status                                                          |
|---|----------------------------------------------------------------------------------------------------|----------------------------------|---------|-----------------------------------------------------------------|
| 1 | Should "matches" exclude dogs that have already been swiped left on by the requesting dog?         | Affects candidate pool filtering | Product | Deferred — excluded dogs are NOT filtered in this iteration     |
| 2 | Is there a maximum value for `limit`?                                                              | Prevents unbounded DB queries    | Tech    | Resolved — max is 10                                            |
| 3 | Should compatibility scoring consider only existing `DogProfile` fields or also future attributes? | Algorithm extensibility          | Tech    | Resolved — current fields only, algorithm must be extensible    |
| 4 | Should the response include the requesting dog's own profile summary for context?                  | Response design                  | Product | Resolved — not included; only matched dog profiles are returned |
