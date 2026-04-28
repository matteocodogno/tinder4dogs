package com.ai4dev.tinderfordogs.agent.service

import com.ai4dev.tinderfordogs.agent.tool.AgentContext
import com.ai4dev.tinderfordogs.agent.tool.DogMatchingTools
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class DogMatchingAgentService(
    private val chatClient: ChatClient,
    private val tools: DogMatchingTools,
    private val agentContext: AgentContext,
    private val objectMapper: ObjectMapper,
) {
    private val log = KotlinLogging.logger {}

    fun chat(message: String): String {
        log.info { "Agent chat: $message" }

        val parsed = parseRequest(message)

        val missing = buildList {
            if (parsed.dogName.isNullOrBlank()) add("the name of your dog")
            if (parsed.latitude == null || parsed.longitude == null) add("a location")
        }

        if (missing.isNotEmpty()) {
            return "I'd love to help! Could you also tell me ${missing.joinToString(" and ")}?"
        }

        agentContext.referenceDogName = parsed.dogName!!
        agentContext.referenceLatitude = parsed.latitude!!
        agentContext.referenceLongitude = parsed.longitude!!
        agentContext.preferredDate = parsed.date

        log.info { "Parsed: dog=${parsed.dogName} at (${parsed.latitude},${parsed.longitude}) date=${parsed.date}" }

        return chatClient.prompt()
            .system(AGENT_SYSTEM_PROMPT)
            .user(message)
            .tools(tools)
            .call()
            .content()
            ?: "Sorry, I could not find a recommendation right now."
    }

    private fun parseRequest(message: String): ParsedRequest {
        val raw = chatClient.prompt()
            .system(EXTRACTION_SYSTEM_PROMPT)
            .user(message)
            .call()
            .content()

        return runCatching { objectMapper.readValue(raw, ParsedRequest::class.java) }
            .getOrElse { ParsedRequest() }
    }

    private data class ParsedRequest(
        val dogName: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val date: String = "weekend",
    )

    companion object {
        private val EXTRACTION_SYSTEM_PROMPT =
            """
            Extract the following fields from the user's message and respond ONLY with a JSON object (no markdown, no extra text):
            {
              "dogName": "<name of the dog to find a match for, or null if not mentioned>",
              "latitude": <decimal latitude — resolve any place name or landmark to coordinates, or null if no location mentioned>,
              "longitude": <decimal longitude, or null if no location mentioned>,
              "date": "<preferred date, or 'weekend' if not specified>"
            }
            """.trimIndent()

        private val AGENT_SYSTEM_PROMPT =
            """
            You are a friendly dog matching assistant for the Tinder for Dogs platform.
            The reference dog's location and preferred date have already been resolved.

            Use the tools in this exact order and do NOT skip any step:
            1. Call searchDogProfiles to find nearby dogs.
            2. Call getMatchScore for EVERY dog returned in step 1.
            3. Call checkOwnerAvailability for the highest-scoring dog.

            Once you have the results, reply in warm, conversational prose — as a helpful chatbot would.
            Mention the best match's name, compatibility score, distance, and availability.
            Keep the response to 2-3 sentences.
            """.trimIndent()
    }
}
