# Research & Design Decisions

---
**Purpose**: Capture discovery findings, architectural investigations, and rationale that inform the technical design.

---

## Summary

- **Feature**: `intelligent-matching-feed`
- **Discovery Scope**: New Feature / Complex Integration
- **Key Findings**:
  - The existing codebase contains no JPA entities, no persistence layer for dog profiles, swipes, or matches; everything must be built from scratch under the `feed/` domain module.
  - The existing `match/model/Dog` and `match/service/DogMatcherService` are prototype stubs (explicitly marked "has bugs"); the new feature supersedes their scoring logic with AI-powered scoring via `TracedPromptExecutor` + a rule-based fallback.
  - Spring Boot 4 + Spring Data JPA natively support pgvector column mapping (`hibernate-vector` module) and ANN similarity queries (`searchBy…Near` repository methods), making a custom query layer unnecessary.
  - `LiteLLMService.embed()` already exists and can generate compatibility vectors; `TracedPromptExecutor` already handles Langfuse-traced LLM calls — both are reused without modification.
  - PostgreSQL `earthdistance` extension enables server-side geo filtering via `earth_distance(ll_to_earth(...))`, keeping exact coordinates off the wire.

---

## Research Log

### pgvector Integration with Spring Boot 4 / Hibernate

- **Context**: The PRD mandates pgvector for compatibility vector storage and ANN search; the current codebase has no vector column usage.
- **Sources Consulted**:
  - https://docs.spring.io/spring-data/jpa/reference/repositories/vector-search.html
  - https://github.com/pgvector/pgvector-java
  - https://spring.io/blog/2025/05/23/vector-search-methods/
- **Findings**:
  - `hibernate-vector` module (Hibernate 6.4+) adds `@JdbcTypeCode(SqlTypes.VECTOR)` + `@Array(length = N)` annotations for mapping `FloatArray` columns to the `vector` PostgreSQL type.
  - Spring Data JPA (Spring Boot 4) supports `searchByXxxNear(embedding, limit)` repository method naming for cosine ANN search.
  - No additional library (e.g., Spring AI) is required; the Hibernate module alone is sufficient given the project already has Spring Data JPA.
  - The `vector` extension must be enabled via a Liquibase changeset: `CREATE EXTENSION IF NOT EXISTS vector`.
- **Implications**: Add `hibernate-vector` dependency; add pgvector Liquibase migration; map `compatibilityVector: FloatArray` in `DogProfile` entity.

### Geolocation & Distance Filtering

- **Context**: Req 4 mandates radius-based filtering (0–100 km) with ≥ 500 m approximation; exact coordinates must never be returned.
- **Sources Consulted**: PostgreSQL `earthdistance` extension docs; PostGIS comparison.
- **Findings**:
  - PostgreSQL's built-in `earthdistance` + `cube` extensions provide `earth_distance(ll_to_earth(lat, lon), ll_to_earth(lat, lon)) < radius_metres` for efficient geo filtering without PostGIS.
  - Approximation (500 m grid snapping) is applied in `GeolocationService` before storage; the stored value is already approximate — no runtime transformation needed on read.
  - Coordinates are stored only for distance filtering; they are never included in API response payloads.
  - `cube` and `earthdistance` extensions must be enabled via Liquibase migration.
- **Implications**: Add `earth_distance` native query to `DogProfileRepository`; implement coordinate approximation in `GeolocationService`; ensure API response DTOs contain no raw coordinate fields.

### Compatibility Score Architecture (AI + Fallback)

- **Context**: Req 3 mandates AI-driven scoring via LiteLLM/Langfuse with a deterministic fallback on failure.
- **Sources Consulted**: Existing `TracedPromptExecutor`, `BreedCompatibilityService`, `DogMatcherService` source code.
- **Findings**:
  - `TracedPromptExecutor.execute()` already provides Langfuse tracing, prompt injection protection, and TTL-cached prompt fetching — should be reused as-is.
  - The existing `DogMatcherService.calculateCompatibility()` is marked buggy but its signal set (age diff, same breed, same gender, shared preferences) is a reasonable fallback seed; it must be corrected and promoted to a reliable rule-based scorer.
  - The AI scoring prompt must be managed in Langfuse (not hardcoded), following the existing `TracedPromptExecutor` pattern.
  - Coroutine-based async (Kotlin coroutines via `Dispatchers.IO`) already used throughout; `CompatibilityScoringService` should follow the same `suspend fun` convention.
- **Implications**: Create `CompatibilityScoringService` in `ai/common/service/`; it calls `TracedPromptExecutor` and catches exceptions to activate fallback; fallback lives in a corrected `RuleBasedCompatibilityScorer`.

### Event Publishing for Match Notifications

- **Context**: Req 6 requires a match-created event to trigger an in-app notification to both owners.
- **Sources Consulted**: Existing codebase (no event bus found); Spring ApplicationEvent vs. outbox pattern.
- **Findings**:
  - The existing codebase has no event infrastructure (no Kafka, no RabbitMQ). Spring's `ApplicationEventPublisher` is the simplest option for v1.
  - For v1 the notification feature (F-05) is a separate spec; the feed spec only needs to publish the event — consumption is out of scope here.
  - `@TransactionalEventListener(phase = AFTER_COMMIT)` ensures the event is only dispatched after the Match record is successfully committed, avoiding phantom notifications on rollback.
