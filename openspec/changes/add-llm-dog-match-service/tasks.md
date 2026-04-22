## 1. Prompt Configuration

- [x] 1.1 Create `src/main/resources/prompts/dog-matcher.yaml` with the system and user prompt templates.

## 2. Model and Service Implementation

- [x] 2.1 Create `LLMCompatibilityResult` data class in `com.ai4dev.tinderfordogs.match.model`.
- [x] 2.2 Implement `LLMDogMatcherService` in `com.ai4dev.tinderfordogs.match.service`.
- [x] 2.3 Inject `LiteLLMService` and `DogMatcherService` into `LLMDogMatcherService`.
- [x] 2.4 Implement logic to fetch and fill the prompt template from `dog-matcher.yaml`.
- [x] 2.5 Implement JSON parsing of the LLM response into `LLMCompatibilityResult`.
- [x] 2.6 Implement fallback logic to `DogMatcherService` in case of errors or timeouts.

## 3. Testing and Validation

- [x] 3.1 Create `LLMDogMatcherServiceTest` in `src/test/kotlin`.
- [x] 3.2 Implement tests for successful LLM-based matching.
- [x] 3.3 Implement tests for fallback behavior when the AI service fails.
- [x] 3.4 Verify that the reasoning returned by the LLM is correctly captured.
