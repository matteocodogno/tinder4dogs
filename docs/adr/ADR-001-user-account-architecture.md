# ADR-001: JWT-Based Stateless Authentication

## Status
Accepted

## Context
Tinder for Dogs is a Spring Boot monolith requiring secure user authentication and authorization. The platform needs to support:
- RESTful API architecture
- Potential future mobile applications
- Scalability without session state management
- Secure user credential storage

## Decision
We will implement JWT (JSON Web Tokens) for stateless authentication using Spring Security as the security framework.

### Key Components:
- **Security Framework**: Spring Security
- **Authentication Strategy**: JWT tokens with HS512/RS256 algorithm
- **Password Storage**: BCryptPasswordEncoder
- **User Identity**: Email as the primary identifier

## Rationale
1. **Stateless Architecture**: JWT eliminates the need for server-side session storage, simplifying horizontal scaling and reducing memory overhead.

2. **Spring Security Integration**: Industry-standard security framework with robust integration with Spring Boot, WebMVC, and Data JPA.

3. **Mobile-Ready**: JWT tokens work seamlessly with mobile applications and single-page applications (SPAs), supporting future platform expansion.

4. **Industry Standards**: BCrypt password hashing provides strong, adaptive security; JWT is a widely adopted standard (RFC 7519).

5. **Email as Username**: Unique, verifiable identifier that aligns with common user expectations and simplifies account management.

## Consequences

### Positive:
- Simplified server infrastructure (no session store required)
- Better horizontal scalability
- Consistent authentication across web and future mobile clients
- Reduced server memory footprint
- Strong password security through BCrypt

### Negative:
- **Token Revocation Complexity**: JWTs cannot be easily invalidated before expiry without implementing a token blacklist
- **Token Size**: JWTs are larger than session IDs, increasing bandwidth usage
- **Security Dependency**: Compromise of JWT secret key exposes all tokens

## Mitigation Strategies
1. **Secret Management**: Store JWT secret in environment variables, never in code
2. **Short TTL**: Keep token expiration short (24 hours per NFR-02)
3. **Strong Algorithms**: Use HS512 or RS256 for token signing
4. **Future Enhancements**:
   - Rate limiting for brute force protection
   - Token refresh mechanism
   - Optional token blacklist for critical revocation scenarios

## Alternatives Considered
1. **Session-Based Authentication**: Rejected due to stateful nature, poor scalability, and incompatibility with future mobile apps
2. **OAuth2/Social Login**: Deferred to future versions (out of v1 scope)
3. **Basic Authentication**: Rejected due to security limitations and lack of token expiry

## References
- Design Document: `openspec/changes/feat-01-user-account/design.md`
- Spring Security: https://spring.io/projects/spring-security
- JWT RFC 7519: https://tools.ietf.org/html/rfc7519
