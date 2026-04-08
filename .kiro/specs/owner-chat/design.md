# Design Document — Owner Chat

## Overview

Owner Chat delivers real-time, match-gated text messaging to dog owners on PawMatch. Once a mutual match is established, both owners receive a dedicated chat thread through which they can coordinate meetups. Messaging is bidirectional via STOMP over WebSocket; message history and the inbox are accessible via REST. The feature introduces the first Spring Security configuration, the first JPA-persisted domain entities, and the first WebSocket infrastructure into the project.

**Purpose**: Enables matched owners to communicate safely without sharing external contact details, removing the friction of arranging in-person dog meetups.

**Users**: All owners with at least one active match (Personas: Marco — casual owner; Giulia — breeding owner).

**Impact**: Introduces a new `chat` domain module; adds Spring Security, WebSocket, Bucket4j, and Liquibase as first-time project dependencies. Establishes the JPA entity and security patterns for future domains.

### Goals

- Real-time bidirectional messaging via STOMP/WebSocket for matched owner pairs
- Match-gated access control: no chat without a confirmed mutual match
- Per-owner rate limiting (30 msg/min) to prevent spam
- Durable message persistence; hard delete on unmatch (GDPR-compliant)
- REST endpoints for message history (paginated) and inbox

### Non-Goals

- SSE or mobile push notifications
- Read receipts or "seen" status
- Configurable rate-limit threshold (hard-coded; `application.yaml` property only)
- Message editing or individual message deletion
- Multi-node STOMP broker (upgrade path documented; not implemented in v1)

---

## Requirements Traceability

| Requirement | Summary | Components | Interfaces | Flows |
|-------------|---------|------------|------------|-------|
| 1.1–1.5 | Chat access gated on mutual match | `ChatService`, `ChatThreadRepository` | `ChatService.validateAccess` | Send flow (guard) |
| 2.1–2.5 | Persist messages with metadata; validate content | `ChatService`, `ChatMessageRepository` | `ChatService.sendMessage` | Send flow |
| 3.1–3.4 | Per-owner rate limit before persist | `RateLimitService` | `RateLimitService.consume` | Send flow (pre-persist) |
| 4.1–4.5 | Paginated message history via REST | `ChatController`, `ChatService` | `GET /api/v1/chat/threads/{matchId}/messages` | — |
| 5.1–5.4 | Hard delete thread + messages on unmatch | `ChatService`, cascade DELETE | `ChatService.deleteThread` | Unmatch flow |
| 6.1–6.5 | Sorted inbox via REST | `ChatController`, `ChatService` | `GET /api/v1/chat/threads` | — |
| 7.1–7.6 | STOMP/WebSocket real-time send and push | `ChatWebSocketController`, `JwtChannelInterceptor`, `WebSocketConfig` | STOMP `/app/chat.send`, `/user/queue/messages` | Send flow |
| 8.1–8.5 | JWT auth on all endpoints; no cross-thread leaks | `JwtChannelInterceptor`, `SecurityConfig`, `ChatService` | HTTP 401/403 guards | All flows |

---

## Architecture

### Existing Architecture Analysis

The codebase is a single Spring Boot 4.0.4 / Spring Framework 7 service using Spring WebMVC (not WebFlux). Domain modules follow `<domain>/model/service/presentation/` package layout under `com.ai4dev.tinderfordogs`. No security, no WebSocket, no JPA entities with `@Entity` annotations (only in-memory models), and `ddl-auto: update` (no Liquibase).

Chat is the first feature to introduce:
- Spring Security (`spring-boot-starter-security`)
- Spring WebSocket STOMP (`spring-boot-starter-websocket`)
- JPA entities with `@Entity` (real DB persistence)
- Liquibase (replacing `ddl-auto: update` for the new schema)
- Bucket4j (rate limiting)

### Architecture Pattern & Boundary Map

