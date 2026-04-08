# Research & Design Decisions

---
**Purpose**: Capture discovery findings, architectural investigations, and rationale that inform the technical design.

---

## Summary

- **Feature**: `owner-chat`
- **Discovery Scope**: New Feature (new `chat` domain module in existing Spring Boot monolith)
- **Key Findings**:
  - STOMP over WebSocket is the recommended protocol for Spring Boot 4 / Spring Framework 7; raw WebSocket is unsuitable for user-authenticated, routed messaging.
  - JWT must be passed via STOMP CONNECT header (not URL query param) and validated in a `ChannelInterceptor` ordered ahead of Spring Security interceptors; CSRF must be manually disabled for stateless JWT flows.
  - Bucket4j 8.17.0 + `bucket4j-spring-boot-starter` 0.14.0 is the rate-limiting library most compatible with Spring Boot 4; Resilience4j has incomplete Spring Boot 4 support.

---

## Research Log

### WebSocket Protocol Choice: STOMP vs Raw

- **Context**: Requirements call for real-time bidirectional messaging with per-user delivery and authentication; choice of protocol affects implementation complexity and future scalability.
- **Sources Consulted**: Spring Framework 7 documentation, Spring WebSocket guides, websocket.org Spring Boot framework guide.
- **Findings**:
  - STOMP over WebSocket provides message routing (`/app`, `/queue`, `/topic` prefixes), per-user destination queues, broker relay path for future horizontal scaling, and structured frame format.
  - Raw `WebSocketHandler` handles binary/text frames only — no routing, no per-user session management, requires bespoke protocol design.
  - `SimpMessagingTemplate.convertAndSendToUser()` is the correct API for owner-to-owner push; it auto-appends a session suffix to prevent cross-user delivery.
- **Implications**: STOMP selected. Raw WebSocket explicitly rejected. Spring's in-memory simple broker is sufficient for v1 single-node; can be replaced with RabbitMQ STOMP relay without changing controller or service code.

### JWT Authentication on WebSocket CONNECT

- **Context**: Browser WebSocket clients cannot set custom HTTP headers during the HTTP upgrade request — a browser-level limitation.
- **Sources Consulted**: Spring Framework token-based authentication docs, Spring Security WebSocket docs, Spring Security CSRF issue #12378.
- **Findings**:
  - Browser cannot set `Authorization` header on WS upgrade; token must be sent as a STOMP CONNECT frame header instead.
  - `ChannelInterceptor.preSend()` intercepts the STOMP CONNECT frame, extracts JWT from `StompHeaderAccessor`, validates it, and calls `accessor.setUser(principal)`.
  - The interceptor must be registered with `@Order(Ordered.HIGHEST_PRECEDENCE + 99)` so it runs before Spring Security's `AuthorizationChannelInterceptor`.
  - `@EnableWebSocketSecurity` enables CSRF on CONNECT frames by default; for stateless JWT apps CSRF must be disabled by configuring inbound channel interceptors manually (drop `@EnableWebSocketSecurity`).
  - Spring Security CSRF/WebSocket bug #12378 (fixed in Spring Security 6.0.2) — Spring Boot 4 ships Spring Security 7.x, so the bug is resolved, but the design implication (CSRF must be disabled for JWT flows) remains.
- **Implications**: `JwtChannelInterceptor` on inbound channel; `WebSocketSecurityConfig` registers interceptors manually without `@EnableWebSocketSecurity`.

### Rate Limiting Library Selection

- **Context**: Requirements mandate per-owner rate limiting to prevent spam; must be compatible with Spring Boot 4.
- **Sources Consulted**: Bucket4j GitHub releases, `bucket4j-spring-boot-starter` releases (v0.14.0 → Spring Boot 4.0.3), Resilience4j Spring Boot 4 issue #2351.
- **Findings**:
  - Bucket4j 8.17.0 core is pure Java; `bucket4j-spring-boot-starter` 0.14.0 explicitly targets Spring Boot 4.0.3+.
  - Per-user in-memory pattern: `ConcurrentHashMap<UUID, Bucket>` keyed by owner ID — official Bucket4j documented approach.
  - Redis-backed distributed buckets available via same API when horizontal scaling is needed.
  - Resilience4j has no formal Spring Boot 4 release; community workaround via Spring Cloud CircuitBreaker 5.0.0 is fragile.
