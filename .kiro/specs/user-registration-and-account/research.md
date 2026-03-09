# Research & Design Decisions

---
**Feature**: `user-registration-and-account`
**Discovery Scope**: New Feature (Greenfield)
**Key Findings**:
- No existing Spring Security or JWT libraries in the project; both must be introduced as new dependencies.
- JJWT 0.12.x is the best-fit JWT library for this stack (simpler than the Spring OAuth2 Resource Server for a custom email/password setup).
- Refresh tokens must be stored in PostgreSQL (stateful) to support single-use rotation and synchronous revocation on logout and account deletion.
- Liquibase with SQL-dialect changesets replaces `ddl-auto: update`; `ddl-auto` changes to `validate`.

---

## Research Log

### JWT Library Selection

- **Context**: No JWT library exists in the project. Requirements mandate signed access tokens (≤24 h) and rotating refresh tokens (≤30 d).
- **Sources Consulted**: JJWT GitHub (io.jsonwebtoken/jjwt), Spring Security OAuth2 Resource Server reference docs, Spring Boot 4.0 migration guide.
- **Findings**:
  - JJWT 0.12.x (latest stable) provides a fluent builder/parser API and integrates cleanly with Spring Security via a custom `OncePerRequestFilter`.
  - Spring Security's `spring-boot-starter-oauth2-resource-server` with Nimbus JOSE+JWT is the idiomatic Spring approach but is designed for external-authorization-server setups; self-signed JWT configuration requires more boilerplate and pulls in OAuth2 semantics that are unused.
  - Spring Boot 4.0.2 uses Spring Security 7.x, fully compatible with JJWT 0.12.x.
- **Implications**: Use JJWT 0.12.x (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) plus `spring-boot-starter-security`. A custom `JwtAuthenticationFilter` extends `OncePerRequestFilter`.

### Refresh Token Storage Strategy

- **Context**: Refresh tokens require rotation (old token invalidated on use) and explicit revocation (logout, account deletion, password reset). In-memory or stateless approaches cannot satisfy these constraints.
- **Sources Consulted**: OWASP Refresh Token guidance, Spring Security session management docs.
- **Findings**:
  - Storing only the SHA-256 hash of the token in the DB prevents exposure of usable tokens even if the DB is compromised.
  - Atomic rotation (old row revoked, new row inserted) must happen in a single transaction to prevent race conditions.
  - Redis would reduce DB load but introduces an additional infrastructure dependency not present in the project; PostgreSQL is sufficient for MVP load (10 000 DAU).
- **Implications**: `refresh_tokens` table in PostgreSQL with `token_hash` (SHA-256, unique), `expires_at`, and `revoked` columns. Raw token (URL-safe random bytes, 32 bytes) sent to client; hash stored server-side.

### Email Verification and Password Reset Token Strategy

- **Context**: Both flows require short-lived, single-use, out-of-band tokens delivered over email.
- **Sources Consulted**: OWASP Forgot Password cheat sheet.
- **Findings**:
  - Tokens as securely generated random bytes (URL-safe base64, 32 bytes) stored as SHA-256 hashes are simpler and more explicitly revocable than signed JWTs.
  - Single-use enforcement is trivially guaranteed via a `used` boolean updated atomically.
  - Expiry via DB column (`expires_at`) avoids any clock-skew issue.
- **Implications**: Shared pattern for `email_verification_tokens` and `password_reset_tokens` tables. Token generation delegated to `TokenService`.

### Email Sending Infrastructure

- **Context**: Registration (verification), password recovery, and resend-verification flows all require transactional email delivery.
- **Sources Consulted**: Spring Boot Starter Mail reference, JavaMailSender documentation.
- **Findings**:
  - `spring-boot-starter-mail` wraps `JavaMailSender` and configures an SMTP connection from `application.yaml`. No external service SDK is needed.
  - HTML vs plain-text emails: plain text sufficient for MVP; template support (Thymeleaf) can be added later without changing service contracts.
- **Implications**: `EmailService` uses `JavaMailSender` with SMTP settings in `application.yaml`. Properties namespaced under `tinder4dogs.mail.*`.

