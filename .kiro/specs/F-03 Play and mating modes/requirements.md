# Requirements Document — F-03: Play and Mating Modes

## Introduction

tinder4dogs is a REST backend that stores dog profiles and computes a compatibility
score between two dogs. Today it applies a single, mode-agnostic score to every pair,
which biases every ranking toward mating criteria even for owners looking for a
playmate.

Feature F-03 introduces **modes**. A dog profile declares whether it is looking for a
**playmate**, a **mating partner**, or **both**. Each mode has its own candidate list
and its own compatibility score, so owners see only dogs relevant to the goal they
chose, ranked by rules that fit that goal.

Scope decisions confirmed with the product owner for this feature:

- A dog profile always has at least one active mode; creating a profile without one is
  rejected.
- Active modes can be changed at any time, but never down to none.
- Activating mating mode is rejected until the mating-specific profile fields
  (pedigree, neutering status, health status) are present.
- A candidate list for a mode the dog does not have active does not exist.

## Boundary Context

- **In scope**:
  - Declaring and changing the active modes (play, mating, or both) of a dog profile.
  - Mode-specific candidate lists: which dogs belong to each list, and how each list
    is ordered.
  - Mode-specific compatibility scores: each mode has its own score for a pair of
    dogs.
  - The mating-mode activation gate on the mating-specific profile fields.
- **Out of scope**:
  - The semantic input rules of the mating-specific profile fields — pedigree,
    neutering status, health status (owned by F-02 Dog profile). As a bridging
    decision confirmed with the product owner, F-03 carries their minimal
    nullable storage and transport so that the mating gate is complete and
    testable before F-02 exists.
  - The scoring criteria and weights for play and for mating (owned by F-04
    Compatibility match score).
  - Location, radius and distance display (owned by F-05 Location-based dog list).
  - User-configurable search filters (owned by F-06 Search filters).
  - Swipes, likes, matches, notifications and chat (owned by F-07, F-08, F-09).
  - What happens to past likes or matches when a mode is switched off: no likes or
    matches exist in this backend, so F-03 defines none.
  - Per-mode product metrics such as mode distribution or matches by mode (owned by
    F-11 Product metrics tracking).
- **Adjacent expectations**:
  - F-02 owns the input rules and any verification of pedigree, neutering status
    and health status; in v1 they are self-declared with no document verification.
    Until F-02 exists, F-03 stores and transports them as nullable passthrough
    values, and the mating gate checks only their presence.
  - F-04 defines the criteria of the play score (size, energy, temperament, age) and
    of the mating score (breed, size, sex, age, pedigree, neutering, health); this
    feature requires only that each mode has its own score and its own ordering.
  - F-05 will further restrict each mode's list by a location radius; until then, a
    mode's list contains every other dog with that mode active.

## Requirements

### Requirement 1: Declaring and changing the active modes

**Objective:** As an owner, I want to choose whether my dog is looking for a playmate, a mating partner, or both, so that my dog is only suggested for the goals I actually want.

#### Acceptance Criteria

1. When a dog profile is created, the tinder4dogs service shall record the active mode or modes declared for it: play, mating, or both.
2. If a dog profile is created without at least one active mode, the tinder4dogs service shall reject the creation with an error that states that a mode is required.
3. When a dog profile is read, the tinder4dogs service shall return the dog's active modes as part of the profile.
4. When the active modes of a dog are changed, the tinder4dogs service shall use the new modes for every candidate list and score request that follows.
5. If a mode change would leave the dog with no active mode, the tinder4dogs service shall reject the change with an error that states that at least one mode must stay active.

### Requirement 2: A candidate list per mode

**Objective:** As an owner, I want each mode to have its own list of dogs, so that my play search and my mating search stay separate and each shows only relevant dogs.

#### Acceptance Criteria

1. While a dog has play mode active, the tinder4dogs service shall provide a play candidate list that contains only other dogs with play mode active, ordered by the play compatibility score from highest to lowest.
2. While a dog has mating mode active, the tinder4dogs service shall provide a mating candidate list that contains only other dogs with mating mode active, ordered by the mating compatibility score from highest to lowest.
3. The tinder4dogs service shall never offer a dog as a candidate for itself.
4. If a candidate list is requested for a mode that the dog does not have active, the tinder4dogs service shall report that no such list exists.
5. If a candidate in a mode's list cannot be scored for that mode, the tinder4dogs service shall leave that candidate out and still return the rest of the list.
6. If a dog cannot be scored for a mode it has active, the tinder4dogs service shall reject the request for that mode's list as unusable data rather than return an incomplete ranking.

### Requirement 3: A compatibility score per mode

**Objective:** As an owner, I want each mode to rank dogs with its own rules, so that the dogs shown first fit the goal I selected.

#### Acceptance Criteria

1. While two dogs both have play mode active, the tinder4dogs service shall provide a play compatibility score for the pair, computed with the play criteria only.
2. While two dogs both have mating mode active, the tinder4dogs service shall provide a mating compatibility score for the pair, computed with the mating criteria only.
3. Where two dogs have both modes in common, the tinder4dogs service shall provide the play score and the mating score as two separate values, and the two scores are allowed to differ for the same pair.
4. If a compatibility score is requested for a mode that either dog does not have active, the tinder4dogs service shall report that no such score exists.

### Requirement 4: The mating-mode activation gate

**Objective:** As an owner looking for a mating partner, I want mating mode to require the mating-specific profile details, so that every dog suggested for mating carries the information I need to decide.

#### Acceptance Criteria

1. If mating mode is activated for a dog, at profile creation or later, while the dog's profile is missing pedigree, neutering status or health status, the tinder4dogs service shall reject the activation with an error that names the missing information.
2. When mating mode is activated for a dog whose profile provides pedigree, neutering status and health status, the tinder4dogs service shall accept the activation.
3. The tinder4dogs service shall not require pedigree, neutering status or health status for a dog that is in play mode only.
