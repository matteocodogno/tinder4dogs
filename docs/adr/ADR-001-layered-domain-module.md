# ADR-001 — Layered Domain Module for dog-profile

- **Status**: Accepted
- **Context**: The dog-profile feature is the first JPA-backed domain in PawMatch. Two existing domains (`match/`, `support/`) already use a layered `model/ → service/ → presentation/` structure. The team must choose the architectural pattern for this new greenfield domain given a small team, local MinIO as the only external storage dependency, and no event bus in the current stack.
- **Decision**: Adopt the layered domain module pattern for `dogprofile/`. Extract a single `PhotoStoragePort` interface (outbound port) to decouple MinIO from the service layer without introducing full hexagonal overhead.
- **Consequences**:
  - ✔ New domain follows identical conventions to `match/` and `support/` — reviewers navigate the code without a map and contributors can contribute to `dogprofile/` from day one.
  - ✔ Spring `@Transactional`, Bean Validation, and `@Scheduled` work out-of-the-box; no adapter boilerplate reduces initial implementation time by an estimated 2 developer-days.
  - ✔ `PhotoStoragePort` keeps `DogProfileService` unit-testable (mock the port) while remaining swappable to AWS S3 in production with only a new adapter class.
  - ✘ Domain logic (completion evaluation, ownership checks) lives in `DogProfileService` rather than on the aggregate — if business rules grow significantly, extracting a richer domain model will require touching service, test, and controller code simultaneously.
  - ✘ Unit-testing `DogProfileService` without constructor injection of the port mock requires a running Spring context or TestContainers setup, slowing the inner feedback loop for pure domain logic changes.
- **Alternatives**:
  - **Hexagonal (Ports & Adapters)**: rejected — four additional adapter classes with no hexagonal precedent in the codebase; measurable onboarding cost for a small team; the single `PhotoStoragePort` captures the one abstraction worth retaining from this pattern.
  - **CQRS**: rejected — single-entity domain at v1 scale meets NFR-P01 (p95 < 200 ms) with a plain indexed query; eventual consistency on the completion gate would violate requirement 4.2; no event bus infrastructure exists.
- **References**:
  - `.kiro/specs/dog-profile/requirements.md` — NFR-P01, NFR-SC01, NFR-R01, NFR-R02
  - `.kiro/specs/dog-profile/research.md` — Architecture Pattern Evaluation section
