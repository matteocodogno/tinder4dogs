package com.ai4dev.tinderfordogs.ai.observability.service

import com.ai4dev.tinderfordogs.ai.observability.model.CachedPrompt
import com.ai4dev.tinderfordogs.ai.observability.model.LangfusePrompt
import com.langfuse.client.resources.ingestion.requests.IngestionRequest
import com.langfuse.client.resources.ingestion.types.CreateGenerationBody
import com.langfuse.client.resources.ingestion.types.CreateGenerationEvent
import com.langfuse.client.resources.ingestion.types.IngestionEvent
import com.langfuse.client.resources.ingestion.types.TraceBody
import com.langfuse.client.resources.ingestion.types.TraceEvent
import com.langfuse.client.resources.prompts.requests.GetPromptRequest
import com.langfuse.client.resources.prompts.types.ChatMessage
import com.langfuse.client.resources.prompts.types.ChatMessageWithPlaceholders
import com.langfuse.client.resources.prompts.types.CreateChatPromptRequest
import com.langfuse.client.resources.prompts.types.CreateChatPromptType
import com.langfuse.client.resources.prompts.types.CreatePromptRequest
import com.langfuse.client.resources.prompts.types.CreateTextPromptRequest
import com.langfuse.client.resources.prompts.types.Prompt
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.langfuse.client.LangfuseClient as LangfuseSdkClient

private val logger = KotlinLogging.logger {}

