package com.ai4dev.tinder4dogs.profile

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class ChatRequest(val message: String)

/**
 * The feature this module is about, and it is not implemented here on purpose.
 *
 * The endpoint exists, the single-page application already calls it, and it
 * answers 501. That is the hole the room fills: a system prompt, a typed
 * extraction, and the code-side validation that survives the prompt being
 * talked out of its instructions.
 *
 * Leave the 501. A participant who finds a working implementation here has been
 * given the answer to the exercise.
 */
@RestController
class ProfileChatController {

    @PostMapping("/api/profile/chat")
    fun chat(@RequestBody request: ChatRequest): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(
            mapOf(
                "error" to "not_implemented",
                "detail" to "The profile chat is the feature you are about to build. " +
                    "It needs a system prompt, a typed result and validation that does not " +
                    "depend on the model behaving.",
                "receivedCharacters" to request.message.length,
            ),
        )
}
