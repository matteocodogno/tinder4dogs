# Research & Design Decisions

## Summary
- **Feature**: `post-match-chat`
- **Discovery Scope**: Extension (light discovery) — a new vertical-slice package in an existing Spring Boot service, plus one capability the codebase does not have yet: live push.
- **Key Findings**:
  - Live delivery needs no new dependency: `SseEmitter` is part of `spring-webmvc`, already on the classpath via `spring-boot-starter-web` (unlike the Boot 4 Liquibase module trap). Verified against the Spring Framework 7 reference docs.
  - The `CREATE_CHAT` interface fits Spring's in-process application events: the emitter (F-07/F-08) does not exist yet, the monolith is the only consumer, and `@TransactionalEventListener(AFTER_COMMIT)` gives commit-safe delivery for free.
  - Ordering, no-duplicates, and reconnect replay are one problem — a per-listener watermark over monotonically increasing message ids — solved by one small mechanism, not three.
  - Design review (`/kiro-validate-design`) found a commit-order race: without per-chat send serialization the watermark can advance past an uncommitted lower id and skip a message on live streams. Fixed with an advisory lock plus executor-dispatched pushes.

## Research Log

### Live delivery channel
- **Context**: Requirement 3.1 demands delivery to a connected side within 1 s p95; steering forbids WebFlux and the stack is servlet MVC.
- **Sources Consulted**: Spring Framework 7 reference — *Asynchronous Requests* (https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html); steering `tech.md`.
- **Findings**: Servlet async + SSE streaming in annotated controllers is current, documented Framework 7 functionality; no separate Boot module or starter is involved. WebSocket would require `spring-boot-starter-websocket` and a messaging subprotocol for a push-only need; polling contradicts the live expectation and multiplies requests.
- **Implications**: Adopt SSE. Delivery is an in-process push after commit — orders of magnitude inside the 1 s budget at v1 scale.

### Event ingestion and post-commit delivery
- **Context**: `CREATE_CHAT` is the single interface to F-07/F-08 (not built yet); messages must never be pushed unless their transaction committed.
- **Sources Consulted**: Spring Framework reference — application events, transaction-bound events (https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html).
- **Findings**: `ApplicationEventPublisher` + `@EventListener` gives a synchronous, decoupled, in-process contract; `@TransactionalEventListener(AFTER_COMMIT)` runs only for committed transactions. A broker or webhook adds infrastructure with no current consumer.
- **Implications**: Chat owns the `CreateChatEvent` data class; the future match flow publishes it (dependency direction match → chat → dog, acyclic). Delivery listens to an internal `MessageAcceptedEvent` after commit. Both stop working if chat ever leaves this process — recorded as a revalidation trigger.

### Ordering and deduplication under concurrency
- **Context**: 3.2 (acceptance order) and 3.4 (no loss, no duplicates) must hold with concurrent sends and reconnecting clients; `SseEmitter.send` is not safe for unsynchronized concurrent use.
- **Findings**: Two AFTER_COMMIT listeners can fire out of id order on different threads. A per-listener watermark (highest message id sent) plus an id-ordered catch-up fetch (`chat_id = X and id > watermark order by id asc`) fixes ordering, prevents duplicates, and — seeded from the SSE `Last-Event-ID` header — provides reconnect replay. One mechanism, three guarantees.
- **Implications**: Delivery holds per-listener state (emitter, dog id, watermark, lock) and always re-fetches rather than trusting the event payload ordering.

### Test doubles for repository-backed services
- **Context**: `tech.md` pins unit-level tests: services instantiated directly, no Spring context, no database. `ChatService` cannot be pure — it needs three repositories and an event publisher.
- **Findings**: Mockito is already on the test classpath via `spring-boot-starter-test` (unused so far). `SseEmitter` is a non-final Java class — mockable for verifying ordered sends.
- **Implications**: Adopt Mockito for repository and publisher mocks; delivery tests mock `SseEmitter` and capture event ids.

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|--------------------|-------|
| SSE via `SseEmitter` | server-to-client stream over HTTP, servlet async | no new dependency; REST-consistent; `Last-Event-ID` replay built into the protocol | one-directional (fine: sending is a POST); proxies can buffer the stream | **selected** |
| WebSocket + STOMP | bidirectional messaging | richer protocol | new starter, subprotocol and broker concepts, overkill for push-only | rejected |
| Client polling | periodic GET | simplest | not live; load; violates the intent of 3.1 | rejected |
| In-process Spring events | `ApplicationEventPublisher` | zero infrastructure, transaction-aware, synchronous | in-process only; single point of delivery | **selected** for `CreateChatEvent` and `MessageAcceptedEvent` |
| Message broker | Kafka / Rabbit | durable, cross-service | new infrastructure with one producer and no consumers yet | rejected |
| HTTP webhook | emitter calls a chat endpoint | explicit contract | indirection inside one monolith; auth questions | rejected |

