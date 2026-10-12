package com.ai4dev.tinder4dogs.profile

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/** One exchange: what the owner said, and what we answered. */
data class ChatTurn(val owner: String, val assistant: String)

/**
 * The last few exchanges per owner, so that a one-word answer has something to
 * be an answer TO.
 *
 * Without this the feature loops, and the loop looks exactly like a broken
 * model: the owner writes "a labrador called Melo, five years old", the
 * assistant asks whether it is a male, the owner types "maschietto", and the
 * assistant asks for the dog's name again. Nothing was wrong with the model. It
 * was sent one word with no question attached and answered the only way it
 * could.
 *
 * Deliberately the simplest thing that works, and the limits are the design:
 *
 *  - **In memory**, so a restart forgets. A profile conversation lasts a minute
 *    and the profile itself is in PostgreSQL, so nothing that matters is here.
 *  - **Bounded per owner**, because this text is re-sent on every single call
 *    and an unbounded transcript turns a fixed cost into a growing one. That is
 *    the whole argument of a prompt that runs thousands of times a day.
 *  - **Not bounded by number of owners**, which is fine for one profile per
 *    signed-in person on a laptop and would not be fine in production. Spring
 *    AI's own ChatMemory is where this goes when it needs to be real.
 */
@Component
class ProfileChatConversations {

    private val byOwner = ConcurrentHashMap<String, MutableList<ChatTurn>>()

    fun history(ownerSubject: String): List<ChatTurn> =
        byOwner[ownerSubject].orEmpty().toList()

    fun record(ownerSubject: String, owner: String, assistant: String) {
        byOwner.compute(ownerSubject) { _, existing ->
            val turns = existing ?: mutableListOf()
            turns.add(
                ChatTurn(
                    owner = owner.take(MAX_TURN_CHARACTERS),
                    assistant = assistant.take(MAX_TURN_CHARACTERS),
                ),
            )
            while (turns.size > MAX_TURNS) turns.removeAt(0)
            turns
        }
    }

    fun forget(ownerSubject: String) {
        byOwner.remove(ownerSubject)
    }

    private companion object {
        const val MAX_TURNS = 8
        const val MAX_TURN_CHARACTERS = 500
    }
}
