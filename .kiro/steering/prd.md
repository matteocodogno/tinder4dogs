# PRD — Tinder4dogs

> **Version:** 1.2 · **Status:** Draft · **Date:** 2026-03-09

---

## Problem Statement

Finding a playmatce or breeding partner for a dog is today a scattered and inefficient process: owners rely on Facebook groups, word of mouth, or chance encounters at the park. No dedicated tool exists that lets owners filter compatible dogs by characteristics and geographic proximity. Tinder4dogs solves this by offering a structured, geolocation-driven matching platform that is oriented around the owner's declared intent.

---

## Target Users & Personas

### Persona 1 — Marco, private owner (playmate seeker)
- **Profile:** 30 years old, city dweller, owns a 3-year-old high-energy Labrador. Has no garden and takes his dog to the park every day.
- **Pain points:** His dog gets bored; he struggles to find other dogs with a compatible temperament and size to play with regularly.
- **Goals:** Find nearby dogs for his dog to socialise with on a recurring basis.

### Persona 2 — Giulia, private owner (occasional breeding)
- **Profile:** 38 years old, owns a pedigree female Golden Retriever. Wants to breed her once, with a certified and healthy male.
- **Pain points:** Doesn't know where to find a suitable partner; online groups are noisy and hard to filter.
- **Goals:** Find a male compatible by breed, pedigree, and health, within a reachable geographic area.

---

## Value Proposition

Tinder4dogs is the first dog-matching app that combines proximity-based geolocation, declared intent, and behavioural/profile filters in an intuitive swipe experience. Unlike generic social groups, every search session is contextualised by the owner's goal — socialisation or breeding — ensuring matches that are relevant and actionable.

---

## Feature List

### F-01 — User Registration & Account · Priority: P0
**Description:** The user creates a personal account with email and password.
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to create an account so that I can access the platform and manage my dog's profile.
**Success metrics:** Registration completion rate > 80%.
**Constraints / notes:** MVP is single-account, single-dog per user.

---

### F-02 — Dog Profile · Priority: P0
**Description:** The user creates a profile for their dog with the following attributes: name, photos, breed, age, sex, size, temperament, energy level, pedigree (yes/no).
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to enter my dog's characteristics so that other users can assess compatibility.
**Success metrics:** % of profiles with all fields completed > 70%.
**Constraints / notes:** At least one photo, breed, age, and sex are mandatory. Maximum 5 photos per dog profile.

---

### F-03 — Intent Declaration · Priority: P0
**Description:** Before each search session, the user declares their goal: "Playmate" or "Breeding". The declared intent filters the profiles shown and the available search filters.
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to declare my goal before searching so that I only see relevant profiles.
**Success metrics:** 100% of swipe sessions associated with a declared intent.
**Constraints / notes:** Intent is per-session, not stored permanently.

---

### F-04 — Geolocation & Search Radius · Priority: P0
**Description:** The user sets a search radius in km. Only dogs whose approximate location falls within the radius are shown. Location is acquired dynamically at the start of each search session. The exact location is never exposed: other users only see an approximate distance (e.g. "2.3 km away").
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to see only dogs near me so that I can realistically organise a meeting.
**Success metrics:** 0 exposures of exact location; % of matches within the set radius = 100%.
**Constraints / notes:** Non-negotiable privacy requirement. Location is acquired only with the user's explicit consent.

---

### F-05 — Swipe & Matching · Priority: P0
**Description:** The user browses compatible dog profiles one at a time via swipe (right = interested, left = pass). When two users both express interest, a match is created.
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to swipe through profiles and indicate interest so that I can find compatible dogs effortlessly.
**Success metrics:** Number of matches generated per week; right-swipe rate > 10%.
**Constraints / notes:** Profiles shown are filtered by geographic radius and intent. Additional filters (breed, size, age, sex, temperament, energy level, pedigree) can be applied optionally.

---

### F-06 — Post-Match Chat · Priority: P0
**Description:** After a match, both owners can start an in-app text conversation to get to know each other and arrange a meeting.
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to chat with the other owner after a match so that I can independently plan a meetup.
**Success metrics:** Number of messages exchanged per match; % of matches with at least one message sent.
**Constraints / notes:** No in-app booking or calendar management in the MVP. Chat is text-only. Matches expire automatically after 30 days of inactivity (no message sent by either party).

---

### F-07 — Search Filters · Priority: P1
**Description:** The user can refine the search by applying filters on: breed, size, age, sex, temperament, energy level, pedigree.
**Personas served:** Giulia (breeding), Marco (playmate)
**User story:** As an owner, I want to filter profiles by specific characteristics so that I only see dogs that are truly compatible with mine.
**Success metrics:** % of sessions with at least one active filter > 40%.
**Constraints / notes:** Filters are optional and non-blocking.

---

## Non-Functional Requirements

| ID | Category | Requirement | Acceptance Criterion | Priority | Source |
|---|---|---|---|---|---|
| NFR-01 | Privacy | The user's geographic location must never be exposed precisely to other users | Displayed distance is always approximate (e.g. rounded to the nearest 100m); exact coordinates never appear in any public API response | P0 | explicit |
| NFR-02 | Security | Users' personal data must be protected from unauthorised access | JWT authentication with expiry ≤ 24h; all communications over HTTPS/TLS 1.2+; passwords hashed with bcrypt | P0 | implicit |
| NFR-03 | Compliance | Processing of personal and geolocation data must comply with GDPR | Explicit consent collected before location acquisition; privacy policy available; right to erasure implemented within 30 days of request | P0 | implicit |
| NFR-04 | Performance | Swipe cards must load without perceptible latency | Next profile load time < 500ms at the 95th percentile on a 4G connection | P1 | implicit |
| NFR-05 | Scalability | The system must sustain user growth without extraordinary architectural changes | Architecture must support up to 10,000 daily active users without performance degradation | P1 | implicit |
| NFR-06 | Availability | The platform must be available during peak hours | Uptime ≥ 99% measured monthly | P1 | implicit |
| NFR-07 | Usability | The swipe experience must be immediately understandable without onboarding | Swipe pattern is standard (Tinder-like); no mandatory tutorial required to complete the first swipe | P1 | implicit |
| NFR-08 | Observability | Product metrics must be monitorable in real time | Dashboard showing DAU/WAU, matches/week, and messages/day, updated at least every hour | P1 | explicit |
| NFR-09 | Maintainability | The codebase must be structured to allow introduction of the premium tier without major refactoring | Authorisation logic decoupled from domain logic; feature flags for premium features already in place | P2 | implicit |

### Unaddressed Categories
- **Reliability** — No Recovery Time Objective (RTO) or Recovery Point Objective (RPO) targets have been defined for failure scenarios. Should be addressed before public launch.
- **Portability** — Deployment strategy (cloud provider, containerisation) has not been discussed. Relevant for future scalability.

---

## Out of Scope — v1
- Premium tier for professional breeders (multi-dog profiles, boosted visibility)
- In-app appointment or calendar management
- Push notifications (to be evaluated post-MVP)
- Native mobile app (iOS/Android)
- Integration with official registries (e.g. ENCI/AKC) for pedigree verification
- User reviews or reputation system
- In-chat content moderation

---

## Resolved Product Decisions

| # | Question | Decision |
|---|---|---|
| Q1 | Chat moderation (spam, inappropriate content) | Out of scope for MVP — added to "Out of Scope v1" |
| Q2 | Location acquisition | Dynamic — updated at the start of each search session |
| Q3 | Photo limit per dog profile | Maximum 5 photos per profile |
| Q4 | Match expiry with no messages | Matches expire after 30 days of inactivity |
