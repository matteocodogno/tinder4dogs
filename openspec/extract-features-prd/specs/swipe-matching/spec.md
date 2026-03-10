## ADDED Requirements

### Requirement: Swipe Action
The system SHALL support swiping through dog profiles: "Right" to express interest and "Left" to skip.

#### Scenario: Swiping right on a dog
- **WHEN** user swipes right on a dog profile
- **THEN** the system records the interest and moves to the next profile

### Requirement: Match Creation
The system SHALL create a match only when two users have both expressed interest in each other's dog profile.

#### Scenario: Mutual interest match
- **WHEN** user A swipes right on user B's dog, and user B swipes right on user A's dog
- **THEN** a match is created and both users are notified