```mermaid
graph TB
    subgraph Browser
        ClientREST[REST Client]
        ClientWS[WebSocket Client]
    end

    subgraph ChatDomain [chat domain module]
        ChatController[ChatController]
        ChatWebSocketController[ChatWebSocketController]
        JwtChannelInterceptor[JwtChannelInterceptor]
        WebSocketConfig[WebSocketConfig]
        SecurityConfig[SecurityConfig]
        ChatService[ChatService]
        RateLimitService[RateLimitService]
        ChatThreadRepo[ChatThreadRepository]
        ChatMessageRepo[ChatMessageRepository]
    end

    subgraph MatchDomain [match domain module]
        MatchService[MatchService]
    end

    subgraph Infrastructure
        Postgres[(PostgreSQL)]
        StompBroker[In-Memory STOMP Broker]
    end

    ClientREST -->|JWT Bearer| ChatController
    ClientWS -->|WS Upgrade + STOMP CONNECT| WebSocketConfig
    WebSocketConfig --> JwtChannelInterceptor
    JwtChannelInterceptor --> ChatWebSocketController
    ChatController --> ChatService
    ChatWebSocketController --> RateLimitService
    ChatWebSocketController --> ChatService
    ChatService --> ChatThreadRepo
    ChatService --> ChatMessageRepo
    ChatService --> StompBroker
    ChatThreadRepo --> Postgres
    ChatMessageRepo --> Postgres
    StompBroker -->|push /user/queue/messages| ClientWS
    MatchService -->|createThread / deleteThread| ChatService
```

**Architecture Integration**:
- Selected pattern: domain module (chat) within existing layered monolith — consistent with `match/` and `support/` domains
- Domain boundary: `ChatService` is the only entry point for both REST and WebSocket layers; `MatchService` calls `ChatService` directly for thread lifecycle events
- Existing patterns preserved: `model/service/presentation` layering, `/api/v1/` REST prefix, `KotlinLogging.logger {}`, `@RestController` + `@RequestMapping`
- New components: `WebSocketConfig`, `JwtChannelInterceptor`, `SecurityConfig`, `RateLimitService`
- Steering compliance: domain-first packages; no global `service/` or `controller/` packages; secrets via env-var placeholders in `application.yaml`

### Technology Stack

| Layer | Choice / Version | Role in Feature | Notes |
|-------|------------------|-----------------|-------|
| Backend | Spring Boot 4.0.4 / Spring Framework 7 | Hosts all chat endpoints | Existing; no change |
| Real-time | spring-boot-starter-websocket (STOMP) | Bidirectional WebSocket messaging | New dependency |
| Security | spring-boot-starter-security + auth0 java-jwt 4.x | JWT validation on HTTP filter chain and STOMP CONNECT | First security dependency in project |
| Rate limiting | Bucket4j 8.17.0 core | Per-owner token-bucket rate limiting | `bucket4j-spring-boot-starter` 0.14.0; see research.md |
| Persistence | PostgreSQL + Spring Data JPA | `chat_thread` and `chat_message` tables | First `@Entity` usage in project |
| DB migrations | Liquibase | Schema versioning with rollback | First Liquibase adoption; replaces `ddl-auto: update` for chat |
| Serialization | tools.jackson (Jackson 3, existing) | STOMP message serialization | Jackson 2 dependency in pom.xml is a pre-existing risk; see research.md |

---

## System Flows

### Send Message (WebSocket)

```mermaid
sequenceDiagram
    participant Owner as Owner Browser
    participant WS as WebSocket Endpoint
    participant JWTInt as JwtChannelInterceptor
    participant ChatWS as ChatWebSocketController
    participant RL as RateLimitService
    participant Svc as ChatService
    participant DB as PostgreSQL
    participant Broker as STOMP Broker
    participant Recip as Recipient Browser

    Owner->>WS: STOMP CONNECT Authorization Bearer JWT
    JWTInt->>JWTInt: validate JWT set Principal
    Owner->>ChatWS: SEND /app/chat.send SendMessageCommand
    ChatWS->>RL: consume(ownerId)
    alt rate limit exceeded
        RL-->>ChatWS: RateLimitExceededException
        ChatWS-->>Owner: /user/queue/errors ErrorEvent retryAfterMs
    else within limit
        ChatWS->>Svc: sendMessage(ownerId, matchId, content)
        Svc->>Svc: validateAccess(ownerId, matchId)
        Svc->>DB: INSERT chat_message
        Svc->>DB: UPDATE chat_thread last_message
        Svc->>Broker: convertAndSendToUser(recipientId, queue/messages, MessageEvent)
        Broker-->>Recip: /user/queue/messages MessageEvent
        Svc-->>ChatWS: MessageSentEvent
        ChatWS-->>Owner: /user/queue/messages MessageSentEvent
    end
```

Key decisions: message is persisted before the STOMP push is dispatched (satisfies NFR-C07); if the recipient is offline the message remains in history (satisfies 7.5).

### Unmatch — Thread Deletion

