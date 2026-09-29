# Gap Analysis: nearby-dog-matches

## Scope and Method
Gap between `.kiro/specs/nearby-dog-matches/requirements.md` (5 requirement areas, 20 acceptance criteria) and the current codebase. Analysis done by direct inspection: all production Kotlin, the Liquibase changelog, build files, compose.yaml, mise tasks, and steering. No sub-agents were needed — the production code is 7 files.

Note: at the time of this analysis requirements were generated but not yet approved in spec.json; all three phases have since been reviewed and approved (see `spec.json`), so this analysis now reflects the approved requirements.

## Current State

### Domain assets
- `dog/` — `Dog` entity (id, name, breed, gender, age, preferences), `DogRepository` (bare `JpaRepository`), `DogController` with `DogRequest`/`DogResponse` (GET all, GET by id, POST).
- `match/` — `MatchController` with the ranked listing (`bestFor`) and the pairwise endpoint, sharing one `MatchResponse`; `MatchScoreService` with `score` + `canScore` and named-constant weights.
- Schema — `dog`, `dog_preference` (changesets 001–004, YAML master index). Six seeded demo dogs plus one deliberately corrupt legacy row (negative age, no CHECK constraint — must stay).
- Tests — `MatchScoreServiceTest` only: unit-level, direct instantiation, no Spring context, no database.
- Runtime — Spring Boot 4.1, Hibernate 7, Liquibase (Boot module + core), PostgreSQL 18 (alpine image), `ddl-auto: validate`, `open-in-view: false`.

### Conventions that constrain the design
- Package per concept; dependency direction one-way (`match` → `dog`, keep acyclic).
- Domain rules in services, never controllers; `require` at the top of public functions; total predicate beside the partial one (`canScore`/`score`).
- Bean validation on request DTOs with `@field:` use-site targets; companion `of()` factory for responses.
- Liquibase: plain SQL, `NNN-kebab-description.sql`, explicit `--rollback`, appended to the YAML index, immutable once run. Next free file number: **005**.
- Named constants for scoring/validation numbers — the 25 km default and zero-tolerance rules belong in named constants.
- No `@ControllerAdvice` today; `require`/constraint failures can surface as unstructured 500 (documented in steering).
- Tests stay unit-level; `mise run test` must keep needing no database.

### Documented inconsistencies this feature aggravates
From `structure.md` (Known Inconsistencies — "do not copy, follow the dominant pattern"):
1. `MatchController` orchestrates candidate selection and ordering — steering says it belongs in a service.
2. `MatchResponse` means two different things — steering says split it rather than extend it.
3. The whole HTTP/persistence surface is untested — `@WebMvcTest` slices named as the fix.

## Requirement-to-Asset Map

| Requirement | Existing asset | Gap tag | Detail |
|---|---|---|---|
| R1 profile location | `Dog`, `DogRequest`/`DogResponse`, bean-validation pattern | Missing | Nullable latitude/longitude on entity + `ALTER TABLE` changeset (005); location on request/response DTOs with range validation; echo-when-present / omit-when-absent in responses |
| R2 radius parameter | No endpoint takes a parameter yet; named-constant pattern exists | Missing | `radius` query param, default 25 km; negative → 400; non-numeric → 400 |
| R2 → 400 mechanics | No `@ControllerAdvice`; `require` currently surfaces as 500 | Unknown / Constraint | How Boot 4 maps `@Validated`-controller `ConstraintViolationException` and `MethodArgumentTypeMismatchException` must be verified in design; alternatives: param checks in the controller returning `ResponseEntity`, or a (structural) `@ControllerAdvice` |
| R3 radius filtering | `bestFor` loads all dogs and filters in memory (self, `canScore`) | Missing | Distance function; candidate-without-location exclusion; subject-without-location → 422 (pattern exists: unscorable subject → 422) |
| R4 ordering + distance | `sortedByDescending { score }` exists; `MatchResponse` (dogId, name, score) | Missing / Constraint | Distance per listing entry in km; steering recommends splitting `MatchResponse` — this feature is the natural split moment |
| R5 preserved guarantees | All present in `MatchController` today | Constraint | Zero tests pin them — listing is untested; corrupt legacy row must keep being dropped, not crash the endpoint, through whatever refactor happens |
| Demo data | 7 shipped dogs, none with a location | Missing / decision | After the change, `GET /api/matches/{id}` on any shipped subject dog returns 422 (correct per R3) — the demo loses all listing value unless a new changeset backfills coordinates |

