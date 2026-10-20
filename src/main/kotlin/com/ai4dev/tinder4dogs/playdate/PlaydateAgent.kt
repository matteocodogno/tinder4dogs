package com.ai4dev.tinder4dogs.playdate

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

/**
 * The playdate agent: one owner message in, one reply out.
 *
 * The system prompt is read from the jar, `prompts/playdate-agent.md`, and NOT
 * through the prompt registry. LAB 4 and LAB 6 change that file, and a prompt
 * with the same name in Langfuse would silently win over the edit.
 */
@Service
class PlaydateAgent(
    private val chatClients: ObjectProvider<ChatClient.Builder>,
    private val conversations: PlaydateConversations,
    private val tools: PlaydateTools,
    private val resources: ResourceLoader,
    private val manualLoop: PlaydateLoop,
    @Value("\${app.playdate.loop:framework}") private val loop: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun reply(ownerSubject: String, message: String): String {
        val builder = chatClients.ifAvailable ?: return NO_MODEL
        val input = sanitise(message).take(MAX_INPUT_CHARACTERS)
        if (input.isEmpty()) return EMPTY

        val system = systemPrompt()
        val user = withHistory(conversations.history(ownerSubject), input)

        val answer = try {
            if (loop == "manual") {
                // LAB 7: the same loop, written by hand, with our own step limit.
                manualLoop.run(system, user, ownerSubject)?.trim()
            } else {
                builder.build()
                    .prompt()
                    .system(system)
                    .user(user)
                    // The tools, and who is acting. Spring AI runs the loop:
                    // model, tool, model, until the model answers.
                    .tools(tools)
                    .toolContext(mapOf(PlaydateTools.OWNER to ownerSubject))
                    .call()
                    .content()
                    ?.trim()
            }
        } catch (failure: Exception) {
            log.warn("the playdate agent could not answer: {}", failure.message)
            null
        }

        val reply = answer?.takeIf { it.isNotEmpty() } ?: UNREADABLE
        conversations.record(ownerSubject, input, sanitise(reply))
        return reply
    }

    /**
     * The prompt with today's date in it, so the model can turn "Saturday" into
     * a date. The placeholder is replaced here, by hand, because ChatClient
     * renders the system text as a template and a stray brace would break it.
     */
    private fun systemPrompt(): String {
        val text = resources.getResource(PROMPT_LOCATION).inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        val today = LocalDate.now()
        val label = today.toString() + ", " + today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return text.replace(TODAY_PLACEHOLDER, label)
    }

    /** The history travels inside the user message, as in the profile chat. */
    private fun withHistory(history: List<PlaydateTurn>, message: String): String {
        if (history.isEmpty()) return message
        val transcript = history.joinToString("\n") { "owner: " + it.owner + "\nyou: " + it.assistant }
        return "CONVERSATION SO FAR\n" + transcript + "\n\nTHE OWNER'S NEW MESSAGE\n" + message
    }

    /** Braces out: the text goes through the template renderer. */
    private fun sanitise(text: String): String = text.replace('{', ' ').replace('}', ' ').trim()

    private companion object {
        const val PROMPT_LOCATION = "classpath:/prompts/playdate-agent.md"
        const val TODAY_PLACEHOLDER = "{today}"
        const val MAX_INPUT_CHARACTERS = 2000
        const val NO_MODEL = "The service is not available: no model is configured."
        const val EMPTY = "Tell me what you would like to plan for your dog."
        const val UNREADABLE = "I could not answer. Please try again in a moment."
    }
}