- **Implications**: Publish `MatchCreatedEvent` via `ApplicationEventPublisher`; use `@TransactionalEventListener` in the consuming notification service (referenced, not implemented here).

---

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| Domain-driven modules (selected) | `feed/` module following existing `model/ → service/ → presentation/` pattern | Consistent with steering; clear ownership; parallel-implementable | Requires new JPA entities and migrations | Aligns with structure.md domain-first principle |
| Single `match/` extension | Expand existing `match/` domain to contain feed, swipe, and match logic | Fewer new packages | Creates a bloated domain; mixes distinct responsibilities | Rejected — violates single-responsibility |
| Hexagonal / ports & adapters | Core domain isolated from infrastructure via port interfaces | Highly testable; infrastructure-swappable | Build-out overhead for v1; team unfamiliarity | Deferred — worthwhile for future AI adapter swap |

---

## Design Decisions

### Decision: Domain Module Placement

- **Context**: Where to place the new entities and services given the existing domain structure.
- **Alternatives Considered**:
  1. Extend `match/` domain — keeps code near existing `Dog` stub.
  2. New `feed/` domain — separate bounded context for feed, swipe, and match.
- **Selected Approach**: New `feed/` domain module. The `match/` domain retains the `DogProfile` entity as its aggregate root; `feed/` owns `SwipeInteraction`, `MatchRecord`, `FeedEntry` as its aggregates.
- **Rationale**: Swipe lifecycle and feed orchestration are distinct from the dog identity data. Separation enables independent development and avoids merge conflicts.
- **Trade-offs**: Two domains means cross-domain reads (feed needs DogProfile); mitigated by read-only repository injection.
- **Follow-up**: Confirm domain boundary holds when chat (F-04) is added.

### Decision: Vector Dimensionality

- **Context**: What embedding dimension to use for `compatibilityVector`.
- **Alternatives Considered**:
  1. 1536 dims (OpenAI text-embedding-3-small) — standard.
  2. 768 dims (smaller local models) — lower storage cost.
- **Selected Approach**: Configurable via `application.yaml` property `ai.vector.dimensions`; defaulting to 1536 for OpenAI compatibility.
- **Rationale**: LiteLLM proxy abstracts the provider; the dimension must match whatever model is configured. Making it configurable avoids a migration when the embedding model changes.
- **Trade-offs**: Slightly more complex Liquibase migration (parameterized); acceptable.

### Decision: Geolocation Approximation Strategy

- **Context**: Req 4 requires ≥ 500 m approximation before storage. How to implement.
- **Alternatives Considered**:
  1. Snap to 500 m grid (round to nearest 0.005° ≈ 500 m at mid-latitudes).
  2. Add Gaussian noise to coordinates.
- **Selected Approach**: Round to 3 decimal places (≈ 111 m per 0.001°) then further round to nearest 0.005° — effective ~500 m grid.
- **Rationale**: Deterministic snapping is reproducible and does not require a secret seed. Easier to audit for GDPR compliance.
- **Trade-offs**: Users very close together on opposite sides of a grid boundary may appear further apart; acceptable at 500 m granularity.

### Decision: AI Score Computation — Eager vs. Lazy

- **Context**: Open Question Q3 — whether to score all candidates at feed-build time (eager) or on demand (lazy).
- **Selected Approach**: **Hybrid** — pgvector ANN retrieves top-N candidates (configurable, default 50) from the vector index; AI scoring is applied to this shortlist only (not the entire candidate pool).
- **Rationale**: Scoring all candidates in the DB at request time would require one LLM call per candidate — prohibitively expensive. Scoring only the pgvector shortlist bounds LLM calls to a small fixed set per request.
- **Trade-offs**: The final ranked list quality depends on the ANN retrieval quality; mitigated by configuring a sufficiently large shortlist (N=50 for a feed of 20).
- **Follow-up**: Tune shortlist size based on observed LLM latency in production.

---

## Risks & Mitigations

- **LLM latency degrades feed p95 < 500 ms target** — Mitigate by applying AI scoring only to the pgvector shortlist (top-50); cache scores per (source, target) pair with a short TTL (e.g., 5 min).
- **pgvector index missing on first deploy** — Mitigate via Liquibase changeset that creates HNSW index on `dog_profiles.compatibility_vector`.
- **SwipeInteraction race condition (duplicate mutual match)** — Mitigate via DB unique constraint on `(dog_profile_id_1, dog_profile_id_2)` in `match_records`; rely on constraint violation as idempotency guard.
- **Coordinate approximation privacy audit** — Mitigate by unit-testing the `GeolocationService` to assert output coordinates differ from input by at most 1 km and at least 400 m.
- **Existing `DogMatcherService` bugs propagate to fallback** — Mitigate by rewriting the rule-based scorer from scratch in `RuleBasedCompatibilityScorer`; retire `DogMatcherService` or mark it as legacy.

---

## References

- [Spring Data JPA Vector Search](https://docs.spring.io/spring-data/jpa/reference/repositories/vector-search.html) — ANN query method naming
- [pgvector-java](https://github.com/pgvector/pgvector-java) — Kotlin pgvector type bindings
- [Spring Vector Search Blog Post (May 2025)](https://spring.io/blog/2025/05/23/vector-search-methods/) — `searchBy…Near` method naming
- [PostgreSQL earthdistance docs](https://www.postgresql.org/docs/current/earthdistance.html) — geo filtering without PostGIS
