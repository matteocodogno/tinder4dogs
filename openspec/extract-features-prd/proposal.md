## Why

The current feature definitions exist in a legacy PRD document (`.kiro/steering/prd.md`). To leverage the OpenSpec workflow for implementation and validation, these features need to be extracted into structured capabilities and specifications. This establishes a formal contract for the MVP and enables automated task generation and tracking.

## What Changes

- **Extract** seven core features from the PRD into individual OpenSpec capabilities.
- **Formalize** requirements, success metrics, and constraints for each feature.
- **Establish** a baseline for the Tinder for Dogs MVP within the `openspec/` directory.

## Capabilities

### New Capabilities
- `user-account`: F-01 — User registration and basic account management.
- `dog-profile`: F-02 — Dog profile creation with attributes (breed, age, temperament, photos).
- `intent-declaration`: F-03 — Session-based declaration of intent (Playmate or Breeding).
- `geolocation-search`: F-04 — Privacy-preserving proximity-based search within a radius.
- `swipe-matching`: F-05 — Standard swipe experience and match generation based on filters.
- `post-match-chat`: F-06 — Secure in-app text messaging between matched owners.
- `search-filters`: F-07 — Advanced filtering by breed, size, age, and other attributes.

### Modified Capabilities
- (None)

## Impact

- **Artifacts**: New specification files will be created under `openspec/specs/`.
- **Workflow**: Future implementation tasks will be derived from these formal specs.
- **Documentation**: The PRD remains as the source of truth for product vision, while OpenSpec becomes the source of truth for technical requirements and implementation status.
