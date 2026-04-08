# Technology Stack

## Architecture

Single Spring Boot service (REST/MVC) backed by PostgreSQL. AI calls are proxied through LiteLLM; observability runs through Langfuse. No frontend yet — API-only for v1.

## Core Technologies

- **Language**: Kotlin 2.2.21 (strict JSR-305 null-safety, `all-open` for JPA entities)
- **Framework**: Spring Boot 4.0.4 — WebMVC (not WebFlux)
- **Runtime**: Java 24 (Zulu), managed via mise
- **Build**: Maven (mvnw wrapper) — `./mvnw` always, never system `mvn`
- **Database**: PostgreSQL via Spring Data JPA + Hibernate (PostgreSQLDialect)
- **Coroutines**: `kotlinx-coroutines-reactor` for async AI calls

## Key Libraries

- **LiteLLM** — local AI proxy for all LLM calls (configured in `application.yaml` as `litellm.url`)
- **Langfuse** (`langfuse-java 0.2.0`) — prompt management and AI observability
- **kotlin-logging-jvm** — structured logging (`KotlinLogging.logger {}`)
- **Jackson Kotlin module** — JSON serialisation; YAML data format also included
- **Spring Boot Validation** — bean validation on request models

## Development Standards

### Null Safety
Kotlin strict JSR-305 mode (`-Xjsr305=strict`). Never use `!!` without explicit justification.

### Code Quality
- JaCoCo coverage reports generated on `mvn test` (target: `target/site/jacoco/`)
- `gitleaks` pre-commit for secret scanning
- Conventional Commits enforced (see CLAUDE.md)

### Testing
- JUnit 5 + Spring Boot Test slices; MockK for Kotlin mocking
- Integration tests hit real database (TestContainers pattern expected for DB tests)
- Coverage: `./mvnw verify` produces JaCoCo report

## Development Environment

### Required Tools (managed via mise / `.tool-versions`)
```
java  zulu-25.28.85
maven 3.9.12
kotlin 2.3.10
nodejs 25.8.1
```

Other required tools: `docker`, `just`, `direnv`, `gitleaks`, `infisical` (or 1Password CLI)

### Required Environment Variables
`LITELLM_MASTER_KEY`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`

### Common Commands
```bash
# Dev (auto-reload):  just dev
# Build:              just build        (./mvnw clean install)
# Test:               just test         (./mvnw test)
# Coverage:           just test-coverage (./mvnw verify)
# AI review:          just review <file>
# AI gen-tests:       just gen-tests <file>
# Commit msg:         just commit-msg
```

## Key Technical Decisions

- **MVC over WebFlux**: simpler coroutine integration; team familiarity
- **LiteLLM proxy**: all LLM providers routed through a single local endpoint — avoids hardcoding provider SDKs in the app
- **Langfuse for prompts**: prompts are managed server-side in Langfuse (not hardcoded), fetched at runtime with a configurable TTL cache
- **pgvector planned**: compatibility vectors to be stored in PostgreSQL pgvector extension for semantic search (not yet implemented)
- **Secrets via direnv + Infisical/1Password**: no `.env` files committed; `direnv allow` loads env from secret manager

---
_Document standards and patterns, not every dependency_
