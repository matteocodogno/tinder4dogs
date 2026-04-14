# Implementation Plan

- [ ] 1. Set up project dependencies and database schema <!-- gh:#28 -->
- [x] 1.1 Add required Maven dependencies <!-- gh:#36 -->
  - Add the WebSocket/STOMP starter to enable real-time messaging infrastructure
  - Add the Spring Security starter for JWT-based HTTP authentication
  - Add the Auth0 Java JWT library for token signature verification and claims extraction
  - Add the Bucket4j core library for in-memory token-bucket rate limiting
  - Add Liquibase core and configure it as the schema migration tool; disable Hibernate DDL auto-update
  - _Requirements: 7.1, 8.1_

- [ ] 1.2 Create Liquibase migration for the chat database schema <!-- gh:#37 -->
  - Define the `chat_thread` table: UUID primary key, unique match reference, two participant owner ID columns, last-message preview (up to 100 characters), last-message timestamp, and creation timestamp
  - Define the `chat_message` table: UUID primary key, foreign key to `chat_thread` with cascade delete, sender owner ID, content column capped at 2,000 characters, and sent-at timestamp
  - Add indexes on the match reference and both owner ID columns of `chat_thread`; add a composite `(thread_id, sent_at ASC)` index on `chat_message` for efficient paginated history queries
  - Include a rollback block that drops both tables in reverse dependency order
  - _Requirements: 2.1, 4.5, 5.1, 5.2_

- [ ] 2. Implement the chat data access layer <!-- gh:#29 -->
- [ ] 2.1 (P) Implement the chat thread entity and repository <!-- gh:#38 -->
  - Map the `chat_thread` table as a persistent entity with all columns from the migration
  - Provide a query to look up a thread by its associated match reference
  - Provide a query to find all threads where a given owner is either of the two participants (used for inbox listing)
  - _Requirements: 1.3, 6.1, 6.3_

- [ ] 2.2 (P) Implement the chat message entity and repository <!-- gh:#39 -->
  - Map the `chat_message` table as a persistent entity
  - Provide a paginated query to retrieve all messages for a given thread ordered by sent-at ascending
  - Provide a bulk-delete query to remove all messages for a given thread by ID
  - _Requirements: 2.1, 4.1, 4.3, 5.1_

- [ ] 3. Implement core chat service business logic <!-- gh:#30 -->
- [ ] 3.1 Implement chat thread lifecycle <!-- gh:#40 -->
  - Implement thread creation: persist a new thread given a match reference and two participant owner IDs; prevent duplicate threads for the same match
  - Implement thread deletion: remove all messages and then the thread itself within a single transaction; rely on the cascade FK as a safety net; treat deletion of an unknown match as a not-found condition
  - _Requirements: 1.3, 5.1, 5.2, 5.3, 5.4_

- [ ] 3.2 Implement message sending with access control and content validation <!-- gh:#41 -->
  - Enforce the participant invariant before any read or write: verify the requesting owner is one of the two participants; raise an access-denied error for non-participants
  - Reject messages that are blank or exceed 2,000 characters before any database write
  - Persist the accepted message with sender owner ID, thread reference, content, and UTC timestamp; atomically update the thread's last-message preview and timestamp
  - Dispatch the persisted message to the recipient over the STOMP broker only after the database write succeeds (persist-before-push)
  - _Requirements: 1.1, 1.2, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 8.3_

- [ ] 3.3 Implement message history retrieval and inbox listing <!-- gh:#42 -->
  - Implement paginated message history: verify participant access, then return messages in ascending chronological order with sender owner ID, content, and UTC timestamp
  - Implement inbox listing: return all threads the owner participates in, each including matched-dog name, photo URL, last-message preview, and last-message timestamp; sort by most recent message first
  - Return 403 when a non-participant requests history; return an empty list (not an error) when the owner has no active threads; include threads with no messages in the inbox with null preview and null timestamp
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 6.1, 6.2, 6.3, 6.4, 6.5, 8.3_