`docs/PRD.md` contains no location/geolocation intent (no matches for geo/location/radius/km/distance), so requirements.md is the sole scope source; no conflict.

## Implementation Approach Options

### Option A — Extend in place
Add nullable lat/lon columns + fields; extend `DogRequest`/`DogResponse`; add the radius parameter and in-memory filtering directly in `MatchController`; append a nullable `distance` to the shared `MatchResponse`.
- Touches: 005 changeset + master index, `Dog.kt`, `DogController.kt`, `MatchController.kt`.
- ✅ Fewest files; no structural change.
- ❌ Deepens both documented inconsistencies (domain logic in controller; `MatchResponse` overload — nullable distance on the pairwise endpoint is exactly the "extend the overload" anti-pattern steering warns about); new rules hard to unit-test in isolation.

### Option B — New listing service + DTO split (recommended lean)
A new service in `match/` owns the listing: radius default and rejection, exclusions (self, unscorable, no location), distance computation, ordering — with a total eligibility predicate beside its partial functions, mirroring `canScore`/`score`. A listing-specific response DTO (id, name, score, distance) replaces `MatchResponse` on the listing endpoint; `MatchResponse` stays for pairwise only. `MatchController` reduces to HTTP translation (404/400/422). Dog side: optional location on entity/DTOs, `@field:` range validation, `of()` factory.
- ✅ Resolves both documented inconsistencies at the exact moment they would be aggravated; rules unit-testable without Spring; fits all validation-layer conventions.
- ❌ More files; existing listing logic must be moved out of the controller without breaking R5 (the move is small — `bestFor` is ~15 lines).

### Option C — Hybrid: service in front, database push-down behind
Same shape as B, but filtering happens in a repository query method (native SQL haversine; optionally PostGIS/earthdistance later) instead of `findAll` + in-memory filter.
- ✅ Scales beyond trivial dog counts.
- ❌ Breaks the unit-test-without-DB property for the core rule; no integration-test infrastructure exists (no Testcontainers, per tech.md); PostGIS/earthdistance needs `CREATE EXTENSION` privileges and is overkill — the current code already loads every row, so scale is not an evidenced problem.

### Distance computation (orthogonal to A/B/C)
1. Pure-Kotlin haversine in the service — no new dependency, unit-testable, matches tech.md posture.
2. Native SQL haversine in a repository query method — steering does say query methods belong in repositories, but the rule becomes untestable at unit level with the current setup.
3. PostGIS / earthdistance extension — new runtime requirement + privileges; research only if scale ever demands it.

## Complexity and Risk
- **Effort: S–M.** Extends established patterns; the only algorithmic piece is a distance formula; the schema change is one nullable-column changeset; no external integrations.
- **Risk: Low** with Option B + in-memory distance (familiar stack, clear scope, unit-testable rules). Rises to **Medium** only with DB-side geo (privileges, untestable rules) or if the 400-mapping plumbing for parameter validation turns out non-trivial in Boot 4.

## Research Needed (carry into design)
1. **Negative radius → 400:** verify Spring Boot 4 behaviour for `@Validated` controller + constraint on `@RequestParam` (`ConstraintViolationException` → 500 by default?), vs. manual checks returning `ResponseEntity` in the controller, vs. introducing `@ControllerAdvice` (a structural change per steering, worth its own commit).
2. **Non-numeric radius → 400:** confirm Boot 4's default mapping of `MethodArgumentTypeMismatchException`.
3. **JSON omission of absent location:** per-property `@JsonInclude(NON_NULL)` vs global `spring.jackson.default-property-inclusion`.
4. **Boundary-inclusive AC testing:** planted coordinates with exact km distances and a floating-point tolerance policy for `distance ≤ radius`.
5. **Seed backfill decision:** new changeset giving the six demo dogs coordinates (else every shipped subject 422s); the legacy row stays location-less by design.
6. **Only if DB-side geo is chosen:** earthdistance/PostGIS availability and privileges in postgres:18-alpine, and Liquibase `CREATE EXTENSION` changeset semantics.

