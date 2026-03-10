# Project: Tinder for Dogs

## Domain
Tinder for Dogs — a dog-to-dog matching and playdate platform.
The platform facilitates dog-to-dog connections based on owner intent, breed characteristics, and geographic proximity.

## Personas
- **Dog Owner (casual)**: Primarily looking for playmates and socialization for their dog (e.g., Marco).
- **Breeder (professional)**: Looking for suitable breeding partners with specific health and pedigree requirements (e.g., Giulia).
- **Rescue Coordinator**: Looking for foster or adoption matches for dogs in rescue (e.g., facilitating playdates or potential forever homes).

## Stack
- **Language**: Kotlin 2.2 (JVM 24)
- **Framework**: Spring Boot 3 (Note: pom.xml indicates 4.0.2, but user specified Spring Boot 3)
- **Database**: PostgreSQL
- **AI Infrastructure**: 
    - **LiteLLM**: OpenAI-compatible proxy for all LLM calls.
    - **Langfuse**: Prompt management, tracing, and generation tracking.
- **Build Tool**: Maven / Justfile

## Core Features
- **Profile Management**: Detailed dog profiles (breed, age, temperament, photos).
- **Intent-based Discovery**: Users declare intent (Playmate vs. Breeding) per session.
- **Geolocation**: Proximity-based matching with privacy-preserving approximate distances.
- **Match & Chat**: Swipe-based matching followed by secure in-app messaging.

## Architectural Patterns
- **Layered Monolith**: Separation of concerns between presentation, service, and data layers.
- **AI Integration**: Centralized `LiteLLMService` and prompt management via `PromptRegistry`.
- **Privacy-First**: No exposure of exact coordinates in API responses.
