## Why

User registration and authentication are foundational requirements for Tinder for Dogs. This change implements the core user account functionality, enabling users to create accounts and securely access the platform.

## What Changes

- **Implement** user registration endpoint (email/password).
- **Implement** JWT-based authentication and login endpoint.
- **Set up** initial user data model in PostgreSQL.
- **Configure** security filters for protected routes.

## Capabilities

### New Capabilities
- `user-account`: Provides user registration and secure authentication using JWT.

### Modified Capabilities
- (None)

## Impact

- **Database**: New `users` table in PostgreSQL.
- **Security**: Addition of Spring Security configuration and JWT handling logic.
- **API**: New endpoints under `/api/auth/*` (or similar).
