# Implementation Plan

## Task Format Template

Use whichever pattern fits the work breakdown:

### Major task only
- [ ] {{NUMBER}}.{{PARALLEL_MARK}} {{TASK_DESCRIPTION}}
  - {{DETAIL_ITEM_1}} (Include details only when needed. If the task stands alone, omit bullet items.)
  - _Requirements: {{REQUIREMENT_IDS}}_

### Major + Sub-task structure
- [ ] {{MAJOR_NUMBER}}. {{MAJOR_TASK_SUMMARY}}
- [ ] {{MAJOR_NUMBER}}.{{SUB_NUMBER}}{{SUB_PARALLEL_MARK}} {{SUB_TASK_DESCRIPTION}}
  - {{DETAIL_ITEM_1}}
  - {{DETAIL_ITEM_2}}
  - {{OBSERVABLE_COMPLETION_ITEM}} (At least one detail item should state the observable completion condition for this task.)
  - _Boundary: {{COMPONENT_NAMES}}_ (Only for (P) tasks. Omit when scope is obvious.)
  - _Depends: {{TASK_IDS}}_ (Only for non-obvious cross-boundary dependencies. Most tasks omit this.)
  - _Requirements: {{REQUIREMENT_IDS}}_ (IDs only; do not add descriptions or parentheses.)

> **Parallel marker**: Insert ` (P)` immediately after the task number for tasks that can execute in parallel. Omit the marker when running in `--sequential` mode.
>
> **Optional test coverage**: When a sub-task is deferrable test work tied to acceptance criteria, mark the checkbox as `- [ ]*` and explain the referenced requirements in the detail bullets.

## Tasks

- [x] 1. Foundation: schema and test infrastructure
- [x] 1.1 Give the dog table optional location columns via a new append-only changeset
  - Add two nullable coordinate columns of double precision to the dog table in a new plain-SQL Liquibase changeset with an explicit rollback that drops them
  - Register the changeset in the master changelog index; no existing changeset is edited
  - The application still starts cleanly against a migrated database (schema validation passes at startup)
  - _Requirements: 1.1, 1.2, 1.6, 1.7_
- [x] 1.2 Add the web-test starter used by controller slice tests
  - Declare the Spring Boot webmvc test starter as a test-scoped dependency in the build file; no runtime dependency changes
  - The default build still packages the application, and the test compilation picks up the web slice annotations
  - _Requirements: 2.4, 2.5_

- [x] 2. Core: the dog profile carries a location
- [x] 2.1 (P) Store a coordinate pair as one optional unit on the dog profile
  - Model the location as a value object holding both coordinates, placed in the dog concept alongside the existing gender enumeration; the entity gains a single optional field of that type so a half-present location cannot exist
  - The value object provides value equality and no domain logic; range rules live at the API boundary, not here
  - The persisted dog round-trips its location through the entity layer against the new columns
  - _Boundary: dog model_
  - _Requirements: 1.1, 1.2, 1.6, 1.7_
- [x] 2.2 (P) Expose the location on profile create and read
  - The create request accepts an optional location with both coordinates validated against the earth's coordinate ranges at the request boundary
  - The profile response includes the location when present and omits the key entirely when absent
  - Creating a dog with or without a location both succeed and the response echoes the stored state faithfully
  - _Boundary: dog HTTP surface_
  - _Depends: 1.1, 2.1_
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_
- [x] 2.3 Let an owner set or replace their dog's stored location
  - A dedicated edit endpoint on the dog profile's location resource accepts a complete, validated coordinate pair
  - Setting a location on a dog that had none, and replacing an existing one, both store the new value and return the updated profile with its location present
  - An out-of-range coordinate is rejected as bad input; an unknown dog is reported as not found; clearing a location is deliberately not offered
  - _Boundary: dog HTTP surface_
  - _Depends: 1.1, 2.1, 2.2_
  - _Requirements: 1.6, 1.7, 1.8, 1.9_

- [ ] 3. Core: the nearby match listing
- [ ] 3.1 (P) Compute straight-line distances over the earth's surface
  - Implement the distance rule between two locations as kilometres over the earth's surface, with the earth's radius and the default radius of twenty-five kilometres as named constants
  - The same point yields exactly zero distance; the computation is symmetric in its arguments and correct near the equator scale (about 111 km per latitude degree)
  - The distance logic is a private rule inside the listing service, not a shared utility
  - _Boundary: match listing service_
  - _Depends: 2.1_
  - _Requirements: 2.1, 4.2_