## Design Decisions

### Decision: `CreateChatEvent` is an in-process Spring event owned by the chat package
- **Context**: requirements fix the interface ("`CREATE_CHAT` fired at the end of the successful execution of swipe + mutual match") but not its carrier.
- **Alternatives Considered**: 1. Spring application event. 2. Broker message. 3. HTTP webhook.
- **Selected Approach**: `data class CreateChatEvent(dogAId, dogBId)` in `chat`, published by the future F-07/F-08 flow after its match commits.
- **Rationale**: platform-native, no infrastructure, keeps the dependency direction acyclic (match → chat → dog).
- **Trade-offs**: the contract dissolves if the service splits; acceptable until there is a second service.
- **Follow-up**: F-07/F-08 implementers must publish exactly this event; shape changes are a revalidation trigger.

### Decision: idempotent chat creation via canonical pair + unique constraint
- Store the pair as `(min, max)` with `CHECK (dog_a_id < dog_b_id)` and `UNIQUE (dog_a_id, dog_b_id)`; lookup first, insert on miss, and on a concurrent-duplicate constraint violation re-fetch.
- Rejecting an invalid event is logged (WARN) and swallowed — a committed match must not fail because chat creation declined; this is a deliberate deviation from the `require` convention.

### Decision: per-listener watermark delivery
- Fresh connections start at the chat's newest message (history is the GET endpoint); reconnections seed the watermark from `Last-Event-ID`.
- Correctness precondition: id order must equal commit order per chat — provided by the advisory-lock decision below. Pushes dispatch through a bounded executor; the catch-up re-fetch makes dispatch order irrelevant, and the sender's response never waits on a recipient.

### Decision: per-chat send serialization via advisory lock
- **Context**: design review found that concurrent sends can commit ids out of order; the watermark catch-up then skips the later-committing lower id on the stream, and `Last-Event-ID` replay skips it too.
- **Alternatives Considered**: 1. Gap-aware delivery (send only contiguous id prefixes) — rejected: rolled-back sends leave permanent id holes, so contiguity would wait forever without timeout heuristics. 2. Client-side ordering by id — rejected: weakens 3.2 and pushes the problem into every client. 3. Advisory-lock serialization.
- **Selected Approach**: `send` acquires `pg_advisory_xact_lock(chatId)` (a native `@Query` repository method) before inserting; the lock releases at commit, so insert order and commit order agree per chat.
- **Rationale**: one statement, database-native, keeps the `@Transactional` style; human-paced chats make per-chat serialization free.
- **Trade-offs**: concurrent senders to one chat queue behind the lock — negligible at chat cadence.
- **Follow-up**: a unit test pins that the lock call precedes the insert.

### Decision: plain `Long` dog references, no JPA associations
- The codebase has no `@ManyToOne` anywhere; plain id columns avoid lazy-loading hazards under `open-in-view: false`. Dog names for summaries are resolved via `DogRepository`, same as `MatchController` does today.

### Decision: non-disclosure 404s
- Unknown chat and non-participant both answer 404, so an outsider cannot distinguish "exists" from "not for you".

### Decision: Mockito for repository-backed unit tests
- Already on the classpath; the alternative (hand-written `JpaRepository` fakes) costs more than it protects.

## Risks & Mitigations
- Reverse proxies buffering `text/event-stream` — deployment note: disable proxy buffering for the stream endpoint.
- Unbounded in-memory listener registry — acceptable at launch scale; cap when there is a measured reason.
- Out-of-order concurrent delivery — mitigated by per-chat advisory-lock serialization (id order = commit order) plus the watermark catch-up.
- Slow or blocked SSE recipients stalling senders — mitigated by executor-dispatched pushes.
- `dogId` parameters are spoofable — documented auth stand-in; F-01 landing is a revalidation trigger.
- Spring events are in-process only — service split forces a contract change; recorded as a revalidation trigger.

## References
- [Spring Framework 7 — Asynchronous Requests (SSE)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html) — SseEmitter in annotated controllers, servlet async.
- [Spring Framework — Transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html) — AFTER_COMMIT semantics.
- `docs/PRD.md` — F-09, NFR-08, NFR-09, open question Q3.
- `.kiro/steering/tech.md` — Boot 4 per-technology auto-configuration modules; unit-level testing rules.
- `.kiro/steering/structure.md` — package-per-concept, REST conventions, known inconsistencies.
- `.kiro/steering/product.md` — current capabilities and known gaps.