# Requirements Document

## Introduction

The Owner Chat feature enables matched dog owners on PawMatch to exchange text messages once a mutual match has been established. Chat access is strictly gated on mutual matching to ensure privacy and prevent unsolicited contact. Message history is retained for the lifetime of the match; unmatching permanently deletes the chat thread. Real-time bidirectional messaging is delivered via WebSocket, and a per-owner rate limit prevents message spam.

---

## Requirements

### Requirement 1: Chat Access Control

**Objective:** As an owner, I want chat to be available only with my matched owners, so that I can communicate safely without unsolicited contact.

#### Acceptance Criteria

1. When an owner attempts to open a chat with another owner, the Chat Service shall verify that a mutual match exists between them before granting access.
2. If no mutual match exists between the two owners, the Chat Service shall return a 403 Forbidden response.
3. When a mutual match is created, the Chat Service shall automatically make a chat thread available to those two owners.
4. The Chat Service shall enforce that only the two participants of a match can read or send messages in that match's chat thread.
5. If an owner attempts to access a chat thread they are not a participant of, the Chat Service shall return a 403 Forbidden response.

---

### Requirement 2: Sending Messages

**Objective:** As a matched owner, I want to send text messages, so that I can coordinate a meetup with the other dog owner.

#### Acceptance Criteria

1. When an authenticated owner submits a text message to a match's chat, the Chat Service shall persist the message with sender owner ID, match ID, UTC timestamp, and content.
2. When a message is successfully persisted, the Chat Service shall return a confirmation event over the WebSocket connection containing the message ID and server-assigned UTC timestamp.
3. If the message payload is empty or blank, the Chat Service shall reject it and return an error event to the sender.
4. If the message content exceeds 2,000 characters, the Chat Service shall reject it and return an error event to the sender; this limit is a hard constraint and is not configurable.
5. While an owner is authenticated and a mutual match exists, the Chat Service shall allow them to send multiple messages sequentially within the same chat thread.

---

### Requirement 3: Rate Limiting

**Objective:** As the platform, I want to limit how fast a single owner can send messages, so that the chat feature cannot be abused for spam.

#### Acceptance Criteria

1. The Chat Service shall enforce a per-owner rate limit on message sends within any given chat thread.
2. If an owner exceeds the rate limit, the Chat Service shall reject the message and return a rate-limit error event to the sender indicating when they may retry.
3. The Chat Service shall not count rejected messages against the rate-limit window.
4. The Chat Service shall apply the rate limit per owner, not per match thread, so that spamming multiple threads is also constrained.

---

### Requirement 4: Message History Retrieval

**Objective:** As a matched owner, I want to view the full message history, so that I can review past conversations and track planned meetups.

#### Acceptance Criteria

1. When an authenticated owner requests the message history for a chat thread via REST, the Chat Service shall return all messages for that match in ascending chronological order.
2. When returning message history, the Chat Service shall include sender owner ID, message content, and UTC timestamp for each message.
3. When message history is requested, the Chat Service shall support offset pagination with a default page size of 50 messages.
4. If an owner requests history for a chat thread they are not a participant of, the Chat Service shall return a 403 Forbidden response.
5. The Chat Service shall retain message history for as long as the corresponding match exists.

---

### Requirement 5: Unmatch and History Deletion

**Objective:** As an owner, I want unmatching to fully remove the conversation, so that there is no residual data from a terminated relationship.

#### Acceptance Criteria

1. When a match is deleted (unmatched) by either owner, the Chat Service shall permanently delete all messages belonging to that match's chat thread.
2. When a match is deleted, the Chat Service shall permanently delete the chat thread record itself.
3. If either participant attempts to access a chat thread after the match has been deleted, the Chat Service shall return a 404 Not Found response.
4. The Chat Service shall not expose any previously sent messages after the match deletion has been committed.

---

### Requirement 6: Chat Inbox (Conversation List)

**Objective:** As an owner, I want to see a list of all my active chat conversations, so that I can quickly navigate to any ongoing exchange.

#### Acceptance Criteria

1. When an authenticated owner requests their chat inbox via REST, the Chat Service shall return all match chat threads the owner currently participates in.
2. When returning the inbox, the Chat Service shall include for each thread: matched dog name, matched dog primary photo URL, last message preview (first 100 characters), and last message UTC timestamp.
3. The Chat Service shall sort inbox entries by last message timestamp, most recent first.
4. If an owner has no active matches, the Chat Service shall return an empty list with a 200 OK response.
5. If a chat thread has no messages yet, the Chat Service shall still include it in the inbox with a null last message preview and null last message timestamp.

---

### Requirement 7: Real-Time Messaging via WebSocket

