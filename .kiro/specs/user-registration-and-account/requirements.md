# Requirements Document

## Introduction

This document defines the requirements for **User Registration & Account** (F-01), the foundational feature of the Tinder4Dogs platform. It covers account creation, authentication, account management, and account deletion. All platform features depend on a valid, authenticated user account. This feature targets both casual owners (e.g., Marco) and private breeders (e.g., Giulia), who are non-technical users expecting a simple and secure onboarding flow.

MVP constraints: single account per user, single dog profile per user. No social/OAuth login in v1.

---

## Requirements

### Requirement 1: User Registration

**Objective:** As an owner, I want to create an account with my email and a password, so that I can access the platform and manage my dog's profile.

#### Acceptance Criteria

1. When a user submits a registration form with a valid email address and a password that meets strength requirements, the Registration Service shall create a new user account in an **unverified** state and return a success response.
2. If the submitted email address is already associated with an existing account, the Registration Service shall reject the request with an error indicating the email is already in use.
3. If the submitted email address is not in a valid format, the Registration Service shall reject the request with a descriptive validation error.
4. If the submitted password does not meet the minimum strength requirements (at least 8 characters, at least one letter and one digit), the Registration Service shall reject the request with a descriptive validation error.
5. When a new account is created, the Registration Service shall store the password as a bcrypt hash and never persist or return the plaintext password.
6. When a new account is created, the Registration Service shall record the timestamp of registration.
7. When a user submits a registration request without providing explicit consent to the privacy policy and data processing terms, the Registration Service shall reject the request and return an error indicating consent is required.
8. After account creation, the Registration Service shall send a verification email containing a time-limited confirmation link (expires in 24 hours) to the registered email address.
9. An unverified account shall not be permitted to log in; the Authentication Service shall reject login attempts from unverified accounts with a descriptive error.
10. When a user follows a valid, unexpired confirmation link, the Registration Service shall activate the account and allow subsequent logins.
11. If the confirmation link has expired, the Registration Service shall provide a mechanism for the user to request a new verification email.

---

### Requirement 2: User Authentication (Login)

**Objective:** As an owner, I want to log in with my email and password, so that I can access my account and use the platform.

#### Acceptance Criteria

1. When a user submits valid credentials (registered email and correct password), the Authentication Service shall return a signed JWT access token (expiry ≤ 24 hours) and a refresh token (expiry ≤ 30 days).
2. If the submitted email is not associated with any registered account, the Authentication Service shall return a generic authentication error (without revealing whether the email exists).
3. If the submitted password does not match the stored hash for the given email, the Authentication Service shall return a generic authentication error.
4. The Authentication Service shall never include the user's password hash or any sensitive credential in any API response.
5. While a JWT access token is valid and unexpired, the Tinder4Dogs API shall grant access to protected endpoints for the authenticated user.
6. If a request to a protected endpoint is made without a valid JWT token, the Tinder4Dogs API shall reject the request with a 401 Unauthorized response.
7. When a client submits a valid, unexpired refresh token, the Authentication Service shall issue a new access token and a new refresh token, and invalidate the previously used refresh token (token rotation).
8. If a refresh token is expired, already used, or not recognised, the Authentication Service shall reject the request with a 401 Unauthorized response and require the user to log in again.
9. When a user explicitly logs out, the Authentication Service shall invalidate the current refresh token so it cannot be used to obtain new access tokens.

---

### Requirement 3: Account Information Retrieval

**Objective:** As an owner, I want to view my account information, so that I can verify the data stored about me.

#### Acceptance Criteria

1. When an authenticated user requests their account information, the User Account Service shall return the user's email address and registration date.
2. The User Account Service shall never include the password hash or any internal credential field in the account information response.
3. If an unauthenticated request is made to retrieve account information, the Tinder4Dogs API shall return a 401 Unauthorized response.

---

### Requirement 4: Account Update (Email and Password Change)

**Objective:** As an owner, I want to update my email address or change my password, so that I can keep my account details current and secure.

#### Acceptance Criteria

1. When an authenticated user submits a valid new email address, the User Account Service shall update the account email after verifying the new address is not already in use.
2. If the new email address is already associated with another account, the User Account Service shall reject the update with an error indicating the email is already in use.
3. When an authenticated user submits a password change request with the correct current password and a valid new password, the User Account Service shall update the stored password hash.
4. If the current password provided in a password change request is incorrect, the User Account Service shall reject the request with an authentication error.
5. If the new password provided in a password change request does not meet the minimum strength requirements, the User Account Service shall reject the request with a descriptive validation error.

---

### Requirement 5: Account Deletion (Right to Erasure)

**Objective:** As an owner, I want to delete my account and all associated data, so that I can exercise my right to erasure under GDPR.

#### Acceptance Criteria

