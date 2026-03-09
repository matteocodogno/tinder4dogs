# Project Structure

## Organization Philosophy

The codebase follows a **domain + technical sub-module** pattern. The root package `com.ai4dev.tinderfordogs` holds domain code; AI capabilities live in a dedicated `ai/` sub-tree organized by concern (observability, rag, finetuning, common).

## Directory Patterns

### Domain Core
**Location**: `src/main/kotlin/com/ai4dev/tinderfordogs/service/`
**Purpose**: Core business logic — matching algorithms, compatibility scoring
**Example**: `DogMatcherService.kt`

### Feature Modules
**Location**: `src/main/kotlin/com/ai4dev/tinderfordogs/<feature>/`
**Purpose**: Self-contained vertical slices — each has its own `presentation/`, `service/`, and `model/` sub-packages
**Example**: `support/presentation/SupportController.kt`, `support/service/SupportAssistantService.kt`, `support/model/CompatibilityRequest.kt`

### AI — Common
**Location**: `src/main/kotlin/com/ai4dev/tinderfordogs/ai/common/`
**Purpose**: Shared AI infrastructure — LiteLLM HTTP client (`@HttpExchange`), HTTP config
**Example**: `LiteLLMService.kt`

### AI — Observability
**Location**: `src/main/kotlin/com/ai4dev/tinderfordogs/ai/observability/`
**Purpose**: Langfuse integration — prompt registry, seeding, tracing, sanitization
**Example**: `TracedPromptExecutor.kt`, `PromptRegistry.kt`

### AI — RAG
**Location**: `src/main/kotlin/com/ai4dev/tinderfordogs/ai/rag/`
**Purpose**: Lightweight in-memory retrieval-augmented generation
**Example**: `MiniRagService.kt`, `Chunk.kt`

### AI — Fine-tuning
**Location**: `src/main/kotlin/com/ai4dev/tinderfordogs/ai/finetuning/`
**Purpose**: Fine-tuning pipeline: data loading, cleaning, augmentation, JSONL export, OpenAI job submission
**Pattern**: `service/` (pipeline steps) + `model/` (API response models) + `presentation/` (controller)

### Resources
**Location**: `src/main/resources/`
**Purpose**: App config (`application.yaml`), Langfuse prompt YAML fallbacks (`prompts/`), RAG knowledge base (`knowledge-base/`), fine-tuning seed data (`fine-tuning/`)

## Naming Conventions

- **Classes**: PascalCase (`DogMatcherService`, `SupportController`, `CompatibilityRequest`)
- **Controllers**: `<Domain>Controller` — REST entry points, always in a `presentation/` sub-package
- **Services**: `<Domain>Service` — business logic
- **DTOs/Models**: Descriptive noun as Kotlin `data class`
- **Config**: `<Concern>Config` (e.g., `LangfuseConfig`, `HttpClientConfig`)

## Package Conventions

```kotlin
// Pattern: com.ai4dev.tinderfordogs.<module>.<layer>
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.support.model.CompatibilityRequest
```

No path aliases — standard Maven/Kotlin package structure.

## Code Organization Principles

- **Presentation → Service only**: Controllers depend only on services; no direct repository access from controllers
- **AI isolation**: Domain code does not depend on `ai/` packages; `ai/` sub-modules share infrastructure via `ai/common/`
- **Self-contained modules**: Each feature module (`support/`, `ai/finetuning/`) owns its full vertical slice
- **New AI features**: Follow the `ai/<concern>/` sub-tree pattern with the same `model/service/presentation` split

---
_Document patterns, not file trees. New files following patterns shouldn't require updates_
