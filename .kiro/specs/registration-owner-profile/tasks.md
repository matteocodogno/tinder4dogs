# Implementation Plan

## Task Summary
- **9 major tasks**, **25 sub-tasks**
- **5 requirements** (28 acceptance criteria) fully covered
- Average sub-task size: 1–3 hours

---

- [ ] 1. Project Foundation & Configuration <!-- gh:#53 -->
- [x] 1.1 Add new Maven dependencies for all new capabilities <!-- gh:#54 -->
  - Add `spring-boot-starter-security` for Spring Security 7
  - Add `spring-boot-starter-mail` for email dispatch
  - Add `spring-boot-starter-liquibase` for database migrations
  - Add `bucket4j-core` for IP rate limiting
  - Add `geoip2` (MaxMind) for IP-to-city geolocation
  - Add `tika-core` (Apache) for photo MIME type detection via magic bytes
  - Remove or downgrade `spring.jpa.hibernate.ddl-auto` to `validate` as Liquibase takes ownership of schema
  - _Requirements: 1.6, 1.8, 4.1, 4.2_

- [x] 1.2 Configure application properties for all new services <!-- gh:#55 -->
  - Add Spring Security settings: JWT secret key (sourced from env), bcrypt strength property
  - Add mail server settings under `spring.mail.*` with environment-variable fallbacks
  - Add reCAPTCHA properties: secret key and minimum score threshold (default 0.5)
  - Add file storage path for photo uploads and multipart size limit (`spring.servlet.multipart.max-file-size=5MB`)
  - Add Liquibase change-log path property
  - Add `tinder4dogs.security.*` namespace for rate limit window and Fibonacci cap
  - Verify that HTTP request body access logging is not enabled — `CommonsRequestLoggingFilter` must not be registered in the application context, ensuring request bodies (including passwords) are never written to any log file
  - _Requirements: 1.6, 4.1, 4.2, 4.4, 4.5_

---

- [ ] 2. Database Schema & Domain Entities <!-- gh:#56 -->
- [ ] 2.1 Create Liquibase changesets for the core schema <!-- gh:#57 -->
  - Write a changeset for the `owners` table with all columns, `UNIQUE` index on email, status index, and a tested rollback
  - Write changesets for `verification_tokens` and `refresh_tokens` tables, with FK references to `owners(id) ON DELETE CASCADE` and appropriate indexes
  - Register all changesets in the Liquibase master changelog in dependency order
  - Verify `ddl-auto=validate` passes after migration runs
  - _Requirements: 1.2, 1.10, 2.3, 2.4_

- [ ] 2.2 Implement the Owner domain entity and repository <!-- gh:#58 -->
  - Create the Owner JPA entity covering all fields: id (UUID), email, password hash, name, photo path, status enum, location value object (city, country, latitude, longitude, consent flag), and timestamps
  - Create the OwnerStatus enum with `PENDING_VERIFICATION` and `ACTIVE` values
  - Create the OwnerLocation embeddable value object
  - Create the OwnerRepository with lookup by email and by id
  - _Requirements: 1.8, 1.9, 1.10, 3.1_

- [ ] 2.3 (P) Implement VerificationToken entity and repository <!-- gh:#59 -->
  - Create the VerificationToken JPA entity with token (PK, string), owner FK, `expires_at`, and `used_at` columns
  - Create the VerificationTokenRepository with lookups by token string and by owner id
  - Ensure `used_at` is nullable; an entity with non-null `used_at` is considered consumed
  - _Requirements: 2.3, 2.4_

- [ ] 2.4 (P) Implement RefreshToken entity and repository <!-- gh:#60 -->
  - Create the RefreshToken JPA entity with id (UUID PK), `token_hash` (SHA-256 of raw token, unique), owner FK, `expires_at`, and `revoked_at` columns
  - Create the RefreshTokenRepository with lookups by token hash and owner id
  - _Requirements: 2.1_

---

- [ ] 3. JWT & Security Infrastructure <!-- gh:#61 -->
  > Starts after Task 2 completes. Can run in parallel with Task 6.

- [ ] 3.1 Implement JWT token generation and validation <!-- gh:#62 -->
  - Using Nimbus JOSE + JWT (bundled in Spring Security 7), implement access token generation: claims include owner id, email, status, with 1-hour expiry
  - Implement refresh token generation as a 64-byte cryptographically secure random hex string (not a JWT)
  - Implement access token validation: return null on any failure (expired, tampered, wrong signature) — never throw
  - Sign tokens with HMAC-SHA256 using the configured JWT secret key (≥ 256 bits from environment)
  - _Requirements: 2.1_

