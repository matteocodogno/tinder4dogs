# Requirements Document

## Introduction

This document defines the requirements for the **Registration & Owner Profile** feature of PawMatch (Tinder for Dogs). It covers new owner account creation via a guided multi-step wizard, email verification, post-registration profile management, and the security controls protecting the registration flow.

The feature corresponds to F-01 in the PRD and is a P0 prerequisite for all other platform features: an owner must have a verified account and a complete dog profile before accessing the matching feed.

---

## Requirements

### Requirement 1: Owner Registration — 3-Step Wizard

**Objective:** As a new owner, I want to register via a guided 3-step wizard with inline tutorial tips, so that I can create my account easily without missing required information.

#### Acceptance Criteria

1. When a new visitor opens the registration page, the Registration Service shall display a 3-step wizard (Step 1: Credentials, Step 2: Personal Details, Step 3: Location & Consent) with inline tutorial guidance visible on each step without navigating away.
2. The Registration Service shall treat the wizard as an atomic flow — if the user abandons or closes the browser at any step, the system shall not persist any partial data and shall require the user to restart the registration from step 1.
3. When the user submits Step 1, the Registration Service shall validate that the email address is correctly formatted and is not already registered, and that the password meets the minimum complexity rules (at least 8 characters, one uppercase letter, one lowercase letter, one digit, and one special character).
4. If the submitted email address is already associated with an existing account, the Registration Service shall display a generic error message that does not confirm or deny the existence of that account (enumeration prevention).
5. If the password does not meet complexity rules, the Registration Service shall highlight each failing rule individually so the user can correct them without re-entering compliant fields.
6. When the user submits Step 2, the Registration Service shall accept the owner's name (mandatory) and an optional profile photo; if a photo is provided, the system shall validate that the file does not exceed 5 MB and that the format is JPEG, PNG, or WebP.
7. If the uploaded photo exceeds 5 MB or is in an unsupported format, the Registration Service shall display a descriptive validation error and allow the user to select a different file without losing the name field value.
8. When the user submits Step 3, the Registration Service shall present a clear geolocation consent request; explicit consent is required before storing coordinates more precise than city level.
9. If the user declines geolocation consent, the Registration Service shall derive a city-level location from the user's IP address and store only that approximation.
10. When all 3 steps are successfully completed, the Registration Service shall create the owner account in `PENDING_VERIFICATION` state, hash the password using bcrypt or Argon2 before storage, and dispatch a verification email to the provided address.

---

### Requirement 2: Email Verification

**Objective:** As a registered owner, I want to verify my email address so that my account is activated and I can access the platform.

#### Acceptance Criteria

1. While an owner account is in `PENDING_VERIFICATION` state, the Registration Service shall block access to all platform features and display a dedicated "Check your email" screen with instructions to complete verification.
2. When a valid verification link is clicked, the Registration Service shall transition the account to `ACTIVE` state and redirect the owner to the app home page.
3. The Registration Service shall set the verification link TTL to 72 hours from the moment it is issued; if the link is clicked after expiry, the system shall display an informative message and allow the owner to request a new verification email from the "Check your email" screen.
4. The Registration Service shall invalidate the verification token after successful use so the same link cannot activate the account a second time.
5. If a verification link is clicked for an account already in `ACTIVE` state, the Registration Service shall redirect the user to the home page without error.

---

### Requirement 3: Owner Profile Management

**Objective:** As a verified owner, I want to edit my profile details after registration so that my information stays accurate over time.

#### Acceptance Criteria

1. When a verified owner navigates to their profile settings, the Registration Service shall display the current name, profile photo (or placeholder if none), and stored location.
2. When the owner submits updated profile data, the Registration Service shall apply the same validation rules used during registration (name mandatory, photo max 5 MB and JPEG/PNG/WebP, location consent).
3. If the owner updates their location, the Registration Service shall require renewed explicit geolocation consent and shall never store or expose coordinates at a precision finer than 500 m.
4. When a profile update is saved successfully, the Registration Service shall confirm the change with a visible success notification.
5. If the owner removes their profile photo, the Registration Service shall revert to the default placeholder image and delete the previously stored photo.

---

### Requirement 4: Security & Anti-Abuse Controls

**Objective:** As the platform operator, I want the registration flow protected against bots and abuse so that account quality is maintained and infrastructure is not exploited.

#### Acceptance Criteria

