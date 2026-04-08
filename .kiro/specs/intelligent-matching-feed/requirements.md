# Requirements Document

## Introduction

The Intelligent Matching Feed is the core discovery feature of PawMatch. It presents each owner with a ranked, personalized list of nearby dog profiles that are compatible with their own dog. Compatibility is determined by an AI scoring engine that weighs breed/size affinity, temperament tags, purpose alignment (socialisation vs. breeding), and geographic distance. Owners interact with the feed via swipe actions; a mutual right-swipe from both parties creates a Match and unlocks the owner chat. The feed is the primary value driver of the platform and must be fast, privacy-safe, and grounded in the dog profile data each owner has provided.

---

## Requirements

### Requirement 1: Dog Profile Prerequisite Gate

**Objective:** As an owner, I want the feed to be accessible only after my dog profile is complete, so that every profile shown in the feed has the data needed for meaningful compatibility scoring.

#### Acceptance Criteria

1. While an owner's dog profile is incomplete (missing mandatory fields: breed, size, age, sex, temperament tags, purpose), the Matching Feed Service shall block access to the feed and return an informative error response.
2. When an owner completes their dog profile, the Matching Feed Service shall immediately grant access to the matching feed without requiring a logout/login cycle.
3. The Matching Feed Service shall treat a profile as complete only when breed, size, age, sex, at least one temperament tag, and at least one purpose value are present.
4. If an owner's dog profile is deleted or deactivated, the Matching Feed Service shall suspend feed access until a new complete profile is provided.

---

### Requirement 2: Feed Generation and Ranking

**Objective:** As an owner, I want to see a feed of nearby, compatible dog profiles ranked by relevance, so that the most promising matches appear first and I don't waste time on irrelevant profiles.

#### Acceptance Criteria

1. When an owner requests the matching feed, the Matching Feed Service shall return a paginated list of dog profiles sorted in descending order by compatibility score.
2. The Matching Feed Service shall exclude from the feed any dog profile that belongs to the requesting owner.
3. The Matching Feed Service shall exclude from the feed any dog profile that the owner has already swiped on (left or right) in a previous session.
4. The Matching Feed Service shall exclude from the feed any dog profile with which the owner already has an active Match.
5. When the feed has no remaining candidate profiles within the configured radius, the Matching Feed Service shall return an empty list with a specific status indicator (no more candidates).
6. The Matching Feed Service shall return feed results within p95 ≤ 500 ms under normal load.

---

### Requirement 3: Compatibility Scoring

**Objective:** As an owner, I want profiles to be scored by real compatibility criteria, so that dogs I'm most likely to match with appear at the top of my feed.

#### Acceptance Criteria

1. When calculating the compatibility score for a candidate profile, the Matching Feed Service shall invoke the AI Compatibility Engine passing the requesting dog's breed, size, temperament tags, and purpose.
2. The AI Compatibility Engine shall return a numeric score between 0 and 100 for each candidate profile.
3. The Matching Feed Service shall factor the following signals into the final score: breed/size affinity, temperament tag overlap, purpose alignment (socialisation / breeding / both), and normalised geographic distance within the configured radius.
4. When purpose values do not overlap (e.g., requesting dog is "socialisation only" and candidate is "breeding only"), the Matching Feed Service shall apply a significant score penalty that effectively deprioritises the candidate.
5. The Matching Feed Service shall retrieve AI prompts from Langfuse at runtime (not from hardcoded strings) and apply a configurable TTL cache to avoid excessive remote calls.
6. If the AI Compatibility Engine call fails or times out, the Matching Feed Service shall fall back to a deterministic rule-based score derived from breed matrix and temperament overlap, and shall log the failure with a trace ID.

---

### Requirement 4: Geolocation and Radius Filtering

**Objective:** As an owner, I want to see only dogs within a meaningful distance from me, so that matches lead to real-world meetups.

#### Acceptance Criteria

1. When building the feed candidate pool, the Matching Feed Service shall include only dog profiles whose owner's location falls within the requesting owner's configured search radius (0–100 km).
2. The Matching Feed Service shall never expose an owner's exact GPS coordinates in any API response; all location data returned shall be approximated to a minimum granularity of 500 m server-side.
3. When an owner has not granted geolocation consent, the Matching Feed Service shall fall back to city-level proximity and shall not request or store precise coordinates.
4. The Matching Feed Service shall support a configurable default search radius; if the owner has not explicitly set a radius, the system shall use the platform default (see Open Questions Q1).
5. While computing distances for ranking, the Matching Feed Service shall use the approximated coordinates, not the exact stored coordinates, to maintain consistency with the privacy guarantee.

---

### Requirement 5: Swipe Actions

**Objective:** As an owner, I want to swipe right (interested) or left (skip) on each profile in my feed, so that I can express my preference quickly and intuitively.

#### Acceptance Criteria

1. When an owner submits a right-swipe on a candidate dog profile, the Matching Feed Service shall record an "interested" interaction associated with the owner's dog and the candidate dog.
2. When an owner submits a left-swipe on a candidate dog profile, the Matching Feed Service shall record a "skipped" interaction and shall not show that profile again in future feed requests.
3. If an owner attempts to swipe on a profile that is no longer active (owner deleted or deactivated their profile), the Matching Feed Service shall return an appropriate error and shall remove the profile from the feed.
4. The Matching Feed Service shall process each swipe action idempotently — submitting the same swipe more than once shall not create duplicate interaction records.
5. When a swipe is recorded, the Matching Feed Service shall respond within 200 ms under normal load.

