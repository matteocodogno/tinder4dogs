# Implementation Plan

- [ ] 1. Database foundation
- [ ] 1.1 Enable required PostgreSQL extensions via Liquibase
  - Add a changeset that enables the `vector`, `cube`, and `earthdistance` extensions
  - Include a tested rollback (drop extension only if no vector columns exist)
  - All subsequent migrations depend on this changeset completing successfully
  - _Requirements: 4.1_

- [ ] 1.2 Create the dog profiles table with vector column and spatial indexes
  - Define `dog_profiles` with all mandatory profile columns (breed, size, age, sex, temperament_tags, purpose, active, version)
  - Add the `compatibility_vector` column of type `VECTOR(1536)` — dimension configurable via property
  - Create HNSW index on `compatibility_vector` using cosine distance operator
  - Add B-tree indexes on `owner_id` and `active` for fast filtering
  - Include rollback changeset
  - _Requirements: 2.2, 3.1, 4.1_

- [ ] 1.3 Create owner preferences, swipe interactions, and match records tables
  - `owner_preferences`: radius (0–100, default 30), geolocation consent flag, approximated coordinates
  - `swipe_interactions`: unique constraint on `(swiper_id, target_id)`; B-tree indexes on `(swiper_id, action)` and `(target_id, action)`
  - `match_records`: unique constraint on normalized `(dog_profile_id_1, dog_profile_id_2)`; `chat_channel_id` column
  - Include rollback changesets for all three tables
  - _Requirements: 4.3, 5.4, 6.3, 7.4_

- [ ] 2. Dog profile entity and vector search repository
- [ ] 2.1 Map the DogProfile JPA entity with all profile attributes and compatibility vector
  - Use `@JdbcTypeCode(SqlTypes.VECTOR)` with `@Array(length = …)` for the `FloatArray` vector column
  - Map `temperamentTags` and `purpose` as `@ElementCollection` or native array columns
  - Add `active: Boolean` and `@Version` optimistic lock field
  - Confirm `all-open` plugin applies so Hibernate can proxy the entity
  - _Requirements: 1.3_

- [ ] 2.2 Implement geo-filtered ANN candidate retrieval
  - Add a native query using `earth_distance(ll_to_earth(...), ll_to_earth(...)) < radius_metres` for geo pre-filtering
  - Chain pgvector `<=>` cosine similarity ordering to rank shortlisted candidates (default top 50)
  - Accept a set of excluded profile IDs (own, already-swiped, already-matched) as a query parameter
  - Expose a `searchByCompatibilityVectorNear` Spring Data method for pure ANN retrieval
  - _Requirements: 2.2, 2.3, 2.4, 4.1_

- [ ] 2.3 Add profile completeness validation
  - Define the "complete" predicate: breed, size, age, sex, ≥1 temperament tag, ≥1 purpose value all present and non-null
  - Expose this as a computed property or repository-level check reusable by the feed service
  - _Requirements: 1.1, 1.3, 1.4_

- [ ] 3. Owner preferences persistence and radius management (P)
- [ ] 3.1 (P) Map the OwnerPreferences entity with radius and geolocation consent fields
  - Map `searchRadiusKm`, `geolocationConsent`, `approxLatitude`, `approxLongitude`
  - Default `searchRadiusKm = 30` until Q1 (open question) is resolved via configuration property `app.feed.default-radius-km`
  - Runnable in parallel with Task 2 — separate table, no shared files
  - _Requirements: 4.2, 4.3, 7.3, 7.4_

- [ ] 3.2 Add search radius validation and persistence
  - Enforce `0 ≤ radiusKm ≤ 100` with Bean Validation on the update request
  - Persist changes immediately; no cache invalidation required (read per request)
  - _Requirements: 7.1, 7.2, 7.4_

- [ ] 4. Geolocation service
- [ ] 4.1 (P) Implement coordinate approximation
  - Snap latitude and longitude to the nearest 0.005° grid (≈ 500 m at mid-latitudes)
  - Apply approximation before coordinates are stored — raw precise coordinates never leave this service
  - Unit-test that output differs from input by 400–1000 m across a representative sample of coordinates
  - Can run in parallel with Task 3 — independent logic, no shared state
  - _Requirements: 4.1_

- [ ] 4.2 (P) Implement city-level fallback for owners without geolocation consent
  - When `geolocationConsent = false`, return city-centroid approximated coordinates instead of device coordinates
  - Ensure the feed service uses only `GeolocationService`-provided coordinates, never raw input
  - Can run in parallel with Task 4.1
  - _Requirements: 4.2_