```mermaid
sequenceDiagram
    participant MatchSvc as MatchService
    participant Svc as ChatService
    participant DB as PostgreSQL

    MatchSvc->>Svc: deleteThread(matchId)
    Svc->>DB: DELETE chat_message WHERE thread_id IN SELECT id FROM chat_thread WHERE match_id
    Svc->>DB: DELETE chat_thread WHERE match_id
    Svc-->>MatchSvc: void
```

Deletion is atomic within a single transaction. `ON DELETE CASCADE` on `chat_message.thread_id` serves as a safety net but the service also issues explicit bulk delete to avoid JPA loading all entities.

---

## Components and Interfaces

### Summary Table

| Component | Layer | Intent | Req Coverage | Key Dependencies | Contracts |
|-----------|-------|--------|--------------|------------------|-----------|
| `ChatController` | presentation | REST inbox + history | 4, 6, 8 | `ChatService` (P0) | API |
| `ChatWebSocketController` | presentation | STOMP send/receive | 7, 3, 2, 1, 8 | `ChatService` (P0), `RateLimitService` (P0), `SimpMessagingTemplate` (P0) | API, Event |
| `JwtChannelInterceptor` | config/security | Authenticate STOMP CONNECT | 7.2, 7.3, 8.1 | JWT library (P0) | Service |
| `WebSocketConfig` | config | STOMP broker + endpoint registration | 7.1 | Spring WebSocket (P0) | — |
| `SecurityConfig` | config | HTTP JWT filter chain | 8.1, 8.2 | Spring Security (P0) | — |
| `ChatService` | service | All chat business logic | 1–8 | `ChatThreadRepository` (P0), `ChatMessageRepository` (P0), `SimpMessagingTemplate` (P0) | Service |
| `RateLimitService` | service | Per-owner token-bucket rate limit | 3 | Bucket4j (P0) | Service |
| `ChatThreadRepository` | data | JPA access to `chat_thread` | 1, 5, 6 | PostgreSQL (P0) | — |
| `ChatMessageRepository` | data | JPA access to `chat_message` | 2, 4, 5 | PostgreSQL (P0) | — |

---

### Domain: chat/service

#### ChatService

| Field | Detail |
|-------|--------|
| Intent | Single service encapsulating all chat business rules: access control, message persistence, inbox, thread lifecycle |
| Requirements | 1.1–1.5, 2.1–2.5, 4.1–4.5, 5.1–5.4, 6.1–6.5 |

**Responsibilities & Constraints**
- Owns the `ChatThread` and `ChatMessage` aggregate
- Enforces participant access invariant before every read/write operation
- Is the sole caller of `SimpMessagingTemplate` for outbound WebSocket push
- `createThread` and `deleteThread` are called only by `MatchService`; all other methods are called by presentation layer

**Dependencies**
- Outbound: `ChatThreadRepository` — thread CRUD (P0)
- Outbound: `ChatMessageRepository` — message CRUD (P0)
- Outbound: `SimpMessagingTemplate` — push to recipient WebSocket (P0)

**Contracts**: Service [x]

##### Service Interface

```kotlin
interface ChatService {
    fun createThread(matchId: UUID, ownerAId: UUID, ownerBId: UUID): ChatThread
    fun deleteThread(matchId: UUID)
    fun sendMessage(senderOwnerId: UUID, matchId: UUID, content: String): ChatMessage
    fun getMessageHistory(
        requestingOwnerId: UUID,
        matchId: UUID,
        pageable: Pageable
    ): Page<ChatMessageResponse>
    fun getInbox(ownerId: UUID): List<InboxEntryResponse>
}
```

- **Preconditions for `sendMessage`**: thread exists for `matchId`; `senderOwnerId` is a participant; `content` non-blank, ≤ 2,000 chars
- **Postconditions for `sendMessage`**: message persisted with UTC timestamp; `ChatThread.lastMessagePreview` and `lastMessageAt` updated; push dispatched to recipient
- **Invariant**: `sendMessage`, `getMessageHistory` throw `AccessDeniedException` if owner is not a participant of the thread

---

#### RateLimitService

| Field | Detail |
|-------|--------|
| Intent | Enforce per-owner token-bucket rate limit on message sends |
| Requirements | 3.1–3.4 |

**Responsibilities & Constraints**
- Maintains a `ConcurrentHashMap<UUID, Bucket>` keyed by owner ID
- Bucket configuration: capacity 30 tokens, refill 30 tokens / 60 s (configurable via `tinder4dogs.chat.rate-limit.messages-per-minute`)
- Throws `RateLimitExceededException` (containing `retryAfterMs`) when limit is exceeded
- State is in-memory; resets on application restart (acceptable for v1)

