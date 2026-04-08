# Requirements Document

## Introduction

PawMatch requires a real-time in-app notification system to keep dog owners immediately informed of new matches and incoming chat messages. Notifications are delivered via WebSocket or SSE to authenticated browser clients. There is no mobile push notification support in v1. The feature maps directly to PRD feature **F-05** and serves both the Casual Owner (Marco) and the Breeding Owner (Giulia) personas.

---

## Requirements

### Requirement 1: Real-Time Notification Delivery

**Objective:** As an owner, I want to receive a real-time in-browser notification when I get a new match or a new message, so that I can respond promptly and not miss any meetup opportunity.

#### Acceptance Criteria

1. When a mutual match is created between two owners, the Notification Service shall push a "new match" notification to each matched owner's active connection(s) within 2 seconds of the match event.
2. When a new chat message is sent by one matched owner, the Notification Service shall push a "new message" notification to the recipient's active connection(s) within 2 seconds of message creation.
3. While a user has at least one active SSE or WebSocket connection, the Notification Service shall deliver all notifications in real time without requiring a page refresh.
4. If the user has no active connection at the time of the triggering event, the Notification Service shall persist the notification for delivery on the user's next successful connection.
5. When a reconnecting client provides a `lastEventId`, the Notification Service shall replay all undelivered notifications that occurred after that event ID.

---

### Requirement 2: Connection Lifecycle Management

**Objective:** As an owner, I want a stable, authenticated notification connection so that I receive events reliably throughout my session.

#### Acceptance Criteria

1. When an authenticated user opens the app and requests a notification stream, the Notification Service shall accept and register the connection scoped to that user's identity.
2. The Notification Service shall validate the JWT on every connection attempt and reject connections with missing, expired, or invalid tokens with HTTP 401.
3. When a connection is closed by the client (normal close), the Notification Service shall release all server-side resources associated with that connection immediately.
4. If a connection drops unexpectedly (network error, timeout), the Notification Service shall release all server-side resources associated with that connection within 30 seconds.
5. While a user has multiple concurrent browser-tab connections, the Notification Service shall deliver each notification to all active connections belonging to that user.
6. The Notification Service shall fan out events to all nodes using PostgreSQL `LISTEN/NOTIFY`; no per-node in-memory connection registry shall be the sole source of truth for active connections.

---

### Requirement 3: Notification Payload Structure

**Objective:** As an owner, I want notification payloads to contain enough context to understand the event without navigating away, so that I can decide whether to act immediately.

#### Acceptance Criteria

1. When a "new match" notification is delivered, the Notification Service shall include in the payload: `type: NEW_MATCH`, `matchId`, `matchedDogName`, `matchedDogPhotoUrl` (first photo), and `createdAt` timestamp.
2. When a "new message" notification is delivered, the Notification Service shall include in the payload: `type: NEW_MESSAGE`, `matchId`, `senderDogName`, `messagePreview` (first 100 characters, truncated with ellipsis if longer), and `createdAt` timestamp.
3. The Notification Service shall never include exact geographic coordinates or precise location data in any notification payload.
4. The Notification Service shall use ISO 8601 UTC format for all timestamp fields in notification payloads.

---

### Requirement 4: Notification Persistence and History

**Objective:** As an owner, I want to review past notifications I may have missed, so that I can catch up on activity when I return to the app.

#### Acceptance Criteria

