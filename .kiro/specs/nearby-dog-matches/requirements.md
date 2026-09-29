# Requirements Document

## Introduction
Today `GET /api/matches/{id}` ranks every other dog by match score and ignores geography entirely: dog profiles store no location. This feature gives each dog profile an optional location, provided, read and edited through the dog API, and restricts the ranked match listing to candidates within a radius of the subject dog's stored location — the subject dog stands in for the user's location. The listing stays ordered by match score from highest to lowest and reports each candidate's distance.

## Boundary Context
- **In scope**:
  - An optional stored location on dog profiles, provided and read through the dog API, validated for coordinate range.
  - Editing the stored location of an existing dog through the dog API.
  - A radius limit on `GET /api/matches/{id}`: an optional radius query parameter with a default of 25 km and an inclusive boundary.
  - Each entry of the nearby listing reports its straight-line distance from the subject dog.
- **Out of scope**:
  - The pairwise endpoint `GET /api/matches/{aId}/{bId}` — it keeps its current behaviour: no radius, no distance.
  - Locating the requesting client in real time — the subject dog's stored location anchors the radius.
  - Editing any other attribute of an existing dog (name, breed, gender, age, preferences) — only the location is editable in this feature.
  - Route or travel distance — only straight-line distance over the earth's surface is used.
  - Any change to the scoring rules, their weights, or the `[0.0, 1.0]` score scale.
- **Adjacent expectations**:
  - Dogs stored before this feature — including rows the API would reject today — remain valid profiles; a dog without a stored location simply never appears in the nearby listing.
  - The nearby listing relies on the existing scoring rules; this feature does not alter them.

## Requirements

### Requirement 1: Dog profiles carry an optional location
**Objective:** As a dog owner, I want to attach a location to my dog's profile, so that matches close to us can be found.

#### Acceptance Criteria
1. When a dog is created with a location, tinder4dogs shall store that location and include it in the response describing the created dog.
2. When a dog is created without a location, tinder4dogs shall create the profile and omit the location from the response.
3. When a dog with a stored location is read over the dog API, tinder4dogs shall include the location in the response.
4. When a dog without a stored location is read over the dog API, tinder4dogs shall omit the location from the response.
5. If a create request carries a latitude outside [-90, 90] or a longitude outside [-180, 180], then tinder4dogs shall reject the request with 400 Bad Request.
6. When a location is set on an existing dog that had none, tinder4dogs shall store the location and include it in the response.
7. When a dog's location is replaced with a new one, tinder4dogs shall store the new location and include it in the response.
8. If a location update request carries a latitude outside [-90, 90] or a longitude outside [-180, 180], then tinder4dogs shall reject the request with 400 Bad Request.
9. When a location update is requested for a dog that does not exist, tinder4dogs shall return 404 Not Found.

### Requirement 2: The match listing accepts a radius around the subject dog
**Objective:** As a dog owner, I want to control how far away suggested matches may be, so that every suggestion is one I could actually meet.

#### Acceptance Criteria
1. When the radius parameter is omitted from a match listing request, tinder4dogs shall apply a default radius of 25 km.
2. When a candidate lies exactly at the effective radius, tinder4dogs shall include it in the listing.
3. When a radius of zero is requested, tinder4dogs shall return only candidates at the subject dog's exact location.
4. If a negative radius is requested, then tinder4dogs shall reject the request with 400 Bad Request.
5. If the radius is not a valid number, then tinder4dogs shall reject the request with 400 Bad Request.

### Requirement 3: Only dogs within the radius appear in the listing
**Objective:** As a dog owner, I want the listing to contain only dogs close enough to meet, so that the suggestions are practical.

#### Acceptance Criteria
1. When a match listing is requested for a subject dog with a stored location, tinder4dogs shall return only candidates whose straight-line distance from the subject dog is at most the effective radius.
2. If a candidate has no stored location, then tinder4dogs shall exclude it from the listing.
3. If the subject dog has no stored location, then tinder4dogs shall reject the request with 422 Unprocessable Entity.

### Requirement 4: The listing is ordered by match score and reports each dog's distance
**Objective:** As a dog owner, I want nearby matches ordered best-first with their distance visible, so that I can weigh compatibility against reachability.

#### Acceptance Criteria
1. tinder4dogs shall order the nearby match listing by match score from highest to lowest.
2. tinder4dogs shall report, for every entry in the nearby listing, the entry's straight-line distance in kilometres from the subject dog.
3. tinder4dogs shall keep reporting each entry's dog id, name and match score.

### Requirement 5: Existing listing guarantees are preserved
**Objective:** As a dog owner, I want the radius-filtered listing to keep the guarantees it already had, so that bad data or a missing dog is handled as predictably as before.

#### Acceptance Criteria
1. While producing the nearby match listing, tinder4dogs shall never include the subject dog itself.
2. If a candidate cannot be scored because its profile data is corrupt, then tinder4dogs shall drop that candidate from the listing without failing the request.
3. If the subject dog cannot be scored, then tinder4dogs shall reject the request with 422 Unprocessable Entity.
4. When the requested subject dog does not exist, tinder4dogs shall return 404 Not Found.