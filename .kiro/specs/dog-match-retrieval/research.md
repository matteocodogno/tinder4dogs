# Research & Design Decisions

---
**Purpose**: Capture discovery findings, architectural investigations, and rationale that inform the technical design.

---

## Summary

- **Feature**: `dog-match-retrieval`
- **Discovery Scope**: Extension — adds a new GET endpoint to an existing Spring Boot monolith; relocates
  `DogMatcherService` into the new `match/` feature module; removes root `service/` package; introduces
  `common/` module for shared types
- **Key Findings**:
    - `DogMatcherService` already exists in the root `service/` package but operates on a non-persistent
      `Dog` data class and contains three algorithm bugs; it must be fixed, adapted to `DogProfile`, and moved to
      `match/`
    - `DogProfileRepository` (in
      `dogprofile.repository`) is the correct source for fetching all candidate profiles; cross-module repository access is idiomatic for a Spring Boot monolith
    - `ErrorResponse` lives in `dogprofile.model` despite being cross-cutting; it belongs in a new
      `common/` module shared by all controllers
    - Limit validation via `@Validated` + `@Min(1)` `@Max(10)` on the controller method parameter requires
      `@Validated` at the class level to activate `ConstraintViolationException` handling

## Research Log

### DogMatcherService — current state, bugs, and adaptation needs

- **Context**: Requirements state the existing `DogMatcherService` must be relocated into the
  `match` module, adapted to work with `DogProfile`, and have its algorithm corrected
- **Findings**:
    - Current signature: `calculateCompatibility(dog1: Dog, dog2: Dog): Double` —
      `Dog` is a separate non-persistent data class defined in the same file
    - Current return: divides final score by 100 (returns `Double` in
      `[0.0, 1.0]`); this range is preserved and exposed directly in the API response
    - **Bug 1**: `score + 25.0` on the breed match branch is a no-op (result not assigned to `score`); correct:
      `score += 25.0`
    - **Bug 2**:
      `if (dog1.gender == dog2.gender)` awards a bonus for same gender; intent is different-gender (complementary pair); correct:
      `if (dog1.gender != dog2.gender)`
    - **Bug 3**:
      `commonPreferences * 10` is unbounded; with 10+ shared preferences the score could exceed 100 before division; correct:
      `minOf(commonPreferences * 10, 30)` caps the preference contribution at 30 points
    - `findBestMatch` is unused by the new endpoint; it is left in place during relocation but not exposed
- **Implications**:
    - After fixes, max achievable raw score = 30 (age) + 25 (breed) + 15 (gender) + 30 (preferences) = 100, so
      `/ 100` yields `[0.0, 1.0]` correctly bounded
    - `calculateCompatibility` signature changes from `(Dog, Dog)` to
      `(DogProfile, DogProfile)`; method body updated to read fields from `DogProfile`

### Score range — 0.0 to 1.0 in API response

- **Context**: Initial design proposed 0–100 integer. User confirmed `[0.0, 1.0]` Double is preferred for the endpoint.
- **Findings**: The existing `/ 100` division is correct and sufficient; no conversion layer needed —
  `DogMatcherService` output is used as-is in `DogMatchEntry.compatibilityScore`
- **Implications**: `DogMatchEntry.compatibilityScore` is `Double`; JSON serialisation produces e.g. `0.75` not `75`

### Cross-module repository access pattern

- **Context**: `DogMatchService` (in `match`) needs to load all candidate dog profiles from the database
- **Findings**:
    - The existing `DogProfileRepository` in `dogprofile.repository` provides `findAll()` and `findById()` via
      `JpaRepository`
    - Spring's component scan makes `DogProfileRepository` injectable in any Spring-managed bean regardless of package
    - Steering: "Presentation → Service only" — services may access repositories across module boundaries in a monolith
- **Implications**:
    - `DogMatchService` injects
      `DogProfileRepository` directly; no wrapper or anti-corruption layer is needed at this scale

### ErrorResponse — relocation to `common/` module

- **Context**: `ErrorResponse` is currently in `dogprofile.model`. If `DogMatchController` imports it from there, the
  `match` module takes a dependency on an unrelated domain module.
- **Findings**:
    - `ErrorResponse` is a pure data class with no domain logic; it is a utility shared by every controller
    - Moving it to `com.ai4dev.tinderfordogs.common.model` makes it domain-neutral
    - `DogProfileController` import must be updated from `dogprofile.model.ErrorResponse` to
      `common.model.ErrorResponse` as part of this work
- **Implications**:
    - New `common/` module introduced at `src/main/kotlin/com/ai4dev/tinderfordogs/common/model/`
    - Future shared types (e.g., pagination wrappers) go here; the module has no Spring beans, only data classes

### Root `service/` package removal

- **Context**: After relocating `DogMatcherService` to `match.service`, the root `service/` package contains only
  `DogMatcherService` and the non-persistent `Dog` data class — both become unused