- [ ] 3.2 Implement authentication filter and security configuration <!-- gh:#63 -->
  - Implement a servlet filter that extracts the `Authorization: Bearer` header, validates the token, and populates the security context before the request reaches any controller
  - The filter must read the `status` claim from the token and reject requests to protected endpoints with 403 when status is `PENDING_VERIFICATION`
  - Configure the Spring Security filter chain: mark registration, login, email verification, and resend-verification endpoints as public; require valid token for all others
  - Set session policy to stateless, disable CSRF, and configure CORS for the browser origin
  - Do not use annotation-based security (`@PreAuthorize`) — all rules live in the filter chain configuration
  - _Requirements: 2.1_

---

- [ ] 4. Anti-Abuse Infrastructure <!-- gh:#64 -->
  > Task 4.1 and 4.2 are independent of each other and of Tasks 2 and 3. Both can start after Task 1.

- [ ] 4.1 (P) Implement IP-based rate limiting with Fibonacci-progressive blocks <!-- gh:#65 -->
  - Using Bucket4j, create a per-IP token bucket with capacity 5 and a 30-minute refill window for registration and login attempts
  - Implement a companion in-memory map tracking breach count per IP; on each bucket exhaustion, increment the breach counter and compute the block duration as `Fibonacci(breachCount)` minutes, capped at 60 minutes
  - Expose a single decision method that returns either "allowed" or "blocked with retry-after duration"
  - Apply the same rate-limiting logic to email-lookup patterns to prevent enumeration attacks
  - Log every breach event at WARN level with the IP (last octet zeroed for IPv4 privacy) and the computed block duration
  - _Requirements: 4.2, 4.3_

- [ ] 4.2 (P) Implement reCAPTCHA v3 token verification <!-- gh:#66 -->
  - Implement an HTTP client that calls the Google reCAPTCHA v3 verification endpoint with the configured secret key and the client-supplied token
  - Parse the response: accept only when `success == true` and `score >= min-score` (configurable, default 0.5)
  - Implement honeypot field validation: any submission with a non-empty honeypot field is immediately rejected
  - Return a typed result distinguishing between success, bot-detected, and infrastructure error; default to fail-closed on infrastructure error
  - Support a test-mode bypass flag (configurable via properties) to allow integration tests to skip the external call
  - _Requirements: 4.1_

---

- [ ] 5. Owner Registration Flow <!-- gh:#67 -->
  > Starts after Tasks 2, 3, and 4 complete. Sub-tasks 5.1 and 5.2 can run in parallel.

- [ ] 5.1 (P) Implement location resolution and coordinate precision service <!-- gh:#68 -->
  - Load the MaxMind GeoLite2 City database file from the classpath as a thread-safe singleton bean
  - When geolocation consent is granted and coordinates are provided, truncate latitude and longitude to a 0.005° grid (≈ 500 m precision) before returning the location value
  - When consent is declined or coordinates are absent, resolve city and country from the request IP address using the GeoLite2 database
  - On lookup failure (private IP, DB error), return a location with city = "Unknown" and null coordinates — never block registration
  - Log a startup warning if the GeoLite2 database file is older than 25 days
  - _Requirements: 1.8, 1.9, 3.3_

- [ ] 5.2 (P) Implement photo validation and storage service <!-- gh:#69 -->
  - Implement an abstracted photo storage interface that can be swapped to object storage in v2
  - In the v1 local filesystem implementation, store uploaded photos under a configurable directory using `{ownerId}/profile.{ext}` as the path
  - Validate MIME type via Apache Tika magic-byte detection (accept only `image/jpeg`, `image/png`, `image/webp`); reject files that declare a valid Content-Type but fail Tika detection
  - Return a typed result distinguishing valid, invalid-mime, and invalid-size outcomes
  - Implement the delete operation that removes the file and handles missing-file cases gracefully
  - _Requirements: 1.6, 1.7, 3.5_