- [ ] 5. Compatibility scoring
- [ ] 5.1 (P) Build the rule-based fallback scorer
  - Implement signal weights: breed/size affinity 35%, temperament overlap 35%, purpose alignment 20%, age proximity 10%
  - Apply a 30-point penalty when the source and target purpose sets have no overlap
  - Must complete synchronously with no external calls and return a score in [0, 100]
  - No dependency on Tasks 2–4; can run in parallel
  - _Requirements: 3.2, 3.4, 3.6_

- [ ] 5.2 Build the AI compatibility scoring service
  - Call `TracedPromptExecutor.execute()` with prompt ID read from `app.ai.prompts.compatibility-score-id`
  - Pass breed, size, temperament tags, purpose, and normalised distance for both dogs as prompt variables
  - Parse the integer score (0–100) from the LLM response
  - On any exception: log at WARN with trace ID and activate `RuleBasedCompatibilityScorer`
  - Expose `scoreAll(source, candidates): Map<UUID, CompatibilityScore>` as a suspend function
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [ ] 5.3 Add the compatibility scoring Langfuse prompt template
  - Create the `compatibility-score` prompt in Langfuse with system and user templates
  - Include variables for both dog profiles (breed, size, temperament_tags, purpose) and approximate distance
  - Instruct the model to return only an integer in [0, 100] for reliable parsing
  - Register prompt ID in `application.yaml` under `app.ai.prompts.compatibility-score-id`
  - _Requirements: 3.5_

- [ ] 6. Swipe and match persistence layer
- [ ] 6.1 (P) Map the SwipeInteraction entity and implement idempotent swipe repository
  - Map `SwipeInteraction` with `swiperId`, `targetId`, `action`, `createdAt`
  - Add upsert semantics: on duplicate `(swiper_id, target_id)` return the existing record without error
  - Provide queries to retrieve excluded target IDs by swiper and to check for a specific reverse swipe
  - Can run in parallel with Task 6.2 — separate table and repository
  - _Requirements: 5.1, 5.2, 5.4_

- [ ] 6.2 (P) Map the MatchRecord entity and implement deduplicated match repository
  - Map `MatchRecord` with normalized ID pair `(min(id1,id2), max(id1,id2))`, `chatChannelId`, `createdAt`
  - Rely on the DB unique constraint as the idempotency guard for concurrent match creation attempts
  - Provide a query to check if a match already exists for a given pair
  - Can run in parallel with Task 6.1
  - _Requirements: 6.3, 6.4_

- [ ] 7. Match service
- [ ] 7.1 Implement atomic match creation with chat channel assignment
  - Normalize the dog profile ID pair (min, max) before persisting to satisfy the unique constraint
  - Assign a new `UUID` as `chatChannelId` on each new match record
  - Wrap persistence in `@Transactional`; rely on unique constraint violation for idempotency (no duplicate match)
  - _Requirements: 6.1, 6.3, 6.4, 6.5_

- [ ] 7.2 Publish MatchCreatedEvent after successful commit
  - Use `ApplicationEventPublisher` to dispatch `MatchCreatedEvent` carrying both owner IDs, dog profile IDs, match ID, and chat channel ID
  - Annotate the event listener (in the notification domain, out of scope here) with `@TransactionalEventListener(phase = AFTER_COMMIT)` to prevent phantom events on rollback
  - _Requirements: 6.2_

- [ ] 8. Swipe service
- [ ] 8.1 Implement ownership validation and swipe recording
  - Verify that the requesting owner's dog profile matches the swipe source (403 on mismatch)
  - Check that the target dog profile exists and is active (404 if not)
  - Delegate to `SwipeRepository` with upsert semantics; return existing `interactionId` on duplicate (idempotent)
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 8.2 Implement mutual match detection and delegation
  - After recording a RIGHT swipe, query for a reverse RIGHT swipe from the target
  - If found, call `MatchService.createMatch()` within the same transaction
  - On any DB failure during match creation, roll back the entire transaction (no partial swipe + failed match state)
  - Return `SwipeResponse` with `matchCreated = true` and the new `matchId` when applicable
  - _Requirements: 6.1, 6.5_

