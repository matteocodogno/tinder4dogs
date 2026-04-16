# Research & Design Decisions

---
**Purpose**: Capture discovery findings, architectural investigations, and rationale that inform the technical design.

---

## Summary

- **Feature**: `in-app-notifications`
- **Discovery Scope**: Complex Integration (new domain module + PostgreSQL async channel + SSE lifecycle management)
- **Key Findings**:
  - `SseEmitter` (Spring WebMVC) is the correct SSE primitive; it is NOT thread-safe and requires synchronized send calls.
  - PostgreSQL LISTEN/NOTIFY broadcasts to all listening nodes simultaneously — no split-brain risk; each node maintains its own in-memory emitter registry and fans out locally.
  - Persist-first pattern (INSERT → NOTIFY) is mandatory: if a node has no active connections for a user, the notification survives in the DB and is replayed on reconnect.
  - ShedLock is required to prevent duplicate 30-day TTL cleanup runs in a multi-node deployment.
  - No new infrastructure (Redis, Kafka) is needed for v1 expected load.

---

## Research Log

### SseEmitter in Spring Boot 4.x WebMVC

- **Context**: Need a server-push mechanism that works with WebMVC (not WebFlux).
- **Sources Consulted**:
  - Spring Framework 7.0 API docs (SseEmitter, ResponseBodyEmitter)
  - Spring Boot GitHub issues: #4021 (timeout handling), #12321 (onError/onCompletion unreliability)
  - Spring Framework issue SPR-13224 (thread safety)
- **Findings**:
  - `SseEmitter` extends `ResponseBodyEmitter`; designed for long-lived responses.
  - Three lifecycle callbacks must be registered immediately: `onCompletion`, `onTimeout`, `onError` — all must remove the emitter from the registry.
  - `onError` is sometimes not invoked on IOException; rely on `onCompletion` + `onTimeout` as primary cleanup signals.
  - A background reaper (scan every 5 min) is needed for emitters that silently stale-out.
  - **Critical**: `emitter.send()` is NOT thread-safe (concurrent calls interleave bytes); wrap all sends with a `synchronized` block on the emitter or a per-emitter lock object.
  - No Spring Boot 4.0-specific breaking changes to SseEmitter behavior vs 3.x.
- **Implications**: `SseEmitterRegistry` must synchronize all `send()` calls; must register all three callbacks on every new emitter; must expose `getConnectedUserIds()` for the polling service.

### PostgreSQL LISTEN/NOTIFY

- **Context**: Multi-node fan-out without in-memory shared state.
- **Sources Consulted**:
  - PostgreSQL JDBC driver 42.7.x documentation (`PGConnection.getNotifications()`)
  - Baeldung: "Event-Driven LISTEN/NOTIFY in Java with PostgreSQL"
  - PgDog: "Scaling Postgres LISTEN/NOTIFY"
- **Findings**:
  - LISTEN requires a **dedicated JDBC connection** outside the main pool; sharing with HikariCP connections is not safe.
  - `PGConnection.getNotifications(timeoutMs)` blocks the calling thread for up to `timeoutMs`; use 500 ms polling interval as the sweet spot between latency and CPU.
  - NOTIFY payload limit is 8 000 bytes — more than sufficient for notification payloads (~300–500 bytes JSON).
  - PostgreSQL broadcasts NOTIFY to **all** nodes listening on the channel simultaneously; no additional coordination is needed.
  - No Spring library abstracts LISTEN/NOTIFY; raw JDBC with `Connection.unwrap(PGConnection::class.java)` is the standard approach.
  - Spring Boot 4.0.4 manages `postgresql` JDBC driver version 42.7.x via the parent POM; no explicit version override needed.
- **Implications**: `PostgresNotificationListener` manages one dedicated connection per channel; runs a polling loop on a `ThreadPoolTaskScheduler` thread.

### Thread Safety for SseEmitter Fan-Out

- **Context**: The LISTEN polling thread delivers notifications to emitters; meanwhile HTTP request threads may also try to send (e.g., replay on reconnect).
- **Findings**:
  - Best practice for low-to-medium concurrency: a `synchronized` wrapper method on the emitter (class-level or per-emitter `Any` lock object) is simpler and lower-overhead than a per-emitter `ExecutorService`.
  - `ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>` for the registry: low-write, high-read pattern is optimal.
- **Implications**: `SseEmitterRegistry.send()` acquires a lock per emitter before calling `emitter.send()`. Lock is released immediately after; contention is minimal.

### ShedLock for Distributed Scheduled Jobs

- **Context**: 30-day TTL cleanup runs via `@Scheduled` on every node simultaneously, causing duplicate DELETE contention.
- **Sources Consulted**: ShedLock GitHub (`lukas-krecan/ShedLock`), Spring integration docs.
- **Findings**:
  - `shedlock-spring` + `shedlock-provider-jdbc-template` are the two required artifacts.
  - Creates a `shedlock` table in the database; a single node acquires the lock and runs the job.
  - Lock is released after job completion (or after a configurable `lockAtMostFor` duration on crash).
  - Adds two compile/runtime dependencies to pom.xml.
- **Implications**: Add ShedLock to pom.xml; annotate cleanup method with `@SchedulerLock`; add `@EnableSchedulerLock` to config.