- [ ] 3.2 Build the listing service that owns radius, eligibility, and ordering rules
  - Given a subject dog and a non-negative radius in kilometres, produce the ranked listing: never the subject itself, never an unscorable candidate, never a candidate without a location, and never a candidate farther than the radius, with the radius boundary inclusive and a radius of zero admitting only dogs at the exact same spot
  - Refuse distinct outcomes for a missing subject, an unscorable subject, and a subject without a location, so the HTTP layer can translate each faithfully; reject a negative radius as a domain invariant before any work
  - Order the kept candidates by match score from highest to lowest using the existing scoring rules unchanged, and report each entry's distance in kilometres alongside the dog's identity, name, and score
  - The service is stateless with constructor-injected dependencies only, so its rules are testable without a database or a running server
  - _Boundary: match listing service_
  - _Depends: 2.1_
  - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 5.4_
- [ ] 3.3 Wire the listing endpoint to the new service and its response shape
  - The existing listing endpoint gains an optional radius query parameter: omitted means the twenty-five kilometre default, a negative value is rejected as bad input before the dog is looked up, and a value that is not a number is rejected as bad input by the framework's own type handling
  - The endpoint returns the new listing response carrying each entry's identity, name, score, and distance; the subject-level refusals translate to not-found and unprocessable-entity statuses respectively
  - The pairwise endpoint and its response shape remain exactly as they are; the controller holds no listing rules of its own anymore
  - _Boundary: match HTTP surface_
  - _Depends: 3.1, 3.2_
  - _Requirements: 2.1, 2.4, 2.5, 3.3, 4.2, 4.3, 5.3, 5.4_

- [ ] 4. Validation: behaviour-pinning tests
- [ ] 4.1 (P) Pin the listing rules with unit tests of the service
  - Test the listing rules directly, in plain English behaviour names, using an in-memory stand-in for the dog store and planted coordinates whose separations are known
  - Cover the radius boundary inclusion, zero radius, the default constant, exclusion of the subject, unscorable and location-less candidates, the distinct refusal outcomes, score ordering with reported distances, and the negative-radius rejection
  - Assert relations rather than magic numbers, and every assertion fails on a real defect (a sign error, a swapped axis, a wrong unit each turn a specific test red)
  - The whole suite still runs without a database
  - _Boundary: match listing tests_
  - _Depends: 3.2_
  - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 5.4_
- [ ] 4.2 (P) Pin the listing endpoint with a controller slice test
  - Exercise the listing endpoint through the web slice with the listing service replaced by a mock, covering: omitted radius calls through with the default, a negative radius is rejected with bad-input status, a non-numeric radius is rejected with bad-input status by the framework's own mapping, and each refusal outcome maps to its documented status and body shape
  - The slice test proves the parameter translation and status mapping without a database
  - _Boundary: match HTTP tests_
  - _Depends: 3.3_
  - _Requirements: 2.1, 2.4, 2.5, 3.3, 4.2, 4.3, 5.3, 5.4_
- [ ] 4.3 (P) Pin the dog location surface with a controller slice test
  - Exercise the dog endpoints through the web slice with the dog store replaced by a mock, covering: creation with and without a location and the faithful echo or omission in the response, out-of-range coordinates rejected on both create and location update, setting and replacing a location on an existing dog, and an unknown dog reported as not found
  - The slice test proves the validation and status behaviour of the dog surface without a database
  - _Boundary: dog HTTP tests_
  - _Depends: 2.2, 2.3_
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9_

- [ ] 5. Integration: demo data and the whole system
- [ ] 5.1 Give the demo dogs locations so the listing is demonstrable
  - Add a new append-only seed changeset that stores coordinates for the six existing demo dogs, matched by name as the original seed does, clustered around one city with at least one dog beyond the default radius so the filter is visible in the demo
  - Register the seed changeset in the master changelog index; no existing changeset is edited
  - The deliberately corrupt legacy dog stays without a location, keeping the location-less and unscorable candidate paths visible in every demo listing
  - Rolling back the changeset clears the seeded coordinates without touching any other data
  - _Requirements: 3.2_
- [ ] 5.2 Verify the whole feature against a real database
  - Start the application against its own database with the migrations applied and confirm startup validation passes
  - Walk the user journeys end to end: create a dog with a location and see it echoed; edit a location; request the listing with omitted, explicit, zero, and negative radii; observe far and location-less dogs excluded and the corrupt dog dropped without failing the request
  - The full build packages, and the full test suite passes with the project's own tasks, with no new task invented
  - _Depends: 5.1, 4.1, 4.2, 4.3_
  - _Requirements: 1.1, 1.2, 1.6, 1.7, 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 5.4_
