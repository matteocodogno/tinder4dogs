# Technology Stack

## Architecture

Layered Spring Boot monolith with a domain-centric core (`service/`) and AI capabilities isolated in sub-packages (`ai/`). All LLM calls are proxied through LiteLLM; observability is handled by Langfuse.

## Core Technologies

- **Language**: Kotlin 2.2 (JVM, `-Xjsr305=strict`)
- **Framework**: Spring Boot 4.0.2 (WebMVC, Data JPA, Validation)
- **Runtime**: Java 24
- **Database**: PostgreSQL (JPA/Hibernate, `ddl-auto: update`)
- **Build**: Maven with JaCoCo for coverage

## Key Libraries

- **LiteLLM**: OpenAI-compatible proxy for all LLM and embedding calls (`/chat/completions`, `/embeddings`)
- **Langfuse Java SDK 0.2.0**: Prompt management, tracing, and generation tracking
- **Kotlin Coroutines**: Used alongside Spring MVC for async patterns
- **kotlin-logging-jvm**: Structured logging (`KotlinLogging.logger {}`)

## Development Standards

### Kotlin Style
- Data classes for DTOs and domain models
- `@Service`, `@RestController` Spring stereotypes; constructor injection preferred (no field injection)
- Kotlin-Maven plugins: `spring`, `jpa`, `all-open` (for JPA entities)
- JSR-305 strict null safety enforced at compile time

### AI Integration Pattern
- LLM calls go through `LiteLLMService` — a declarative HTTP interface via `@HttpExchange`
- Prompts are managed via Langfuse `PromptRegistry` with TTL caching; local YAML fallback in `src/main/resources/prompts/`
- Traces and generations tracked via `TracedPromptExecutor`
- `dev`/`test` profiles use `latest` Langfuse label; production uses `production` label

### Testing
- JUnit 5 + `kotlin-test-junit5`
- Spring Boot test slices (`@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`)
- JaCoCo report generated at `test` phase

## Development Environment

### Required Tools
- Java 24, Maven 3.x
- Docker (for `compose.yaml` — PostgreSQL)
- LiteLLM proxy running on `http://localhost:4000`
- Langfuse running on `http://localhost:3000`

### Common Commands
```bash
# Start AI infra:   just ai-start
# Build:            just build   (or mvn clean package)
# Run:              just run     (port 8081)
# Test:             just test    (or mvn test)
# Code review:      just review <file>
```

### Environment Variables
- `LITELLM_MASTER_KEY` — LiteLLM proxy authentication
- `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` — Langfuse observability
- `OPENAI_API_KEY` — only for fine-tuning job submission

## Key Technical Decisions

- **LiteLLM proxy**: All AI models accessed through a single proxy — no direct vendor SDK imports
- **Langfuse prompt versioning**: Prompts stored and versioned in Langfuse; profile-based label selection (`dev` → `latest`, `production` → `production`)
- **Approximate location only**: Exact coordinates never returned in any API response (NFR-01)
- **Authorization decoupled from domain**: Planned ahead for the future premium tier without major refactoring (NFR-09)

---
_Document standards and patterns, not every dependency_
