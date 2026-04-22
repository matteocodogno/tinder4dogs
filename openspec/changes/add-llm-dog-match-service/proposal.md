## Why

The current matching logic in `DogMatcherService` uses basic heuristics like age and breed, which fail to capture the nuances of dog compatibility such as temperament, energy levels, and social behavior. An LLM-powered matching service will leverage natural language processing to analyze dog profiles and provide more accurate and descriptive compatibility assessments.

## What Changes

- Create a new `LLMDogMatcherService` that interacts with `LiteLLMService`.
- Implement logic to construct prompts from dog profiles for compatibility evaluation.
- Define a structured output format for the LLM to return compatibility scores and reasoning.
- Add a new capability `llm-dog-matching` to formalize these requirements.

## Capabilities

### New Capabilities
- `llm-dog-matching`: Advanced compatibility analysis using LLMs to evaluate matches based on temperament, preferences, and profile descriptions.

### Modified Capabilities
- None

## Impact

- **Services**: New `LLMDogMatcherService` in the `match` module.
- **AI**: Increased usage of `LiteLLMService`.
- **Latency**: Matching operations using the LLM will be slower than heuristic ones; may require caching or background processing in the future.
