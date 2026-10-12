package com.ai4dev.tinder4dogs.profile

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class ChatRequest(val message: String)

data class ChatReplyResponse(
    val reply: String,
    val profile: ProfileResponse?,
)

/**
 * The owner describes their dog in free text and the profile fills itself in.
 *
 * The controller is thin on purpose: it resolves who is asking, hands the
 * sentence to [ProfileChatService], and shapes the answer. Everything that
 * decides what is true about the dog lives in the service, next to the
 * validation that makes those decisions safe.
 */
@RestController
class ProfileChatController(
    private val chat: ProfileChatService,
) {

    @PostMapping("/api/profile/chat")
    fun chat(
        @AuthenticationPrincipal principal: OidcUser?,
        @RequestBody request: ChatRequest,
    ): ResponseEntity<ChatReplyResponse> {
        val outcome = chat.complete(
            ownerSubject = CurrentOwner.subjectOf(principal),
            ownerFallbackName = CurrentOwner.displayNameOf(principal),
            message = request.message,
        )
        val body = ChatReplyResponse(
            reply = outcome.reply,
            profile = outcome.dog?.let(ProfileResponse::of),
        )

        // One status code carries real information: a laptop with no gateway
        // configuration is a setup problem, not a conversation that went badly,
        // and the interface should not present it as the model's answer.
        if (!chat.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body)
        }
        return ResponseEntity.ok(body)
    }
}
