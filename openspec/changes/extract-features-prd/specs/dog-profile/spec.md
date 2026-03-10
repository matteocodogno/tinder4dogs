## ADDED Requirements

### Requirement: Dog Profile Attributes
The system SHALL allow users to create a profile for their dog with the following mandatory attributes: Name, at least one photo (max 5), Breed, Age, and Sex. Optional attributes include: Size, Temperament, Energy Level, and Pedigree status.

#### Scenario: Complete profile creation
- **WHEN** user provides all mandatory attributes
- **THEN** a new dog profile is saved and linked to the user account

### Requirement: Profile Editing
The system SHALL allow users to update their dog's profile information.

#### Scenario: Updating temperament
- **WHEN** user modifies the temperament field and saves
- **THEN** the dog profile is updated with the new temperament value
