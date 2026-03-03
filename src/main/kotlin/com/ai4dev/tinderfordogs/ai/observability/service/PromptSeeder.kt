package com.ai4dev.tinderfordogs.ai.observability.service

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("dev", "test") // never runs in production
class PromptSeeder(
    private val langfuse: LangfuseService,
    private val registry: PromptRegistry,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(PromptSeeder::class.java)

    override fun run(args: ApplicationArguments) =
        runBlocking {
            logger.info("Seeding prompts from YAML snapshots into Langfuse...")

            // PromptRegistry.listAll() returns ids loaded from YAML
            registry.listAll().forEach { id ->
                runCatching {
                    val t = registry.getYamlFallback(id) ?: error("No YAML fallback for $id")

                    // Build the chat prompt array from the system + user template
                    val chatMessages =
                        listOf(
                            mapOf("role" to "system", "content" to t.systemPrompt),
                            mapOf("role" to "user", "content" to t.userTemplate),
                        )

                    langfuse.createPrompt(
                        name = t.id,
                        type = "chat",
                        prompt = chatMessages,
                        config =
                            mapOf(
                                "model" to t.model,
                                "temperature" to t.temperature,
                                "maxTokens" to t.maxTokens,
                                "topP" to t.topP,
                                "frequencyPenalty" to t.frequencyPenalty,
                                "presencePenalty" to t.presencePenalty,
                            ),
                        labels = listOf("latest"), // ← "production" promoted manually in the UI
                        tags = t.tags,
                    )

                    logger.info("Seeded: ${t.id}")
                }.onFailure {
                    // Non-fatal: if Langfuse is down, the YAML fallback will be used
                    logger.warn("Failed to seed '$id': ${it.message}")
                }
            }

            logger.info("Prompt seeding complete.")
        }
}