1. The Registration Service shall protect the registration form with both reCAPTCHA v3 (invisible, score-based) and a server-side honeypot field; a submission that fails either check shall be rejected before reaching business logic.
2. The Registration Service shall rate-limit registration attempts per source IP address to a maximum of 5 attempts within any 30-minute window; on the first breach the system shall return HTTP 429 and apply a Fibonacci-progressive block (block durations grow as the Fibonacci sequence: 1 min, 1 min, 2 min, 3 min, 5 min, … per successive breach); each block event shall be logged.
3. If the Registration Service detects repeated email-lookup patterns consistent with enumeration, it shall apply the same 5-attempts-per-30-min rate-limiting and Fibonacci-progressive blocking rules as for excessive registration attempts.
4. The Registration Service shall never log, transmit, or store plaintext passwords at any point in the registration or update flow.
5. The Registration Service shall hash all passwords with bcrypt (cost factor ≥ 12) or Argon2id before persisting them to the database.

---

### Requirement 5: Login Attempt Tracking

**Objective:** As the platform operator, I want every login attempt logged so that I can detect suspicious activity and support security audits.

#### Acceptance Criteria

1. When a login attempt is made, the Registration Service shall record a structured log entry containing: UTC timestamp, source IP address, outcome (`SUCCESS` or `FAILURE`), and a reason code for failures (`INVALID_PASSWORD`, `UNVERIFIED_ACCOUNT`, `ACCOUNT_NOT_FOUND`).
2. The Registration Service shall never include plaintext passwords, password hashes, or unnecessary PII in login attempt log entries.
3. While login attempt logging is active, the Registration Service shall ensure log entries are written synchronously so that no attempt — successful or failed — is silently dropped.

---

## Non-Functional Requirements

### Security

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-REG-01 | Password hashing | bcrypt (cost ≥ 12) or Argon2id; no plaintext storage at any layer | P0 | Req 1, Req 4 |
| NFR-REG-02 | GDPR / nLPD compliance | Explicit consent before storing precise geolocation; right to erasure implemented (data deleted within 30 days of request) | P0 | Req 1, Req 3 |
| NFR-REG-03 | Geolocation precision | Coordinates never stored or exposed at precision finer than 500 m server-side | P0 | Req 1, Req 3 |
| NFR-REG-04 | Anti-bot & rate limiting | reCAPTCHA v3 + honeypot at form level; max 5 registration attempts per IP per 30 min; Fibonacci-progressive block on breach; HTTP 429 returned | P0 | Req 4 |
| NFR-REG-05 | Email enumeration prevention | Generic error on duplicate-email submission; no account existence disclosed | P0 | Req 1 |

### Performance

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-REG-06 | Wizard step response time | Each wizard step submission shall respond in p95 < 500 ms under normal load | P1 | Req 1 |
| NFR-REG-07 | Verification email delivery | Verification email shall be dispatched within 5 seconds of registration completion | P1 | Req 2 |

### Scalability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-REG-08 | Photo storage | Accepted formats: JPEG, PNG, WebP; max 5 MB per file | P1 | Req 1, Req 3 |

### Reliability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-REG-09 | Atomic registration | No partial owner records persisted on wizard abandonment | P0 | Req 1 |

### Observability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-REG-10 | Login attempt logging | 100% of login attempts (success and failure) logged with timestamp, IP, outcome, and reason code; no silent drops | P1 | Req 5 |

### Usability

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-REG-11 | Inline tutorial guidance | Tutorial tips visible on each wizard step without leaving the current step | P1 | Req 1 |
| NFR-REG-12 | Per-rule password feedback | Each failing password rule highlighted individually on submission | P1 | Req 1 |

### Constraints

| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-REG-13 | Toolchain versions | Runtime versions (Java, Kotlin, Node) defined via `.mise.toml` and committed | P0 | All |
| NFR-REG-14 | Reproducible builds | `mvn verify` must pass in a clean CI environment with no implicit global dependencies | P1 | All |

---

## Open Questions

| # | Question | Impact | Owner | Status |
|---|----------|--------|-------|--------|
| 1 | What is the exact IP rate-limit threshold and block duration? | Resolved: max 5 attempts per 30 min, Fibonacci-progressive block per successive breach | Tech / Security | **Closed** |
| 2 | What is the verification email link expiry duration? | Resolved: 72 hours from issuance | Product / Tech | **Closed** |
| 3 | Which anti-bot provider is preferred? | Resolved: reCAPTCHA v3 (invisible) + server-side honeypot | Tech | **Closed** |