---

### Requirement 6: Mutual Match Detection

**Objective:** As an owner, I want to be notified the moment a mutual match occurs, so that I can start chatting with the other owner right away.

#### Acceptance Criteria

1. When a right-swipe is recorded and the candidate dog's owner has already swiped right on the requesting dog, the Matching Feed Service shall create a Match record linking both dog profiles.
2. When a Match is created, the Matching Feed Service shall emit a match-created event that triggers an in-app notification to both owners.
3. The Matching Feed Service shall create at most one Match record per pair of dog profiles regardless of how many swipe interactions occur.
4. When a Match is created, the Matching Feed Service shall unlock the owner chat channel between the two matched owners.
5. If Match record creation fails (e.g., database error), the Matching Feed Service shall roll back the swipe interaction and return a 5xx error, ensuring no partial state is persisted.

---

### Requirement 7: Search Radius Configuration

**Objective:** As an owner, I want to set my preferred search radius, so that I can control how far away potential matches can be.

#### Acceptance Criteria

1. When an owner updates their search radius, the Matching Feed Service shall validate that the value is within the allowed range of 0 to 100 km inclusive.
2. If an owner submits a radius value outside the 0–100 km range, the Matching Feed Service shall reject the request with a 400 error and a descriptive message.
3. When the search radius is updated, the Matching Feed Service shall apply the new value to all subsequent feed requests immediately, without requiring a new login.
4. The Matching Feed Service shall persist the owner's radius preference and restore it across sessions.

---

## Non-Functional Requirements

### Performance

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-P1 | Feed API response time (p95) | ≤ 500 ms under normal load | P0 | Req 2, PRD NFR-01 |
| NFR-P2 | Swipe action response time (p95) | ≤ 200 ms under normal load | P0 | Req 5 |
| NFR-P3 | Compatibility score computation (AI path) | ≤ 300 ms per candidate batch (LiteLLM call) | P1 | Req 3 |

### Security

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-S1 | All feed and swipe endpoints require valid JWT | No endpoint accessible without a valid, non-expired JWT (max 1 h expiry) | P0 | Req 1–7, PRD NFR-02 |
| NFR-S2 | Owners may only swipe and view feed for their own dog | Server-side ownership check on every request; 403 on violation | P0 | Req 5 |

### Privacy / Compliance

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-C1 | Geolocation approximation | Exact coordinates never returned in any API response; minimum 500 m approximation applied server-side | P0 | Req 4, PRD NFR-04 |
| NFR-C2 | Geolocation consent enforcement | Precise coordinates stored only with explicit consent; fallback to city-level otherwise | P0 | Req 4, PRD NFR-03 |
| NFR-C3 | Swipe and match data erasure | All swipe interactions and match records deleted within 30 days of an account erasure request | P0 | PRD NFR-03 |

### Scalability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-SC1 | Stateless feed service | No in-memory session state; horizontally scalable | P1 | PRD NFR-05 |
| NFR-SC2 | pgvector for candidate retrieval | Compatibility vectors stored in PostgreSQL pgvector for semantic search; ANN index used for feed queries | P1 | Req 2–3, PRD F-03 |

### Reliability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-R1 | AI fallback on LLM failure | Deterministic rule-based scoring activated automatically if LiteLLM call fails or times out | P0 | Req 3 |
| NFR-R2 | Swipe atomicity | Swipe and match creation are transactional; no partial state on DB error | P0 | Req 6 |

### Observability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-O1 | AI call tracing | Every LiteLLM call traced in Langfuse with prompt version, input/output, and latency | P0 | Req 3 |
| NFR-O2 | Error logging | All 5xx errors logged with structured trace ID; alert if error rate > 1% over 5 min | P1 | PRD NFR-07 |

### Constraints

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-T1 | Tech stack | Kotlin 2.x + Spring Boot 4 (WebMVC); no WebFlux | P0 | tech.md |
| NFR-T2 | AI routing | All LLM calls via LiteLLM proxy; no direct provider SDK calls in application code | P0 | tech.md |
| NFR-T3 | Prompt management | Prompts fetched from Langfuse at runtime with configurable TTL cache | P0 | tech.md, Req 3 |

---

## Open Questions

| # | Question | Impact | Owner | Status |
|---|----------|--------|-------|--------|
| Q1 | What should the default search radius be when an owner has not explicitly configured one? The PRD allows 5–100 km but does not specify a default (PRD Q2). | Affects Req 4 AC4; a placeholder value is needed before implementation | Product | Open |
| Q2 | Should left-swiped (skipped) profiles eventually reappear (e.g., after 30 days), or are they permanently excluded from the feed? | Affects Req 2 AC3 and data retention design | Product | Open |
| Q3 | Is the compatibility score computed eagerly (at feed-build time for all candidates) or lazily (on demand per profile view)? Eager is simpler but costlier at scale. | Affects Req 3 performance and pgvector query design | Tech | Open |
| Q4 | Should the feed support a Superlike action (Premium, per domain glossary) in v1, or is it deferred? | If included, Req 5 needs an additional swipe type and Req 6 a priority match notification | Product | Open |