1. The Notification Service shall persist every notification for the recipient user with the fields: `id`, `userId`, `type`, `payload` (JSON), `createdAt`, and `isRead` (default `false`).
2. When a user requests their notification history, the Notification Service shall return all notifications for that user in reverse chronological order (`createdAt DESC`).
3. When a user requests their notification history, the Notification Service shall support pagination (page size, cursor-based or offset-based).
4. When a user marks a specific notification as read, the Notification Service shall update its `isRead` to `true` and return the updated total count of unread notifications.
5. When a user requests a bulk "mark all as read" action, the Notification Service shall update all unread notifications for that user to `isRead: true` atomically.
6. If the match or chat message that generated a notification is subsequently deleted, the Notification Service shall retain the notification record unchanged for audit and history purposes.
7. The Notification Service shall expose a dedicated unread-count endpoint that returns the total number of unread notifications for the authenticated user.
8. The Notification Service shall automatically delete notifications older than 30 days from the time of creation.
9. When a user account is deleted, the Notification Service shall delete all notification records for that user within the account deletion transaction.

---

### Requirement 5: Authorization and Data Isolation

**Objective:** As an owner, I want to be confident that I only receive my own notifications and that my notification data is not exposed to other users, so that my privacy is protected.

#### Acceptance Criteria

1. The Notification Service shall only deliver notifications to the authenticated user they belong to; a user shall never receive notifications destined for another user.
2. If an unauthenticated request attempts to open a notification stream endpoint, the Notification Service shall reject the connection with HTTP 401 and no payload.
3. When a user requests their notification history, the Notification Service shall return only notifications where `userId` matches the authenticated requester's identity.
4. If a request attempts to mark as read a notification belonging to a different user, the Notification Service shall reject the request with HTTP 403.
5. The Notification Service shall enforce all authorization checks server-side; client-supplied user IDs in request bodies shall be ignored in favour of the authenticated JWT subject.

---

### Requirement 6: Graceful Degradation and Error Handling

**Objective:** As an owner, I want the notification system to handle errors gracefully so that failures in notification delivery do not affect the core matching or chat functionality.

#### Acceptance Criteria

1. If a notification delivery attempt to an active connection fails (e.g., write error), the Notification Service shall log the failure with a trace ID and mark the notification as undelivered without propagating the error to the match or chat service.
2. If the notification persistence layer (database write) fails, the Notification Service shall log the error with a trace ID; the match or message creation operation that triggered the notification shall not be rolled back.
3. While the notification system is unavailable, the Notification Service shall not degrade the matching feed or owner chat features.
4. If an invalid or malformed connection request is received, the Notification Service shall return HTTP 400 with a descriptive error message and log the incident.

---

### Requirement 7: Notification Preferences

**Objective:** As an owner, I want to control which types of notifications I receive, so that I can reduce noise without disabling the feature entirely.

#### Acceptance Criteria

1. The Notification Service shall maintain per-user notification preferences with a configurable enabled/disabled flag for each notification type (`NEW_MATCH`, `NEW_MESSAGE`).
2. When a user updates their notification preferences, the Notification Service shall persist the change and apply it to all subsequent notification events for that user.
3. When a notification event is triggered and the recipient user has disabled that notification type, the Notification Service shall neither deliver the notification in real time nor persist it to the notification history.
4. When a new user account is created, the Notification Service shall initialize their preferences with all notification types enabled by default.
5. When a user requests their notification preferences, the Notification Service shall return the current enabled/disabled state for every supported notification type.
6. The Notification Service shall enforce authorization on preference reads and writes; a user shall not be able to read or modify another user's preferences.

---

## Non-Functional Requirements

### Performance

| NFR ID  | Description                                                    | Threshold                                      | Priority | Source Req    |
|---------|----------------------------------------------------------------|------------------------------------------------|----------|---------------|
| NFR-N-01 | Real-time notification delivery latency                       | Notification delivered to active client ≤ 2 s from trigger event | P0 | Req 1 |
| NFR-N-02 | Notification history API response time                         | p95 < 500 ms under normal load                 | P1       | Req 4         |
| NFR-N-03 | Unread count endpoint response time                            | p95 < 200 ms                                   | P1       | Req 4         |

### Security