- [ ] 3.4 (P) Implement per-owner rate limiting <!-- gh:#43 -->
  - Implement a rate-limit check that consumes one token from the requesting owner's bucket before allowing a message send; create a bucket for each owner on first access
  - Configure the token bucket: capacity 30 tokens, refill 30 tokens per 60 seconds
  - Signal exhaustion by raising a rate-limit error that includes the number of milliseconds until the next token is available
  - Expose the messages-per-minute threshold as a configurable application property (`tinder4dogs.chat.rate-limit.messages-per-minute`) defaulting to 30
  - Can be developed in parallel with tasks 3.1–3.3; it has no dependency on the chat service
  - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 4. Implement security and WebSocket infrastructure <!-- gh:#31 -->
- [ ] 4.1 (P) Implement JWT authentication for HTTP requests <!-- gh:#44 -->
  - Implement a JWT verification utility that checks token signature, expiry, and extracts the owner UUID as the authenticated principal
  - Implement a per-request filter that reads the `Authorization: Bearer` header, validates the JWT, and populates the security context with the authenticated owner identity
  - Configure the HTTP security filter chain with stateless session management; require authentication on all `/api/v1/chat/**` endpoints; permit existing match and support endpoints without auth during the transition period; allow WebSocket upgrade requests at the HTTP level (auth is handled at the STOMP layer)
  - _Requirements: 8.1, 8.2_

- [ ] 4.2 (P) Configure the STOMP WebSocket broker <!-- gh:#45 -->
  - Register the `/ws` endpoint as the WebSocket handshake point with SockJS fallback enabled for environments that block raw WebSocket upgrades
  - Configure an in-memory STOMP message broker with application prefix `/app`, broker prefix `/queue`, and per-user destination prefix `/user`
  - Enable STOMP heartbeat frames (10 s send / 10 s receive) to prevent proxy-induced connection timeouts
  - Set the Tomcat WebSocket text buffer size to 65,536 bytes in `application.yaml`
  - Can be developed in parallel with 4.1; no shared configuration files
  - _Requirements: 7.1_

- [ ] 4.3 Implement JWT authentication for WebSocket connections <!-- gh:#46 -->
  - Intercept only the STOMP CONNECT frame on the inbound channel; extract the JWT from the STOMP authorization header; validate it using the utility from 4.1; set the authenticated owner identity as the STOMP principal so per-user message routing works correctly
  - Reject the connection (close the WebSocket with code 4001) when the token is absent, expired, or invalid
  - Register the interceptor with higher precedence than Spring Security's own interceptors; disable CSRF for the stateless JWT flow
  - Depends on JWT utility (4.1) and WebSocket broker config (4.2)
  - _Requirements: 7.2, 7.3, 8.1, 8.2_

- [ ] 5. (P) Implement REST chat endpoints <!-- gh:#32 -->
  - Expose `GET /api/v1/chat/threads` for inbox listing; extract the authenticated owner from the security context and delegate to the chat service
  - Expose `GET /api/v1/chat/threads/{matchId}/messages` for paginated message history; accept `page` (default 0) and `size` (default 50, max 100) query parameters; delegate to the chat service
  - Map access-denied errors to 403, not-found errors to 404; unauthenticated requests are rejected upstream by the security filter
  - Can be developed in parallel with task 6; the REST controller and WebSocket controller are separate classes with no shared files
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 6.1, 6.2, 6.3, 6.4, 6.5, 8.1, 8.2, 8.3_

- [ ] 6. (P) Implement the real-time WebSocket messaging handler <!-- gh:#33 -->
  - Implement a STOMP message handler on `/app/chat.send`; extract the authenticated owner identity set by the JWT interceptor; invoke the rate limiter before any business logic
  - On a successful rate-limit check, delegate to the chat service to persist and dispatch the message; push a confirmation event to the sender's `/user/queue/messages`
  - On a rate-limit breach, send an error event to the caller's `/user/queue/errors` with code `RATE_LIMIT_EXCEEDED` and the retry-after duration in milliseconds
  - On an access-denied or content-validation failure, send an appropriate error event to `/user/queue/errors`
  - Can be developed in parallel with task 5; depends on tasks 3, 4.2, and 4.3
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 7.1, 7.2, 7.4, 7.5, 7.6, 8.1, 8.5_

