## Context

Tinder for Dogs requires a secure way for users to manage their profiles and interact with the platform. Currently, the project is a Spring Boot monolith with JPA and PostgreSQL. We need to introduce authentication and user management.

## Goals / Non-Goals

**Goals:**
- Provide a RESTful API for user registration and login.
- Use JWT (JSON Web Tokens) for stateless authentication.
- Persist user credentials securely in PostgreSQL using bcrypt hashing.
- Integrate Spring Security to protect existing and future endpoints.

**Non-Goals:**
- OAuth2/Social Login (v1 scope).
- Password recovery/reset flow (v1 scope).
- Multi-factor authentication.

## Decisions

- **Security Framework**: Spring Security. *Rationale*: Industry standard for Spring Boot, provides robust integration with WebMVC and Data JPA.
- **Authentication Strategy**: JWT. *Rationale*: Stateless, works well with potential future mobile apps and avoids session management complexity.
- **Password Storage**: BCryptPasswordEncoder. *Rationale*: Strong, standard hashing algorithm provided by Spring Security.
- **User Identity**: Email as the primary identifier (username). *Rationale*: Unique, verifiable, and common pattern for user accounts.

## Risks / Trade-offs

- [Risk] JWT Secret Compromise → [Mitigation] Store secret in environment variables, use strong HS512/RS256 algorithm.
- [Risk] Brute force attacks on login → [Mitigation] (Future) Implement rate limiting or account lockout.
- [Trade-off] Statelessness vs Revocation → JWTs cannot be easily revoked before expiry without a blacklist; we will keep TTL short (e.g., 24h as per NFR-02).
