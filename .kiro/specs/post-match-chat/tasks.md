# Implementation Plan — post-match-chat

> No `(P)` markers: this is a single vertical slice — sub-tasks share the chat service file and each other's contracts (2.3 consumes the message-accepted contract 2.2 defines), so order is the dependency mechanism.

- [ ] 1. Foundation: chat storage and contracts
- [x] 1.1 Chat and message storage
  - Create the Liquibase changeset file `005-create-chat.sql` (two changesets — chat, message — each with `--comment` and explicit `--rollback`), with the canonical-pair unique constraint, the pair-order check, both foreign keys, the `(chat_id, id)` index, and `VARCHAR(2000)` text.
  - Register the file in the Liquibase master index (append only; never edit existing entries).
  - Add the `Chat` and `Message` entities in the chat package: plain `Long` dog references (no associations), `Instant` timestamps assigned by the service, mutable-class style matching the existing `Dog` entity.
  - Observable: `mise run build` passes, and with `mise run db` up the application starts through Liquibase and Hibernate `ddl-auto: validate` without errors.
  - _Boundary: chat storage_
  - _Requirements: 1.1, 1.2, 2.1, 2.2_

- [x] 1.2 Cross-feature event contract and repositories
  - Add the `CreateChatEvent` contract class with KDoc stating the emission semantics: fired once at the end of a successful swipe + mutual match, dog ids unordered, both dogs existing and distinct; idempotency is consumer-side.
  - Add `ChatRepository`: canonical pair lookup, a transaction-scoped advisory-lock method (`pg_advisory_xact_lock` via a native query), and the participant list query ordered by last activity (`coalesce` of last message time and creation time, descending).
  - Add `MessageRepository`: history in acceptance order, watermark catch-up fetch, newest-message lookup.
  - Observable: `mise run build` passes; the contract KDoc names every obligation the future emitter must meet.
  - _Boundary: chat contract, chat repositories_
  - _Requirements: 1.1, 1.2, 3.2, 3.4, 4.1, 5.1_

- [ ] 2. Core: domain rules and live delivery
- [x] 2.1 Chat creation on a mutual match
  - Implement the chat service's match listener: canonical `(min, max)` pair, lookup-first idempotency with the unique-constraint violation as the concurrent backstop (re-fetch on violation), dog existence via the dog repository, distinct-dog validation.
  - Reject invalid events by logging WARN with the reason and returning normally — a committed match flow must never fail because chat creation declined.
  - Unit tests (Mockito for repositories): pair stored in canonical order; duplicate event reuses the existing chat without a second save; event for an unknown dog is rejected without saving; event naming the same dog twice is rejected without saving.
  - Observable: the new creation tests pass under `mise run test` alongside the existing suite.
  - _Boundary: ChatService_
  - _Requirements: 1.1, 1.2, 1.3_

- [ ] 2.2 Messaging rules, access, history, and discovery
  - Implement `visibleChat` (null when the chat is absent or the dog is not a participant — the single access rule for send, read, and listen), `send` (advisory lock before insert; `require` sender participation and valid text; update the chat's last-activity timestamp; publish the internal message-accepted event in the same transaction; declare `MAX_MESSAGE_LENGTH`), `history` (acceptance order), and `chatsOf` (most recently active first).
  - `send` is the only message write path: no update or delete operation exists anywhere in the package.
  - Unit tests: participant send records sender, time, and publishes the accepted event with the persisted id and updates last activity; the per-chat lock is acquired before inserting; blank or over-long text is rejected; a chat is not visible to a non-participant; history returns messages in acceptance order; a dog's chats are ordered most recently active first (including a chat with no messages yet).
  - Observable: the messaging tests pass under `mise run test`.
  - _Boundary: ChatService_
  - _Requirements: 1.4, 2.1, 2.2, 2.3, 2.4, 4.1, 4.3, 5.1_

- [ ] 2.3 Live delivery over SSE
  - Implement the delivery service: in-memory listener registry per chat (emitter, listener dog id, watermark, per-listener lock), stream opening (watermark seeded from `Last-Event-ID` when present, otherwise the chat's newest message id; emitters created with no timeout), and the transaction-after-commit handler that dispatches pushes to a bounded Spring-managed executor and returns immediately.
  - The push worker re-fetches everything above the watermark in id order and sends each message as an SSE event carrying the message id — never re-sending ids at or below the watermark; dead emitters are unregistered on completion, timeout, or error.
  - Unit tests (SseEmitter and executor mocked, executor running pushes synchronously): the listener receives the accepted message; out-of-order accepted events are delivered in id order; a listener never receives a message twice; reconnecting with `Last-Event-ID` replays only newer messages; a fresh stream starts at the newest message; a failing emitter is unregistered without propagating the failure.
  - Observable: the delivery tests pass under `mise run test`.
  - _Boundary: ChatDeliveryService_
  - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 3. Chat REST and stream endpoints (integration)
  - Implement the controller with its request/response DTOs: message send (bean validation on the request shape → 400; chat visibility check → 404; 201 with the message response), history for a participating dog, chat discovery for a dog (dog existence via the dog repository; summaries enriched with the other dog's id and name and the last-activity time), and the SSE stream endpoint (participant check, then stream opening; optional `Last-Event-ID`).
  - No domain rules in the controller; unknown chat and non-participant both answer 404 (non-disclosure); response DTOs built by a companion factory.
  - Observable: with the database and application running, all four endpoints answer per the design's API contract table (201 + persisted message; ordered history; ordered summaries; a live stream event for a posted message; 404 for a non-participant).
  - _Boundary: ChatController_
  - _Requirements: 1.4, 2.1, 2.2, 2.3, 3.1, 3.4, 4.1, 4.2, 4.3, 5.1, 5.2_

- [ ] 4. Full build, test suite, and live smoke (validation)
  - Run `mise run build` and `mise run test`: both green, existing dog and match behavior untouched.
  - With `mise run db` and the application running: Liquibase applies the chat changeset; insert one chat row via SQL honoring the pair-order and unique constraints (no emitter exists yet — this SQL insert is the documented stand-in for the future match flow).
  - Smoke the contract with two terminals: open the stream as one dog of the chat, post a message as the other dog, and observe the SSE event arrive carrying the message id; refetch history and confirm ordering; list the dog's chats and confirm the most recently active chat comes first; reconnect the stream with `Last-Event-ID` and confirm only newer messages replay; confirm a non-participant dog gets 404 on message, history, and stream.
  - Observable: every smoke step behaves per the requirements — live delivery, ordering, replay, and access rejection all verified against the running application.
  - _Boundary: chat slice_
  - _Requirements: 1.1, 1.2, 2.1, 3.1, 3.4, 4.1, 5.1_
## Implementation Notes

- Spring Boot 4.1 / Hibernate 7 in this repo does **not** convert camelCase to snake_case (implicit naming lowercases only: `dogAId` → `dogaid`). Every multi-word column needs an explicit `@Column(name = "snake_case")` or `ddl-auto: validate` fails with `missing column`.
- Host port 5432 may be held by another project's compose stack; for startup verification use an ephemeral `postgres:18-alpine` on a spare port plus the `DATABASE_URL` env override — the config is env-first by design.
