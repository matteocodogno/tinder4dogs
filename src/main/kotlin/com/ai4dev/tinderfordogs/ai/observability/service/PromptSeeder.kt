package com.ai4dev.tinderfordogs.ai.observability.service

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Component responsible for seeding prompts into the Langfuse system during application startup.
 * This class is designed to run only in development or test environments and never in production.
 *
 * Prompts are loaded from YAML snapshots and registered in Langfuse if they do not already exist.
 * If a prompt already exists, it will be skipped.
 * In the event of an error (e.g., Langfuse is unavailable), the operation logs a warning and continues
 * without halting the application.
 *
 * This class implements the `ApplicationRunner` interface, ensuring that the `run` method is
 * executed during application startup.
 *
 * @property langfuse Service used to interact with the Langfuse system for prompt management.
 * @property registry Registry that provides a list of prompts stored in YAML for seeding.
 */
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

                    // Check if prompt already exists in Langfuse
                    val existingPrompt =
                        runCatching {
                            langfuse.getPrompt(
                                t.id,
                                label = "latest",
                                cacheTtl = 0,
                            )
                        }.getOrNull()

                    if (existingPrompt != null) {
                        logger.info("Skipping ${t.id}: already exists in Langfuse")
                        return@runCatching
                    }

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
