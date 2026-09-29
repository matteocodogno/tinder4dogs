# Research & Design Decisions — F-03: Play and Mating Modes

## Summary

- **Feature**: `F-03 Play and mating modes`
- **Discovery Scope**: Extension (light discovery, integration-focused)
- **Key Findings**:
  - The codebase has no mode concept, no mating fields, and no profile-update
    endpoints; the single mode-agnostic score in `MatchScoreService` mixes
    mating-only criteria (same breed, opposite genders) into every ranking —
    the exact defect F-03 exists to fix.
  - The repo already has all the patterns F-03 needs: enum persisted as string
    (`Gender`), set-valued `@ElementCollection` (`dog_preference`), the
    predicate-next-to-partial-function convention (`canScore` next to `score`),
    and controller-side 404/422 translation without `@ControllerAdvice`.
  - F-02 (dog profile fields) and F-04 (score criteria) have no spec or
    implementation yet; the product owner confirmed F-03 bridges the three
    mating fields as nullable passthrough storage, and F-03 assigns interim
    per-mode criteria that F-04 later refines.

## Research Log

### Extension points in the existing code

- **Context**: F-03 extends `dog/` and `match/`; discovery had to find the seams.
- **Sources Consulted**: `dog/Dog.kt`, `dog/DogController.kt`, `dog/DogRepository.kt`,
  `match/MatchScoreService.kt`, `match/MatchController.kt`,
  `db/changelog/db.changelog-master.yaml`, `db/changelog/changes/00*.sql`,
  `match/MatchScoreServiceTest.kt`, steering `product.md`/`tech.md`/`structure.md`.
- **Findings**:
  - `Dog` holds `id, name, breed, gender (enum, STRING), age, preferences
    (MutableSet<String> @ElementCollection EAGER)`. `Mode` can mirror `Gender`;
    a modes set can mirror `preferences`.
  - `DogController` offers create/read only; DTOs live in the controller file;
    creation builds the entity inline in the handler.
  - `MatchScoreService.score(a, b)` applies four weighted criteria (age 20,
  breed 25, opposite gender 20, preferences 15) over a derived `MAX_POINTS`
    of 80; `canScore(dog)` guards the seeded bad row (Nonna, age −3).
  - `MatchController` orchestrates list composition itself (known debt), and
    `MatchResponse` doubles as list item and pairwise answer (known debt).
    Both endpoints are mode-agnostic and will be replaced.
  - Migrations are plain SQL, append-only, one YAML index; the next file is
    `005-*.sql`.
- **Implications**: follow the existing enum/set/predicate/translation patterns
  verbatim; move list composition into a service while rewriting the match
  endpoints, which also retires both known controller debts.

### Mode storage options

- **Context**: how to persist "play, mating, or both" per dog.
- **Findings**: candidates below.
- **Implications**: the set-valued child table mirrors the only set pattern in
  the codebase and keeps the API contract (`modes` array) isomorphic with
  storage; two booleans would add a translation seam for no gain at exactly
  two modes.

### Match endpoint shape options

- **Context**: how a request names the mode for lists and pairwise scores.
- **Findings**: candidates below.
- **Implications**: literal `play`/`mating` path segments avoid the pattern clash
  between a `{mode}` variable and the removed `{aId}/{bId}` pair, need no custom
  enum parsing, and make the mode-agnostic endpoints removable with no ambiguity.

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| Modes as `Set<Mode>` in a `dog_mode` child table | mirrors `dog_preference` exactly | pattern-identical, API isomorphic, DB-level PK dedup | one join per fetch (EAGER, same as preferences) | selected |
| Two NOT NULL booleans `play_mode` / `mating_mode` | flags on `dog` row | no join | translation seam between set contract and flags; check constraint duplicates a service rule | rejected |
| Mode as query parameter on existing paths | `?mode=PLAY` | no new paths | keeps ambiguous mode-agnostic endpoints alive; mode is part of the resource, not a filter | rejected |
| `{mode}` path variable | `/api/matches/{id}/{mode}` | single handler | clashes with removed `{aId}/{bId}` pattern; needs enum-parsing error handling | rejected |
| Literal `play` / `mating` segments | `/api/matches/{id}/play` | self-documenting, no clashes, 404 on unknown mode falls out | one mapping per mode (only two exist) | selected |

## Design Decisions

### Decision: Bridge the F-02 mating fields inside F-03

- **Context**: R4's gate needs pedigree, neutering status and health status to
  exist; F-02 has no spec or implementation, and without the fields R4.2 is
  unimplementable and untestable.
- **Alternatives Considered**: ship the gate against absent fields (mating mode
  permanently rejected, R4.2 dead); pause F-03 and spec F-02 first.
