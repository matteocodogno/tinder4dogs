# Product Overview

PawMatch is a dog matchmaking web app that helps owners find compatible playmates or breeding partners. It replaces chance park encounters with intentional, scored matching — combining geolocation, breed/temperament compatibility, and in-app chat into one focused tool for everyday dog owners.

## Core Capabilities

1. **Smart matching feed** — ranks nearby dogs by a compatibility score (breed, size, temperament, distance); mutual interest creates a Match
2. **Dog profiles** — each account manages one dog profile with photos, breed, age, sex, temperament tags, and purpose (socialisation / breeding / both)
3. **Owner chat** — unlocked only after a mutual match; retains full history
4. **AI-powered compatibility** — compatibility scored via LLM calls (LiteLLM proxy) and a breed-compatibility knowledge base; fine-tuning pipeline for continuous improvement
5. **Real-time notifications** — in-app alerts (WebSocket / SSE) for new matches and messages

## Target Use Cases

- Casual owner looking for dog playmates nearby
- Owner seeking a compatible breeding partner with health/breed filtering
- Future: rescue coordinator managing multiple profiles (v2)

## Value Proposition

Brings the intentionality and simplicity of swipe-based matching to canine socialisation, backed by an AI compatibility layer rather than random search. Privacy-first: geolocation is approximated server-side (≥ 500 m), no exact addresses ever exposed.

## Out of Scope (v1)

Native mobile app, paid subscriptions, admin/moderation panel, multi-dog accounts, mobile push notifications.

---
_Focus on patterns and purpose, not exhaustive feature lists_
