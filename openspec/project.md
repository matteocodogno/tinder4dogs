# Tinder for Dogs

A dog-to-dog matching and playdate platform designed to help dog owners, breeders, and rescue coordinators find the perfect matches for their canine companions.

## Domain
Tinder for Dogs is a social and matching platform focused on canine interactions. It facilitates playdates, breeding matches, and rescue placements through an AI-powered matching system.

## Stack
- **Language:** Kotlin (2.2.21)
- **Framework:** Spring Boot 4.0.4
- **Java Version:** 24
- **Database:** PostgreSQL
- **AI Integration:** LiteLLM (via `LiteLLMService`)
- **Observability:** Langfuse
- **Build Tool:** Maven

## Personas
1. **Dog Owner (Casual):** Looking for playdates and socialization for their pets.
2. **Breeder (Professional):** Seeking compatible matches for breeding purposes with a focus on genetics and health.
3. **Rescue Coordinator:** Aiming to find suitable foster or forever homes and social matches for dogs in rescue.

## Architectural Overview
The project is structured around several core domains:
- **AI/Common:** Configuration for HTTP clients and LiteLLM models.
- **AI/Fine-tuning:** Tools for data cleaning, augmentation, and pipeline management for OpenAI fine-tuning.
- **AI/Observability:** Integration with Langfuse for prompt management and tracing.
- **AI/RAG:** A "Mini RAG" service for knowledge-based queries.
- **Match:** Core dog matching logic.
- **Support:** Assistant and evaluation services for user support.

## Conventions
- **Package Structure:** `com.ai4dev.tinderfordogs`
- **Testing:** JUnit 5 with Spring Boot test support.
- **Data Access:** Spring Data JPA with PostgreSQL.
- **AI Patterns:** Prompt templates managed via Langfuse, RAG for knowledge retrieval.