**Dependencies**
- External: Bucket4j 8.17.0 core — token-bucket implementation (P0)

**Contracts**: Service [x]

##### Service Interface

```kotlin
interface RateLimitService {
    /**
     * Attempts to consume one token for [ownerId].
     * @throws RateLimitExceededException if the bucket is exhausted,
     *         containing [RateLimitExceededException.retryAfterMs]
     */
    fun consume(ownerId: UUID)
}

data class RateLimitExceededException(val retryAfterMs: Long) : RuntimeException()
```

---

### Domain: chat/presentation

#### ChatController

| Field | Detail |
|-------|--------|
| Intent | REST endpoints for message history retrieval and inbox listing |
| Requirements | 4.1–4.5, 6.1–6.5, 8.1, 8.2 |

**Dependencies**
- Outbound: `ChatService` — business logic (P0)
- Inbound: Spring Security HTTP filter chain — JWT validation (P0)

**Contracts**: API [x]

##### API Contract

| Method | Endpoint | Request | Response | Errors |
|--------|----------|---------|----------|--------|
| GET | `/api/v1/chat/threads` | — | `200 List<InboxEntryResponse>` | 401 |
| GET | `/api/v1/chat/threads/{matchId}/messages` | `page`, `size` query params (default size=50) | `200 Page<ChatMessageResponse>` | 401, 403, 404 |

**InboxEntryResponse fields**: `matchId: UUID`, `matchedDogName: String`, `matchedDogPhotoUrl: String?`, `lastMessagePreview: String?`, `lastMessageAt: Instant?`

**ChatMessageResponse fields**: `id: UUID`, `senderOwnerId: UUID`, `content: String`, `sentAt: Instant`

---

#### ChatWebSocketController

| Field | Detail |
|-------|--------|
| Intent | STOMP `@MessageMapping` controller; receives send commands, delegates to ChatService, pushes response events |
| Requirements | 7.1–7.6, 3.1–3.4, 2.1–2.5, 1.1, 8.1, 8.3 |

**Dependencies**
- Outbound: `ChatService` — persist and push (P0)
- Outbound: `RateLimitService` — rate check before send (P0)
- Outbound: `SimpMessagingTemplate` — error delivery to `/user/queue/errors` (P0)
- Inbound: `JwtChannelInterceptor` — authenticated `Principal` on every message (P0)

**Contracts**: API [x], Event [x]

##### API Contract (STOMP)

| Direction | Destination | Payload | Description |
|-----------|-------------|---------|-------------|
| Client → Server | `/app/chat.send` | `SendMessageCommand` | Owner sends a message |
| Server → Client | `/user/queue/messages` | `MessageEvent` | Delivered to both sender (ACK) and recipient (push) |
| Server → Client | `/user/queue/errors` | `ErrorEvent` | Rate limit exceeded or validation failure |

**SendMessageCommand fields**: `matchId: UUID`, `content: String`

**MessageEvent fields**: `id: UUID`, `matchId: UUID`, `senderOwnerId: UUID`, `senderDogName: String`, `content: String`, `sentAt: Instant`

**ErrorEvent fields**: `code: String` (e.g. `RATE_LIMIT_EXCEEDED`, `ACCESS_DENIED`, `VALIDATION_ERROR`), `message: String`, `retryAfterMs: Long?`

##### Event Contract
- Published events: `MessageEvent` to `/user/queue/messages` (recipient), `MessageEvent` to `/user/queue/messages` (sender ACK), `ErrorEvent` to `/user/queue/errors`
- Ordering guarantee: publish occurs after successful DB persist; no guarantee of delivery to offline clients (messages are recoverable via REST history)

---

### Domain: chat/config

#### JwtChannelInterceptor

| Field | Detail |
|-------|--------|
| Intent | Authenticate STOMP CONNECT frame using JWT; set `Principal` on the WebSocket session |
| Requirements | 7.2, 7.3, 8.1, 8.2 |

**Responsibilities & Constraints**
- Intercepts only `StompCommand.CONNECT` messages
- Extracts `Authorization` header from `StompHeaderAccessor`; validates JWT; calls `accessor.setUser(principal)` where principal username = owner UUID string
- Registered on inbound channel with `@Order(Ordered.HIGHEST_PRECEDENCE + 99)` (before Spring Security interceptors)
- Rejects connection with `MessageDeliveryException` (closes WS with 4001) if JWT is absent, expired, or invalid

