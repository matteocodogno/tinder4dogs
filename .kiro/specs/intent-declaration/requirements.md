# Requirements Document

## Project Description (Input)
Intent Declaration

## Introduction

The Intent Declaration feature is the mandatory first step of every search session on Tinder4Dogs. Before browsing profiles, an owner must declare their goal — **Playmate** (socialisation) or **Breeding** — which contextualises the entire session: it filters the candidate pool and determines which search filters are offered. Intent is ephemeral: it is bound to the session and never stored as a permanent preference. The intent token expires after 2 hours of inactivity; changing intent mid-session is not supported — the owner must close the current session and start a new one.

This feature is defined as F-03 in the product PRD (Priority: P0) and directly supports both primary personas: Marco (casual playmate seeker) and Giulia (private breeding owner).

---

## Requirements

### Requirement 1: Intent Selection Before Session Start

**Objective:** As an owner, I want to declare my search goal before browsing so that I only see relevant dog profiles.

#### Acceptance Criteria

1. When an authenticated owner initiates a new search session, the Intent Declaration Service shall present exactly two intent options: "Playmate" and "Breeding".
2. When an owner selects an intent option and confirms, the Intent Declaration Service shall record the selected intent and associate it with the current session.
3. If an owner attempts to access the swipe feed without having declared an intent in the current session, the Intent Declaration Service shall redirect the owner to the intent selection screen.
4. The Intent Declaration Service shall not pre-select any intent option by default, requiring an explicit owner choice.
5. When an owner selects an intent, the Intent Declaration Service shall make the selected intent visually distinguishable from the unselected option before confirmation.
6. The Intent Declaration Service shall not provide any affordance to change intent within an active session; to change intent, the owner must close the current session and start a new one.

---

### Requirement 2: Intent-Based Profile Pool Filtering

**Objective:** As an owner, I want the profile pool to be restricted to dogs relevant to my declared goal so that irrelevant profiles are never shown.

#### Acceptance Criteria

1. When a session has a declared intent of "Playmate", the Matching Service shall include only profiles whose owner has also declared "Playmate" intent in their active session.
2. When a session has a declared intent of "Breeding", the Matching Service shall include only profiles whose owner has also declared "Breeding" intent in their active session.
3. When a session has a declared intent of "Breeding", the Matching Service shall automatically restrict the candidate pool to dogs of the opposite sex relative to the requesting owner's dog, without requiring any manual filter by the owner.
4. The Matching Service shall not expose any profile from a mismatched intent category, regardless of geographic proximity or other filters applied.
5. While a session is active with a declared intent, the Matching Service shall apply intent filtering before any other filter (radius, breed, age, etc.) is evaluated.

---

### Requirement 3: Intent-Adaptive Search Filters

**Objective:** As an owner, I want the available search filters to reflect my declared intent so that only meaningful filters are presented.

#### Acceptance Criteria

1. When an owner declares "Playmate" intent, the Search Filter Service shall expose filters for: breed, size, age, energy level, and temperament.
2. When an owner declares "Breeding" intent, the Search Filter Service shall expose filters for: breed, age, pedigree (yes/no), and health criteria.
3. The Search Filter Service shall not expose the pedigree filter when intent is "Playmate".
4. The Search Filter Service shall not expose a sex filter when intent is "Breeding", as opposite-sex filtering is applied automatically by the Matching Service (Req 2.3).
5. When intent changes between sessions, the Search Filter Service shall reset all previously applied filters and present only the filters relevant to the new intent.

---

### Requirement 4: Session-Scoped Intent Lifecycle

**Objective:** As a platform, I want intent to be ephemeral and time-bounded so that owners are not locked into a past preference and resources are not held indefinitely.

#### Acceptance Criteria

1. The Intent Declaration Service shall not persist the declared intent beyond the current session boundary.
2. When a session ends (logout, explicit termination, or 2-hour idle timeout), the Intent Declaration Service shall discard the declared intent with no trace in the owner's permanent profile.
3. The Intent Declaration Service shall expire the intent token after 2 hours of session inactivity.
4. If an intent token has expired due to the 2-hour timeout, the Intent Declaration Service shall treat the next swipe feed access as a new session requiring a fresh intent declaration.
5. When an owner starts a new session after a previous session ends, the Intent Declaration Service shall require a fresh intent declaration without pre-filling the previous choice.
6. The Intent Declaration Service shall not expose the previously declared intent in any API response related to the owner's permanent profile.

---

### Requirement 5: Intent Declaration Enforcement

**Objective:** As a platform, I want every swipe session to be associated with a declared intent so that match quality metrics are always attributable to a goal.

