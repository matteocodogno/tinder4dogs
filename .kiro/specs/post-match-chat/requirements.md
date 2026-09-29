# Requirements Document

## Project Description (Input)

Feature F-09 — Chat after a match (P0), from `docs/PRD.md`.

**Who has the problem:** dog owners — the product's two personas — specifically the two owners whose dogs have just mutually liked each other and received a match notification (F-08).

**Current situation:** the tinder4dogs backend today stores dog profiles and computes a compatibility score between two dogs. It has no swipe, no mutual match, and no chat (see `.kiro/steering/product.md`, "Known Gaps"). Once a match happens, the product offers no way for the two owners to get in touch: arranging a meeting means falling back on exactly the chaotic external channels (Facebook and WhatsApp groups) that the product exists to replace, and a match with no follow-up delivers no value.

**What should change:** add a chat between the two owners of a match, available **only** after a mutual match, so that they can arrange a meeting inside the app. No messaging is possible without a mutual match (PRD F-09 constraint).

**User story:** as an owner, I want to write to the other owner after a match so that we can arrange a meeting.

**Success metrics (PRD):** percentage of matches with at least one message; conversations with a reply.

**Related NFRs:** NFR-08 — a message is delivered to an online recipient within 1 s at p95; NFR-09 — no chat messages are lost (guaranteed delivery with deduplication).

**Dependency on F-07/F-08 (decided):** the dependency is expressed as an interface, not as a build-order coupling. At the end of the successful execution of swipe + mutual match (F-07 swipe, F-08 matches and notifications), a `CREATE_CHAT` event is fired; chat creation in this feature is triggered by that event. This feature owns the consumption of the event and everything from chat creation onwards, and does not depend on how the swipe/match flow is implemented internally. The event contract (`CREATE_CHAT`) is the single interface between the two features.

## Boundary Context

- **In scope**: creation of a chat triggered by the `CREATE_CHAT` event; one chat per mutual match; plain-text messages exchanged between the two matched sides; live delivery to a connected participant; full history retrieval; discovery of the chats a dog takes part in.
- **Out of scope**: the swipe and mutual-match logic that emits `CREATE_CHAT` (owned by F-07/F-08); owner/user identity and authentication (F-01) — a message is attributed to a side of the match, identified by the dog; message editing and deletion; chat closing, leaving, or archiving; read receipts; typing indicators; mobile push notifications for new chat messages — an offline side reads new messages on its next connection; media attachments; moderation actions such as reporting and blocking (F-10); retention and automatic-deletion policies for chat content (PRD open question Q3).
- **Adjacent expectations**: the `CREATE_CHAT` event is fired at the end of the successful execution of swipe + mutual match and identifies the two matched dogs; this feature consumes that event and does not depend on how the emitting flow is implemented. F-08 owns the match notification, not chat-message notifications. When F-10 (reporting and blocking) is implemented, blocking must gate chat access; this feature does not enforce it.

## Requirements

### Requirement 1: Chat creation upon a mutual match
**Objective:** As an owner, I want a chat to open automatically when a mutual match occurs, so that I can write to the other owner immediately.

#### Acceptance Criteria
1. When the backend receives a `CREATE_CHAT` event identifying a mutual match between two distinct existing dogs, the backend shall create a chat for that match.
2. When the backend receives a `CREATE_CHAT` event for a match that already has a chat, the backend shall reuse the existing chat and create no second chat.
3. If a `CREATE_CHAT` event refers to a dog that does not exist, or to the same dog twice, the backend shall reject the event and create no chat.
4. The backend shall provide message exchange only within a chat created for a mutual match.

### Requirement 2: Text messaging between match participants
**Objective:** As an owner, I want to write plain-text messages to the other side of my match, so that we can arrange a meeting.

#### Acceptance Criteria
1. When one of the two matched sides sends a non-blank text message of at most 2000 characters, the backend shall accept the message and record it together with the sending side and the time of acceptance.
2. If the message text is blank or longer than 2000 characters, the backend shall reject the message and record nothing.
3. If the sender is not one of the two matched sides of the chat, the backend shall reject the message.
4. The backend shall keep every accepted message unchanged once it has been recorded.

### Requirement 3: Live delivery and message reliability
**Objective:** As a chatting owner, I want messages to reach the other side immediately while it is online and never to get lost, so that the conversation is dependable.

#### Acceptance Criteria
1. When a matched side sends a message and the other side is connected, the backend shall deliver the message to the connected side within 1 second at p95.
2. When delivering messages to a connected side, the backend shall deliver them in the order they were accepted.
3. If the other side is not connected when a message is sent, the backend shall keep the message stored until that side reads it.
4. The backend shall make every accepted message available to its recipient without loss and without duplicates.

### Requirement 4: Conversation history and chat privacy
**Objective:** As an owner, I want to reread the whole conversation with a match, so that I can pick it up where we left it.

#### Acceptance Criteria
1. When one of the two matched sides requests the history of its chat, the backend shall return all accepted messages in the order they were accepted.
2. The backend shall return each message with its sending side, its text, and the time it was accepted.
3. If a dog that is not one of the two matched sides of a chat requests its messages or history, the backend shall reject the request.

### Requirement 5: Discovering the chats of a dog
**Objective:** As an owner, I want to see the chats of my dog's matches, so that I can choose which conversation to open.

#### Acceptance Criteria
1. When a dog requests the chats it takes part in, the backend shall return every chat whose match includes that dog, ordered with the most recently active chat first.
2. The backend shall return, for each chat, the other dog of the match and the time of the chat's most recent accepted message.