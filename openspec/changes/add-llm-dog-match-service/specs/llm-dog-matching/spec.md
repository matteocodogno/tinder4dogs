## ADDED Requirements

### Requirement: Calculate Compatibility Score
The system SHALL calculate a numerical compatibility score between 0 and 100 for a pair of dogs using an LLM. The LLM SHALL analyze dog profiles, including breed, age, gender, temperament, and preferences.

#### Scenario: Successful compatibility calculation
- **WHEN** a request is made to calculate compatibility between two dogs with valid profiles
- **THEN** the system returns a score between 0 and 100

### Requirement: Provide Compatibility Reasoning
The system SHALL provide a textual explanation explaining why the compatibility score was assigned, highlighting specific factors like shared energy levels or complementary temperaments.

#### Scenario: Reasoning includes specific profile details
- **WHEN** compatibility is calculated for a high-energy dog and a low-energy dog
- **THEN** the reasoning SHALL mention the difference in energy levels as a factor

### Requirement: Graceful Failure Handling
The system SHALL handle timeouts or errors from the LLM service.

#### Scenario: Fallback to heuristic matcher
- **WHEN** the LLM service is unavailable or times out
- **THEN** the system SHALL fall back to the heuristic `DogMatcherService` to provide a result
