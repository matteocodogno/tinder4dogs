# Implementation Plan — F-03: Play and Mating Modes

- [ ] 1. Foundation: mode storage on the dog profile
- [x] 1.1 Persist active modes and mating details in the schema
  - One new append-only migration file with three changesets: a mode child table mirroring the preference table, a seed that makes every existing dog play-only, and three nullable self-declared mating detail columns on the dog table
  - Register the file in the master changelog index by appending one include; never edit existing entries; every changeset carries a comment and an explicit rollback
  - Observable completion: with the database up, the application starts with schema validation green, every pre-existing dog (including the legacy bad row) has a PLAY row, and the dog table carries the three new nullable columns
  - _Boundary: Liquibase migration_
  - _Requirements: 1.1_

- [x] 1.2 Model modes and mating details on the dog profile
  - Introduce the Mode concept — play and mating — beside gender, a set of active modes stored the same way preferences are, and the three nullable mating detail fields on the profile
  - Expose a repository query for the dogs active in a given mode
  - Observable completion: the application still starts with schema validation green and the existing endpoints behave unchanged, while the profile carries modes and mating details and mode membership is queryable
  - _Boundary: Dog entity and Mode, DogRepository_
  - _Requirements: 1.1_

- [ ] 2. Core: mode rules and the profile surface
- [ ] 2.1 Enforce the mode rules on the dog profile
  - A dog always keeps at least one active mode: creation without a mode and replacement down to none are rejected with a clear error
  - Modes are replaceable at any time; replacing with the current set is a harmless no-op
  - Activating mating mode requires pedigree, neutering status and health status to be present, and the rejection names exactly the missing fields; play-only profiles require none of them
  - Pin every rule with unit tests that can fail, following the repository's test conventions
  - Observable completion: the mode-rule unit suite is green — empty-mode creation, last-mode removal, gate rejection naming the missing fields, gate acceptance for a complete profile, play-only creation without details
  - _Boundary: DogService_
  - _Requirements: 1.1, 1.2, 1.4, 1.5, 4.1, 4.2, 4.3_

- [ ] 2.2 Expose modes over the profile API
  - Profile creation accepts the declared modes and mating details, records them through the mode rules, and every profile read returns them
  - Mode replacement becomes a new update operation on the profile that returns the updated profile
  - The mating gate surfaces as a 422 whose body names the missing fields; an empty mode set is rejected as malformed input
  - Pin the status codes and payloads with HTTP slice tests
  - Observable completion: creation returns 201 with modes echoed; mode replacement returns 200 with the new modes, 404 for an unknown dog, 400 for an empty mode set, and 422 naming the missing mating details
  - _Boundary: DogController_
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 4.1, 4.2, 4.3_

- [ ] 3. Core: mode-aware matching
- [ ] 3.1 (P) Score compatibility per mode
  - The scorer becomes mode-aware: each mode gets its own criteria and its own derived maximum — mating keeps today's four criteria, play drops the two mating-only criteria, same breed and opposite genders
  - Scorability is asked per mode, and the negative-age rejection stays per mode
  - The old mode-agnostic score stays temporarily as a delegation to the mating score so the existing endpoints and suite stay green; it is removed by 3.3
  - Rewrite the scorer unit suite per mode with relational assertions
  - Observable completion: the per-mode scorer suite is green — the play score is unchanged by breed or gender, the mating score still rewards both, the two scores differ for a pair that differs in breed or gender, every score stays within zero and one, and a negative age is rejected in both modes
  - _Boundary: MatchScoreService_
  - _Depends: 1.2_
  - _Requirements: 3.1, 3.2, 3.3_

- [ ] 3.2 Compose mode-aware candidate lists
  - List composition moves out of the HTTP layer into a matching service: for a mode it gathers only dogs active in that mode, never the subject itself, skips candidates that cannot be scored in that mode, scores each pair, and sorts best first
  - A pairwise score is served through the same service per mode, with an unscorable dog rejected rather than scored
  - Pin the composition rules with unit tests over a mocked repository
  - Observable completion: the composition suite is green — play lists exclude mating-only dogs and vice versa, a dog is never its own candidate, an unscorable candidate is skipped while the rest of the list still returns, an unscorable subject is rejected, and results are ordered by score from best to worst
  - _Boundary: MatchService, MatchScoreService, DogRepository_
  - _Requirements: 2.1, 2.2, 2.3, 2.5, 2.6, 3.3_

- [ ] 3.3 Serve mode-named match endpoints
  - Replace the match surface: a ranked list and a pairwise score per mode, named by the mode in the path
  - A list for a mode the dog is not active in does not exist; a pairwise score for a mode either dog is not active in does not exist; an unscorable subject is rejected as unusable data
  - Split the dual-purpose match response into a ranked-candidate shape and a pairwise-score shape
  - Remove the mode-agnostic endpoints and the temporary delegation from 3.1
  - Pin the endpoint statuses with HTTP slice tests
  - Observable completion: the mode-named endpoints return 200 with the right dogs; the mating list of a play-only dog and a pairwise score for a non-active mode return 404; an unscorable subject returns 422 on a list, and a pairwise score returns 422 when either dog is unscorable
  - _Boundary: MatchController, MatchScoreService_
  - _Requirements: 2.1, 2.2, 2.4, 2.6, 3.1, 3.2, 3.3, 3.4_

- [ ] 4. Integration: verify the modes end to end against a real database
  - With the database up and the application running, exercise the flows over HTTP: create a both-modes dog with complete mating details, try creating a mating dog without details and see the 422 that names them, switch a dog's modes and see the change in later list requests
  - Read both lists of a both-modes dog and the play list of a play-only dog; request the mating list of a play-only dog and see 404
  - Confirm the pre-existing dogs are play-only after the migration and the legacy bad row is skipped in every mode's list
  - Observable completion: the full test suite is green and the steps above pass against the running service on the migrated database
  - _Boundary: DogService, MatchService, Liquibase migration_
  - _Requirements: 1.1, 1.4, 2.1, 2.2, 2.5, 4.1, 4.2_

## Implementation Notes

- Runtime verification environment: `docker` is absent; start the DB with `podman run` replicating `compose.yaml` (same image/env/port/volume, image as `docker.io/library/postgres:18-alpine`) — and never `pkill -f tinder4dogs`: that pattern also matches the rootless podman container `tinder4dogs-db` and kills the database. Restarting a stopped container can lose the host port mapping; recreate it with `podman rm -f` + `podman run` (the named volume keeps the migrated schema).