- [ ] 9. Feed service
- [ ] 9.1 Implement the profile completeness gate
  - On every feed request, load the requesting owner's dog profile and check completeness
  - Throw `FeedNotAvailableException` with the list of missing fields if the profile is incomplete
  - The gate is stateless: re-evaluated on each request so access is restored immediately after profile completion
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [ ] 9.2 Build candidate retrieval with geo filter and exclusion set
  - Load the owner's approximated location from `GeolocationService` and configured radius from `OwnerPreferences`
  - Assemble the exclusion set: own profile ID + all swiped target IDs (LEFT and RIGHT) + all matched profile IDs
  - Call `DogProfileRepository.findCandidatesWithinRadius(...)` with the exclusion set and shortlist limit (default 50, configurable)
  - Return an empty page with `noMoreCandidates = true` when the result set is empty
  - _Requirements: 2.2, 2.3, 2.4, 2.5, 4.1, 4.2, 4.3, 7.3_

- [ ] 9.3 Implement AI scoring, score merging, and pagination
  - Call `CompatibilityScoringService.scoreAll()` on the shortlist candidates
  - Merge AI score (weight 70%) with normalised inverse distance (weight 30%) into a final 0–100 score
  - Sort candidates descending by final score, apply page/size to produce the response page
  - Expose shortlist size and score weights as configurable properties (`app.feed.shortlist-size`, `app.feed.score-weights`)
  - _Requirements: 2.1, 2.5, 2.6, 3.1, 3.3_

- [ ] 10. REST controllers and API layer
- [ ] 10.1 (P) Expose feed retrieval and search radius endpoints
  - `GET /api/v1/feed?page=&size=` — extract `ownerId` from JWT, delegate to `FeedService.getFeed()`
  - `PATCH /api/v1/me/search-radius` — validate `SearchRadiusRequest` with Bean Validation, delegate to `FeedService.updateSearchRadius()`
  - Map `FeedNotAvailableException` → 422 with missing field list; validation errors → 400
  - Can run in parallel with Task 10.2 — separate controller class, no shared files
  - _Requirements: 1.1, 2.1, 7.1, 7.2_

- [ ] 10.2 (P) Expose swipe action endpoint
  - `POST /api/v1/swipe` — validate `SwipeRequest`, extract `ownerId` from JWT, delegate to `SwipeService.recordSwipe()`
  - Map `OwnershipViolationException` → 403, `DogProfileNotFoundException` → 404, `InvalidSwipeTargetException` → 400
  - On duplicate swipe (unique constraint): return 200 with existing `SwipeResponse` (idempotent contract)
  - Can run in parallel with Task 10.1
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 6.1_

- [ ] 11. Tests
- [ ] 11.1 Unit test core business logic
  - `RuleBasedCompatibilityScorer`: breed/size match, temperament overlap, purpose mismatch penalty, boundary scores (0 and 100), empty tag lists
  - `GeolocationService.approximate()`: output within 400–1000 m of input across a sample grid
  - `FeedService` score merging: correct weighted combination, `noMoreCandidates` flag when shortlist is empty
  - `MatchService.createMatch()`: normalized ID pair ordering, correct `MatchCreatedEvent` payload
  - `SwipeService` idempotency: duplicate right-swipe returns same `interactionId` without duplicate DB row
  - _Requirements: 1.1, 1.3, 2.5, 3.2, 3.4, 3.6, 5.4, 6.3, 6.4_

- [ ] 11.2 Integration test with real database (TestContainers)
  - `DogProfileRepository.findCandidatesWithinRadius()`: verifies geo boundary, own-profile exclusion, swiped-profile exclusion, and pgvector ANN ordering using a real PostgreSQL + pgvector container
  - Mutual match flow: two sequential right-swipes in a real DB transaction → exactly one `MatchRecord` row and one `MatchCreatedEvent` dispatched
  - Feed profile gate: `GET /api/v1/feed` with an incomplete dog profile → 422 response
  - Search radius validation: `PATCH /api/v1/me/search-radius` with radius=150 → 400; radius=0 → 204
  - _Requirements: 1.1, 2.2, 4.1, 5.4, 6.1, 6.3, 7.1, 7.2_

- [ ]* 11.3 Performance baseline for feed and swipe endpoints
  - Feed endpoint: 50 concurrent requests with ≥ 100 candidate profiles per owner → assert p95 ≤ 500 ms
  - Swipe endpoint: 200 concurrent swipes → assert p95 ≤ 200 ms and zero duplicate `MatchRecord` rows
  - pgvector ANN query: compare HNSW index vs. sequential scan at 10k profiles → assert ≥ 10x speedup
  - _Requirements: 2.6, 5.5_
