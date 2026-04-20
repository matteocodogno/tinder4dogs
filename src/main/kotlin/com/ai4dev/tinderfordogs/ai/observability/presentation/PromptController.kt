package com.ai4dev.tinderfordogs.ai.observability.presentation

import com.ai4dev.tinderfordogs.ai.observability.model.PromptExecuteRequest
import com.ai4dev.tinderfordogs.ai.observability.model.PromptResult
import com.ai4dev.tinderfordogs.ai.observability.model.PromptSummary
import com.ai4dev.tinderfordogs.ai.observability.service.LangfuseService
import com.ai4dev.tinderfordogs.ai.observability.service.PromptInjectionException
import com.ai4dev.tinderfordogs.ai.observability.service.PromptRegistry
import com.ai4dev.tinderfordogs.ai.observability.service.TracedPromptExecutor
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/prompts")
class PromptController(
    private val executor: TracedPromptExecutor,
    private val registry: PromptRegistry,
    private val langfuse: LangfuseService,
    @Value($$"${langfuse.prompt.label:production}") private val defaultLabel: String,
) {
    @GetMapping
    suspend fun listPrompts(
        @RequestParam(defaultValue = "") label: String,
    ): List<PromptSummary> {
        val resolvedLabel = label.ifBlank { defaultLabel }
        return registry.listAll().mapNotNull { id ->
            runCatching {
                val t = registry.get(id, resolvedLabel)
                PromptSummary(t.id, t.version, t.tags, resolvedLabel)
            }.getOrNull()
        }
    }

    @PostMapping("/execute")
    suspend fun execute(
        @RequestBody req: PromptExecuteRequest,
    ): PromptResult =
        executor.execute(
            promptId = req.promptId,
            variables = req.variables,
            sessionId = req.sessionId,
            label = req.label ?: defaultLabel,
        )

    @PostMapping("/{promptId}/invalidate-cache")
    fun invalidateCache(
        @PathVariable promptId: String,
    ): Map<String, String> {
        langfuse.invalidateCache(promptId)
        return mapOf("status" to "cache invalidated", "promptId" to promptId)
    }

    @ExceptionHandler(PromptInjectionException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInjection(ex: PromptInjectionException) = mapOf("error" to "prompt_injection", "message" to ex.message)

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(ex: IllegalStateException) = mapOf("error" to "prompt_not_found", "message" to ex.message)
}
