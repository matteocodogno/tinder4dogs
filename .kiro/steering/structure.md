# Project Structure

## Organization Philosophy

Domain-driven with layered sub-packages. Each domain module is self-contained: `model/`, `service/`, `presentation/` live inside the domain folder, not in global layers. Cross-cutting AI infrastructure lives under `ai/`.

## Directory Patterns

### Domain modules
**Location**: `src/main/kotlin/com/ai4dev/tinderfordogs/<domain>/`
**Pattern**: `model/` (data classes/entities) → `service/` (business logic, `@Service`) → `presentation/` (`@RestController`)
**Examples**: `match/`, `support/`

### AI infrastructure
**Location**: `src/main/kotlin/com/ai4dev/tinderfordogs/ai/`
**Sub-modules**: `common/` (shared config/clients), `observability/` (Langfuse), `finetuning/` (OpenAI fine-tuning pipeline), `rag/` (retrieval-augmented generation)
**Pattern**: same `model/` → `service/` → `presentation/` layering within each sub-module

### Resources
**Location**: `src/main/resources/`
**Conventions**:
- `application.yaml` — Spring config; secrets via env-var placeholders (`${VAR:default}`)
- `prompts/*.yaml` — Langfuse-managed prompt templates (YAML format)
- `knowledge-base/*.md` — RAG source documents
- `fine-tuning/*.json` — seed examples for model fine-tuning

### Tests
**Location**: `src/test/kotlin/` mirroring main package structure
**Naming**: `<ClassName>Test.kt` — e.g., `DogMatcherServiceTest.kt`

## Naming Conventions

- **Packages**: `lowercase`, domain-first (e.g., `match.service`, `ai.observability.config`)
- **Classes**: `PascalCase`; controllers end in `Controller`, services in `Service`
- **Data classes**: noun-only (e.g., `Dog`, `CompatibilityRequest`, `SupportResponse`)
- **Config beans**: `<Technology>Config` (e.g., `LangfuseConfig`)

## Code Organization Principles

- No global `controller/`, `service/`, `model/` packages — everything is nested under its domain
- Shared AI utilities go to `ai/common/`; nothing domain-specific leaks there
- Prompt content lives in Langfuse or `resources/prompts/`, never hardcoded in service classes
- `application.yaml` only holds config structure; real secrets come from environment

---
_Document patterns, not file trees. New files following patterns shouldn't require updates_
