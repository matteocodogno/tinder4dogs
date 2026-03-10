## ADDED Requirements

### Requirement: Search Radius Setting
The system SHALL allow users to specify a search radius in kilometers.

#### Scenario: Setting 5km radius
- **WHEN** user sets the radius to 5km
- **THEN** only dog profiles within 5km of the user's approximate location are returned

### Requirement: Location Privacy
The system SHALL NOT expose the user's exact coordinates to other users. Only an approximate distance (e.g., "2.3 km away") SHALL be displayed.

#### Scenario: Displaying distance to another dog
- **WHEN** a user views another dog profile
- **THEN** only the relative distance is shown, never the exact coordinates
