# Research & Design Decisions

---
**Purpose**: Capture discovery findings, architectural investigations, and rationale that inform the technical design.

---

## Summary

- **Feature**: `registration-owner-profile`
- **Discovery Scope**: New Feature (greenfield — no auth or owner persistence exists in the codebase)
- **Key Findings**:
  - Spring Security 7 (bundled with Spring Boot 4.0) ships with Nimbus JOSE + JWT natively; JJWT is no longer the canonical choice.
  - Spring MVC + Kotlin coroutines works for `suspend` controller functions; `@PreAuthorize` annotations lose coroutine context, so filter-based security (`JwtAuthFilter extends OncePerRequestFilter`) is the required pattern.
  - Bucket4j is fully compatible with Spring Boot 4.0; Fibonacci-progressive blocking requires a companion in-memory breach-count map alongside the Bucket4j token bucket.
  - MaxMind GeoLite2 (free, embedded JAR) is the standard JVM solution for IP-to-city resolution; requires database refresh within 30 days (EULA).
  - Photo storage on local filesystem (`./uploads/photos/`) is adequate for v1; migration path to object storage (S3) is straightforward by swapping the `PhotoStorageService` implementation.
  - Liquibase integration via `spring-boot-starter-liquibase` with `ddl-auto=validate` replaces the current `ddl-auto=update`.

---

## Research Log

### Spring Security 7 + JWT in Spring Boot 4

- **Context**: No security layer exists; need to introduce JWT-based stateless auth.
- **Sources Consulted**: Spring Security 7 reference docs, Spring Boot 4 migration guide.
- **Findings**:
  - Nimbus JOSE + JWT is bundled in `spring-boot-starter-oauth2-resource-server`; no external JWT library needed.
  - `SecurityFilterChain` bean replaces legacy `WebSecurityConfigurerAdapter`.
  - `SessionCreationPolicy.STATELESS` is required for stateless JWT mode.
  - Custom `OncePerRequestFilter` for JWT extraction works correctly with Spring MVC coroutines.
- **Implications**: Use Nimbus for token signing/verification. Introduce `spring-boot-starter-security` and optionally `spring-boot-starter-oauth2-resource-server` for the JWT decoder bean.

---

### Kotlin Coroutines + Spring Security Filter Chain

- **Context**: Existing controllers use `suspend` functions; adding Spring Security must not break them.
- **Sources Consulted**: Spring Framework coroutines support docs.
- **Findings**:
  - `@PreAuthorize` / `@PostAuthorize` annotations can silently lose coroutine context in servlet-based Spring MVC.
  - Filter-based security (a `OncePerRequestFilter` that populates `SecurityContextHolder` before the controller is invoked) is safe and the recommended approach for Spring MVC + coroutines.
  - `SecurityContextHolder.setContext(context)` in the filter is sufficient; no coroutine-specific propagation needed because MVC dispatches synchronously.
- **Implications**: Use filter-based authentication exclusively; avoid method-level security annotations for now.

---

### Rate Limiting — Bucket4j + Fibonacci Blocks

- **Context**: Requirement 4.2 mandates Fibonacci-progressive IP blocks after 5 attempts per 30 min.
- **Sources Consulted**: Bucket4j GitHub, Spring Boot 4 compatibility notes.
- **Findings**:
  - Bucket4j `com.github.vladimir-bukhtoyarov:bucket4j-core` is compatible with Spring Boot 4.
  - A single Bucket4j bucket handles the 5-attempt window (fixed window, 30 min refill).
  - Fibonacci-progressive block duration is not natively supported; it requires a companion `ConcurrentHashMap<String, Int>` (IP → breach count) to compute the current Fibonacci interval.
  - Block enforcement uses a `BlockingStrategy` or a timestamp-check: if `now < blockExpiresAt`, reject immediately with HTTP 429.
- **Implications**: `RateLimitService` maintains two maps per IP: bucket state (via Bucket4j) and breach-count (plain `ConcurrentHashMap`). Block state is in-memory (v1 is single-instance). For horizontal scaling, replace the in-memory maps with a Redis-backed Bucket4j `ProxyManager`.

---

### reCAPTCHA v3 Server-Side Verification

