## Context

The Tinder for Dogs project currently has a high-level PRD and a technical stack overview. However, the core features lack a structured representation within the OpenSpec framework. This change focuses on formalizing those features into OpenSpec capabilities (`user-account`, `dog-profile`, etc.) to enable automated task generation and better tracking of the MVP progress.

## Goals / Non-Goals

**Goals:**
- Extract all seven core features from the legacy PRD into formal OpenSpec capabilities.
- Define a set of baseline requirements and scenarios for each capability.
- Ensure the OpenSpec artifacts are consistent with the existing project stack (Kotlin, Spring Boot, PostgreSQL, LiteLLM).

**Non-Goals:**
- Implementation of the features themselves (this change only defines the specs).
- Refactoring the existing codebase to match these specs (to be handled in subsequent changes).
- Defining advanced or premium features beyond the MVP scope.

## Decisions

- **Granularity of Specs**: We've decided to create one capability per core feature (F-01 to F-07) to maintain a clear 1:1 mapping with the PRD. This ensures that the original product intent is preserved while moving to a more structured format.
- **Requirement Specification**: Requirements are written using "SHALL" and "MUST" to denote normative behavior, and scenarios are provided in WHEN/THEN format for testability.
- **Directory Structure**: Each capability is placed in its own folder under `openspec/specs/` (following the OpenSpec convention) to allow for independent versioning and modularity.

## Risks / Trade-offs

- **Risk**: Discrepancies between the legacy PRD and the new OpenSpec requirements.
- **Mitigation**: The extraction process involves a careful review of the PRD to ensure all key constraints (like location privacy and match expiry) are captured accurately.
- **Trade-off**: The formalized specs may feel more rigid than the original PRD descriptions. However, this is a necessary trade-off to enable more automated and predictable development workflows.
