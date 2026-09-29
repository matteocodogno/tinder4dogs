# Implementation Plan

## 1. Foundation

- [x] 1.1 Add and register the profile schema migration.
  - Add nullable profile scalar fields, temperament and ordered photo tables, deterministic owner backfill for every existing dog, including the legacy invalid-age row, then enforce non-null ownership and active-owner uniqueness.
  - Preserve the legacy age value and define rollback in reverse dependency order.
  - Verify child-table keys, cascade behavior, photo positions, and string enum compatibility are represented in the migration.
  - The migration is registered after the existing changelog entries and its SQL is ready for execution without modifying immutable changesets.
  - _Boundary: Profile schema_
  - _Requirements: 5.6, 5.7, 8.4, 9.1, 9.2_

- [ ] 1.2 Map scalar dog profile data.
  - Add fixed size, energy, and health vocabularies; optional mating declarations; opaque owner id; removal state; and preserve existing preferences independently.
  - Keep new profile values nullable where legacy rows may not have them, while enforcing the 1–100 character owner-id invariant for new input.
  - Legacy rows load successfully and scalar values round-trip through persistence.
  - _Depends: 1.1_
  - _Boundary: Dog scalar model_
  - _Requirements: 1.1, 1.2, 1.6, 2.1, 2.2, 2.3, 2.4, 2.5, 5.1, 5.5, 6.4, 7.1, 7.2, 8.1, 9.2, 9.4_

- [ ] 1.3 Map owned temperament and photo data.
  - Persist the fixed temperament set as dog-owned values and photo references with an explicit position.
  - Load both collections eagerly and retain photo order, with position zero representing the primary photo.
  - Temperament values and ordered photo references persist and load without image bytes.
  - _Depends: 1.1_
  - _Boundary: Dog child model_
  - _Requirements: 1.3, 3.1, 3.2, 3.4, 3.6, 7.1, 9.2_

- [ ] 1.4 Add active and direct repository operations.
  - Provide active list, active owner occupancy, active match subject/candidate/pairwise, and direct-id profile lookups.
  - Ensure active operations exclude removed rows while direct lookup continues to return removed rows.
  - Repository tests or focused verification demonstrate all active/direct query behaviors.
  - _Depends: 1.2, 1.3_
  - _Boundary: Dog repository_
  - _Requirements: 5.3, 8.2, 8.3, 8.4, 9.1, 9.5_

## 2. Domain

- [ ] 2.1 Define profile validation and domain error contracts.
  - Validate required fields, non-negative input age, trimmed 1–100 character owner ids, fixed vocabularies, and the six-photo limit.
  - Define field-identifiable validation failures separately from the generic duplicate-owner domain failure; do not add HTTP translation here.
  - Invalid values produce typed domain outcomes and duplicate ownership produces a generic failure without owner-specific detail.
  - _Depends: 1.2, 1.3_
  - _Boundary: Profile validation_
  - _Requirements: 1.4, 3.3, 4.2, 4.3, 5.2, 6.3_

- [ ] 2.2 Implement transactional minimal profile creation.
  - Convert validated input into persisted profile data, including optional mating values, preferences, temperament values, photos, and the unverified owner id.
  - Check active owner occupancy before persisting and return the generic duplicate-owner domain failure when occupied.
  - A minimal valid profile persists and returns its owner id, optional values, preferences, and derived completeness inputs within one transaction.
  - _Depends: 1.4, 2.1_
  - _Boundary: Profile service_
  - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4, 2.5, 4.1, 4.4, 4.5, 5.1, 5.3, 5.4, 5.5, 7.1, 7.2_

- [ ] 2.3 Implement transactional direct and active profile read projections.
  - Build profile projections for active lists and direct reads, retaining removed and legacy invalid-age profiles for direct reads.
  - Include optional values, preferences, ordered photos, removal state, and the derived completeness result.
  - Active lists omit removed profiles while direct reads return removed and legacy profiles successfully.
  - _Depends: 1.4, 2.2_
  - _Boundary: Profile service_
  - _Requirements: 1.5, 7.1, 7.2, 7.3, 9.1, 9.2, 9.4, 9.5_