- [ ] 5.3 Implement registration orchestration and account creation <!-- gh:#70 -->
  - Orchestrate the full atomic registration flow: reCAPTCHA + honeypot check → email format and uniqueness check → password complexity validation → location resolution → photo storage → account persistence → verification email dispatch
  - Enforce password complexity: at least 8 characters, one uppercase, one lowercase, one digit, one special character; return individual per-rule violations when the password fails so the client can highlight each failing rule
  - On duplicate email, return the same generic failure result as any other validation error (no account existence disclosure)
  - Treat the entire flow as atomic: do not persist the owner row if any preceding step fails; roll back the photo write if the DB insert fails after the photo was already stored
  - Hash the password with BCrypt at strength ≥ 12 before storage; never log, transmit, or return the plaintext password
  - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.10, 4.4, 4.5_

- [ ] 5.4 Add the registration API endpoint <!-- gh:#71 -->
  - Accept a multipart/form-data request with a JSON registration part (email, password, name, reCAPTCHA token, honeypot field, location consent, optional coordinates) and an optional photo file
  - Run the IP rate-limit check before delegating to the registration service
  - Respond 201 with a generic success message on success; respond 400 with structured violation details on validation failure; respond 422 when bot detection fails; respond 429 on rate limit breach with a `Retry-After` header
  - _Requirements: 1.1, 1.3, 1.4, 1.5, 4.2_

---

- [ ] 6. Email Verification Service <!-- gh:#72 -->
  > Starts after Task 2 completes. Can run in parallel with Task 3.

- [ ] 6.1 (P) Implement verification token lifecycle management <!-- gh:#73 -->
  - Generate 64-character cryptographically secure random hex verification tokens using `SecureRandom`
  - Persist each token with a 72-hour TTL (`expires_at = now + 72h`); at most one active token per owner (invalidate previous tokens on resend)
  - On token consumption, set `used_at = now` atomically; reject any subsequent use of the same token
  - On resend request, invalidate any existing active token and issue a new one
  - _Requirements: 2.3, 2.4_

- [ ] 6.2 (P) Implement verification email dispatch <!-- gh:#74 -->
  - Using `JavaMailSender`, compose and send a plain-text (plus optional HTML) verification email containing the secure verification link
  - Dispatch the email asynchronously (in a coroutine IO context) to meet the 5-second SLA without blocking the registration response thread
  - Log at WARN level if the email dispatch fails, but do not abort the registration — the owner can request a resend
  - _Requirements: 1.10_

- [ ] 6.3 Add email verification API endpoints <!-- gh:#75 -->
  - Implement a GET endpoint for verification link callbacks: validate the token, transition the owner account to `ACTIVE`, and redirect to the app home page on success
  - When the token is expired, respond with a clear message and offer a resend link
  - When the token has already been used or the account is already active, redirect to the home page without error
  - Implement a POST endpoint to resend the verification email, subject to IP rate limiting
  - _Requirements: 2.2, 2.3, 2.5_

---

- [ ] 7. Login & Authentication <!-- gh:#76 -->
  > Starts after Tasks 3 and 5 complete.

- [ ] 7.1 Implement login attempt structured logging <!-- gh:#77 -->
  - Record every login attempt as a structured log entry using the project's KotlinLogging pattern
  - Include UTC timestamp, source IP (last octet zeroed for IPv4), outcome (`SUCCESS` or `FAILURE`), and reason code for failures (`INVALID_PASSWORD`, `UNVERIFIED_ACCOUNT`, `ACCOUNT_NOT_FOUND`)
  - Never include email addresses, plaintext passwords, password hashes, or other unnecessary PII in log entries
  - Write log entries synchronously so no attempt is silently dropped
  - _Requirements: 5.1, 5.2, 5.3_

- [ ] 7.2 Implement authentication service and credential verification <!-- gh:#78 -->
  - Look up the owner by email; if not found, run BCrypt against a pre-computed dummy hash to prevent timing attacks that reveal whether an account exists, then return the same generic failure result
  - Verify the BCrypt password hash (cost ≥ 12); on mismatch, log the failure and return a generic authentication failure
  - Reject login for accounts in `PENDING_VERIFICATION` status with a specific "email not verified" response
  - On successful authentication, generate an access token (1h expiry) and a refresh token (30-day expiry), store the refresh token hash in the DB, log the success, and return both tokens
  - Implement token refresh: validate the raw refresh token against its stored hash, check expiry and revocation, issue a new access token
  - Implement logout: mark the refresh token as revoked in the DB
  - _Requirements: 2.1, 4.4, 4.5_

