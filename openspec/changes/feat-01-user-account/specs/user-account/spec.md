## ADDED Requirements

### Requirement: User Registration
The system SHALL allow users to create an account using an email and password.

#### Scenario: Successful Registration
- **WHEN** user provides a valid email and password
- **THEN** an account is created and the user is logged in

### Requirement: Authentication
The system SHALL secure access to user data and features using JWT-based authentication.

#### Scenario: Login with correct credentials
- **WHEN** user provides registered email and correct password
- **THEN** system returns a valid JWT for subsequent requests