- **Implications**: Bucket4j core (no Spring Boot starter required for simple in-memory use) selected. `RateLimitService` wraps a `ConcurrentHashMap<UUID, Bucket>`.

### Jackson Dual-Version Risk

- **Context**: The existing `pom.xml` declares both `tools.jackson.module:jackson-module-kotlin` (Jackson 3) and `com.fasterxml.jackson.module:jackson-module-kotlin` (Jackson 2) simultaneously.
- **Sources Consulted**: Spring Boot 4 migration guide, Spring Boot 4 WebSocket auto-configuration requirements.
- **Findings**:
  - Spring Boot 4 STOMP auto-configuration requires `tools.jackson` (Jackson 3) on the classpath.
  - Having both Jackson 2 and Jackson 3 on the classpath can cause message converter conflicts at runtime.
  - This is a pre-existing technical debt item — flagged here as a risk but not in scope to fix within the chat feature.
- **Implications**: No action in this feature; logged as a known risk. If STOMP message serialization behaves unexpectedly, removing the `com.fasterxml.jackson.module:jackson-module-kotlin` dependency should be the first diagnostic step.

### Tomcat WebSocket Buffer Limit

- **Context**: Tomcat 11 (Spring Boot 4 default) has an 8 KB default WebSocket text buffer.
- **Sources Consulted**: garvik.dev Tomcat buffer documentation, Spring Boot Tomcat configuration properties.
- **Findings**:
  - Messages exceeding 8 KB cause Tomcat to close the WebSocket connection with `CloseStatus 1009` before Spring processes them.
  - Our hard limit is 2,000 characters ≈ ~6 KB worst case (UTF-8 multibyte) — safely under 8 KB for normal Latin text.
  - For safety margin, the limit should be explicitly configured to 64 KB.
- **Implications**: Add `server.tomcat.websocket.max-text-message-buffer-size=65536` to `application.yaml`.

### Codebase Existing Patterns

- **Context**: Chat domain must integrate cleanly with existing conventions.
- **Sources Consulted**: Codebase read — `match/`, `support/`, `ai/` domains.
- **Findings**:
  - Package convention: `com.ai4dev.tinderfordogs.<domain>/model/service/presentation/`
  - REST prefix: `/api/v1/` (seen in `BreedCompatibilityController`)
  - No Spring Security present yet — chat feature introduces the first security dependency
  - No Liquibase yet — `ddl-auto: update` used currently; PRD NFR-08 mandates Liquibase with rollback
  - `Dog` entity is not yet JPA-persisted (no `@Entity` annotation) — no reusable JPA baseline to extend
  - `kotlin-logging-jvm` already used — `KotlinLogging.logger {}` pattern to follow
- **Implications**: Chat is the first domain to introduce JPA persistence with proper entities, Spring Security, WebSocket, and Liquibase. It establishes the pattern for future domains.

---

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| STOMP over WebSocket (selected) | Spring STOMP broker with `@MessageMapping` controllers | Per-user routing built-in; scalable to broker relay; matches Spring MVC patterns | Adds STOMP protocol learning curve | Officially recommended by Spring |
| Raw WebSocket | Custom `WebSocketHandler` | No protocol overhead | No routing, no per-user queues, bespoke auth wiring | Rejected — too much custom infrastructure |
| SSE + REST | SSE for server push, REST for sends | Simpler than WebSocket | Unidirectional only; does not satisfy Req 7.4 (bidirectional) | Rejected — requirements mandate WebSocket |
| Bucket4j rate limiting (selected) | Token-bucket per owner UUID | Spring Boot 4 compatible; Redis-upgradable | In-memory lost on restart (acceptable for rate limiting) | Preferred over Resilience4j |