- **Context**: Requirement 4.1 mandates reCAPTCHA v3 + honeypot on the registration form.
- **Sources Consulted**: Google reCAPTCHA v3 developer guide, Baeldung Spring CAPTCHA integration.
- **Findings**:
  - Verification endpoint: `POST https://www.google.com/recaptcha/api/siteverify` with params `secret` and `response` (client token).
  - Response: `{ success: Boolean, score: Float (0–1), action: String, hostname: String }`. Score < 0.5 is typically treated as bot.
  - No Spring Boot starter required; a plain HTTP call via `RestClient` (Spring 6+) suffices.
  - Tokens expire in 2 minutes; must be verified immediately after form submission.
- **Implications**: `RecaptchaService` makes a blocking HTTP POST during registration. Score threshold is configurable via `application.yaml` (`tinder4dogs.security.recaptcha.min-score`, default 0.5). Verification happens before any business logic.

---

### Email Sending (spring-boot-starter-mail)

- **Context**: Requirement 2 requires dispatching a verification email within 5 seconds of registration.
- **Sources Consulted**: Spring Boot 4 email docs.
- **Findings**:
  - `spring-boot-starter-mail` is available in Spring Boot 4.x with no breaking API changes.
  - `JavaMailSender` bean is auto-configured via `spring.mail.*` properties.
  - For async dispatch within the 5-second SLA: call `JavaMailSender.send()` in a coroutine (via `withContext(Dispatchers.IO)`) to avoid blocking the main thread.
- **Implications**: Add `spring-boot-starter-mail` dependency. SMTP credentials sourced from `.envrc` environment variables.

---

### IP Geolocation (MaxMind GeoLite2)

- **Context**: Requirement 1.9 — if consent is declined, derive city from IP.
- **Sources Consulted**: MaxMind GeoLite2 EULA, GeoIP2 Java API docs.
- **Findings**:
  - `com.maxmind.geoip2:geoip2` Java library; `GeoLite2-City.mmdb` embedded in JAR or loaded from filesystem.
  - Free under Creative Commons with requirement to update database within 30 days.
  - `DatabaseReader` is thread-safe after construction; can be a Spring singleton bean.
  - Returns city, country, latitude/longitude (to be truncated to 500 m grid server-side).
- **Implications**: Include `GeoLite2-City.mmdb` in `src/main/resources/geoip/`. Add refresh reminder to operational runbook. Truncate coordinates to a 0.005° grid (≈ 500 m) before storage.

---

### Verification Token Design

- **Context**: Requirement 2.3–2.4 — 72 h TTL, one-time use, secure.
- **Sources Consulted**: OWASP session management guidelines, Spring Security token patterns.
- **Findings**:
  - `UUID.randomUUID()` is not cryptographically strong; prefer `SecureRandom`-based 32-byte hex tokens.
  - JWT-based tokens (self-contained, no DB lookup) are preferred for scale, but DB storage is required for single-use invalidation (requirement 2.4).
  - Selected approach: DB-backed token (`verification_tokens` table) with `expires_at` and `used_at` columns. Secure 32-byte random token stored as hex string.
- **Implications**: `VerificationToken` JPA entity; `EmailVerificationService` checks expiry and marks `used_at` on consumption.

---

### Photo Storage

- **Context**: Requirements 1.6–1.7 — photo max 5 MB, JPEG/PNG/WebP, deletable.
- **Findings**:
  - Local filesystem storage (`./uploads/photos/{ownerId}/profile.{ext}`) via Spring's `MultipartFile`.
  - MIME type validated via `MultipartFile.contentType` and Apache Tika (magic-byte detection, more reliable than content-type header).
  - Size validated via `spring.servlet.multipart.max-file-size=5MB`.
  - Photo URL returned as a relative API path: `/api/v1/owner/profile/photo`.
- **Implications**: `PhotoStorageService` abstraction allows swapping to S3 in v2. Tika adds `apache-tika-core` dependency for MIME validation.

---

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| **Layered (selected)** | Domain-first with Controller → Service → Model layers, matching existing project structure | Consistent with existing codebase; no learning curve | Less isolation than hexagonal | Matches `owner/` and `auth/` domain boundaries in steering |
| Hexagonal (ports & adapters) | Core domain isolated behind ports | Highly testable core, swappable adapters | Adds adapter boilerplate; overkill for v1 | Could be adopted in v2 when external auth providers are added |
| Monolithic Security Module | Single `security/` domain for all auth + owner data | Simple | Couples identity data with security logic | Rejected: mixing domain data (owner profile) with auth infrastructure |

---