1. When an authenticated user submits an account deletion request, the User Account Service shall permanently delete the user account and all directly associated personal data (email, password hash, registration timestamp, consent record).
2. When an account is deleted, the User Account Service shall also cascade deletion to all data owned by the user (dog profile, swipe history, matches, chat messages) within the same transaction or process.
3. When an account deletion is completed, the User Account Service shall respond with a success confirmation; subsequent login attempts with the same credentials shall fail.
4. If an unauthenticated request is made to delete an account, the Tinder4Dogs API shall return a 401 Unauthorized response.
5. The User Account Service shall complete account deletion and data erasure within 30 days of the deletion request being submitted.

---

### Requirement 6: GDPR Consent and Privacy

**Objective:** As an owner, I want to give explicit consent for my data to be processed, so that the platform complies with GDPR requirements.

#### Acceptance Criteria

1. The Registration Service shall present a link to the privacy policy and a mandatory opt-in consent checkbox before creating an account.
2. When a user grants consent during registration, the Registration Service shall record the consent with a timestamp and the version of the privacy policy accepted (version identifier `1.0`).
3. The Tinder4Dogs API shall never acquire or process the user's location data without the user having explicitly granted location consent in the current session.
4. The User Account Service shall provide an endpoint for users to retrieve their stored consent records.

### Requirement 7: Password Recovery

**Objective:** As an owner, I want to reset my password via email if I forget it, so that I can regain access to my account without losing my data.

#### Acceptance Criteria

1. When a user submits a password recovery request with a registered email address, the Password Recovery Service shall send a time-limited reset link to that email address.
2. The reset link shall expire after no more than 1 hour from the time of issuance.
3. Each reset token shall be single-use; once a password has been successfully reset with a token, that token shall be invalidated immediately.
4. If the submitted email address is not associated with any registered account, the Password Recovery Service shall return a generic success response (without revealing whether the email exists).
5. When a user submits a valid, unexpired reset token and a new password that meets the minimum strength requirements, the Password Recovery Service shall update the stored password hash and invalidate all existing sessions for that user.
6. If the reset token is expired or has already been used, the Password Recovery Service shall reject the request with a descriptive error.
7. If the new password provided during reset does not meet the minimum strength requirements (at least 8 characters, at least one letter and one digit), the Password Recovery Service shall reject the request with a descriptive validation error.


---

## Non-Functional Requirements

### Performance
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-P-01 | Registration response time | < 1000ms at P95 | P1 | Req 1 |
| NFR-P-02 | Login response time | < 500ms at P95 | P1 | Req 2 |

### Security
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-S-01 | Password storage | Passwords hashed with bcrypt (cost ≥ 10); never stored in plaintext | P0 | Req 1, Req 4 |
| NFR-S-02 | JWT token expiry | Access tokens expire in ≤ 24 hours | P0 | Req 2 |
| NFR-S-03 | Transport security | All API communication over HTTPS/TLS 1.2+ | P0 | NFR-02 (PRD) |
| NFR-S-04 | Authentication error messages | Login errors must not reveal whether the email exists (prevent user enumeration) | P0 | Req 2 |
| NFR-S-05 | Credential exposure | Password hashes and internal tokens must never appear in any API response | P0 | Req 2, Req 3 |

### Scalability
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-SC-01 | Concurrent registrations | System must handle up to 10,000 DAU without degradation | P1 | NFR-05 (PRD) |

### Reliability
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-R-01 | Registration atomicity | Account creation is atomic; partial state must not be persisted on failure | P0 | Req 1 |
| NFR-R-02 | Deletion atomicity | Account deletion and cascade must be transactional | P0 | Req 5 |

### Observability
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-O-01 | Registration events | Registration success and failure events must be logged with timestamp | P1 | Req 1 |
| NFR-O-02 | Authentication events | Login success and failure events must be logged (without credentials) | P1 | Req 2 |

### Usability
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-U-01 | Validation messages | All validation errors must return a human-readable description identifying the failing field | P1 | Req 1, Req 4 |

### Constraints
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-C-01 | Single account per user | One account per email address; no duplicate accounts | P0 | PRD F-01 |
| NFR-C-02 | No OAuth / social login | v1 supports only email + password authentication | P0 | PRD F-01 |
| NFR-C-03 | GDPR right to erasure | Account data must be erasable within 30 days of deletion request | P0 | NFR-03 (PRD) |
| NFR-C-04 | Authorization decoupling | Authorization logic must remain decoupled from domain logic to support future premium tier | P1 | NFR-09 (PRD) |

---

## Open Questions

| # | Question | Impact | Owner | Status |
|---|----------|--------|-------|--------|
| 1 | Should email verification (confirmation email) be required before account activation? | Affects registration flow complexity and deliverability requirements | Product | **Resolved** — yes, added as Req 1 AC#8–11 |
| 2 | Is a "forgot password" / password reset flow in scope for v1? | Impacts usability (locked-out users); requires email infrastructure | Product | **Resolved** — added as Req 7 |
| 3 | Should a refresh token mechanism be implemented alongside the JWT access token, or is re-login after 24h expiry acceptable? | Affects UX for long sessions | Tech | **Resolved** — refresh token with rotation, added as Req 2 AC#7–9 |
| 4 | What is the exact version identifier for the privacy policy that must be stored with consent? | Required for GDPR consent record validity | Legal/Product | **Resolved** — version identifier `1.0` |