| NFR ID  | Description                                                    | Threshold                                      | Priority | Source Req    |
|---------|----------------------------------------------------------------|------------------------------------------------|----------|---------------|
| NFR-N-04 | JWT authentication on all notification endpoints              | All unauthenticated requests rejected with HTTP 401; tokens validated on every request | P0 | Req 2, Req 5 |
| NFR-N-05 | No geolocation exposure in notifications                       | Zero occurrences of coordinate fields in notification payloads | P0 | Req 3 |
| NFR-N-06 | User-scoped data isolation                                     | Cross-user notification access returns HTTP 403; verified by integration test | P0 | Req 5 |

### Scalability

| NFR ID  | Description                                                    | Threshold                                      | Priority | Source Req    |
|---------|----------------------------------------------------------------|------------------------------------------------|----------|---------------|
| NFR-N-07 | Stateless connection management via PostgreSQL LISTEN/NOTIFY  | Events fanned out via PostgreSQL `LISTEN/NOTIFY`; multiple service instances must operate without split-brain | P0 | Req 2 (NFR-05 from PRD) |

### Reliability

| NFR ID  | Description                                                    | Threshold                                      | Priority | Source Req    |
|---------|----------------------------------------------------------------|------------------------------------------------|----------|---------------|
| NFR-N-08 | Notification persistence on missed delivery                   | All notifications persisted to DB before attempting SSE/WebSocket push; zero notification loss on reconnect | P0 | Req 1, Req 4 |
| NFR-N-09 | Notification delivery failure isolation                        | Failure in notification delivery must not cause rollback of match or chat transaction | P0 | Req 6 |

### Observability

| NFR ID  | Description                                                    | Threshold                                      | Priority | Source Req    |
|---------|----------------------------------------------------------------|------------------------------------------------|----------|---------------|
| NFR-N-10 | Structured logging for all delivery failures                  | Every connection error and delivery failure logged with trace ID using kotlin-logging | P1 | Req 6 (PRD NFR-07) |

### Usability

| NFR ID  | Description                                                    | Threshold                                      | Priority | Source Req    |
|---------|----------------------------------------------------------------|------------------------------------------------|----------|---------------|
| NFR-N-11 | Notification open rate                                        | ≥ 40% of delivered notifications opened (tracked via read-state updates) | P1 | PRD F-05 success metric |

### Constraints

| NFR ID  | Description                                                    | Threshold                                      | Priority | Source Req    |
|---------|----------------------------------------------------------------|------------------------------------------------|----------|---------------|
| NFR-N-12 | Transport mechanism                                           | SSE or WebSocket only; no mobile push notifications in v1 | P0 | PRD F-05 |
| NFR-N-13 | Spring Boot WebMVC (not WebFlux)                              | Implementation must use WebMVC-compatible async mechanism (e.g., `SseEmitter`, `DeferredResult`) | P0 | tech.md |
| NFR-N-14 | Notification history retention                                | Notifications older than 30 days shall be automatically deleted; retention applied per `createdAt`; account deletion removes all notifications immediately | P0 | Req 4, PRD NFR-03 (GDPR) |

---

## Open Questions

| #  | Question                                                                                                    | Impact                                                                         | Owner       | Status |
|----|-------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|-------------|--------|
| Q1 | Should users be able to opt out of individual notification types (e.g., disable new-message notifications)? | Per-type preference management added as Requirement 7       | Product     | **Resolved** — yes, per-type opt-out required |
| Q2 | What is the intended retention period for notification history?                                             | 30-day auto-deletion added as NFR-N-14; account deletion purges all records immediately | Product/Legal | **Resolved** — 30 days |
| Q3 | Should the API surface an unread-notification badge count?                                                  | Dedicated unread-count endpoint added as Req 4 AC 7         | Product/Tech | **Resolved** — dedicated lightweight endpoint |
| Q4 | How will events be fanned out across nodes?                                                                 | PostgreSQL `LISTEN/NOTIFY` specified in Req 2 AC 6 and NFR-N-07 | Tech     | **Resolved** — PostgreSQL LISTEN/NOTIFY |