## Design Decisions

### Decision: Two New Domains — `owner/` and `auth/`

- **Context**: Registration mixes domain data (owner name, photo, location) with security concerns (JWT, email verification, rate limiting).
- **Alternatives Considered**:
  1. Single `registration/` domain — simple but conflates security logic with domain data.
  2. `owner/` + `auth/` — clear separation of concerns; `auth/` can evolve independently (social login in v2).
- **Selected Approach**: `owner/` owns the domain entity (`Owner`, `OwnerLocation`) and profile management. `auth/` owns JWT, email verification, rate limiting, reCAPTCHA.
- **Rationale**: Consistent with the existing domain-first structure and the principle that security infrastructure does not belong in a business domain.
- **Trade-offs**: Two packages instead of one; cross-package dependency (`auth/` reads `owner/` repository).
- **Follow-up**: Ensure `auth/` only depends on `owner/` via a service interface, not directly on the JPA repository.

---

### Decision: Single Final POST for Registration (Client-Aggregated Wizard)

- **Context**: The UI is a 3-step wizard; requirement 1.2 mandates no partial data persistence on abandonment.
- **Alternatives Considered**:
  1. Per-step API endpoints that persist nothing — adds endpoints, no benefit.
  2. Single `POST /api/v1/auth/register` with all data — cleanest atomic semantics.
  3. Server-side session/draft — violates statelessness (NFR-05) and requirement 1.2.
- **Selected Approach**: Single final `POST /api/v1/auth/register` (multipart/form-data). A separate `GET /api/v1/auth/email-available` provides real-time duplicate-email feedback during Step 1 without persisting anything.
- **Rationale**: True atomicity: nothing is persisted until all data is validated. Stateless by design.
- **Trade-offs**: Email-available check is advisory (race condition possible if two users submit simultaneously — resolved by unique DB constraint + generic error).

---

### Decision: DB-Backed Refresh Tokens (Not Fully Stateless)

- **Context**: NFR-02 requires access token ≤ 1h + refresh token. NFR-05 requires stateless backend.
- **Selected Approach**: Access tokens are stateless JWTs (1h). Refresh tokens are securely random 64-byte hex strings stored in `refresh_tokens` DB table with expiry (30 days) and `revoked_at`. This is a deliberate compromise: stateless for the hot path (every API call), DB round-trip only on token refresh (rare).
- **Rationale**: Full statelessness requires short-lived JWTs with no refresh, forcing re-login every hour — poor UX. The refresh table is append-only and low-write volume.

---

### Decision: Filter-Based Security (No @PreAuthorize)

- **Context**: Coroutines context loss with `@PreAuthorize` in Spring MVC.
- **Selected Approach**: `JwtAuthFilter` (`OncePerRequestFilter`) extracts and validates the JWT before the request reaches any controller; populates `SecurityContextHolder`. Endpoint-level protection is declared in `SecurityConfig.filterChain` via `requestMatchers` rules.
- **Rationale**: Safe with Kotlin coroutines, consistent with Spring Security 7 best practices for stateless APIs.

---

## Risks & Mitigations

- **In-memory rate limit state lost on restart** — Mitigate in v1 by accepting this (benign for dev/test); document that Redis-backed Bucket4j is required before horizontal scale.
- **GeoLite2 database staleness** — Mitigate by adding a startup-time log warning if the database file is older than 25 days; add to operational runbook.
- **Photo filesystem not shared across instances** — Mitigate by documenting that a shared NFS volume or object storage is required before horizontal scale; `PhotoStorageService` interface already abstracts this.
- **reCAPTCHA score threshold misconfigured** — Mitigate via configurable `tinder4dogs.security.recaptcha.min-score` property with default 0.5; add a test mode bypass for integration tests.
- **Refresh token table growth** — Mitigate with a scheduled cleanup job removing expired tokens (outside v1 scope; document as a known gap).

---

## References

- [Spring Security 7 JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Bucket4j Spring Boot Starter](https://github.com/MarcGiffing/bucket4j-spring-boot-starter)
- [Google reCAPTCHA v3 Developer Guide](https://developers.google.com/recaptcha/docs/v3)
- [MaxMind GeoLite2 Java API](https://github.com/maxmind/GeoIP2-java)
- [Spring Boot Mail Reference](https://docs.spring.io/spring-boot/reference/io/email.html)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [Spring Framework Kotlin Coroutines](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html)