**Objective:** As an owner, I want to send and receive messages in real time, so that conversations feel instant and I don't miss new messages.

#### Acceptance Criteria

1. The Chat Service shall provide a WebSocket endpoint that authenticated owners can connect to for real-time bidirectional messaging.
2. When an owner connects to the WebSocket endpoint, the Chat Service shall authenticate the connection using the owner's JWT token before accepting it.
3. If the JWT token provided during WebSocket handshake is absent, expired, or invalid, the Chat Service shall reject the connection with a 401 status.
4. When a message is sent by one participant, the Chat Service shall push the message event to the other participant's active WebSocket connection without polling.
5. If the recipient owner does not have an active WebSocket connection when a message is sent, the Chat Service shall not lose the message; it shall remain fully retrievable via the message history endpoint.
6. The Chat Service shall not use SSE or mobile push notifications as the real-time channel for chat messages (WebSocket only for v1).

---

### Requirement 8: Security and Privacy

**Objective:** As an owner, I want my chat messages to stay private between myself and my match, so that I can communicate confidentially without exposing personal contact details.

#### Acceptance Criteria

1. The Chat Service shall require a valid JWT access token on every REST chat endpoint (message history, inbox).
2. If a JWT token is absent, expired, or invalid on a REST endpoint, the Chat Service shall return a 401 Unauthorized response.
3. The Chat Service shall never return messages belonging to one match thread in a response scoped to a different match thread.
4. The Chat Service shall not require or prompt owners to share external contact information (email, phone number, social handles) as part of the chat workflow.
5. The Chat Service shall log all 5xx errors with a trace ID to support incident investigation without exposing message content in logs.

---

## Non-Functional Requirements

### Performance

| NFR ID  | Description                              | Threshold                              | Priority | Source Req |
|---------|------------------------------------------|----------------------------------------|----------|------------|
| NFR-C01 | Message send response time               | p95 < 200 ms from send to persist      | P0       | Req 2      |
| NFR-C02 | Message history first-page load time     | p95 < 500 ms                           | P0       | Req 4      |
| NFR-C03 | WebSocket message delivery latency       | < 1 s from persist to recipient push   | P1       | Req 7      |

### Security

| NFR ID  | Description                                          | Threshold                          | Priority | Source Req  |
|---------|------------------------------------------------------|------------------------------------|----------|-------------|
| NFR-C04 | JWT authentication enforced on all chat endpoints    | 100% of endpoints protected        | P0       | Req 8       |
| NFR-C05 | Message visibility scoped to match participants only | Zero cross-participant data leaks  | P0       | Req 1, 8    |

### Scalability

| NFR ID  | Description                        | Threshold                                                        | Priority | Source Req |
|---------|------------------------------------|------------------------------------------------------------------|----------|------------|
| NFR-C06 | Message history storage growth     | Schema must accommodate unbounded message growth without changes  | P1       | Req 4      |

### Reliability

| NFR ID  | Description                              | Threshold                                                                    | Priority | Source Req |
|---------|------------------------------------------|------------------------------------------------------------------------------|----------|------------|
| NFR-C07 | Message persist-before-push ordering     | Message durably persisted before WebSocket push is dispatched                | P0       | Req 2, 7   |
| NFR-C08 | No message loss on disconnection         | Messages retrievable via history even when recipient is offline              | P0       | Req 7      |

### Observability

| NFR ID  | Description                       | Threshold                               | Priority | Source Req |
|---------|-----------------------------------|-----------------------------------------|----------|------------|
| NFR-C09 | Structured error logging          | All 5xx responses logged with trace ID  | P1       | Req 8      |

### Usability

| NFR ID  | Description                   | Threshold                                                   | Priority | Source Req |
|---------|-------------------------------|-------------------------------------------------------------|----------|------------|
| NFR-C10 | Paginated message history API | Default page 50 messages; clients must not load all at once | P1       | Req 4      |

### Constraints

| NFR ID  | Description                            | Threshold                                              | Priority | Source Req       |
|---------|----------------------------------------|--------------------------------------------------------|----------|------------------|
| NFR-C11 | Stateless backend                      | No in-memory chat session state; horizontally scalable | P1       | PRD NFR-05       |
| NFR-C12 | Real-time channel: WebSocket only      | No SSE or mobile push notifications in v1              | P0       | Req 7, PRD F-05  |
| NFR-C13 | Hard message length limit              | 2,000 characters maximum; not configurable             | P0       | Req 2            |

---

## Open Questions

| # | Question | Impact | Owner | Status |
|---|----------|--------|-------|--------|
| 1 | What is the specific rate-limit threshold (e.g. N messages per minute per owner)? | Required to implement and test Req 3 | Product/Tech | Open |
