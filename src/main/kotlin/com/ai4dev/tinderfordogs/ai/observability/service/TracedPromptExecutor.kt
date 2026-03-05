package com.ai4dev.tinderfordogs.ai.observability.service

import com.ai4dev.tinderfordogs.ai.common.service.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.common.service.Message
import com.ai4dev.tinderfordogs.ai.observability.model.PromptResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class TracedPromptExecutor(
    private val llm: LiteLLMService,
    private val registry: PromptRegistry,
    private val sanitizer: PromptSanitizer,
    private val langfuse: LangfuseService,
    @Value($$"${langfuse.prompt.label:production}") private val defaultLabel: String,
) {
    suspend fun execute(
        promptId: String,
        variables: Map<String, String>,
        sessionId: String,
        label: String = defaultLabel,
    ): PromptResult {
        // 1. Sanitize all user-provided variables
        variables.forEach { (key, value) ->
            val result = sanitizer.sanitize(value)
            if (result.blocked) throw PromptInjectionException("Variable '$key': ${result.reason}")
        }

        // 2. Fetch template from Langfuse (with YAML fallback)
        val template = registry.get(promptId, label)
        val userMessage = renderTemplate(template.userTemplate, variables)

        // 3. Start trace — include prompt linkage for native Langfuse tracking
        val traceId =
            langfuse.startTrace(
                name = "$promptId@${template.version}",
                sessionId = sessionId,
                input =
                    mapOf(
                        "variables" to variables,
                        "promptName" to promptId, // ← native Langfuse prompt linkage
                        "promptVersion" to template.version,
                        "promptLabel" to label,
                    ),
            )

        // 4. Call LLM
        val messages =
            listOf(
                Message("system", template.systemPrompt),
                Message("user", userMessage),
            )
        val startTime = java.time.Instant.now()
        val response =
            llm.chat(
                ChatRequest(
                    model = template.model,
                    messages = messages,
                    metadata = mapOf("trace_id" to traceId),
                ),
            )
        val endTime = java.time.Instant.now()
        val latencyMs = System.currentTimeMillis() - startTime.toEpochMilli()
        val output =
            response.choices
                .first()
                .message.content

        // 5. Create Generation with complete data (input + output + prompt linkage)
        langfuse.createGeneration(
            traceId = traceId,
            name = "tinder4dogs",
            promptName = promptId,
            promptVersion = template.version.toInt(),
            model = template.model,
            input = messages.map { mapOf("role" to it.role, "content" to it.content) },
            output = output,
            endTime = endTime,
            metadata =
                mapOf(
                    "latencyMs" to latencyMs,
                    "promptLabel" to label,
                ),
        )

        return PromptResult(
            output = output,
            traceId = traceId,
            latencyMs = latencyMs,
            promptId = promptId,
            promptVersion = template.version,
        )
    }

    private fun renderTemplate(
        template: String,
        variables: Map<String, String>,
    ): String = variables.entries.fold(template) { acc, (key, value) -> acc.replace("{{$key}}", value) }
}
