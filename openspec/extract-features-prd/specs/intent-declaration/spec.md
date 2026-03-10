## ADDED Requirements

### Requirement: Session Intent Selection
The system SHALL require users to select an intent ("Playmate" or "Breeding") at the start of each search session.

#### Scenario: Selection of playmate intent
- **WHEN** user selects "Playmate" intent at the session start
- **THEN** only profiles with matching intent are shown in the search results

### Requirement: Intent Scope
The system SHALL NOT store the intent permanently; it is scoped only to the current search session.

#### Scenario: New session restart
- **WHEN** user restarts the app or begins a new session
- **THEN** system prompts for intent selection again