---

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Decision |
|--------|-------------|-----------|---------------------|----------|
| SSE via `SseEmitter` | WebMVC-native long-poll push | No new deps; familiar to team; sufficient for 1-way server→client push | Not thread-safe; requires synchronized wrapper | **Selected** |
| WebSocket | Bidirectional full-duplex | More flexible; native reconnect | Adds `spring-boot-starter-websocket`; overkill for server-only push | Rejected |
| WebFlux `Flux<ServerSentEvent>` | Reactive SSE | Clean async model | Conflicts with WebMVC-only constraint (tech.md) | Rejected |
| PostgreSQL LISTEN/NOTIFY | Built-in pub/sub; no new infra | No Redis/Kafka; all in PG | Requires dedicated JDBC connection outside HikariCP; added complexity | Rejected — replaced by DB polling |
| DB SELECT polling | Periodic query of notifications table | No dedicated connection; uses HikariCP pool; simpler implementation | Slightly higher DB read load; up to 500 ms delivery latency | **Selected** |
| Redis Pub/Sub | High-throughput fan-out | Very fast; rich ecosystem | New infrastructure; out of scope for v1 | Deferred (v2) |
| ShedLock (JDBC) | Distributed lock via `shedlock` table | No Zookeeper/Redis; stays in PostgreSQL | 2 new deps | **Selected** |

---

## Design Decisions

### Decision: Persist-First Pattern

- **Context**: Notifications must survive if no client connections are active at delivery time.
- **Alternatives Considered**:
  1. Fire-and-forget NOTIFY only (no persistence) — fails for offline users
  2. Notify-first, persist on ACK — complex, race conditions
- **Selected Approach**: INSERT notification to DB → NOTIFY → fan-out locally. Delivery failure never loses the notification.
- **Rationale**: Correctness over performance; aligns with Requirement 1.4 and 1.5.
- **Trade-offs**: Extra DB write per notification; acceptable at v1 scale.
- **Follow-up**: Monitor INSERT latency under load; consider async DB write (decoupled from match/chat transaction) via `@TransactionalEventListener(AFTER_COMMIT)`.

### Decision: Preferences Checked Before Persistence

- **Context**: If a user disables `NEW_MESSAGE` notifications, no record should be created or delivered.
- **Alternatives Considered**:
  1. Persist all, filter at delivery — wastes storage, complicates history
  2. Check preferences before INSERT — clean, no orphan records
- **Selected Approach**: `NotificationService.isEnabled(userId, type)` check before any INSERT or NOTIFY.
- **Rationale**: Aligns with Requirement 7.3; simpler history queries.
- **Trade-offs**: Extra SELECT per notification event; acceptable (preferences cached or indexed by PK).

### Decision: Full Payload in NOTIFY Message

- **Context**: After receiving NOTIFY, node needs notification data to push to the emitter.
- **Alternatives Considered**:
  1. Include only `notificationId` in NOTIFY → each node fetches from DB → extra round-trip
  2. Include full payload in NOTIFY → node pushes directly → no extra round-trip
- **Selected Approach**: Full payload JSON in NOTIFY message; within 8 000-byte limit (~300–500 bytes for notification payloads).
- **Rationale**: Eliminates per-delivery DB query; lower latency for real-time delivery.
- **Trade-offs**: NOTIFY payload slightly larger; negligible.

### Decision: SseEmitter Heartbeat

- **Context**: Browser connections drop silently after idle; proxies often have short keepalive timeouts.
- **Selected Approach**: Send a `comment:` (`:heartbeat`) SSE event every 30 seconds to all active emitters.
- **Rationale**: Keeps TCP connection alive through proxies; confirms emitter is still writable (if send fails, emitter is stale and removed from registry).

---

## Risks & Mitigations

- **SseEmitter callback not firing on silent disconnect** — Implement a background reaper in `SseEmitterRegistry` that scans for emitters not seen in > 10 minutes (tracked by `lastHeartbeatAt` timestamp). Reaper runs every 5 minutes via `@Scheduled`.
- **LISTEN dedicated connection dropped** — `PostgresNotificationListener` detects `getNotifications()` throwing `SQLException`; reconnects with exponential backoff (max 30 s); missed NOTIFYs during downtime are covered by the persist-first pattern.
- **Auth not yet implemented** — Notification endpoints must check `SecurityContextHolder.getContext().authentication`; design leaves a placeholder for the future JWT `SecurityFilterChain`. Blocked until auth module is built.
- **User entity not yet persisted** — `notifications.user_id` references `users.id`; the User JPA entity and migration must be delivered before or alongside this feature.
- **ShedLock `shedlock` table migration** — ShedLock requires a `shedlock` table in PostgreSQL; must be included in the DB migration for this feature.

---

## References

- [SseEmitter (Spring Framework 7.0 API)](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html)
- [SseEmitter thread-safety issue (SPR-13224)](https://github.com/spring-projects/spring-framework/issues/17815)
- [PostgreSQL LISTEN/NOTIFY with Java (Baeldung)](https://www.baeldung.com/java-postgresql-listen-notify-events)
- [PostgreSQL JDBC Extensions (pgjdbc.org)](https://jdbc.postgresql.org/documentation/server-prepare/)
- [Scaling Postgres LISTEN/NOTIFY (PgDog)](https://pgdog.dev/blog/scaling-postgres-listen-notify)
- [ShedLock — Distributed Lock for @Scheduled](https://github.com/lukas-krecan/ShedLock)
- [Spring Boot 4.0.0 release notes](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now/)
