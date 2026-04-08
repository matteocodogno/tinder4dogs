# Implementation Plan

- [ ] 1. Set up dependencies and database schema
- [ ] 1.1 Add new Maven dependencies to pom.xml
  - Add `spring-boot-starter-websocket` for STOMP/WebSocket support
  - Add `spring-boot-starter-security` for JWT HTTP filter chain
  - Add `com.auth0:java-jwt:4.x` for JWT token creation and validation
  - Add `com.bucket4j:bucket4j-core:8.17.0` for per-owner rate limiting
  - Add `org.liquibase:liquibase-core` and configure it in `application.yaml` (disable `ddl-auto`)
  - _Requirements: 7.1, 8.1_

- [ ] 1.2 Create Liquibase migration for chat schema
  - Create the `chat_thread` table: UUID primary key, unique `match_id`, two owner ID columns, last-message preview and timestamp, created-at timestamp
  - Create the `chat_message` table: UUID primary key, foreign key to `chat_thread` with `ON DELETE CASCADE`, sender owner ID, content (max 2000 chars), sent-at timestamp
  - Add indexes: `match_id` on `chat_thread`; both owner ID columns on `chat_thread`; composite `(thread_id, sent_at ASC)` on `chat_message`
  - Include a functional rollback block that drops both tables in reverse order
  - _Requirements: 2.1, 4.5, 5.1, 5.2_

- [ ] 2. Implement JPA entities and repositories
- [ ] 2.1 (P) Implement the ChatThread entity and its repository
  - Map the `chat_thread` table as a JPA entity with all columns from the Liquibase migration
  - Add repository method to look up a thread by `matchId`
  - Add repository method to find all threads where the caller is either owner (for inbox queries)
  - _Requirements: 1.3, 6.1, 6.3_

- [ ] 2.2 (P) Implement the ChatMessage entity and its repository
  - Map the `chat_message` table as a JPA entity
  - Add repository method to fetch messages by thread ID ordered by `sentAt` ascending, with pagination support
  - Add repository method to bulk-delete all messages for a given thread ID
  - _Requirements: 2.1, 4.1, 4.3, 5.1_

- [ ] 3. Implement core chat service
- [ ] 3.1 Implement thread lifecycle in ChatService
  - Implement `createThread`: persist a new `ChatThread` given a match ID and two owner IDs; guard against duplicate threads for the same match ID
  - Implement `deleteThread`: delete all messages for the thread and then delete the thread itself in a single transaction, using the `ON DELETE CASCADE` FK as a safety net
  - Raise a not-found exception when `deleteThread` is called for an unknown match ID
  - _Requirements: 1.3, 5.1, 5.2, 5.3, 5.4_

- [ ] 3.2 Implement message send in ChatService
  - Implement internal `validateAccess` helper that confirms the requesting owner is a participant of the thread; throw `AccessDeniedException` otherwise
  - Implement `sendMessage`: validate access, reject blank content and content exceeding 2,000 characters, persist the message, update the thread's `lastMessagePreview` and `lastMessageAt`, then dispatch the message event to the recipient via `SimpMessagingTemplate` — persist before dispatch
  - _Requirements: 1.1, 1.2, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 8.3_

- [ ] 3.3 Implement read operations in ChatService
  - Implement `getMessageHistory`: validate that the requesting owner is a participant, then return a paginated page of messages in ascending chronological order
  - Implement `getInbox`: return all threads where the owner participates, each enriched with matched-dog name, photo URL, last message preview, and last message timestamp; sort by most recent message first
  - Return 403 when a non-participant requests history; return empty list (not an error) when the owner has no threads
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 6.1, 6.2, 6.3, 6.4, 6.5, 8.3_