- **Findings**:
    - No other class in the project imports from `com.ai4dev.tinderfordogs.service`
    - Removing the package eliminates dead code and a misleading package boundary
- **Implications**:
    - `DogMatcherService.kt` and its co-located `Dog` data class are deleted from `service/`
    - The directory `src/main/kotlin/com/ai4dev/tinderfordogs/service/` is removed

### limit parameter validation approach

- **Context**: `limit` is a query parameter that must be validated as integer in `[1, 10]` before business logic runs
- **Findings**:
    - Spring MVC supports `@Validated` at the class level + `@Min`/`@Max` on
      `@RequestParam` parameters; violations throw `ConstraintViolationException` (not
      `MethodArgumentNotValidException`)
    - Type mismatch (non-integer string) throws `MethodArgumentTypeMismatchException`
    - Both must be handled to return a consistent `400 VALIDATION_ERROR` response
- **Implications**:
    - Controller must be annotated `@Validated`; `@ExceptionHandler` must cover both exception types

## Architecture Pattern Evaluation

| Option                                                 | Description                                                                                  | Strengths                                                                               | Risks / Limitations                                                                                   | Notes                                                        |
|--------------------------------------------------------|----------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| Extend `dogprofile` module                             | Add match endpoint and matcher logic directly to the existing dogprofile vertical slice      | No new module, DogProfileRepository is local                                            | Mixes profile CRUD with discovery/ranking; single-responsibility violated                             | Not recommended                                              |
| New `match` module with cross-module repository access | Separate vertical slice; DogMatchService injects DogProfileRepository from dogprofile        | Clean separation of concerns; follows existing module pattern; minimal new abstractions | Cross-module repository dependency (acceptable in monolith)                                           | **Recommended**                                              |
| New `match` module with service-to-service API         | DogMatchService calls `DogProfileService.findAll()` instead of accessing repository directly | Strict module encapsulation; future-proof if modules become services                    | Requires adding `findAll()` to DogProfileService (not its concern); over-engineering for monolith MVP | Deferred — revisit if modules are extracted to microservices |

## Design Decisions

### Decision: Relocate `DogMatcherService` to `match.service` and fix algorithm

- **Context
  **: Requirements 5.1 and 5.2 mandate matching logic lives in the same module as the controller; three bugs in the existing algorithm produce incorrect scores
- **Alternatives Considered**:
    1. Keep in root `service/` and have `match` depend on it — leaves root `service/` growing unbounded; bugs remain
    2. Move to `match.service`, fix bugs, adapt to `DogProfile` — clean vertical slice with correct behaviour
- **Selected Approach**: Move to `match.service`; fix all three bugs; update to accept `DogProfile`
- **Rationale**: Follows the vertical slice pattern; fixes correctness issues; removes the root
  `service/` package entirely
- **Trade-offs**: `DogMatcherService` now imports `DogProfile` from
  `dogprofile.model` (cross-module model reference); acceptable since `DogProfile` is a stable domain entity
- **Follow-up**: Verify no other class references `com.ai4dev.tinderfordogs.service` before deletion

### Decision: Compatibility score `Double` in `[0.0, 1.0]` (not 0–100 integer)

- **Context**: Domain glossary defines Compatibility Score as 0–100; user confirmed
  `[0.0, 1.0]` is preferred for the API
- **Selected Approach**: Keep the `/ 100` division in `DogMatcherService`; expose raw `Double` in
  `DogMatchEntry.compatibilityScore`
- **Trade-offs
  **: API consumers receive a fractional value; slightly less human-readable than an integer percentage but more precise and idiomatic for ML-adjacent scoring

### Decision: Move `ErrorResponse` to `common/` module

- **Context**: `ErrorResponse` was in `dogprofile.model`; a second controller (`DogMatchController`) needs it
- **Selected Approach**: Introduce `com.ai4dev.tinderfordogs.common.model` containing `ErrorResponse`; update
  `DogProfileController` import
- **Rationale**: Avoids cross-domain module dependency (`match` →
  `dogprofile`) for a utility class; provides a clean home for future shared types
- **Trade-offs**: Minor churn — one import updated in `DogProfileController`; no behaviour change

## Risks & Mitigations

- `findAll()` on large datasets — no pagination on
  `DogProfileRepository.findAll()`; for MVP the table is small; add a note in tasks to consider a max-rows guard in a future iteration
- Algorithm max score assumption — fixed bugs yield max raw score = 100 exactly; if future scoring factors are added without adjusting the divisor, scores could exceed 1.0 — mitigation: document the 100-point budget in
  `DogMatcherService` inline
- `DogProfileController` import update — low risk (compile-time error if missed); covered by existing tests

## References

- `.kiro/specs/dog-match-retrieval/requirements.md` — source requirements
- `.kiro/steering/structure.md` — module layering principles
-
`src/main/kotlin/com/ai4dev/tinderfordogs/service/DogMatcherService.kt` — existing implementation being relocated and fixed
