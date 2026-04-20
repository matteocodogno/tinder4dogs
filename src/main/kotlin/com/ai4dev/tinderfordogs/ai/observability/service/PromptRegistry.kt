package com.ai4dev.tinderfordogs.ai.observability.service

import com.ai4dev.tinderfordogs.ai.observability.model.LangfusePrompt
import com.ai4dev.tinderfordogs.ai.observability.model.OutputFormat
import com.ai4dev.tinderfordogs.ai.observability.model.PromptRole
import com.ai4dev.tinderfordogs.ai.observability.model.PromptTemplate
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.langfuse.client.resources.prompts.types.ChatMessage
import com.langfuse.client.resources.prompts.types.ChatMessageWithPlaceholders
import com.langfuse.client.resources.prompts.types.PlaceholderMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class PromptRegistry(
    private val langfuse: LangfuseService,
    @Value($$"${langfuse.prompt.label:production}") private val defaultLabel: String,
) {
    private val yamlMapper = YAMLMapper().registerKotlinModule()

    // YAML snapshots, loaded once at startup as fallback
    private val yamlFallbacks: MutableMap<String, PromptTemplate> = mutableMapOf()

    @PostConstruct
    fun init() {
        yamlFallbacks.putAll(loadYamlSnapshots())
        logger.info { "PromptRegistry initialized with ${yamlFallbacks.size} YAML snapshots: ${yamlFallbacks.keys}" }
    }

    /**
     * Fetch the prompt template from Langfuse (primary source).
     * Falls back to the YAML snapshot if Langfuse is unreachable.
     *
     * The [label] defaults to the environment-configured label:
     *   - "production" in prod
     *   - "latest"     in dev/test (set via langfuse.prompt.label=latest)
     */
    suspend fun get(
        name: String,
        label: String = defaultLabel,
    ): PromptTemplate =
        runCatching {
            val lf = langfuse.getPrompt(name, label)
            lf.toPromptTemplate().also {
                logger.debug { "Resolved prompt '$name' from Langfuse (label=$label, version=${lf.version})" }
            }
        }.getOrElse { ex ->
            logger.warn { "Langfuse unavailable for '$name' (${ex.message}) — using YAML fallback" }
            yamlFallbacks[name] ?: error("No YAML fallback found for prompt: $name")
        }

    /**
     * Blocking variant for use in non-suspend contexts (e.g. @PostConstruct, tests).
     */
    fun getBlocking(
        name: String,
        label: String,
    ): PromptTemplate = runBlocking { get(name, label) }

    fun getBlocking(name: String): PromptTemplate = getBlocking(name, defaultLabel)

    fun listAll(): List<String> = yamlFallbacks.keys.toList()

    fun getYamlFallback(name: String): PromptTemplate? = yamlFallbacks[name]

    // ── YAML Snapshots ─────────────────────────────────────────────────────

    private fun loadYamlSnapshots(): Map<String, PromptTemplate> {
        val snapshots = mutableMapOf<String, PromptTemplate>()
        PathMatchingResourcePatternResolver()
            .getResources("classpath:prompts/*.yaml")
            .forEach { resource ->
                runCatching {
                    val t: PromptTemplate = yamlMapper.readValue(resource.inputStream, PromptTemplate::class.java)
                    snapshots[t.id] = t
                    logger.info { "Loaded YAML snapshot: ${t.id} v${t.version}" }
                }.onFailure {
                    error { "Failed to load YAML snapshot ${resource.filename}: ${it.message}" }
                }
            }
        return snapshots
    }

    // ── LangfusePrompt → PromptTemplate ────────────────────────────────────

    private fun LangfusePrompt.toPromptTemplate(): PromptTemplate {
        val cfg = config

        // Chat prompts: extract system + user template from messages array
        val (systemPrompt, userTemplate) =
            if (type == "chat") {
                @Suppress("UNCHECKED_CAST")
                val messages =
                    (prompt as List<ChatMessageWithPlaceholders>).mapNotNull { wrapper ->
                        wrapper.visit(
                            object : ChatMessageWithPlaceholders.Visitor<ChatMessage?> {
                                override fun visit(value: ChatMessage): ChatMessage = value

                                override fun visit(value: PlaceholderMessage): ChatMessage? = null
                            },
                        )
                    }
                val system = messages.firstOrNull { it.role == "system" }?.content ?: ""
                val user = messages.firstOrNull { it.role == "user" }?.content ?: ""
                system to user
            } else {
                "" to (prompt as? String ?: "")
            }

        return PromptTemplate(
            id = name,
            version = version.toString(),
            role = PromptRole.DEVELOPER,
            systemPrompt = systemPrompt,
            userTemplate = userTemplate,
            outputFormat = OutputFormat.JSON,
            model = cfg["model"] as? String ?: "gpt-4o",
            maxTokens = (cfg["maxTokens"] as? Int) ?: 2000,
            temperature = (cfg["temperature"] as? Double) ?: 0.3,
            topP = (cfg["topP"] as? Double) ?: 0.9,
            tags = tags,
            changelog = "fetched from Langfuse (label=$defaultLabel)",
        )
    }
}