## Recommendations for the Design Phase
- Lean toward Option B: it is the shape steering already prescribes, keeps `mise run test` database-free, and gives R5 a real home in unit tests.
- Keep distance in-memory (sub-option 1) until scale is an evidenced problem; revisit as C later if ever.
- Decide the 400-mapping mechanism early (research item 1/2) — it touches the no-`@ControllerAdvice` convention.
- Decide the seed backfill (research item 5) explicitly — it is user-visible demo behaviour, and an immutable-changeset question (new changeset, never edit 003).

---

# Gap Analysis Addendum: location editing (Requirement 1, ACs 6–9)

## Scope of this addendum
After the main analysis, requirements.md was extended: the stored location of an existing dog can now be edited through the dog API (Requirement 1, ACs 6–9), while all other dog attributes stay read-only. This addendum covers only the delta; the main analysis above is otherwise unchanged.

## Current State relevant to the delta
- **No update operation exists anywhere.** `DogController` has GET (all), GET (by id), POST — no PUT/PATCH. No repository update beyond `save`. No precedent for partial update semantics in this codebase.
- **Mutable entity**: `Dog` is a JPA-pragmatic mutable `class` with `var` fields (per structure.md) — assigning a new location in place is idiomatic; a new `save` on a detached/managed entity is the standard JPA path.
- **Validation pattern**: `@Valid` on `@RequestBody` with `@field:`-targeted constraints on the request DTO, giving 400 on violations with zero controller code.

## Requirement-to-Asset Map (delta)

| Requirement | Existing asset | Gap tag | Detail |
|---|---|---|---|
| R1 AC6 — set location on a dog without one | Mutable `Dog`, `DogRepository.save`, `of()` factory | Missing | New update endpoint on `DogController`; new request DTO for the location; `save` of the modified entity; response echoes the new location |
| R1 AC7 — replace an existing location | Same as above | Missing | Same endpoint; no special case needed — the "set" and "replace" ACs pin that neither is rejected |
| R1 AC8 — out-of-range coords on update → 400 | Bean-validation pattern | Missing | Same `@field:Min/-Max`-style constraints reused on the update DTO; validation answers both create and update ACs |
| R1 AC9 — unknown dog → 404 | `byId` 404 pattern | Constraint | Same `.orElseGet { notFound }` translation; nothing new |

## Design decisions the update endpoint introduces (not present before)

1. **Update semantics — full location object, not partial lat/lon.** The ACs speak of setting/replacing "a location". Requiring both coordinates in every update request avoids the partial-update question ("only lat sent — is lon kept?"). The requirements do not ask for clearing/nulling a location; a design that wants to support removal must go back to requirements first — as written, **clearing a location is out of scope** and the update request should carry a complete, validated location.
2. **HTTP shape.** The dog API today is REST-conventional (base `/api/dogs`, item under `/{id}`, POST with explicit `@ResponseStatus(CREATED)`). Options:
   - `PATCH /api/dogs/{id}/location` — names the one editable attribute explicitly; partial-update intent is unambiguous.
   - `PUT /api/dogs/{id}/location` — same surface, "replace" semantics, matches AC7's replace wording.
   - `PATCH /api/dogs/{id}` with a dog-update DTO — anticipates future editable attributes, but today only location is editable and the DTO would mislead.
   - The subresource (`.../location`) variants keep the contract honest about what is editable; the choice between PATCH/PUT belongs in design.
3. **No new 400-mechanism research needed.** AC8 reuses the bean-validation path already required by R1 AC5 — it does not add to research item 1/2 (those concern *query-parameter* validation, not `@RequestBody`).
4. **Optimistic concurrency / lost updates** — not required by any AC, no version column exists; flag only as a known limitation if concurrent edits ever matter. Not a research item now.

## Impact on effort/risk
- **Effort: unchanged at S–M.** One more handler + DTO reusing established patterns; no new schema, service, or test infrastructure.
- **Risk: unchanged, Low.** No new mechanism; the update path is the safest part of the feature (pure bean validation + standard 404/200 translation).