**Dependencies**
- External: auth0 java-jwt 4.x — JWT verification (P0)

**Contracts**: Service [x]

**Implementation Notes**
- Integration: must be registered in `WebSocketSecurityConfig` without `@EnableWebSocketSecurity` (CSRF must be off for stateless JWT)
- Risk: Jackson dual-version conflict in pom.xml may surface as STOMP serialization failures at runtime; see research.md

#### WebSocketConfig

| Field | Detail |
|-------|--------|
| Intent | Register STOMP WebSocket endpoint and configure in-memory message broker |
| Requirements | 7.1 |

**Contracts**: (configuration only — no service/API contract)

**Configuration Summary**:
- WebSocket endpoint: `/ws` (SockJS fallback enabled for browser compatibility)
- App destination prefix: `/app`
- Message broker destination prefix: `/queue`
- User destination prefix: `/user`
- Tomcat text buffer: `server.tomcat.websocket.max-text-message-buffer-size=65536` in `application.yaml`

#### SecurityConfig

| Field | Detail |
|-------|--------|
| Intent | Configure Spring Security HTTP filter chain with JWT stateless auth; permit existing endpoints |
| Requirements | 8.1, 8.2 |

**Contracts**: (configuration only)

**Configuration Summary**:
- Stateless session management (no HTTP session)
- `OncePerRequestFilter` validates `Authorization: Bearer <jwt>` on each request
- Existing endpoints (`/api/v1/matches/**`, `/api/v1/support/**`) permitted without auth during transition (to be tightened when those domains add auth)
- All `/api/v1/chat/**` endpoints require authentication
- WebSocket handshake endpoint `/ws/**` permitted at HTTP level (auth handled by `JwtChannelInterceptor`)

---

## Data Models

### Domain Model

- **`ChatThread`** — aggregate root. Owns the participant invariant (exactly two owner IDs) and inbox metadata. Creation is triggered by `MatchService`; deletion cascades to all messages.
- **`ChatMessage`** — entity within `ChatThread`. Immutable after creation (no update operations). Content is a bounded value object (≤ 2,000 chars, non-blank).
- **Invariant**: A `ChatMessage` always belongs to exactly one `ChatThread`. A `ChatThread` always has exactly two distinct owner IDs (`ownerAId ≠ ownerBId`).
- **Domain event** (conceptual): `MessageSentEvent` — carries `matchId`, `recipientOwnerId`, `MessageEvent` payload — used internally between `ChatService` and `SimpMessagingTemplate`.

### Logical Data Model

```
ChatThread (1) ──────< (N) ChatMessage
  matchId : UUID [unique]     threadId : UUID [FK]
  ownerAId : UUID             senderOwnerId : UUID
  ownerBId : UUID             content : String
  lastMessagePreview : String sentAt : Instant
  lastMessageAt : Instant
  createdAt : Instant
```

Cardinality: one thread per match; unbounded messages per thread.

### Physical Data Model

```sql
-- Liquibase changeset: create_chat_schema

CREATE TABLE chat_thread (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id              UUID         NOT NULL UNIQUE,   -- loose ref; no FK until match table exists
    owner_a_id            UUID         NOT NULL,
    owner_b_id            UUID         NOT NULL,
    last_message_preview  VARCHAR(100),
    last_message_at       TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_thread_match_id   ON chat_thread(match_id);
CREATE INDEX idx_chat_thread_owner_a_id ON chat_thread(owner_a_id);
CREATE INDEX idx_chat_thread_owner_b_id ON chat_thread(owner_b_id);

CREATE TABLE chat_message (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id         UUID         NOT NULL REFERENCES chat_thread(id) ON DELETE CASCADE,
    sender_owner_id   UUID         NOT NULL,
    content           VARCHAR(2000) NOT NULL,
    sent_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_message_thread_sent ON chat_message(thread_id, sent_at ASC);
```

**Rollback**: `DROP TABLE chat_message; DROP TABLE chat_thread;` — must be included in Liquibase changeset rollback block.

### Data Contracts & Integration

**API Data Transfer**

All REST responses serialized as JSON via Jackson 3. Timestamps as ISO-8601 `Instant` strings.

Validation at REST boundary (Spring Validation):
- `matchId` — valid UUID format
- `page` ≥ 0, `size` 1–100 (default 50)

