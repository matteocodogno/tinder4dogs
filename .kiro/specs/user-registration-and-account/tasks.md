# Implementation Plan

## Tasks

- [ ] 1. Set up security dependencies and Liquibase infrastructure
- [ ] 1.1 Add new Maven dependencies to pom.xml
  - Add `spring-boot-starter-security` for Spring Security 7 filter chain and BCrypt support
  - Add `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (JJWT 0.12.x) for JWT token generation and parsing
  - Add `spring-boot-starter-mail` for transactional email dispatch
  - Add `spring-boot-starter-liquibase` for schema migration management
  - _Requirements: 1.5, 2.1, NFR-S-01_

- [ ] 1.2 Configure Liquibase and update application properties
  - Create `src/main/resources/db/changelog/db.changelog-master.yaml` as the Liquibase master file that includes all migration changesets in order
  - Change `spring.jpa.hibernate.ddl-auto` from `update` to `validate` so Hibernate validates against the Liquibase-managed schema
  - Add `spring.liquibase` configuration pointing to the master changelog
  - Add mail properties (`spring.mail.*`) with environment variable placeholders for SMTP host, port, username, and password
  - Add `tinder4dogs.jwt.secret` and `tinder4dogs.jwt.access-token-expiry-seconds` properties with environment variable bindings
  - _Requirements: 2.1, NFR-S-02_

- [ ] 2. Define database schema with Liquibase SQL migrations
- [ ] 2.1 Create the users table migration
  - Write `V001__create_users.sql` with columns: `id` (UUID PK), `email` (VARCHAR 255, unique, not null), `password_hash` (VARCHAR 72, not null), `email_verified` (BOOLEAN, default false), `status` (VARCHAR 20, default ACTIVE), `registered_at` and `updated_at` (TIMESTAMPTZ)
  - Add a unique index on `email`
  - Reference the changeset from `db.changelog-master.yaml`
  - _Requirements: 1.1, 1.5, 1.6, 1.9_

- [ ] 2.2 (P) Create auxiliary table migrations
  - Write `V002__create_consent_records.sql`: `id`, `user_id` (FK → users ON DELETE CASCADE), `privacy_policy_version`, `consented_at`
  - Write `V003__create_refresh_tokens.sql`: `id`, `user_id` (FK → users ON DELETE CASCADE), `token_hash` (unique), `issued_at`, `expires_at`, `revoked` (boolean)
  - Write `V004__create_email_verification_tokens.sql`: `id`, `user_id` (FK → users ON DELETE CASCADE), `token_hash` (unique), `expires_at`, `used` (boolean)
  - Write `V005__create_password_reset_tokens.sql`: `id`, `user_id` (FK → users ON DELETE CASCADE), `token_hash` (unique), `expires_at`, `used` (boolean)
  - Add composite index on `refresh_tokens(user_id, revoked, expires_at)` for efficient bulk revocation queries
  - Register all changesets in the master file after V001
  - These files can be authored in parallel; Liquibase executes them sequentially per changelog order
  - _Requirements: 2.7, 5.1, 5.2, 6.2, 7.1, NFR-R-01, NFR-R-02_

- [ ] 3. Implement domain entities and repositories
- [ ] 3.1 Implement User entity and repository
  - Create the `User` JPA entity with all columns from the schema (id, email, passwordHash, emailVerified, status, registeredAt, updatedAt) and appropriate column mappings
  - Define a `UserStatus` enum with at least `ACTIVE` and `DELETED` values
  - Create a `UserRepository` extending `JpaRepository` with query methods: find by email, check existence by email
  - Create all request and response DTOs: `RegistrationRequest`, `LoginRequest`, `UpdateEmailRequest`, `UpdatePasswordRequest`, `AccountInfoResponse`, `AuthTokenResponse`, `RegistrationResponse`
  - All response DTOs must exclude password hash and any credential field
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 3.1, 3.2, NFR-C-01_

- [ ] 3.2 (P) Implement token entities and repositories
  - Create JPA entities for `RefreshToken`, `EmailVerificationToken`, and `PasswordResetToken` with exact column mappings from the schema
  - Create corresponding Spring Data repositories with query methods: find by token hash, find all active (non-revoked, non-expired) tokens by user, delete all by user
  - Create request/response DTOs for token flows: `RefreshTokenRequest`, `ForgotPasswordRequest`, `ResetPasswordRequest`, `ResendVerificationRequest`
  - Depends on `User` entity from 3.1 for the foreign key relationship mapping
  - _Requirements: 2.7, 2.8, 2.9, 7.1, 7.2, 7.3_

- [ ] 3.3 (P) Implement ConsentRecord entity and repository
  - Create the `ConsentRecord` JPA entity mapped to the consent_records table
  - Create `ConsentRecordRepository` with a method to find all records by user id
  - Create the `ConsentRecordResponse` DTO exposing `privacyPolicyVersion` and `consentedAt` (no user-identifying data beyond what the endpoint already scopes)
  - Depends on `User` entity from 3.1 for the foreign key relationship mapping
  - _Requirements: 6.1, 6.2, 6.4_

- [ ] 4. Implement core security infrastructure
- [ ] 4.1 Implement TokenService
  - Implement JWT access token generation using JJWT: sign with HMAC-SHA256 using the configured secret key, embed `sub` (userId), `email`, `iat`, and `exp` claims
  - Implement JWT access token validation: parse and verify signature and expiry; return typed `AccessTokenClaims` on success
  - Implement refresh token issuance: generate 32 cryptographically-random bytes, encode URL-safe Base64, store SHA-256 hash in the DB with a 30-day expiry
  - Implement refresh token rotation: within a single transaction, mark the old token hash as revoked and persist a new one; reject already-revoked or expired tokens
  - Implement refresh token revocation by raw token and bulk revocation by user id (for logout and password reset)
  - Implement email verification token issuance and validation (24-hour expiry, single-use via `used` flag)
  - Implement password reset token issuance and validation (1-hour expiry, single-use)
  - _Requirements: 2.1, 2.2, 2.5, 2.7, 2.8, 2.9, 1.8, 1.10, 1.11, 7.2, 7.3, NFR-S-02_

- [ ] 4.2 Configure Spring Security and JWT authentication filter
  - Implement a `JwtAuthenticationFilter` extending `OncePerRequestFilter` that extracts the Bearer token from the `Authorization` header, delegates validation to `TokenService`, and populates `SecurityContextHolder` on success; clears context silently on failure
  - Implement `SecurityConfig` declaring a stateless `SecurityFilterChain`: register the JWT filter before `UsernamePasswordAuthenticationFilter`, declare public endpoints (all `/api/v1/auth/**` routes and `/api/v1/support/**`), require authentication for all other paths
  - Register `BCryptPasswordEncoder` with strength 12 as a Spring bean
  - Disable CSRF (stateless API)
  - _Requirements: 2.5, 2.6, NFR-S-01, NFR-S-03, NFR-C-04_

- [ ] 4.3 (P) Implement EmailService
  - Implement `EmailService` using `JavaMailSender` to send plain-text transactional emails
  - `sendVerificationEmail`: compose a message containing the verification link (`/api/v1/auth/verify-email?token=<raw-token>`) and dispatch it to the recipient address
  - `sendPasswordResetEmail`: compose a message containing the reset link with the raw token and dispatch it
  - Log failures at ERROR level; raise a typed `EmailDispatchException` so callers can decide to swallow (registration) or propagate
  - Can be implemented in parallel with 4.1 and 4.2 since it has no dependency on security infrastructure; requires only the mail configuration from 1.2
  - _Requirements: 1.8, 1.11, 7.1, NFR-O-01_

- [ ] 5. Implement user registration and email verification
- [ ] 5.1 Implement RegistrationService
  - Implement `register`: validate email format and uniqueness, validate password strength (≥8 chars, ≥1 letter, ≥1 digit), verify `consentGiven = true`, hash the password with BCrypt, persist the `User` in unverified state and a `ConsentRecord` (version `1.0`) in the same transaction, then delegate token issuance and email dispatch to `TokenService` and `EmailService`
  - Implement `verifyEmail`: hash the raw token, look it up via `TokenService`, mark token used and flip `user.emailVerified = true` atomically
  - Implement `resendVerification`: silently succeed for unknown emails (anti-enumeration), otherwise issue a new verification token and resend the email; invalidate any previously unused tokens for the user
  - Return typed sealed results for all outcomes (success, email already exists, validation failure, consent missing)
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11, 6.1, 6.2, NFR-R-01, NFR-O-01_

- [ ] 5.2 Expose registration and email verification endpoints in AuthController
  - Create `AuthController` mapped to `/api/v1/auth` and wire in `RegistrationService`
  - `POST /register`: validate request with `@Valid`, delegate to `RegistrationService.register`, map sealed results to 201/400/409/422 HTTP responses
  - `GET /verify-email?token=`: delegate to `RegistrationService.verifyEmail`, map to 200/400/410
  - `POST /resend-verification`: validate request, delegate, always respond 200
  - All validation errors must include a human-readable `field` and `message` in the response body
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.7, 1.10, 1.11, NFR-U-01_

- [ ] 6. Implement user authentication (login, refresh, logout)
- [ ] 6.1 Implement AuthenticationService
  - Implement `login`: load user by email (no timing difference between "not found" and "wrong password" — always run BCrypt verify); reject if `emailVerified = false`; on success, call `TokenService` to generate an access token and issue a refresh token; return typed sealed result
  - Implement `refresh`: delegate to `TokenService.rotateRefreshToken`; generate a new access token for the returned userId; return new token pair on success or typed failure sealed result
  - Implement `logout`: delegate to `TokenService.revokeRefreshToken` to invalidate the submitted refresh token
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.7, 2.8, 2.9, 1.9, NFR-S-04, NFR-O-02_

- [ ] 6.2 Expose authentication endpoints in AuthController
  - Add to existing `AuthController`: `POST /login`, `POST /refresh`, `POST /logout`
  - `POST /login`: map `LoginResult` sealed results to 200/401/403 with `AuthTokenResponse` on success; never include credential or hash in response
  - `POST /refresh`: map `RefreshResult` to 200/401
  - `POST /logout`: authenticate via JWT (protected endpoint), revoke refresh token, return 204
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, NFR-S-05_

- [ ] 7. Implement account management
- [ ] 7.1 (P) Implement AccountService
  - Implement `getAccount`: load user by id from security context, return `AccountInfo` (email, registeredAt, emailVerified) — never expose password hash
  - Implement `updateEmail`: verify new email is not already taken, update `user.email` and `updatedAt`; return typed sealed result
  - Implement `changePassword`: verify current password with BCrypt, validate new password strength, update hash; return typed sealed result
  - Implement `deleteAccount`: within a single transaction, hard-delete the `User` row (cascades to all owned rows: consent records, refresh tokens, verification tokens, reset tokens); log the deletion event
  - Implement `getConsents`: load all `ConsentRecord` rows for the user and return as list
  - Can be implemented in parallel with tasks 6 and 8 since `AccountService` touches only `User`, `ConsentRecord`, and `RefreshToken` — separate from `AuthenticationService` and `PasswordRecoveryService`
  - _Requirements: 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 6.4, NFR-R-02, NFR-C-03_

- [ ] 7.2 (P) Expose account management endpoints in UserAccountController
  - Create `UserAccountController` mapped to `/api/v1/users/me`, wiring in `AccountService`
  - Extract `userId` exclusively from `SecurityContextHolder` — never from request body or path variable
  - `GET /`: return `AccountInfoResponse` (200/401)
  - `PATCH /email`: validate, delegate, map sealed results to 200/400/401/409
  - `PATCH /password`: validate, delegate, map to 204/400/401/422
  - `DELETE /`: delegate, return 204/401
  - `GET /consents`: return `List<ConsentRecordResponse>` (200/401)
  - Can be implemented in parallel with task 6 (different controller class and file)
  - _Requirements: 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.3, 5.4, 6.4, NFR-U-01_

- [ ] 8. Implement password recovery
- [ ] 8.1 (P) Implement PasswordRecoveryService
  - Implement `requestPasswordReset`: look up user by email silently (do not reveal existence); if found, call `TokenService.issuePasswordResetToken` and `EmailService.sendPasswordResetEmail`; always return void without error
  - Implement `resetPassword`: validate token via `TokenService.validatePasswordResetToken`, validate new password strength, update password hash, then call `TokenService.revokeAllRefreshTokensForUser` to invalidate all active sessions
  - Return typed sealed results for `resetPassword` (success, invalid token, expired token, validation failure)
  - The service class is independent and can be developed in parallel with task 7
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, NFR-S-04_

- [ ] 8.2 Expose password recovery endpoints in AuthController
  - Add to existing `AuthController`: `POST /forgot-password`, `POST /reset-password`
  - `POST /forgot-password`: validate request, delegate to `PasswordRecoveryService.requestPasswordReset`, always respond 200 regardless of whether the email is registered
  - `POST /reset-password`: validate request with `@Valid`, delegate, map `ResetPasswordResult` sealed results to 200/400/410
  - Must follow 6.2 since it extends the same `AuthController` class
  - _Requirements: 7.1, 7.4, 7.5, 7.6, 7.7, NFR-S-04_

- [ ] 9. Integration testing and validation
- [ ] 9.1 Write unit tests for all service classes
  - Test `RegistrationService`: valid registration, duplicate email, weak password, missing consent, email verification token expiry, already-verified account on resend
  - Test `AuthenticationService`: successful login, wrong password (constant-time), unverified email rejection, refresh rotation, reuse of revoked token, logout revocation
  - Test `TokenService`: access token claims round-trip, expired token rejection, single-use enforcement for verification and reset tokens
  - Test `AccountService`: cascade deletion completeness, email uniqueness guard on update, wrong current password rejection
  - Test `PasswordRecoveryService`: anti-enumeration (no error for unknown email), expired token rejection, session invalidation on reset
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.7, 2.1, 2.2, 2.3, 2.7, 2.8, 2.9, 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 7.1, 7.4, 7.5, 7.6, NFR-S-01, NFR-S-04_

- [ ] 9.2 Write integration tests for all API flows
  - Full happy path: register → verify email → login → access protected endpoint → refresh → logout
  - Account deletion: confirm all dependent rows are removed in the same transaction; verify subsequent login returns 401
  - Password reset: request reset → reset with token → confirm old sessions invalidated → confirm token cannot be reused
  - Protected endpoint guard: verify 401 is returned for missing token, expired token, and malformed token
  - Backward compatibility: confirm existing `/api/v1/support/**` endpoints remain accessible without authentication
  - Confirm no password hash appears in any response body across all endpoints
  - _Requirements: 2.5, 2.6, 3.3, 5.2, 5.3, 7.3, 7.5, 7.6, NFR-S-05_

- [ ]* 9.3 Verify anti-enumeration behaviour across all sensitive endpoints
  - Confirm `POST /login` returns identical HTTP status and response shape for unknown email vs wrong password
  - Confirm `POST /forgot-password` returns 200 for both registered and unregistered email addresses
  - Confirm `POST /resend-verification` returns 200 regardless of email existence
  - Optional: can be deferred post-MVP if covered by unit tests in 9.1
  - _Requirements: 2.2, 2.3, 7.4, NFR-S-04_