@Service
class LangfuseService(
    @Value($$"${langfuse.prompt.cache_ttl_seconds:60}") private val cacheTtlSeconds: Long,
    private val langfuseClient: LangfuseSdkClient,
) {
    // key = "name:label" or "name:v{version}"
    private val promptCache = ConcurrentHashMap<String, CachedPrompt>()

    /**
     * Fetch a prompt from Langfuse by label (default: "production").
     * Results are cached for [cacheTtlSeconds]. Pass cacheTtl=0 to bypass cache.
     */
    suspend fun getPrompt(
        name: String,
        label: String = "production",
        cacheTtl: Long = cacheTtlSeconds,
    ): LangfusePrompt {
        val cacheKey = "$name:$label"

        if (cacheTtl > 0) {
            promptCache[cacheKey]?.let { cached ->
                val age = Instant.now().epochSecond - cached.cachedAt.epochSecond
                if (age < cacheTtl) {
                    logger.debug { "Prompt cache hit: $cacheKey (age=${age}s)" }
                    return cached.prompt
                }
            }
        }

        logger.info { "Fetching prompt from Langfuse: $name (label=$label)" }
        val request = GetPromptRequest.builder().label(label).build()
        val response = langfuseClient.prompts().get(name, request)

        val prompt = convertToLangfusePrompt(response)
        promptCache[cacheKey] = CachedPrompt(prompt, Instant.now())
        return prompt
    }

    /**
     * Fetch a prompt by exact version number.
     */
    suspend fun getPromptVersion(
        name: String,
        version: Int,
    ): LangfusePrompt {
        val cacheKey = "$name:v$version"
        promptCache[cacheKey]?.let { return it.prompt }

        logger.info { "Fetching prompt from Langfuse: $name (version=$version)" }
        val request = GetPromptRequest.builder().version(version).build()
        val response = langfuseClient.prompts().get(name, request)

        val prompt = convertToLangfusePrompt(response)
        promptCache[cacheKey] = CachedPrompt(prompt, Instant.now())
        return prompt
    }

    private fun convertToLangfusePrompt(response: Prompt): LangfusePrompt =
        when {
            response.isText -> {
                val textPrompt = response.text.get()
                LangfusePrompt(
                    name = textPrompt.name,
                    version = textPrompt.version,
                    type = "text",
                    prompt = textPrompt.prompt,
                    config =
                        (textPrompt.config as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value ?: "" }
                            ?: emptyMap(),
                    labels = textPrompt.labels,
                    tags = textPrompt.tags,
                )
            }

            response.isChat -> {
                val chatPrompt = response.chat.get()
                LangfusePrompt(
                    name = chatPrompt.name,
                    version = chatPrompt.version,
                    type = "chat",
                    prompt = chatPrompt.prompt,
                    config =
                        (chatPrompt.config as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value ?: "" }
                            ?: emptyMap(),
                    labels = chatPrompt.labels,
                    tags = chatPrompt.tags,
                )
            }

            else -> {
                throw IllegalStateException("Unknown prompt type")
            }
        }

    /**
     * Seed a prompt into Langfuse (create or add a new version).
     * Used by PromptSeeder on startup in dev/test environments.
     */
    suspend fun createPrompt(
        name: String,
        type: String,
        prompt: Any, // String for text, List<Map> for chat
        config: Map<String, Any>,
        labels: List<String>,
        tags: List<String>,
    ) {
        val createRequest =
            if (type == "text") {
                val textRequest =
                    CreateTextPromptRequest
                        .builder()
                        .name(name)
                        .prompt(prompt as String)
                        .config(Optional.ofNullable(config))
                        .labels(Optional.of(labels))
                        .tags(Optional.of(tags))
                        .build()
                CreatePromptRequest.of(textRequest)
            } else {
                // For chat prompts, the builder follows stages: name -> type -> config/labels/tags
                @Suppress("UNCHECKED_CAST")
                val messages =
                    (prompt as List<Map<String, String>>).map { msg ->
                        val chatMessage =
                            ChatMessage
                                .builder()
                                .role(msg["role"] ?: "")
                                .content(msg["content"] ?: "")
                                .build()
                        ChatMessageWithPlaceholders.of(chatMessage)
                    }
                val chatRequest =
                    CreateChatPromptRequest
                        .builder()
                        .name(name)
                        .type(CreateChatPromptType.CHAT)
                        .config(Optional.ofNullable(config))
                        .labels(Optional.of(labels))
                        .tags(Optional.of(tags))
                        .addAllPrompt(messages)
                        .build()
                CreatePromptRequest.of(chatRequest)
            }

        langfuseClient.prompts().create(createRequest)
        logger.info { "Seeded prompt: $name (labels=$labels)" }
    }

    fun invalidateCache(name: String) {
        promptCache.keys.filter { it.startsWith("$name:") }.forEach { promptCache.remove(it) }
        logger.info { "Invalidated prompt cache: $name" }
    }

    fun startTrace(
        name: String,
        sessionId: String? = null,
        input: Map<String, Any>,
        output: Map<String, Any>? = null,
    ): String {
        val traceId = UUID.randomUUID().toString()

        runCatching {
            // Create trace using TraceBody and TraceEvent
            val traceBody =
                TraceBody
                    .builder()
                    .name(Optional.of(name))
                    .apply {
                        sessionId?.let { sessionId ->
                            sessionId(Optional.of(sessionId))
                        }
                    }.input(Optional.ofNullable(input))
                    .apply {
                        output?.let { output ->
                            output(Optional.of(output))
                        }
                    }.build()
            val traceEvent =
                TraceEvent
                    .builder()
                    .id(traceId)
                    .timestamp(Instant.now().toString())
                    .body(traceBody)
                    .build()
            val ingestionEvent = IngestionEvent.traceCreate(traceEvent)
            val request = IngestionRequest.builder().batch(listOf(ingestionEvent)).build()
            langfuseClient.ingestion().batch(request)
        }.onFailure { logger.warn { "Failed to start trace $traceId: ${it.message}" } }

        return traceId
    }

    fun createGeneration(
        traceId: String,
        name: String,
        promptName: String,
        promptVersion: Int,
        model: String,
        input: List<Map<String, String>>,
        output: String,
        endTime: Instant,
        metadata: Map<String, Any> = emptyMap(),
    ): String {
        val generationId = UUID.randomUUID().toString()
        logger.info { "Creating generation $generationId for trace $traceId with prompt $promptName v$promptVersion" }

        runCatching {
            val generationBody =
                CreateGenerationBody
                    .builder()
                    .id(Optional.of(generationId))
                    .traceId(Optional.of(traceId))
                    .name(Optional.of(name))
                    .promptName(Optional.of(promptName))
                    .promptVersion(Optional.of(promptVersion))
                    .model(Optional.of(model))
                    .input(Optional.ofNullable(input))
                    .output(Optional.of(output))
                    .metadata(Optional.ofNullable(metadata))
                    .build()
            val generationEvent =
                CreateGenerationEvent
                    .builder()
                    .id(generationId)
                    .timestamp(endTime.toString())
                    .body(generationBody)
                    .build()
            val ingestionEvent = IngestionEvent.generationCreate(generationEvent)
            val request = IngestionRequest.builder().batch(listOf(ingestionEvent)).build()
            val response = langfuseClient.ingestion().batch(request)
            logger.info { "Create generation response: $response" }
        }.onFailure {
            logger.error(it) { "Failed to create generation $generationId: ${it.message}" }
        }

        return generationId
    }
}