**STOMP Payloads**: serialized as JSON via Jackson 3's STOMP message converter. `SendMessageCommand.content` validated in `ChatWebSocketController` before `ChatService` call (blank check deferred to service).

---

## Error Handling

### Error Strategy

Validate at the controller/interceptor boundary; throw domain exceptions from `ChatService`; map to HTTP status or STOMP error event in the presentation layer.

### Error Categories and Responses

| Category | Error | Channel | Response |
|----------|-------|---------|----------|
| Auth | Missing/expired JWT (REST) | HTTP | 401 `{ "error": "Unauthorized" }` |
| Auth | Missing/expired JWT (WebSocket) | WS close | 4001 connection rejected |
| Access | Non-participant access (REST) | HTTP | 403 `{ "error": "Forbidden" }` |
| Access | Non-participant access (WebSocket) | STOMP error | `ErrorEvent(code=ACCESS_DENIED)` to `/user/queue/errors` |
| Not Found | Thread deleted or never existed | HTTP | 404 `{ "error": "Chat thread not found" }` |
| Validation | Blank or >2000 char message | STOMP error | `ErrorEvent(code=VALIDATION_ERROR, message=...)` |
| Rate Limit | Bucket exhausted | STOMP error | `ErrorEvent(code=RATE_LIMIT_EXCEEDED, retryAfterMs=...)` |
| System | Unexpected persistence failure | HTTP / STOMP | 500 / `ErrorEvent(code=INTERNAL_ERROR)`; logged with trace ID |

### Monitoring

- All 5xx errors logged via `KotlinLogging.logger {}` with trace ID (MDC); message content never included in log entries (satisfies 8.5).
- WebSocket connection count and message-send rate tracked via Spring Boot Actuator metrics.

---

## Testing Strategy

### Unit Tests

- `ChatService`: access control (`validateAccess` — participant vs non-participant), `sendMessage` content validation (blank, exactly 2000 chars, 2001 chars), `deleteThread` cascading behaviour
- `RateLimitService`: token consumed successfully within limit; `RateLimitExceededException` thrown when bucket exhausted; bucket refills after window

### Integration Tests

- `ChatMessageRepository` + `ChatThreadRepository` with TestContainers PostgreSQL: create thread, persist messages, paginated history query, cascade delete on thread removal
- `ChatService.sendMessage` end-to-end: persist → update thread → verify `SimpMessagingTemplate` called with correct user and destination (MockK)
- `ChatService.deleteThread`: verify both thread and messages deleted in single transaction

### WebSocket Tests

- `StompClient` integration test (Spring's `WebSocketStompClient`): authenticated CONNECT → send to `/app/chat.send` → receive on `/user/queue/messages`
- Unauthorized CONNECT (no JWT) → connection rejected
- Non-participant send → `ErrorEvent` on `/user/queue/errors`
- Rate-limit breach (31st message in window) → `ErrorEvent(code=RATE_LIMIT_EXCEEDED)`

### Security Tests

- REST: `GET /api/v1/chat/threads` without JWT → 401
- REST: `GET /api/v1/chat/threads/{matchId}/messages` for a thread the caller does not participate in → 403
- REST: access deleted thread → 404

---

## Security Considerations

- **JWT validation is dual-layer**: HTTP filter chain (REST) and `JwtChannelInterceptor` (WebSocket CONNECT). A valid token is required in both paths — no bypass through the WebSocket upgrade.
- **Participant check in service layer**: `ChatService` re-verifies ownership on every read/write, independently of the controller. Protects against accidental future refactoring that bypasses controller-level guards.
- **Message content never logged**: trace IDs reference internal request context only; no message body in any log statement.
- **No CSRF for WebSocket**: CSRF explicitly disabled for the stateless JWT flow; see research.md for Spring Security issue context.
- **Existing endpoints unprotected during transition**: `permit` rules in `SecurityConfig` for `support` and `matches` endpoints until those domains add auth — document as technical debt.

## Performance & Scalability

- **Inbox query**: uses indexed columns `owner_a_id` / `owner_b_id`; sorted by `last_message_at` (not a full table scan)
- **History query**: `(thread_id, sent_at ASC)` composite index makes keyset pagination efficient; default page size 50 avoids large result sets
- **In-memory STOMP broker**: sufficient for single-node v1; upgrade path to RabbitMQ STOMP relay requires adding `configureMessageBroker` relay config only — no controller or service changes
- **Bucket4j in-memory**: acceptable for single node; upgrade to Redis-backed buckets via same API if multi-node rate limiting is needed