- [ ] 2.4 Implement transactional incremental profile updates.
  - Treat absent patch fields as unchanged, allow optional fields to be set independently, replace the complete photo list when supplied, and keep owner identity immutable.
  - Reuse the same validation rules as creation for supplied patch values.
  - A one-field patch preserves unrelated values and invalid patch input is rejected.
  - _Depends: 2.2, 2.3_
  - _Boundary: Profile service_
  - _Requirements: 3.1, 3.2, 3.4, 3.5, 4.3, 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 2.5 Implement live transactional completeness derivation.
  - Derive completeness from all required legacy fields plus size, energy, at least one temperament, and at least one photo.
  - Exclude pedigree, neutering, and health status from the completeness predicate.
  - Completeness toggles only when the complete predicate changes after profile updates.
  - _Depends: 2.2, 2.4_
  - _Boundary: Profile service_
  - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [ ] 2.6 Implement transactional soft removal and conflict mapping.
  - Retain profile data while marking removal, preserve the first removal state on repeated requests, and release the active owner slot.
  - Catch a concurrent database uniqueness conflict and map it to the same generic duplicate-owner domain failure as the pre-check.
  - Removed data remains directly readable, replacement creation is allowed, repeated removal is idempotent, and a simulated uniqueness conflict produces the generic domain failure.
  - _Depends: 1.4, 2.2, 2.3_
  - _Boundary: Profile service_
  - _Requirements: 5.3, 8.1, 8.3, 8.4, 8.6_

- [ ] 2.7 Add focused unit tests for validation, creation, and reads.
  - Cover fixed vocabularies, owner-id format, optional mating values, preferences independence, minimal creation, duplicate owner rejection, completeness inputs, and legacy tolerance.
  - Assert relationships and observable outcomes rather than implementation constants.
  - Tests fail when validation, optional-field handling, minimal creation, duplicate-owner behavior, or legacy direct reads regress.
  - _Depends: 2.2, 2.3_
  - _Boundary: Profile service tests_
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4, 2.5, 4.1, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 7.1, 7.2, 7.3, 9.2, 9.4_

- [ ] 2.8 Add focused unit tests for updates, completeness, removal, and concurrency.
  - Cover owner immutability, independent patching, full photo replacement, completeness transitions, retained removal, idempotency, owner-slot reuse, and uniqueness-conflict mapping.
  - Verify each mutation path uses the service transaction boundary.
  - Tests fail when any update, completeness, removal, reuse, or concurrent-conflict behavior regresses.
  - _Depends: 2.4, 2.5, 2.6_
  - _Boundary: Profile service tests_
  - _Requirements: 3.1, 3.2, 3.4, 3.5, 6.1, 6.2, 6.4, 6.5, 7.1, 7.2, 7.3, 7.4, 8.1, 8.3, 8.4, 8.6_

## 3. HTTP Integration

- [ ] 3.1 Expose list and direct-read workflows.
  - Return active profiles from the list workflow and allow direct reads of removed profiles.
  - Map optional fields, ordered photos, primary photo, completeness, removal state, and unknown identifiers into responses.
  - Responses return 200 with the documented fields; active lists exclude removed profiles and unknown ids return 404.
  - _Depends: 2.3_
  - _Boundary: Dog HTTP API_
  - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 3.1, 3.2, 3.4, 7.3, 8.3, 9.1, 9.2, 9.4, 9.5_

- [ ] 3.2 Expose profile creation workflow.
  - Accept minimal required fields and all optional profile fields through the documented request shape.
  - Return the created identifier, owner id, profile values, and derived completeness.
  - A valid minimal creation returns 201 and optional fields remain optional.
  - _Depends: 2.2_
  - _Boundary: Dog HTTP API_
  - _Requirements: 1.1, 1.2, 1.3, 1.6, 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.4, 4.1, 4.4, 4.5, 5.1, 5.4, 5.5_

- [ ] 3.3 Expose patch and delete workflows.
  - Apply absent-means-unchanged patch semantics and return the updated profile.
  - Soft-delete profiles without erasing stored data and make repeated deletion successful.
  - PATCH returns the updated state and DELETE succeeds idempotently while direct reads retain the data.
  - _Depends: 2.4, 2.6_
  - _Boundary: Dog HTTP API_
  - _Requirements: 3.5, 6.1, 6.2, 6.4, 6.5, 7.4, 8.1, 8.3, 8.5, 8.6_

- [ ] 3.4 Translate dog domain outcomes into HTTP responses.
  - Translate validation failures to field-keyed 400 responses, duplicate-owner failures to generic 422 responses, and missing profiles to 404 responses.
  - Scope the translation to dog profile behavior and leave existing match error behavior unchanged.
  - Invalid fields return 400 with field keys, occupied owners return generic 422 without owner-specific detail, and unknown ids return 404.
  - _Depends: 2.1, 2.2, 2.6, 3.1, 3.2, 3.3_
  - _Boundary: Dog HTTP error integration_
  - _Requirements: 1.4, 3.3, 4.2, 4.3, 5.2, 5.3, 6.3_

- [ ] 3.5 Add HTTP tests for reads and creation.
  - Pin 200 and 201 responses, all declared response fields, primary photo ordering, completeness, active filtering, removed direct reads, and unknown-id handling.
  - Verify omitted optional values are represented without errors.
  - Controller tests fail if read/create status, visibility, response fields, primary photo, completeness, or 404 behavior regresses.
  - _Depends: 3.1, 3.2, 3.4_
  - _Boundary: Dog HTTP tests_
  - _Requirements: 4.1, 4.2, 4.4, 7.3, 8.3, 9.1, 9.2, 9.4, 9.5_

