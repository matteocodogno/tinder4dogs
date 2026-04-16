## Architecture Decision Record

**ADR-001 — stomp-websocket-real-time-messaging**

- **Status**: Accepted
- **Context**: Real-time bidirectional messaging is required between two matched owners within a single Spring Boot 4 / WebMVC service. No WebSocket, messaging infrastructure, or session management existed prior to this feature. NFR-C11 mandates a stateless, horizontally scalable backend; NFR-C12 mandates WebSocket as the sole real-time channel.
- **Decision**: Use STOMP over WebSocket with Spring's in-memory simple broker for v1. Authenticate STOMP CONNECT frames via `JwtChannelInterceptor`. Document the RabbitMQ STOMP relay as the v2 multi-node upgrade path requiring only configuration changes.
- **Consequences**:
    - ✔ Per-user routing via `SimpMessagingTemplate.convertAndSendToUser()` requires no custom session registry (NFR-C11 satisfied — no in-memory chat session state).
    - ✔ Multi-node scaling requires only a `configureMessageBroker()` configuration change to add a RabbitMQ relay; no controller, service, or client changes needed.
    - ✔ Spring STOMP auto-configuration integrates with the existing `@RestController` pattern within the same JVM — no new infrastructure process or network hop.
    - ✘ Introduces a second authentication path (`JwtChannelInterceptor` for STOMP vs. `OncePerRequestFilter` for REST) that must be maintained in sync throughout the security lifecycle.
    - ✘ Browser clients cannot set `Authorization` headers during the WebSocket HTTP upgrade; clients must transmit the JWT as a STOMP CONNECT frame header, deviating from the standard Bearer token pattern.
- **Alternatives**:
    - *Raw WebSocket*: requires a custom per-user session registry (violates NFR-C11); no broker relay upgrade path; estimated 5–10× more custom infrastructure code.
    - *SSE + REST*: explicitly prohibited by NFR-C12; exhausts Tomcat's 200-thread pool at ~500 concurrent SSE connections; two HTTP transactions per message exceed NFR-C03 under contention.
- **References**:
    - `.kiro/specs/owner-chat/requirements.md` — NFR-C11, NFR-C12, Req 7.1–7.6
    - `.kiro/specs/owner-chat/research.md` — "WebSocket Protocol Choice: STOMP vs Raw", "JWT Authentication on WebSocket CONNECT"

---
 