- **Selected Approach**: F-03 adds `pedigree: String?`, `neutered: Boolean?`,
  `healthStatus: String?` as nullable, self-declared passthrough columns,
  accepted at creation and echoed in responses; the gate checks presence only.
- **Rationale**: confirmed with the product owner; keeps all 18 ACs live.
- **Trade-offs**: F-03 temporarily carries storage F-02 will own; requirements
  boundary context updated to record this.
- **Follow-up**: F-02's spec must adopt these columns rather than re-adding them.

### Decision: Interim per-mode criteria split

- **Context**: each mode needs its own score today, but F-04 owns the final
  criteria; the PRD assigns sex and breed to mating only.
- **Alternatives Considered**: both modes reuse the current mode-agnostic score
  (leaves play contaminated by mating criteria — violates 3.1); wait for F-04
  (no per-mode scores at all — violates 3.1–3.3).
- **Selected Approach**: mating keeps today's four criteria unchanged (they are
  all mating-aligned); play drops the two mating-only criteria (same breed,
  opposite genders), keeping age closeness and shared preferences. Per-mode
  maxima stay derived from their own weights (mating 80, play 35).
- **Rationale**: today's score already behaves like a mating score, so assigning
  it to mating mode is honest continuity; the play score becomes the first
  ranking that is not mating-biased.
- **Trade-offs**: interim criteria are approximations of the PRD's final lists
  (size, energy, temperament and pedigree/neutering/health have no fields yet).
- **Follow-up**: F-04 redefines criteria inside the per-mode seam only.

### Decision: Replace the mode-agnostic match endpoints

- **Context**: with modes, a mode-agnostic list or pairwise score has no
  defined meaning.
- **Selected Approach**: remove `GET /api/matches/{id}` and
  `GET /api/matches/{aId}/{bId}`; add mode-named list and pairwise endpoints.
- **Rationale**: pre-launch internal API with no external clients; keeping
  compatibility shims would preserve endpoints whose semantics are now wrong.
- **Trade-offs**: breaking change for any existing caller (none known beyond
  the course demos).

### Decision: New `MatchService` and split `MatchResponse`

- **Context**: `structure.md` lists MatchController's in-controller composition
  and `MatchResponse` meaning two things as known inconsistencies; F-03 rewrites
  both endpoints anyway.
- **Selected Approach**: `MatchService` owns list composition and pairwise
  retrieval; `MatchController` only translates absence (404), unusable data
  (422) and payload shape; `MatchResponse` splits into
  `RankedCandidateResponse` (list item) and `PairwiseScoreResponse`.
- **Rationale**: retires both debts in the seam being rewritten, without a
  dedicated refactoring spec.

### Decision: Mockito for repository seams in service tests

- **Context**: service unit tests need `DogRepository`; tests are unit-level
  with no Spring context.
- **Selected Approach**: mock `DogRepository` with Mockito (already on the
  classpath, previously unused); HTTP-observable ACs additionally pinned with
  `@WebMvcTest` slices using `@MockitoBean`, which `structure.md` explicitly
  recommends for the untested HTTP surface.
- **Rationale**: no new dependencies; keeps the no-Spring-context convention
  for domain tests while finally pinning status-code behaviors.

### Decision: Existing dogs become play-only in the migration

- **Context**: seven existing rows (six demo dogs plus the legacy Nonna row)
  predate modes and must satisfy "at least one active mode".
- **Selected Approach**: the seed changeset inserts `PLAY` for every existing
  dog; mating mode can be added later per dog once mating details exist.
- **Rationale**: play is the default goal of the primary persona; the seed dogs'
  preferences are play-flavored; Nonna stays unscoreable and is skipped in lists
  regardless of mode.

## Risks & Mitigations

- Derived query `findByModesContaining` on an `@ElementCollection` set may not
  resolve as expected — verify during implementation; fallback is an explicit
  `@Query` with `member of`.
- Removing the old match endpoints breaks any undocumented caller — accepted
  breaking change, recorded here and in design.md.
- Two EAGER element collections on `Dog` (preferences, modes) add a second
  fetch join per dog read — same complexity class as today; revisit only when
  F-05 introduces location-based queries.
- The bridge fields may drift from F-02's eventual richer semantics — F-02 must
  treat these columns as its starting point; revalidation trigger recorded.

## References

- `docs/PRD.md` — F-02, F-03, F-04 feature definitions; scoring criteria lists.
- `.kiro/steering/product.md`, `tech.md`, `structure.md` — domain rules, stack
  constraints, package and validation conventions.
- `.kiro/specs/F-03 Play and mating modes/requirements.md` — approved
  requirements and boundary context.
