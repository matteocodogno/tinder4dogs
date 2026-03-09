# Research & Design Decisions

---
**Purpose**: Capture discovery findings and design decision rationale for the Dog Profile feature.

---

## Summary

- **Feature**: `dog-profile`
- **Discovery Scope**: Extension — new vertical-slice module following the established `support/` pattern in an existing Spring Boot monolith
- **Key Findings**:
  - No existing JPA entities in codebase; Dog Profile introduces the first `@Entity` layer and the first Spring Data repositories
  - No Spring Security / authentication layer exists yet; owner identity must be passed as a `X-Owner-Id` header in MVP, decoupled from domain logic per NFR-C02/NFR-09
  - PostgreSQL `bytea` chosen for photos — eliminates external object-storage dependency at the cost of increased DB size; acceptable for MVP scale (≤10k DAU)

---

## Research Log

### Existing Codebase Patterns

- **Context**: Need to confirm vertical-slice module structure before designing new packages.
- **Sources Consulted**: Codebase file tree, `structure.md` steering
- **Findings**:
  - Feature modules live at `com.ai4dev.tinderfordogs.<feature>/` with sub-packages `model/`, `service/`, `presentation/`
  - Controllers use `@RestController` + `@RequestMapping("/api/v1/...")` + constructor injection
  - Services annotated `@Service`, use `suspend` functions (Kotlin Coroutines + Spring WebMVC)
  - Models are Kotlin `data class`; no `@Entity` annotations exist anywhere yet
  - `ddl-auto: update` — Hibernate auto-creates/updates tables from entity classes
- **Implications**: Dog Profile introduces the first JPA entity layer; must add `@Entity`, `@Table`, Spring Data repositories. Pattern diverges slightly from existing `model/` DTOs but follows JPA conventions.

### JPA ElementCollection for Temperament Tags

- **Context**: Temperament tags are a list of enum values (max 10). Options: dedicated join table, `@ElementCollection`, or serialized string.
- **Findings**:
  - `@ElementCollection` with `@Enumerated(EnumType.STRING)` maps directly to a `dog_profile_temperament_tags` table without a separate entity class
  - Avoids over-engineering a full entity for a simple value list
  - Querying by tag is supported via JPQL `MEMBER OF`
- **Implications**: Use `@ElementCollection` for `temperamentTags`. Introduces a second table automatically managed by Hibernate.

### PostgreSQL BYTEA for Photo Storage

- **Context**: Requirements resolve photo storage to DB blob (PostgreSQL `bytea`).
- **Findings**:
  - Spring Data JPA maps `ByteArray` in Kotlin to `bytea` in PostgreSQL natively; no extra library needed
  - Photo retrieval endpoint must stream bytes back with correct `Content-Type` header
  - Performance: individual photo reads isolated per `photoId`; no full-profile join needed for photo endpoint
  - Risk: large `bytea` columns can bloat table size; mitigated by 10 MB/photo cap and 5-photo limit (max 50 MB per profile)
- **Implications**: `DogPhoto` is a separate JPA entity with a `data: ByteArray` column. Controller streams bytes via `ResponseEntity<ByteArray>`.

### Owner Identity (No Auth Layer)

- **Context**: Requirements reference "authenticated owner" but no Spring Security / JWT exists in the codebase.
- **Findings**:
  - The PRD lists F-01 (User Registration & Account) as a P0 feature that is also not implemented
  - Adding full JWT auth is out of scope for this feature; design must not block on it
  - NFR-09 (steering): authorization logic must be decoupled from domain logic
- **Implications**: MVP uses `X-Owner-Id: <UUID>` request header resolved by a `OwnerIdResolver` component (Spring `HandlerMethodArgumentResolver`). This header is replaced by a JWT claim when F-01 is implemented without touching the service layer.

### Breed Reference Table

- **Context**: Breed is validated against an enumerated list stored in a `breeds` table.
- **Findings**:
  - A `Breed` JPA entity with a `name` unique constraint allows validation via `BreedRepository.findByName()`
  - Seeded at startup via `BreedSeeder` (`@Component` + `CommandLineRunner`); idempotent via `INSERT ... ON CONFLICT DO NOTHING`
  - FCI recognises ~360 breeds; a `MIXED` entry handles crossbreeds
- **Implications**: `DogProfile` holds a `@ManyToOne` reference to `Breed`. Creating/validating a profile requires one additional DB read (breed lookup); cacheable.

