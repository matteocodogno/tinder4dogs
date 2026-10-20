package com.ai4dev.tinder4dogs.playdate

import com.ai4dev.tinder4dogs.profile.CurrentOwner
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class PlaydateChatRequest(val message: String)

data class PlaydateChatResponse(val reply: String)

/**
 * The only way in to the agent. Who is talking comes from the session, here,
 * and from nowhere else: the agent receives it as a value, never as text the
 * model could rewrite.
 */
@RestController
class PlaydateChatController(
    private val agent: PlaydateAgent,
) {

    @PostMapping("/api/playdate/chat")
    fun chat(
        @AuthenticationPrincipal principal: OidcUser?,
        @RequestBody request: PlaydateChatRequest,
    ): PlaydateChatResponse =
        PlaydateChatResponse(agent.reply(CurrentOwner.subjectOf(principal), request.message))
}