- [ ] 3.6 Add HTTP tests for updates, removal, and errors.
  - Pin patch semantics, 204 and idempotent deletion, field-keyed 400 responses, generic duplicate-owner 422 responses, and missing-id 404 responses.
  - Verify dog-specific error handling does not alter match endpoint errors.
  - Controller tests fail if update/removal/error contracts or match error isolation regresses.
  - _Depends: 3.3, 3.4_
  - _Boundary: Dog HTTP tests_
  - _Requirements: 3.3, 4.2, 5.3, 6.2, 6.3, 7.4, 8.5, 8.6_

## 4. Matching Integration

- [ ] 4.1 Exclude removed profiles from all matching reads.
  - Use active-only lookups for match subjects, candidates, and pairwise requests.
  - Leave the compatibility scoring implementation and score weights unchanged.
  - Removed subjects and candidates are absent from results, removed pairwise requests are not scored, and active matching retains existing behavior.
  - _Depends: 1.4_
  - _Boundary: Match integration_
  - _Requirements: 8.2, 9.3_

- [ ] 4.2 Add matching regression tests.
  - Cover removed list subjects, removed candidates, and removed pairwise operands.
  - Keep the existing score assertions intact and verify active dogs still follow the prior scoring behavior.
  - Matching tests fail if removed visibility or score behavior regresses.
  - _Depends: 4.1_
  - _Boundary: Match tests_
  - _Requirements: 8.2, 9.3_

## 5. Runtime Validation

- [ ] 5.1 Start the project database and apply the profile migration.
  - Use the repository's database task and verify the new migration runs without SQL, checksum, or registration errors.
  - Confirm the application can reach the migrated database before proceeding.
  - The migration completes successfully against the project PostgreSQL runtime.
  - _Depends: 1.1_
  - _Boundary: Migration runtime_
  - _Requirements: 5.6, 5.7, 9.1, 9.2_

- [ ] 5.2 Verify migrated schema and legacy data.
  - Verify distinct owner backfills, unchanged `Nonna` age `-3`, nullable optional fields, child keys/order/cascade, string enum compatibility, and application schema validation startup.
  - Record no new migration or schema artifact; this task validates the delivered migration.
  - The migrated application starts successfully and all listed legacy/schema checks pass.
  - _Depends: 1.2, 1.3, 5.1_
  - _Boundary: Migration verification_
  - _Requirements: 5.6, 5.7, 9.1, 9.2_

- [ ] 5.3 Verify owner reuse after removal.
  - Remove an active profile, create a replacement for the same owner id, and attempt a second active profile.
  - Verify replacement creation succeeds and the second active create returns the generic duplicate-owner outcome.
  - _Depends: 2.6, 3.3, 5.1_
  - _Boundary: Ownership integration_
  - _Requirements: 5.3, 8.4_

- [ ] 5.4 Run project tests and build with bounded remediation.
  - Run `mise run test` and `mise run build` after all feature verification tasks complete.
  - Remediate only failures attributable to this feature; do not broaden scope during final validation.
  - Both project commands complete successfully with feature and regression tests green.
  - _Depends: 3.5, 3.6, 4.2, 5.2, 5.3_
  - _Boundary: Final validation_
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.3, 7.4, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 9.1, 9.2, 9.3, 9.4, 9.5_

## Implementation Notes

- Task 1.1 (schema): `dog.removed_at` is `TIMESTAMP WITH TIME ZONE`; task 1.2 must map `removedAt` as `Instant?` (Hibernate 6/7 maps Instant to timestamptz) or `ddl-auto: validate` will fail startup.
- Task 1.1 (schema): `position` is unreserved in PostgreSQL 18 (probed in DDL and DML); task 1.3 can map the photo column as a plain `@Column(name = "position")` without quoting. `dog_photo.reference` is `TEXT` with no length limit.
- Task 1.1 (schema): the unique active-owner index is `uq_dog_owner_active ON dog (owner_id) WHERE removed_at IS NULL`; task 2.6 must map its integrity violation to the same generic duplicate-owner failure as the service pre-check.
- A leftover `postgres:13` container (`tinder4dogs-legacy-db-1`) squats host port 5432; task 5.1 will need that port free to start the project database.
- PR review (task 1.1): `owner_id SET NOT NULL` was removed from 005 because the deployed `POST /api/dogs` does not populate `owner_id` yet — the migration alone would break creation. Task 2.2 must ship its own `ALTER TABLE dog ALTER COLUMN owner_id SET NOT NULL` changeset atomically with the owner-aware creation path (design "Persistence" step 4). NULLs never collide in `uq_dog_owner_active`, so the duplicate-owner backstop is unaffected meanwhile.