---

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| Vertical Slice (selected) | New `dogprofile/` module with model/service/presentation/repository sub-packages | Matches existing `support/` pattern; self-contained; no cross-module coupling | Slight deviation: adds `repository/` sub-package not seen in existing modules | Needed because this is the first JPA-persisted feature |
| Shared Domain Core | Place entity in root `service/` alongside `DogMatcherService` | Fewer packages | Violates self-contained module principle from steering | Rejected |

---

## Design Decisions

### Decision: Separate `repository/` Sub-Package

- **Context**: Existing modules (`support/`, `ai/*`) have no repositories; all logic is stateless.
- **Alternatives Considered**:
  1. Place repositories inside `service/` — reduces package count but mixes concerns
  2. Add a dedicated `repository/` sub-package — aligns with Spring Data convention
- **Selected Approach**: `dogprofile/repository/` sub-package alongside `model/`, `service/`, `presentation/`
- **Rationale**: JPA repositories are a distinct architectural layer; placing them with service classes creates cognitive noise for reviewers
- **Trade-offs**: One extra package but much cleaner separation
- **Follow-up**: Future features with persistence should follow this 4-sub-package layout

### Decision: Soft Delete via `deletedAt` Timestamp

- **Context**: GDPR requires profile data to persist (for cascade identification) for up to 30 days before hard deletion.
- **Alternatives Considered**:
  1. Immediate hard delete — violates 30-day window; cascade may be incomplete
  2. Soft delete flag (`isDeleted: Boolean`) — simpler but less queryable
  3. Soft delete timestamp (`deletedAt: Instant?`) — supports TTL-based scheduled hard deletion
- **Selected Approach**: `deletedAt: Instant?`; `null` = active, non-null = soft-deleted
- **Rationale**: Timestamp allows a scheduled job to identify profiles ready for hard deletion (deletedAt + 30 days < now)
- **Trade-offs**: All queries must filter `WHERE deleted_at IS NULL`; mitigated by a `@Where` Hibernate annotation on the entity
- **Follow-up**: Implement a `@Scheduled` hard-deletion job (out of scope for this spec, but the schema must support it)

### Decision: Completeness Score Computed in Service Layer

- **Context**: `completenessScore` must be updated on every create/update; could live in DB trigger or application service.
- **Alternatives Considered**:
  1. PostgreSQL trigger — DB-side, always consistent, but harder to test and invisible to Kotlin code
  2. Service-layer calculation — testable, transparent, follows existing service pattern
- **Selected Approach**: `CompletenessCalculator` service computes score before each persist
- **Rationale**: Consistent with the project's service-layer-first philosophy; easily unit-testable
- **Trade-offs**: Score could drift if data is modified directly in DB; acceptable for MVP
- **Score Formula**: 5 optional factors × 20 points each = 100: `size` filled (20), `temperamentTags ≥ 1` (20), `energyLevel` filled (20), `pedigree` explicitly provided (20), `photos.size ≥ 3` (20)

### Decision: `X-Owner-Id` Header as MVP Auth Mechanism

- **Context**: No JWT/auth exists; service layer must know the caller's owner ID.
- **Selected Approach**: `OwnerIdResolver` (`HandlerMethodArgumentResolver`) reads `X-Owner-Id` header and injects it as a `UUID` method parameter
- **Rationale**: Decouples domain logic from auth; when JWT is added, only `OwnerIdResolver` changes
- **Trade-offs**: No real auth enforcement in MVP — acceptable for internal/dev use only

---

## Risks & Mitigations

- **BYTEA table bloat** — 5 photos × 10 MB × DAU growth; mitigate with DB storage monitoring alert (NFR-SC02) and enforce file size limit at controller level before DB write
- **N+1 photo query** — fetching profile with photos; mitigate with `@OneToMany(fetch = FetchType.LAZY)` and explicit join fetch in profile-with-photos query
- **Cascade delete incompleteness** — profile deleted but matches/swipes orphaned; mitigate with `@Transactional` on delete service method and explicit cascade across all related tables (matches, swipes, chats — currently unimplemented; service must fail gracefully until those modules exist)
- **No real auth in MVP** — `X-Owner-Id` is unauthenticated; document as a known security gap; replace at F-01 implementation

---

## References

- Spring Data JPA — `@ElementCollection` with enums: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/
- PostgreSQL `bytea` type and Hibernate mapping: https://docs.jboss.org/hibernate/orm/6.x/userguide/html_single/Hibernate_User_Guide.html
- FCI breed group list: https://www.fci.be/en/nomenclature
