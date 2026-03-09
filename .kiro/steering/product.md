# Product Overview

Tinder4Dogs is a dog-matching platform that connects dog owners seeking compatible partners for their pets — either for socialisation (playmates) or breeding. It combines proximity-based geolocation, declared intent, and behavioural/profile filters in an intuitive swipe experience.

## Core Capabilities

- **Intent-driven search**: Each session starts with a declared goal (Playmate or Breeding), filtering the pool to relevant profiles only
- **Geolocation matching**: Proximity-based discovery within a user-set radius; exact location is never exposed — only approximate distance
- **Swipe & mutual match**: Tinder-style swipe UI; a match is created only when both owners express mutual interest
- **Dog profile management**: Rich profiles with breed, age, sex, energy level, temperament tags, and up to 5 photos
- **Post-match chat**: Text-only in-app chat unlocked after a match; matches expire after 30 days of inactivity

## Target Use Cases

- Casual owner finding nearby playmates for a high-energy dog
- Private owner finding a compatible breeding partner by breed, pedigree, and health criteria
- Future: professional breeders managing multiple profiles (out of scope v1)

## Value Proposition

Unlike generic social groups, Tinder4Dogs contextualises every search by the owner's stated goal, ensuring matches are relevant and actionable. Privacy is non-negotiable: location data is approximate, never exact.

## MVP Boundaries

Single account / single dog per user. No push notifications, no booking/calendar, no native mobile app, no pedigree registry integrations. Premium tier (multi-profile, boosts) is architecturally planned but not yet implemented — authorization logic must remain decoupled from domain logic.

---
_Focus on patterns and purpose, not exhaustive feature lists_
