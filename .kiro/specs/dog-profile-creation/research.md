# Research Log — dog-profile-creation

## Summary

- **Feature**: dog-profile-creation
- **Discovery Scope**: Simple Addition (first JPA entity; standard CRUD endpoint)
- **Key Findings**:
  1. No `@Entity` classes exist in the codebase — `DogProfile` will be the first; JPA infrastructure (`spring-boot-starter-data-jpa`, Kotlin `all-open` plugin for `@Entity`) is already configured.
  2. Bean Validation (`spring-boot-starter-validation`) is present but unused — introducing it here establishes the pattern for the project.
  3. Exception handling is local to controllers via `@ExceptionHandler` (no global `@ControllerAdvice`) — consistent with `PromptController` pattern.
  4. No authentication or one-profile-per-owner constraint is required for this iteration (explicit scoping decision).

---

## Research Log

### Topic 1: JPA Entity Configuration for Kotlin

**Context**: Kotlin data classes are `final` by default; JPA requires open classes for proxying.

**Sources Consulted**: Kotlin Maven plugin docs, Spring Boot steering (`tech.md`)

**Findings**:
- The `all-open` compiler plugin is already configured in `pom.xml` for `jakarta.persistence.Entity`, `MappedSuperclass`, and `Embeddable` — no additional config needed.
- Using a plain `class` (not `data class`) for the entity avoids JPA proxy conflicts and aligns with Hibernate's expectations.
- `@PrePersist` hook is the correct mechanism for setting `createdAt` without relying on clock injection.

**Implications**: `DogProfile` will be a regular Kotlin class (not `data class`) with a `@PrePersist` method.

---

### Topic 2: Bean Validation on Kotlin DTOs

**Context**: Validation annotations must target the backing field in Kotlin, not the constructor parameter.

**Sources Consulted**: Spring Boot Validation docs, Kotlin JSR-303 guides

**Findings**:
- `@field:NotBlank`, `@field:NotNull`, etc. are required (not `@NotBlank` directly on constructor params) to ensure the annotation is placed on the JVM field.
- Enum fields (`size`, `gender`) declared as nullable (`DogSize?`) paired with `@field:NotNull` provides the cleanest validation: a missing/null value triggers a 400, while an invalid string value causes a deserialization error (also 400 via Spring's default handling).

**Implications**: `CreateDogProfileRequest` fields use `@field:` prefix for all validation annotations.

---

### Topic 3: Error Response for Invalid Enum Values

**Context**: When the client sends an invalid enum string (e.g. `"size": "GIANT"`), Spring throws `HttpMessageNotReadableException` before validation runs.

**Sources Consulted**: Spring MVC exception hierarchy

**Findings**:
- `HttpMessageNotReadableException` is thrown during deserialization — before `@Valid` is processed.
- Spring's default handler returns 400 with a generic error body.
- For consistency, adding a second `@ExceptionHandler(HttpMessageNotReadableException::class)` in the controller would return the same `ErrorResponse` shape. This is deferred — the default 400 already satisfies the acceptance criteria.

**Implications**: Invalid enum values already return 400; adding a consistent `ErrorResponse` body is a future improvement (not blocking).

---

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks/Limitations |
|--------|-------------|-----------|-------------------|
| Vertical slice module (`dogprofile/`) | Self-contained feature module with `presentation/service/model` | Consistent with all existing modules; easy to navigate | N/A — matches project convention |
| Global `@ControllerAdvice` | Centralised exception handling | Avoids per-controller boilerplate | Breaks existing per-controller pattern; out of scope |
| Separate `mapper/` layer | Dedicated mapping classes (e.g. MapStruct) | Useful at scale | Unnecessary complexity for single endpoint |

**Selected**: Vertical slice module — matches `support/`, `ai/finetuning/` patterns exactly.

---

## Design Decisions

### Decision 1: `DogProfile` as a plain Kotlin `class`, not `data class`

- **Context**: JPA entities require open, mutable classes; `data class` in Kotlin is `final` and generates `equals`/`hashCode` based on all fields including mutable ones.
- **Alternatives**: `data class` with all-open plugin, or `data class` with `copy()` semantics.
- **Selected**: Plain `class` — avoids subtle JPA proxy bugs, consistent with Hibernate best practices for Kotlin.
- **Trade-offs**: No auto-generated `equals`/`hashCode` — acceptable since entities use identity equality.

### Decision 2: `@PrePersist` for `createdAt`

- **Context**: `createdAt` must be set automatically before insert; no `@CreatedDate` JPA auditing is configured.
- **Alternatives**: Set in service layer, use Spring Data `@EnableJpaAuditing`.
- **Selected**: `@PrePersist` — self-contained, no additional Spring config required for MVP.
- **Trade-offs**: `@EnableJpaAuditing` would be cleaner at scale, but adds config overhead not yet needed.

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| `@PrePersist` not triggered in unit tests (JPA lifecycle not active) | Service tests use mocked repository returning a pre-built entity with `createdAt` set |
| Invalid enum deserialisation returns inconsistent error shape | Default Spring 400 satisfies acceptance criteria; consistent shape is a follow-up |

---

## References

- Spring Boot Data JPA: https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.sql.jpa-and-spring-data
- Bean Validation with Kotlin: https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html
- Kotlin JPA Plugin: https://kotlinlang.org/docs/no-arg-plugin.html#jpa-support
