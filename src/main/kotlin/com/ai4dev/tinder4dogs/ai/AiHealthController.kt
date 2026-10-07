package com.ai4dev.tinder4dogs.ai

import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The product's first model call, and for now its only one.
 *
 * It exists so that the first lab step of Module 12 has something to trace: one
 * request, through the course gateway, attributed to the participant, landing in
 * the dashboard next to the calls their coding agent made the day before.
 *
 * Two deliberate choices:
 *
 *  - the builder is injected through an [ObjectProvider], so a clone with no AI
 *    configuration starts normally instead of failing at context refresh
 *  - every exception is caught and reported as a payload. A health endpoint that
 *    throws tells you less than one that says what went wrong, and before the
 *    key is issued "what went wrong" is the expected answer.
 */
@RestController
class AiHealthController(
    private val chatClients: ObjectProvider<ChatClient.Builder>,
) {

    @GetMapping("/api/ai/health")
    fun health(): ResponseEntity<Map<String, Any?>> {
        val builder = chatClients.ifAvailable
            ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                mapOf(
                    "ok" to false,
                    "detail" to "No chat client is configured. Set the gateway base URL and the key.",
                ),
            )

        return try {
            val reply = builder.build()
                .prompt()
                .user("Reply with exactly: ready")
                .call()
                .content()
            ResponseEntity.ok(mapOf("ok" to true, "reply" to reply))
        } catch (failure: Exception) {
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                mapOf(
                    "ok" to false,
                    "detail" to (failure.message ?: failure::class.java.simpleName),
                ),
            )
        }
    }
}