- [ ] 3.4 (P) Implement RateLimitService
  - Implement the `consume(ownerId)` method using a Bucket4j token bucket: capacity 30, refill 30 tokens per 60 seconds
  - Store buckets in a `ConcurrentHashMap` keyed by owner UUID; create on first access
  - Throw `RateLimitExceededException` containing `retryAfterMs` when the bucket is exhausted
  - Expose the messages-per-minute value as a configurable `application.yaml` property (`tinder4dogs.chat.rate-limit.messages-per-minute`) defaulting to 30
  - Can be developed in parallel with tasks 3.1–3.3 as it is a standalone class with no dependency on ChatService
  - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 4. Implement security and WebSocket infrastructure
- [ ] 4.1 (P) Implement JWT utility and HTTP security filter chain
  - Implement a JWT utility that verifies token signature, expiry, and extracts the owner UUID as the principal name
  - Implement a `OncePerRequestFilter` that reads the `Authorization: Bearer` header, validates the JWT, and sets the Spring Security authentication context
  - Configure `SecurityFilterChain`: stateless session, apply the JWT filter, require authentication on `/api/v1/chat/**`, and permit existing endpoints (`/api/v1/matches/**`, `/api/v1/support/**`) without auth during transition
  - _Requirements: 8.1, 8.2_

- [ ] 4.2 (P) Configure the WebSocket STOMP broker
  - Register the `/ws` WebSocket endpoint with SockJS fallback enabled
  - Configure the in-memory STOMP broker: app destination prefix `/app`, broker prefix `/queue`, user destination prefix `/user`
  - Enable STOMP heartbeat (10 s send / 10 s receive) to prevent proxy-induced connection drops
  - Add Tomcat WebSocket text buffer size configuration (`65536` bytes) to `application.yaml`
  - Can be developed in parallel with 4.1 — no shared configuration files
  - _Requirements: 7.1_

- [ ] 4.3 Implement JwtChannelInterceptor for STOMP authentication
  - Intercept only `CONNECT` frames; extract the `Authorization` header from the STOMP headers
  - Validate the JWT using the utility from 4.1; set the authenticated principal on the accessor so Spring routes per-user messages correctly
  - Reject the connection (throw `MessageDeliveryException`) if the token is absent, expired, or invalid
  - Register the interceptor on the inbound channel with `@Order(Ordered.HIGHEST_PRECEDENCE + 99)` ahead of Spring Security interceptors; disable CSRF manually (no `@EnableWebSocketSecurity`)
  - Depends on JWT utility (4.1) and WebSocket config (4.2)
  - _Requirements: 7.2, 7.3, 8.1, 8.2_

- [ ] 5. (P) Implement REST API endpoints
  - Implement `GET /api/v1/chat/threads` — returns the inbox list; delegates to `ChatService.getInbox` using the authenticated owner ID from the security context
  - Implement `GET /api/v1/chat/threads/{matchId}/messages` — accepts `page` (default 0) and `size` (default 50, max 100) query parameters; delegates to `ChatService.getMessageHistory`
  - Map `AccessDeniedException` to 403, not-found exceptions to 404, and unauthenticated requests are handled upstream by the security filter
  - Can be developed in parallel with task 6 — separate controller class, no shared files
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 6.1, 6.2, 6.3, 6.4, 6.5, 8.1, 8.2, 8.3_

- [ ] 6. (P) Implement WebSocket messaging controller
  - Implement `@MessageMapping("/chat.send")` handler: extract the authenticated principal (set by `JwtChannelInterceptor`), call `RateLimitService.consume`, then delegate to `ChatService.sendMessage`
  - On success: push a `MessageEvent` to the sender's `/user/queue/messages` (ACK) and, via `ChatService`, to the recipient's `/user/queue/messages` (push)
  - On `RateLimitExceededException`: send an `ErrorEvent(code=RATE_LIMIT_EXCEEDED, retryAfterMs=...)` to the caller's `/user/queue/errors`
  - On `AccessDeniedException` or validation failure: send an appropriate `ErrorEvent` to `/user/queue/errors`
  - Can be developed in parallel with task 5 — separate controller class, no shared files
  - Depends on tasks 3, 4.2, and 4.3
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 7.1, 7.2, 7.4, 7.5, 7.6, 8.1, 8.5_

