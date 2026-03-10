## 1. Foundation & Model

- [ ] 1.1 Add `spring-boot-starter-security` and JWT dependencies (if not present) to `pom.xml`.
- [ ] 1.2 Create `User` JPA entity with fields for email, hashed password, and roles.
- [ ] 1.3 Create `UserRepository` for database access.

## 2. Security Configuration & JWT

- [ ] 2.1 Implement `JwtTokenProvider` to handle generation, validation, and parsing of tokens.
- [ ] 2.2 Configure `SecurityConfig` to use JWT filters and permit access to authentication endpoints.
- [ ] 2.3 Implement custom `UserDetailsService` to load users from the database.

## 3. API Implementation

- [ ] 3.1 Implement `AuthController` with `register` and `login` endpoints.
- [ ] 3.2 Add request DTOs for registration and login (email, password).
- [ ] 3.3 Add validation annotations to registration DTOs.

## 4. Verification & Testing

- [ ] 4.1 Create integration tests for successful registration.
- [ ] 4.2 Create integration tests for successful login and JWT generation.
- [ ] 4.3 Verify that protected endpoints return 401/403 when no valid JWT is provided.