## Impact on options A/B/C and research items
- **None.** The location-edit endpoint lives entirely in `dog/` and is orthogonal to the listing refactor choice (A/B/C) and to every research item in the main analysis. The one carry-forward note: `DogController` gains a second mutation path while remaining untested — if `@WebMvcTest` slices are added for this feature (see structure.md's known inconsistency #3), the update endpoint belongs in them.

---

# Design-Phase Research & Decisions

## Summary
- **Feature**: nearby-dog-matches
- **Discovery scope**: Extension (light discovery). Codebase patterns came from the gap analysis above; this pass closed the Boot 4 testing/exception-mapping unknowns (research items 1–3 of the main analysis) via the Spring Boot 4.0 release notes and migration guide.
- **Consulted guidance**: `kiro-spec-design` rules (principles, light discovery, synthesis, review gate); steering `product.md`, `tech.md`, `structure.md`.

## Research Log

### Spring Boot 4 testing support for the new controller slices
- **Context**: structure.md names `@WebMvcTest` as the fix for the untested HTTP surface; the main analysis left the Boot 4 specifics of mocking and slicing open.
- **Sources**: Spring Boot 4.0 Release Notes and Migration Guide (github.com/spring-projects/spring-boot wiki).
- **Findings**:
  - `@MockBean`/`@SpyBean` are **removed** in Boot 4; `@MockitoBean`/`@MockitoSpyBean` (Spring Framework's `org.springframework.test.context.bean.override.mockito`) replace them.
  - Boot 4 modularized test infrastructure: `@WebMvcTest` support ships in `spring-boot-starter-webmvc-test` (module `spring-boot-webmvc-test`); the classic `spring-boot-starter-test` does not carry it.
  - `@SpringBootTest` no longer auto-configures MockMvc (needs `@AutoConfigureMockMvc`); not relevant to `@WebMvcTest` slices.
- **Implications**: pom.xml gains one **test-scope** dependency (`spring-boot-starter-webmvc-test`); slices use `@MockitoBean`. No runtime dependencies change.

### Radius 400-mapping mechanism (main-analysis research items 1 and 2)
- **Context**: requirements demand 400 for a negative (2.4) and a non-numeric (2.5) radius; the repo has no `@ControllerAdvice` and steering calls adding one a structural change.
- **Findings / decision**: Avoid both `@Validated` method validation and a `@ControllerAdvice`. Bind the parameter as `@RequestParam(required = false) radius: Double?`:
  - non-numeric input fails Double conversion → `MethodArgumentTypeMismatchException` → 400 by Spring MVC's long-standing default resolver behavior (empirically pinned by the new `MatchControllerTest` slice);
  - negative input is an explicit `if (radius < 0) return badRequest()` in the controller, plus `require(radiusKm >= 0)` in the service as the domain-invariant layer.
- **Implications**: fully deterministic, no new exception plumbing, no structural change, and the `@WebMvcTest` tests verify the framework mapping rather than trusting documentation.

### Jackson generation in this project (main-analysis research item 3)
- **Context**: "omit location when absent" needs a JSON inclusion strategy; Boot 4 moved to Jackson 3 (`tools.jackson`) with Jackson 2 in a deprecated form.
- **Findings**: this project resolves `com.fasterxml.jackson.module:jackson-module-kotlin:2.21.4` (Jackson 2 path) while Boot 4's Jackson 3 artifacts are also on the classpath; `@JsonInclude` from `com.fasterxml.jackson.annotation` is shared by both generations (the annotations module kept its old coordinates).
- **Implications**: per-property `@JsonInclude(JsonInclude.Include.NON_NULL)` on `DogResponse.location` works as-is. A full Jackson 2→3 migration is **out of boundary** for this feature (pre-existing condition, no user-visible need).

## Design Synthesis
- **Generalization**: the four listing exclusions (self, unscorable, location-less, beyond radius) are one eligibility filter chain in one service; the `canScore`-before-`score` pattern generalizes into a sealed result type that tells the controller *why* a subject cannot anchor a listing (missing / unscorable / location-less). Location is modeled once as a pair value object — the interface generalizes (any future consumer of coordinates reuses `Location`); the implementation stays minimal (two columns).
- **Build vs adopt**: hand-rolled haversine (~6 lines) over a geo library — rejected dependencies for one formula; no PostGIS/earthdistance (privileges, untestable rules at unit level, no evidenced scale problem); no `@ControllerAdvice` framework — explicit checks suffice. The only adoption is the Boot-provided test starter the slices need anyway.
- **Simplification**: no separate distance service (one private function); no separate location table (embedded columns); no `LocationService` (no domain rules beyond range validation, which bean validation owns); no centralised error handler; one listing DTO instead of extending the `MatchResponse` overload.

## Design Decisions

### Decision: listing logic moves to a new `MatchListingService`; DTO split at the listing endpoint
- **Selected**: Option B from the main analysis. `MatchListingService` (dog repository + score service injected) owns the radius rule, distance, eligibility chain, ordering; returns a sealed `ListingResult`. `MatchController` translates HTTP only. `NearbyMatchResponse` (id, name, score, distanceKm) replaces `MatchResponse` on the listing; `MatchResponse` remains pairwise-only.
- **Rationale**: resolves both documented inconsistencies (domain logic in controller; `MatchResponse` overload) exactly where this feature would deepen them; rules unit-testable without Spring; preserves `mise run test` database-free.
- **Trade-offs**: one more file; existing ~15 lines of listing logic move (small, and R5 guarantees get their first real tests as part of the move).

### Decision: location as an embeddable value object, present or absent as a unit
- **Selected**: `@Embeddable data class Location(latitude, longitude)` in `Dog.kt` (alongside `Gender`, per the same-file convention); `Dog.location: Location?` with `@Embedded`; changeset 005 adds nullable `latitude`/`longitude` DOUBLE PRECISION columns; no DB CHECK constraints.
- **Rationale**: the pair is atomic — no state with latitude but no longitude can exist in the entity; nullable single field gives clean present/absent semantics for 1.1–1.4; follows the codebase's app-layer-validation philosophy (the legacy-row lesson: the schema stays permissive, behavior copes).
- **Trade-offs**: imported rows with out-of-range coordinates are not rejected by the DB; haversine is total so they produce garbage distances, not crashes — accepted and noted as out of boundary.

### Decision: location editing is `PUT /api/dogs/{id}/location` with a complete location body
- **Selected**: subresource endpoint, both coordinates required and validated (`@field:Min/@field:Max`), replace semantics; 404 for unknown dog; clearing a location is not supported (not in requirements).
- **Rationale**: the subresource keeps the contract honest about the single editable attribute; full-object body avoids partial-update ambiguity; AC 1.6–1.9 map directly.

### Decision: distance is in-service haversine in kilometres
- **Selected**: private function in `MatchListingService`, named constants `EARTH_RADIUS_KM = 6371.0`, `DEFAULT_RADIUS_KM = 25.0`; inclusive comparison `distance <= radius`; zero radius admitted.
- **Rationale**: no new dependency, unit-testable, matches the transparent-tunability value proposition of the product.

### Decision: seed backfill changeset gives the six demo dogs coordinates
- **Selected**: new changeset `006-seed-dog-locations.sql` (never editing 003), coordinates clustered around one city with at least one dog beyond the default 25 km radius so the filter is visible in the demo; the legacy row (`Nonna`) deliberately stays location-less.
- **Rationale**: without it, every shipped subject dog returns 422 and the demo loses all listing value; keeping `Nonna` location-less preserves the location-less-candidate path in every demo listing.

## Risks & Mitigations
- Boot 4 exception-mapping assumptions wrong (non-numeric radius not 400) — mitigated: pinned empirically by `MatchControllerTest`; if mapping differs, the fix is one explicit check, no architecture change.
- `@Embedded` nullable mapping surprises under Hibernate 7 — mitigated: changeset 005 keeps columns nullable, `ddl-auto: validate` fails startup loudly on drift; verified by `mise run db` + startup during implementation.
- Concurrent location edits can lose updates (no version column) — accepted: out of requirements, single-owner profile edits, noted as a known limitation.

## References
- Spring Boot 4.0 Release Notes — https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes
- Spring Boot 4.0 Migration Guide (@MockBean removal, test starters, Jackson 3) — https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide