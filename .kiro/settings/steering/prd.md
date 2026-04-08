# PRD — Tinder4dogs

> **Version:** 1.1 · **Status:** Draft · **Date:** 2026-04-08

---

## Problem Statement

Finding playmates or breeding partners for a dog is today a random and inefficient process: owners rely on chance encounters at the park, with no way to proactively search for compatible dogs by breed, size, temperament, or distance. PawMatch solves this by offering a matchmaking web app that enables dog owners to find, evaluate, and contact each other in a targeted way — turning a casual encounter into a deliberate choice.

---

## Target Users & Personas/

### Persona 1 — Marco, owner with a sociable dog
- **Profile:** 30 years old, lives in the city, owns a 2-year-old Labrador. Uses his smartphone for everything, not technically savvy.
- **Pain points:** Always goes to the same park; his dog always plays with the same dogs. He'd like variety but doesn't know where to find compatible new playmates.
- **Goals:** Find dogs of the right size and similar temperament, and organise meetups easily.

### Persona 2 — Giulia, owner interested in breeding
- **Profile:** 38 years old, owns a female Golden Retriever. Not a professional breeder, but wants to find a suitable partner for a litter.
- **Pain points:** Doesn't know who to turn to — online options are either disorganised forums or professional breeder sites that feel too formal.
- **Goals:** Find a breed- and health-compatible male, and be able to communicate with the owner before meeting in person.

---

## Value Proposition

PawMatch is the first dog matchmaking web app designed for everyday owners, combining intelligent compatibility scoring (breed, size, temperament, geolocation) with an integrated chat to organise real-world meetups. Unlike generic forums or chance encounters at the park, PawMatch brings the intentionality and simplicity of Tinder to the world of canine socialisation.

---

## Feature List

### F-01 — Registration & owner profile · Priority: P0
**Description:** User registration via email and password. The profile includes name, optional photo, and geographic location (city-level by default; more precise if the user explicitly consents).
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to create a secure account so that I can use the app and manage my dog's profile.
**Success metrics:** Profile completion rate > 70% after registration.
**Constraints / notes:** Data collection and processing must comply with GDPR and nLPD. Explicit consent required for geolocation. Passwords hashed with bcrypt or Argon2.

---

### F-02 — Dog profile · Priority: P0
**Description:** Each user creates a single dog profile with: name, photos, breed, size, age, sex, temperament (selectable tags: playful, calm, energetic, etc.), optional health/breeding fields (e.g. pedigree, health certificates — not mandatory), and purpose (socialisation / breeding / both).
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to create a detailed profile for my dog so that other users can assess compatibility before matching.
**Success metrics:** ≥ 80% of dog profiles have at least one photo and all mandatory fields filled in.
**Constraints / notes:** One dog profile per account (multi-dog support deferred to v2). A completed dog profile is required to access the matching feed.

---

### F-03 — Intelligent matching feed · Priority: P0
**Description:** The user browses a feed of nearby dogs within a configurable radius (5–100 km). The system ranks and filters suggestions based on: geographic distance, breed/size compatibility, temperament, and purpose (socialisation vs breeding). Swipe right (interested) / left (skip). A match is confirmed when both owners express mutual interest.
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to see compatible dogs near me so that I can find a good match without scrolling through irrelevant profiles.
**Success metrics:** ≥ 1 match per active user in the first week; mutual match rate > 15% of right swipes.
**Constraints / notes:** Compatibility vectors stored in pgvector for semantic search and scoring. Geolocation must never be exposed at address level — minimum approximation of 500 m applied server-side.

---

### F-04 — Owner chat · Priority: P0
**Description:** Once a mutual match is established, the two owners can open a text chat to organise a meetup or get to know each other better. Chat is accessible only between matched users. Message history is retained indefinitely.
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to chat with the owner of a matched dog so that I can safely arrange a meetup.
**Success metrics:** ≥ 50% of matches lead to at least one message sent.
**Constraints / notes:** No forced sharing of external contact details. Indefinite message retention; covered in privacy policy.

---

### F-05 — In-app notifications · Priority: P0
**Description:** Real-time in-app notifications (browser) for: new match, new message received.
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to receive a notification when I get a match or a message so that I don't miss any meetup opportunity.
**Success metrics:** Notification open rate > 40%.
**Constraints / notes:** Delivered via WebSocket or SSE. No mobile push notifications required for v1.

---

### F-06 — Match history · Priority: P0
**Description:** Each user can view their list of past matches and conversations, with the ability to reopen any chat.
**Personas served:** Marco, Giulia
**User story:** As an owner, I want to see my match and chat history so that I can find past contacts and keep track of organised meetups.
**Success metrics:** Feature used by ≥ 30% of monthly active users.
**Constraints / notes:** History visible only to the user themselves. Data retention compliant with GDPR/nLPD policies.

---

## Non-Functional Requirements

| ID | Category | Requirement | Acceptance Criterion | Priority | Source |
|---|---|---|---|---|---|
| NFR-01 | Performance | API response time for the matching feed | p95 < 500 ms under normal load | P0 | implicit (pgvector search) |
| NFR-02 | Security | Authentication & authorisation | JWT with expiry ≤ 1h + refresh token; no sensitive endpoint accessible without a valid token | P0 | implicit |
| NFR-03 | Privacy / Compliance | GDPR and nLPD compliance | Privacy policy published; explicit consent for geolocation; right to erasure implemented (account + data deleted within 30 days of request) | P0 | explicit |
| NFR-04 | Security | Geolocation data protection | Coordinates never exposed at exact address level; minimum approximation of 500 m applied server-side | P0 | implicit |
| NFR-05 | Scalability | Stateless backend | Spring Boot backend must be horizontally scalable — no in-memory session state | P1 | implicit |
| NFR-06 | Availability | Service uptime | ≥ 99.5% monthly uptime measured via external health check | P1 | unaddressed |
| NFR-07 | Observability | Structured logging | All 5xx errors logged with trace ID; automated alert if error rate > 1% over 5 min | P1 | implicit |
| NFR-08 | Reliability | Database migrations | Every Liquibase changeset must include a tested, functional rollback | P0 | explicit |
| NFR-09 | Usability | UI accessibility | Radix UI components used per WAI-ARIA guidelines; colour contrast meets WCAG AA | P1 | implicit |
| NFR-10 | Maintainability | Toolchain version management | All runtime versions (Node, Bun, Java, etc.) defined via mise (.mise.toml) and committed to the repository | P0 | explicit |
| NFR-11 | Portability | Reproducible builds | `bun install --frozen-lockfile` and `mvn verify` must pass in a clean CI environment with no implicit global dependencies | P1 | implicit |

### Unaddressed Categories
- **Disaster Recovery** — No backup/restore strategy defined. Relevant as soon as user data exists in production.
- **Rate Limiting** — No limits on swipes or messages to prevent abuse. Should be addressed before user growth scales.

---

## Out of Scope — v1
- Native mobile app (iOS / Android)
- Premium profiles or paid subscriptions
- User review and rating system
- Professional breeders / B2B features
- Mobile push notifications
- Owner identity or dog health verification
- Content moderation tooling or admin panel
- Geofencing or group event organisation
- Multi-dog support per account

---

## Open Questions
- **Q1 [Operations]:** Who handles infrastructure and on-call for production incidents at launch?
- **Q2 [Matching]:** Should the search radius default to a specific value within the 5–100 km range, or should the user set it explicitly on first use?
