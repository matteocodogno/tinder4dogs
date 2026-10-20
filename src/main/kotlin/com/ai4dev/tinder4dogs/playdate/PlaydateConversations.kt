package com.ai4dev.tinder4dogs.playdate

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

data class PlaydateTurn(val owner: String, val assistant: String)

/**
 * The conversation with the playdate agent, per owner, in memory.
 *
 * Only the text of each turn is kept: what the owner wrote and what the agent
 * answered. The tool calls and their results are NOT kept, so a later message
 * cannot rely on an id the agent saw in an earlier one. That is why LAB 3 adds
 * a tool that lists the owner's playdates.
 *
 * Lost on restart, and bounded, the same way as the profile chat.
 */
@Component
class PlaydateConversations {

    private val byOwner = ConcurrentHashMap<String, MutableList<PlaydateTurn>>()

    fun history(ownerSubject: String): List<PlaydateTurn> =
        byOwner[ownerSubject].orEmpty().toList()

    fun record(ownerSubject: String, owner: String, assistant: String) {
        byOwner.compute(ownerSubject) { _, existing ->
            val turns = existing ?: mutableListOf()
            turns.add(PlaydateTurn(owner.take(MAX_TURN_CHARACTERS), assistant.take(MAX_TURN_CHARACTERS)))
            while (turns.size > MAX_TURNS) turns.removeAt(0)
            turns
        }
    }

    private companion object {
        const val MAX_TURNS = 8
        const val MAX_TURN_CHARACTERS = 500
    }
}
