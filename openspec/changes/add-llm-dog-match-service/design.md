## Context

The current `DogMatcherService` provides basic heuristic-based matching. To improve the user experience and provide more meaningful matches, we are introducing an LLM-based matching service that can analyze the nuances of dog profiles.

## Goals / Non-Goals

**Goals:**
- Implement `LLMDogMatcherService` using `LiteLLMService`.
- Use a YAML-based prompt template for compatibility analysis.
- Provide a compatibility score (0-100) and natural language reasoning.
- Implement a fallback mechanism to `DogMatcherService` on failure.

**Non-Goals:**
- Modifying the existing `Dog` data model.
- Implementing UI changes.
- Optimizing for high throughput (e.g., batching) in this iteration.

## Decisions

### 1. New Service: `LLMDogMatcherService`
- **Rationale**: Keeps the LLM logic separate from the heuristic logic, allowing for independent testing and evolution.
- **Location**: `com.ai4dev.tinderfordogs.match.service`.

### 2. Prompt Management
- **Decision**: Store the prompt template in `src/main/resources/prompts/dog-matcher.yaml`.
- **Rationale**: Consistent with existing patterns for prompt management in the codebase.

### 3. Response Format
- **Decision**: Request JSON response from the LLM containing `score` (int) and `reasoning` (string).
- **Rationale**: Simplifies parsing and ensures predictable output.

### 4. Error Handling
- **Decision**: Catch all exceptions from `LiteLLMService` and log them, then fall back to `DogMatcherService.calculateCompatibility`.
- **Rationale**: Ensures the system remains functional even if the AI service is down or behaving unexpectedly.

## Risks / Trade-offs

- **[Risk] LLM Latency** → [Mitigation] Set a reasonable timeout on the HTTP client and use the heuristic matcher as a fallback.
- **[Risk] Non-deterministic Output** → [Mitigation] Use a low temperature (e.g., 0.2) in the prompt configuration to encourage consistent results.
- **[Risk] High Token Usage** → [Mitigation] Keep profiles concise and use a cost-effective model if possible.