#### Acceptance Criteria

1. The Matching Service shall reject any request to load the swipe feed that does not carry a valid, non-expired session-scoped intent token.
2. When a valid intent token is present in the session, the Intent Declaration Service shall include the intent value in all session-level analytics events.
3. The Intent Declaration Service shall ensure that 100% of swipe sessions are associated with a declared intent, as measured by session analytics.
4. If the intent token in a session is found to be invalid or expired, the Intent Declaration Service shall invalidate the swipe feed access and redirect the owner to the intent selection screen.

---

### Requirement 6: Session Analytics and Swipe Activity Tracking

**Objective:** As a platform, I want to distinguish zero-swipe sessions from active sessions so that success metrics accurately reflect engaged usage.

#### Acceptance Criteria

1. When a session is created with a declared intent, the Analytics Service shall emit a `session.started` event containing the intent value and a zero swipe count.
2. When an owner performs the first swipe in a session, the Analytics Service shall emit a `session.activated` event marking the transition from zero-swipe to active-swipe status.
3. The Analytics Service shall track swipe count per session and make the distinction between zero-swipe sessions and sessions with ≥ 1 swipe available in the analytics dashboard.
4. When a session ends (any cause), the Analytics Service shall emit a `session.ended` event with the final swipe count and the declared intent.
5. The Analytics Service shall not count a zero-swipe session as an "active" swipe session for the purposes of the 100% intent attribution success metric in Req 5.3.

---

## Non-Functional Requirements

### Performance
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-P1 | Intent selection screen must load without perceptible delay | Render time < 200ms at p95 | P1 | Req 1 |
| NFR-P2 | Intent-based profile filtering must not add latency to the swipe feed | Additional filter overhead < 50ms at p95 | P1 | Req 2 |

### Security
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-S1 | Intent is session-scoped and must not leak between sessions or users | No intent data accessible outside the originating session; verified by integration test | P0 | Req 4 |
| NFR-S2 | Intent token must be validated server-side on every swipe feed request | Server rejects feed requests with missing or tampered intent token | P0 | Req 5 |

### Scalability
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-SC1 | Intent filtering must not degrade under concurrent session load | Sustained correctness at up to 10,000 DAU without performance degradation | P1 | Req 2 |

### Reliability
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-R1 | Intent declaration flow must be available whenever the swipe feed is available | Uptime tied to overall platform SLA (≥ 99% monthly) | P1 | Req 1, Req 5 |

### Observability
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-O1 | Every swipe session must emit an intent-declared analytics event | 100% of swipe sessions traceable to a declared intent in the analytics dashboard | P0 | Req 5, Req 6 |
| NFR-O2 | Analytics must distinguish zero-swipe sessions from active sessions | Separate `session.started` and `session.activated` events available in dashboard | P1 | Req 6 |

### Usability
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-U1 | The intent selection screen must be immediately understandable without guidance | No mandatory tutorial; intent options self-explanatory; confirmed by usability heuristic review | P1 | Req 1 |
| NFR-U2 | Intent selection must require no more than two taps/clicks to complete | Select intent → confirm → enter swipe feed in ≤ 2 interactions | P1 | Req 1 |

### Constraints
| NFR ID | Description | Threshold | Priority | Source Req |
|--------|-------------|-----------|----------|------------|
| NFR-C1 | Intent is per-session only; permanent user profile must not store intent | Verified by data model review and integration test | P0 | Req 4 (PRD F-03) |
| NFR-C2 | Exactly two intent options in v1: "Playmate" and "Breeding" | No additional options added without explicit PRD change | P0 | PRD F-03 |
| NFR-C3 | Intent token TTL is fixed at 2 hours of inactivity | Token expiry enforced server-side; not configurable per user | P0 | Req 4 |

---

## Open Questions

| # | Question | Impact | Owner | Status |
|---|----------|--------|-------|--------|
| 1 | Should an owner be allowed to change their intent mid-session? | No mid-session change: owner must close session and start a new one | Product | **Resolved** |
| 2 | Is there a session timeout that automatically expires the intent token? | Intent token TTL is 2 hours of inactivity | Product / Tech | **Resolved** |
| 3 | Should analytics distinguish zero-swipe sessions from sessions with ≥ 1 swipe? | Yes — captured in Req 6 with `session.started` / `session.activated` events | Product | **Resolved** |
| 4 | Should Breeding intent automatically filter by opposite sex? | Yes — automatic, no manual sex filter needed for Breeding sessions (Req 2.3, Req 3.4) | Product | **Resolved** |