- [ ] 7. Wire match domain integration and finalize configuration <!-- gh:#34 -->
- [ ] 7.1 Hook up chat thread lifecycle to the match domain <!-- gh:#47 -->
  - Add a thread-creation call at the point in the match domain where a mutual match is confirmed; pass the match reference and both participant owner IDs to the chat service
  - Add a thread-deletion call at the point where a match is removed; ensure the deletion is invoked before or within the same transaction as the match removal
  - If a persistent match service does not yet exist, establish a clearly named placeholder at the expected call site to make the integration boundary visible
  - _Requirements: 1.3, 5.1, 5.2, 5.3, 5.4_

- [ ] 7.2 Finalize application configuration <!-- gh:#48 -->
  - Add `tinder4dogs.chat.rate-limit.messages-per-minute: 30` to `application.yaml`
  - Add `server.tomcat.websocket.max-text-message-buffer-size: 65536` to `application.yaml`
  - Configure the Liquibase change-log path; set `spring.jpa.hibernate.ddl-auto` to `validate` to prevent accidental schema drift
  - _Requirements: 3.1, 7.1_

- [ ] 8. Write automated tests <!-- gh:#35 -->
- [ ] 8.1 (P) Unit test the chat service and rate limiter <!-- gh:#49 -->
  - Test the participant access check: a participant owner passes; a non-participant raises an access-denied error
  - Test message content validation: blank content is rejected; exactly 2,000 characters is accepted; 2,001 characters is rejected
  - Test thread deletion: both messages and the thread are removed; calling delete again on an unknown thread raises a not-found error
  - Test the rate limiter: 30 consecutive calls succeed; the 31st raises a rate-limit error with a positive retry-after value
  - Stub repository and messaging broker dependencies
  - _Requirements: 1.1, 1.2, 1.4, 2.3, 2.4, 3.1, 3.2, 5.3, 5.4_

- [ ] 8.2 (P) Integration test the data layer and chat service with a real database <!-- gh:#50 -->
  - Spin up a PostgreSQL test container and run the Liquibase migration before tests
  - Verify a round-trip persist-and-retrieve for chat threads and messages
  - Verify paginated message history returns messages in ascending sent-at order
  - Verify that deleting a thread removes all its messages via the cascade constraint
  - Verify the full send-message flow: message persisted, thread last-message fields updated, and the broker push triggered with the correct recipient identity
  - _Requirements: 2.1, 2.2, 4.1, 4.3, 5.1, 5.2_

- [ ] 8.3 (P) WebSocket integration test with a live STOMP client <!-- gh:#51 -->
  - Connect with a valid JWT in the STOMP CONNECT header; send to `/app/chat.send`; assert a message event is received on `/user/queue/messages`
  - Assert that connecting without a JWT (or with an expired token) is rejected
  - Assert that a non-participant send produces an error event on `/user/queue/errors` with code `ACCESS_DENIED`
  - Assert that the 31st message in a rate-limit window produces an error event with code `RATE_LIMIT_EXCEEDED` and a positive retry-after value
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 3.2, 8.1, 8.2_

- [ ]* 8.4 (P) REST security and edge-case tests <!-- gh:#52 -->
  - Verify `GET /api/v1/chat/threads` without a JWT returns 401
  - Verify `GET /api/v1/chat/threads/{matchId}/messages` for a non-participant thread returns 403
  - Verify access to a deleted thread returns 404
  - Verify an owner with no active matches receives 200 with an empty list
  - Verify message content does not appear in any logged output
  - _Requirements: 4.4, 5.3, 6.4, 8.1, 8.2, 8.5_