- [ ] 7. Wire MatchService integration and finalize configuration
- [ ] 7.1 Expose chat thread lifecycle hook for the match domain
  - Add a `createThread` call site in the match domain (in the existing or future persistent `MatchService`) to be triggered when a mutual match is confirmed; wire it to `ChatService.createThread`
  - Add a `deleteThread` call site in the match domain to be triggered when a match is deleted; wire it to `ChatService.deleteThread`
  - If a persistent `MatchService` does not yet exist, create a placeholder integration point (a clearly named method stub or TODO marker at the expected call site) so the boundary is established
  - _Requirements: 1.3, 5.1, 5.2, 5.3, 5.4_

- [ ] 7.2 Finalize application configuration
  - Add `tinder4dogs.chat.rate-limit.messages-per-minute: 30` to `application.yaml`
  - Add `server.tomcat.websocket.max-text-message-buffer-size: 65536` to `application.yaml`
  - Add Liquibase `change-log` path and disable `spring.jpa.hibernate.ddl-auto` (set to `validate`)
  - _Requirements: 3.1, 7.1_

- [ ] 8. Write tests
- [ ] 8.1 (P) Unit test ChatService and RateLimitService
  - Test `validateAccess`: participant owner passes, non-participant throws `AccessDeniedException`
  - Test `sendMessage` validation: blank content rejected, exactly 2,000 chars accepted, 2,001 chars rejected
  - Test `deleteThread`: verify messages and thread are deleted; calling again on a missing thread raises not-found exception
  - Test `RateLimitService`: 30 consecutive `consume` calls succeed; 31st throws `RateLimitExceededException` with a positive `retryAfterMs`
  - Use MockK to stub repository and `SimpMessagingTemplate` dependencies
  - _Requirements: 1.1, 1.2, 1.4, 2.3, 2.4, 3.1, 3.2, 5.3, 5.4_

- [ ] 8.2 (P) Integration test repositories and ChatService with TestContainers
  - Spin up a PostgreSQL container; run Liquibase migrations before tests
  - Test persist-and-retrieve round-trip for `ChatMessage` and `ChatThread`
  - Test paginated `getMessageHistory` returns messages in ascending `sentAt` order
  - Test cascade delete: deleting a `ChatThread` removes all its `ChatMessage` rows
  - Test `ChatService.sendMessage` end-to-end: persists message, updates thread `lastMessageAt`, and calls `SimpMessagingTemplate.convertAndSendToUser` with the recipient's owner ID
  - _Requirements: 2.1, 2.2, 4.1, 4.3, 5.1, 5.2_

- [ ] 8.3 (P) WebSocket integration test with StompClient
  - Use Spring's `WebSocketStompClient` to connect with a valid JWT CONNECT header; send to `/app/chat.send`; assert `MessageEvent` received on `/user/queue/messages`
  - Assert that connecting without a JWT (or with an expired token) is rejected
  - Assert that a non-participant send results in an `ErrorEvent(code=ACCESS_DENIED)` on `/user/queue/errors`
  - Assert that the 31st message in a rate-limit window produces an `ErrorEvent(code=RATE_LIMIT_EXCEEDED)` with a positive `retryAfterMs`
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 3.2, 8.1, 8.2_

- [ ]* 8.4 (P) REST security and edge-case tests
  - `GET /api/v1/chat/threads` without JWT → 401
  - `GET /api/v1/chat/threads/{matchId}/messages` for a thread the caller does not participate in → 403
  - `GET /api/v1/chat/threads/{matchId}/messages` after the match is deleted → 404
  - `GET /api/v1/chat/threads` when the owner has no matches → 200 with empty list
  - Verify message content is not present in any logged output (mock appender check)
  - _Requirements: 4.4, 5.3, 6.4, 8.1, 8.2, 8.5_
