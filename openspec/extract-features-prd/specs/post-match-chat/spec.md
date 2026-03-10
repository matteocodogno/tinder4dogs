## ADDED Requirements

### Requirement: Chat Session Initialization
The system SHALL enable a private text conversation between two owners once a match is created.

#### Scenario: Starting a chat after a match
- **WHEN** user A and user B match
- **THEN** a private chat session becomes available to both

### Requirement: Match Expiry
The system SHALL automatically expire a match after 30 days of inactivity (no messages sent by either party).

#### Scenario: Match expiry after 30 days
- **WHEN** more than 30 days pass since the last message in a chat
- **THEN** the match is expired and the chat session is closed