- [ ] 7.3 Add login, refresh, and logout API endpoints <!-- gh:#79 -->
  - Implement a POST login endpoint accepting email and password; apply IP rate limiting before processing; return the token pair on success or a uniform 401 on any credential failure
  - Implement a POST refresh endpoint that accepts a refresh token and returns a new access token
  - Implement a POST logout endpoint (auth required) that revokes the provided refresh token
  - _Requirements: 2.1_

---

- [ ] 8. Owner Profile Management <!-- gh:#80 -->
  > Starts after Task 5 completes.

- [ ] 8.1 Implement profile retrieval and update service <!-- gh:#81 -->
  - Implement profile retrieval: return the current owner's name, photo URL (if any), location data, and account status
  - Implement profile update: apply the same validation rules as registration (name mandatory, photo max 5 MB + JPEG/PNG/WebP via Tika, location consent handling)
  - When the owner updates their location, require explicit re-consent before storing coordinates; always truncate coordinates to the 500 m grid before persistence
  - When the owner removes their photo, delete the stored file and clear the path in the DB
  - _Requirements: 3.1, 3.2, 3.3, 3.5_

- [ ] 8.2 Add profile management API endpoints <!-- gh:#82 -->
  - Implement a GET endpoint to retrieve the authenticated owner's profile (auth required)
  - Implement a PUT endpoint for profile updates, accepting multipart/form-data with a JSON update part and an optional new photo; return the updated profile on success with a confirmation message
  - Implement a DELETE endpoint to remove the profile photo; return 204 on success and 404 if no photo was stored
  - All endpoints require an `ACTIVE` account status; return 403 for `PENDING_VERIFICATION` owners
  - _Requirements: 3.1, 3.4, 3.5_

---

- [ ] 9. Integration & Testing <!-- gh:#83 -->
  > Sub-tasks 9.1, 9.2, and 9.3 are independent and can run in parallel after all feature tasks complete.

- [ ] 9.1 (P) Integration tests: registration flow and anti-abuse <!-- gh:#84 -->
  - Test the happy-path registration against a real PostgreSQL container: verify the owner row is created in `PENDING_VERIFICATION` state and the verification token has the correct TTL
  - Test the atomic rollback: simulate a DB failure after photo write and verify no orphaned files or owner rows remain
  - Test the duplicate-email race condition using two concurrent registration requests for the same email; verify only one account is created and both receive the same generic failure message
  - Test rate-limit enforcement: 5 requests within 30 minutes → 429; verify Fibonacci block durations for successive breaches
  - Test reCAPTCHA and honeypot rejection in test-mode bypass configuration
  - _Requirements: 1.2, 1.3, 1.4, 4.1, 4.2, 4.3_

- [ ] 9.2 (P) Integration tests: email verification lifecycle <!-- gh:#85 -->
  - Test the full verification flow: register → click link → account transitions to `ACTIVE`
  - Test token expiry: force-expire the token timestamp and verify the expired-token response and resend capability
  - Test one-time-use: click the same link twice and verify the second click is rejected
  - Test already-active: click a link for an already-active account and verify redirect without error
  - Use GreenMail or a similar SMTP stub for integration-level email capture
  - _Requirements: 2.2, 2.3, 2.4, 2.5_

- [ ] 9.3 (P) Integration tests: authentication and JWT lifecycle <!-- gh:#86 -->
  - Test login happy path: valid credentials → token pair returned; verify access token claims (owner id, email, status, expiry)
  - Test login failure paths: unknown email, wrong password, unverified account — verify each returns the same 401 status and that the correct failure reason code appears in the structured log
  - Test `PENDING_VERIFICATION` JWT blocked at the security filter layer: generate a token with PENDING status, call a protected endpoint, verify 403
  - Test token refresh: issue a new access token from a valid refresh token; verify the old refresh token is still valid (rotation not required in v1)
  - Test logout: revoke the refresh token and verify subsequent refresh attempts are rejected
  - _Requirements: 2.1, 4.4, 5.1, 5.2, 5.3_

- [ ]* 9.4 E2E tests: critical user paths <!-- gh:#87 -->
  - Wizard happy path: submit all 3 steps combined → 201 → verify email → login → access protected endpoint
  - Duplicate-email hint: Step 1 submission with existing email → generic error displayed
  - Photo rejection: upload a 6 MB file → 400 error with clear reason; upload a valid 2 MB JPEG → accepted
  - Profile update: change name, swap photo, update location with consent → verify persisted correctly
  - _Requirements: 1.1, 1.5, 1.6, 1.7, 3.1, 3.2, 3.4_