### Spring Security Filter Configuration

- **Context**: Coroutines (`suspend` functions) are used throughout the codebase. Spring Security 7's filter chain is synchronous by default (Servlet stack).
- **Sources Consulted**: Spring Security 7 reference, Spring WebMVC + coroutines integration notes.
- **Findings**:
  - Spring WebMVC (not WebFlux) is used; `suspend` functions work via `CoroutinesUtils` adapters in Spring MVC — no impact on the security filter chain.
  - The custom `JwtAuthenticationFilter` is synchronous; service methods called from controllers remain `suspend` where beneficial.
- **Implications**: `SecurityConfig` configures a stateless session policy and public/protected endpoint matchers. All domain service methods can remain `suspend`.

### Schema Migration Strategy

- **Context**: The project currently uses `ddl-auto: update`, which is not suitable for production (silent destructive changes, no migration history). Five new tables are required for this feature.
- **Sources Consulted**: Liquibase Spring Boot Starter docs, Liquibase SQL changelog format reference.
- **Findings**:
  - `spring-boot-starter-liquibase` integrates with Spring Boot's auto-configuration and runs changesets at startup before the application context is fully initialised.
  - SQL-dialect changesets (plain `.sql` files referenced in a YAML master) are preferred over XML/YAML changesets because they are readable, reviewable in PRs, and portable to DBA tooling.
  - `ddl-auto` must be set to `validate` once Liquibase owns the schema to prevent Hibernate from auto-modifying tables managed by migrations.
  - Liquibase tracks applied changesets in `databasechangelog` and `databasechangeloglock` tables (created automatically).
- **Implications**: Add `spring-boot-starter-liquibase` to `pom.xml`. Create `src/main/resources/db/changelog/db.changelog-master.yaml` and one `.sql` file per table in `db/changelog/migrations/`. Set `spring.jpa.hibernate.ddl-auto: validate` in `application.yaml`.

### BCrypt Cost Factor

- **Context**: NFR-S-01 requires bcrypt cost ≥ 10. Spring Security's default is 10.
- **Findings**: Cost 12 provides ~250 ms hash time on modern hardware, well within NFR-P-01 (< 1000 ms). Set explicitly to 12 to meet and exceed the minimum.
- **Implications**: `BCryptPasswordEncoder(12)` registered as a Spring bean.

---

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| Vertical slice `auth/` module | Feature module with `presentation/`, `service/`, `model/`, `repository/`, `config/` sub-packages | Consistent with existing `support/` module pattern; clear boundary | `config/` sub-package is new but small | **Selected** |
| Shared `security/` package across features | Global security config alongside domain code | Easier cross-feature access | Violates module isolation; conflicts with steering structure.md | Rejected |
| Separate microservice for auth | Auth as an independent deployable | High isolation | Over-engineering for MVP monolith; no infra for service mesh | Rejected |

---

## Design Decisions

### Decision: Liquibase with SQL-dialect migrations

- **Context**: `ddl-auto: update` is unsuitable for production; five new tables require versioned, auditable DDL.
- **Alternatives Considered**:
  1. Liquibase (`spring-boot-starter-liquibase`) — Spring Boot native, SQL or XML/YAML changesets.
  2. Flyway (`spring-boot-starter-flyway`) — similar capabilities, simpler API, SQL-first by default.
- **Selected Approach**: Liquibase with SQL-dialect changesets and YAML master changelog.
- **Rationale**: Explicit product decision. SQL changesets are human-readable and DBA-friendly. YAML master provides inclusion order and metadata without embedding SQL in YAML.
- **Trade-offs**: Slightly more setup than Flyway; Liquibase's `databasechangeloglock` can cause startup delays under concurrent restart — mitigated by single-instance MVP deployment.
- **Follow-up**: Set `spring.jpa.hibernate.ddl-auto: validate` in `application.yaml` as part of the implementation task; ensure test profile uses `validate` or an in-memory Liquibase context.

### Decision: JJWT 0.12.x over Spring OAuth2 Resource Server

