# Implementation Plan

- [ ] 1. (P) Establish the `common/` module with shared cross-cutting types
- [ ] 1.1 (P) Create `ErrorResponse` in the common module
  - Create the `common/model/` package under `tinderfordogs`
  - Define `ErrorResponse` as a data class with `code` and `message` string fields
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [ ] 1.2 (P) Update `DogProfileController` to import from `common.model`
  - Replace the `dogprofile.model.ErrorResponse` import with `common.model.ErrorResponse`
  - Verify existing controller tests still pass after the import change
  - _Requirements: 2.1_

- [ ] 2. (P) Relocate, correct, and clean up the matcher service
- [ ] 2.1 (P) Move `DogMatcherService` into the `match` module and adapt it to operate on persisted dog profiles
  - Create the `match/service/` package
  - Copy `DogMatcherService` into the new package
  - Replace the `Dog` parameter type with `DogProfile` in the `calculateCompatibility` signature and body
  - Remove the `Dog` data class from the source; the `DogProfile` entity from `dogprofile.model` is the sole input type
  - _Requirements: 5.1, 5.2_

- [ ] 2.2 (P) Fix the three algorithm bugs in the relocated `DogMatcherService`
  - Fix Bug 1 — same-breed bonus: change `score + 25.0` to `score += 25.0` so the bonus is actually applied
  - Fix Bug 2 — gender bonus: change the condition from same-gender to different-gender (`!=`) to award points for complementary pairs
  - Fix Bug 3 — preference overlap cap: wrap the preference contribution with `minOf(commonPreferences * 10, 30)` to prevent the score from exceeding 100 before the `/ 100` division
  - Add an inline comment documenting the 100-point score budget so future contributors respect the `/ 100` divisor
  - _Requirements: 1.5, 3.1_

- [ ] 2.3 Delete the root `service/` package
  - Verify no class outside of `match.service` imports from `com.ai4dev.tinderfordogs.service`
  - Delete `DogMatcherService.kt` from the root `service/` directory
  - Remove the now-empty `service/` directory
  - _Requirements: 5.1_

- [ ] 3. (P) Create the `match` module domain model
- [ ] 3.1 (P) Create the response DTOs for the matches endpoint
  - Create the `match/model/` package
  - Define a per-match entry type containing all dog profile fields (id, name, breed, size, age, gender, bio) plus a `compatibilityScore` field of type `Double`
  - Define the list wrapper type with a `matches` property holding a list of the per-match entry
  - _Requirements: 1.6, 4.2, 4.3_

- [ ] 3.2 (P) Create the domain exception for an unknown dog
  - Define a runtime exception for the case where the requested `dogId` is not found in the database
  - Place the exception in the `match/model/` package
  - _Requirements: 2.1_

- [ ] 4. Implement the match orchestration service
  - Create `DogMatchService` in `match/service/`
  - Inject `DogProfileRepository` (from `dogprofile.repository`) and `DogMatcherService`
  - Fetch the source dog by `dogId`; throw the domain not-found exception when the profile does not exist
  - Load all candidate profiles and filter out the source dog
  - Score each candidate using `DogMatcherService.calculateCompatibility`
  - Sort by `compatibilityScore` descending; apply a deterministic secondary sort by profile `id` ascending
  - Take the first `limit` results and return them in the `DogMatchListResponse` wrapper
  - Return an empty `matches` list when no candidates exist after filtering
  - _Requirements: 1.3, 1.4, 1.5, 2.1, 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3_

- [ ] 5. Implement the match retrieval controller
  - Create `DogMatchController` in `match/presentation/` mapped to `/api/v1/dogs`
  - Annotate the class with `@Validated` to enable method-level constraint validation
  - Implement a GET handler for `/{dogId}/matches` accepting a `UUID` path variable and an optional `limit` query parameter defaulting to `1`, constrained to the range `[1, 10]`
  - Delegate to `DogMatchService.findMatches` and return HTTP 201 with the response body on success
  - Add a local exception handler for the domain not-found exception returning HTTP 404 with `code: "DOG_NOT_FOUND"`
  - Add a local exception handler for constraint violations and type mismatch exceptions returning HTTP 400 with `code: "VALIDATION_ERROR"`
  - Use `ErrorResponse` from `common.model` for all error responses
  - _Requirements: 1.1, 1.2, 2.1, 2.2, 2.3, 2.4, 4.1_

- [ ] 6. Write tests
- [ ] 6.1 Unit-test the matcher service algorithm
  - Verify the score is always in `[0.0, 1.0]` for boundary input combinations
  - Verify same-breed candidates score higher than different-breed ones (all other fields equal)
  - Verify different-gender candidates score higher than same-gender ones (all other fields equal)
  - Verify that 3 or more common preferences produce the same score as exactly 3 (cap enforcement)
  - _Requirements: 1.5, 3.1_

- [ ] 6.2 Unit-test the match orchestration service
  - Verify that results are ordered by score descending and that `id` ascending is the tiebreaker
  - Verify the requesting dog is never present in the returned list
  - Verify an empty list is returned when no other profiles exist
  - Verify the domain not-found exception is thrown when `dogId` is absent from the repository
  - _Requirements: 1.3, 1.4, 2.1, 3.2, 3.3, 3.4_

- [ ] 6.3 `@WebMvcTest` the match controller
  - Verify `GET /api/v1/dogs/{dogId}/matches` returns HTTP 200 with a `matches` array
  - Verify omitting `limit` defaults to 1 result in the service call
  - Verify `limit=0` returns HTTP 400 with `code: "VALIDATION_ERROR"`
  - Verify `limit=11` returns HTTP 400 with `code: "VALIDATION_ERROR"`
  - Verify `limit=abc` returns HTTP 400 with `code: "VALIDATION_ERROR"`
  - Verify an unknown `dogId` returns HTTP 404 with `code: "DOG_NOT_FOUND"`
  - _Requirements: 1.1, 1.2, 2.2, 2.3, 2.4, 4.1, 4.2_