---

## Design Decisions

### Decision: `ChatThread` as Explicit Entity vs Deriving Thread from Match

- **Context**: Whether to create a dedicated `chat_thread` table or re-use the future `match` table identity directly.
- **Alternatives Considered**:
  1. Embed thread metadata directly in `match` table (no separate table)
  2. Dedicated `chat_thread` table with `match_id` reference
- **Selected Approach**: Dedicated `chat_thread` table with `match_id` (UUID, unique, no FK) as a loose reference.
- **Rationale**: Match persistence does not exist yet; loose coupling avoids blocking chat on match domain evolution. The `chat_thread` table owns the participant IDs and inbox metadata, keeping the chat domain self-contained.
- **Trade-offs**: No referential integrity on `match_id` at the DB level — the application layer enforces the invariant. Acceptable for v1.
- **Follow-up**: Add FK when the `match` table is implemented.

### Decision: Unmatch Deletion — Hard Delete vs Soft Delete

- **Context**: Requirements 5.1–5.4 mandate permanent deletion of messages and thread on unmatch.
- **Alternatives Considered**:
  1. Soft delete (set `deleted_at`; filter in queries)
  2. Hard delete (cascade DELETE from DB)
- **Selected Approach**: Hard delete with `ON DELETE CASCADE` on `chat_message.thread_id`.
- **Rationale**: Product decision (see resolved Q1) is that history does not survive unmatch. Hard delete is simpler, privacy-correct (GDPR erasure), and avoids filtering overhead.
- **Trade-offs**: Irreversible; no undo path. Acceptable given explicit product requirement.
- **Follow-up**: Ensure deletion is transactional — thread and messages deleted atomically.

### Decision: Rate Limit Threshold

- **Context**: Requirement 3 mandates rate limiting; threshold value is still an open question (Q1 in requirements).
- **Selected Approach**: Default to 30 messages per minute per owner, implemented as a Bucket4j token bucket with capacity 30, refill rate 30 tokens/60 seconds.
- **Rationale**: Placeholder until product decision; configurable via `application.yaml` property `tinder4dogs.chat.rate-limit.messages-per-minute`.
- **Trade-offs**: In-memory state lost on restart — buckets reset. Acceptable for rate limiting (slightly more generous window after restart).

---

## Risks & Mitigations

- **Jackson dual-version conflict** — Both Jackson 2 and Jackson 3 in pom.xml; STOMP message converter may pick up wrong version. Mitigation: monitor integration tests; if serialization fails, remove `com.fasterxml.jackson.module:jackson-module-kotlin` (Jackson 2).
- **No existing Match entity** — Chat domain requires `match_id` to be provided by the match domain; no FK enforceability. Mitigation: loose coupling via UUID reference; add FK constraint when match persistence is implemented.
- **In-memory STOMP broker for multi-node** — Simple broker does not share state across instances. Mitigation: documented upgrade path to RabbitMQ STOMP relay; no code changes required in controllers/services.
- **Bucket4j state loss on restart** — Rate limit windows reset. Mitigation: acceptable for v1 (brief generosity after restart is not a security threat); upgrade to Redis-backed buckets if abuse is detected at scale.
- **First Spring Security introduction** — No baseline security config exists; new security constraints could inadvertently block existing endpoints. Mitigation: configure `SecurityFilterChain` to permit existing `/api/v1/matches/**` and `/api/v1/support/**` paths during transition.

---

## References

- [Spring Framework 7 — WebSocket STOMP Authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html)
- [Spring Framework 7 — SimpMessagingTemplate per-user routing](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html)
- [Spring Security 7 — WebSocket Security](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html)
- [Spring Boot 4 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Bucket4j — Spring Boot 4 starter v0.14.0](https://github.com/MarcGiffing/bucket4j-spring-boot-starter/releases)
- [Tomcat WebSocket buffer limit (garvik.dev)](https://www.garvik.dev/spring-boot/websocket-tomcat-buffer-limit)