- **Context**: Need JWT creation and validation for a custom email/password flow with no external auth server.
- **Alternatives Considered**:
  1. JJWT 0.12.x — standalone JWT library with fluent API.
  2. Spring Security OAuth2 Resource Server — Spring-native JWT support via Nimbus.
- **Selected Approach**: JJWT 0.12.x with a custom `OncePerRequestFilter`.
- **Rationale**: Minimal dependencies, clear separation of concerns, no OAuth2 semantics overhead. OAuth2 Resource Server adds no benefit without an external authorization server.
- **Trade-offs**: Manual filter implementation required vs auto-configured resource server. Mitigated by the filter being ~30 lines of straightforward code.
- **Follow-up**: Evaluate migration to Spring Authorization Server if OAuth2/OIDC is added in a future premium tier.

### Decision: Stateful Refresh Tokens in PostgreSQL

- **Context**: Refresh token rotation and revocation-on-logout require server-side state.
- **Alternatives Considered**:
  1. Stateful DB (PostgreSQL) — store token hash, expiry, revocation flag.
  2. Redis — lower-latency token lookups.
  3. Stateless refresh JWT — no server-side state required.
- **Selected Approach**: PostgreSQL with `refresh_tokens` table.
- **Rationale**: Redis adds infrastructure complexity not justified by MVP load. Stateless refresh JWT cannot support explicit revocation (logout/password-reset invalidation).
- **Trade-offs**: DB query on every refresh request (~1/24 h per session). Acceptable at MVP scale.
- **Follow-up**: Add Redis cache layer if token lookup becomes a bottleneck at higher scale.

### Decision: Token Hashing (SHA-256) in DB

- **Context**: Storing raw tokens in DB is a security risk if the DB is breached.
- **Selected Approach**: Store SHA-256 of raw token; send raw token to client.
- **Rationale**: Consistent with password hashing philosophy — DB stores only a non-reversible representation. SHA-256 is appropriate here because the token has sufficient entropy (256-bit random).
- **Trade-offs**: Minor CPU overhead on every token lookup. Negligible.

### Decision: `auth/` vertical slice following existing module structure

- **Context**: New auth feature must fit cleanly into the existing codebase.
- **Selected Approach**: `com.ai4dev.tinderfordogs.auth.*` with sub-packages `presentation/`, `service/`, `model/`, `repository/`, `config/`.
- **Rationale**: Mirrors the `support/` module exactly. The `repository/` and `config/` sub-packages are new additions but follow the same encapsulation principle. Domain code outside `auth/` remains unmodified.
- **Trade-offs**: `config/` as a sub-package of `auth/` slightly mixes concerns (security config is cross-cutting) but is acceptable at MVP scale.

---

## Risks & Mitigations

- **Token reuse race condition** — two concurrent requests using the same refresh token before rotation completes. Mitigation: refresh token rotation is wrapped in a `@Transactional` block that atomically revokes the old token and issues the new one; a unique constraint on `token_hash` ensures duplicate issuance fails at the DB level.
- **Email delivery failure during registration** — SMTP failure after account creation leaves user without verification email. Mitigation: the `resend-verification` endpoint allows the user to request a new token. Log the failure for ops visibility.
- **Expired token cleanup** — accumulation of expired tokens in `refresh_tokens`, `email_verification_tokens`, and `password_reset_tokens` tables. Mitigation: Spring `@Scheduled` job runs nightly to purge expired rows. Out of scope for v1 implementation (noted for follow-up).
- **BCrypt timing at high load** — bcrypt with cost 12 is CPU-intensive; concurrent login storms could saturate threads. Mitigation: acceptable at 10 000 DAU with default Spring thread pool; monitor P95 against NFR-P-02.

---

## References

- [JJWT GitHub Repository](https://github.com/jwtk/jjwt) — JWT library API and migration guide
- [OWASP Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html) — token design and anti-enumeration patterns
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) — refresh token rotation rationale
- [Spring Boot 4 / Spring Security 7 Reference](https://docs.spring.io/spring-security/reference/) — filter chain configuration, stateless sessions
- [Spring Boot Starter Mail](https://docs.spring.io/spring-boot/reference/io/email.html) — JavaMailSender configuration
