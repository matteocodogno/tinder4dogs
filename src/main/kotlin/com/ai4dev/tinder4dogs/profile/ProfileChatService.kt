package com.ai4dev.tinder4dogs.profile

import com.ai4dev.tinder4dogs.dog.Dog
import com.ai4dev.tinder4dogs.dog.DogRepository
import com.ai4dev.tinder4dogs.dog.DogSize
import com.ai4dev.tinder4dogs.dog.Gender
import com.ai4dev.tinder4dogs.dog.Temperament
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** What the controller hands back: one sentence, and the profile if it changed. */
data class ChatOutcome(
    val reply: String,
    val dog: Dog?,
)

/**
 * Fills in a dog profile from a sentence the owner typed.
 *
 * The shape to read first: **the model's answer is a proposal and nothing in
 * this class trusts it.** Every field goes through a mapper that either
 * recognises the value or drops it, every string is capped, and the age has to
 * be a number a dog could plausibly be. That is not belt and braces on top of a
 * good prompt, it is the only part of this file that still works when the prompt
 * has been talked out of its instructions.
 *
 * The second shape: fields are MERGED, never replaced. A profile is filled in
 * over several messages, and a second sentence that happens to mention only the
 * dog's age must not erase the breed the first one established.
 */
@Service
class ProfileChatService(
    private val dogs: DogRepository,
    private val conversations: ProfileChatConversations,
    private val chatClients: ObjectProvider<ChatClient.Builder>,
    @Value("classpath:/prompts/profile-chat.system.md") private val systemPrompt: Resource,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Null when no model is configured, so the caller can say so precisely. */
    fun isAvailable(): Boolean = chatClients.ifAvailable != null

    @Transactional
    fun complete(ownerSubject: String, ownerFallbackName: String?, message: String): ChatOutcome {
        val builder = chatClients.ifAvailable
            ?: return ChatOutcome(NO_MODEL_REPLY, null)

        // A cap before the call, not after. The owner's message is the one input
        // a stranger controls, and an uncapped one is a bill rather than a
        // profile. Truncating keeps the chat working; refusing would not.
        val input = sanitise(message).take(MAX_INPUT_CHARACTERS)
        if (input.isEmpty()) return ChatOutcome(EMPTY_REPLY, null)

        // Declared nullable deliberately: entity() is annotated nullable, and a
        // null answer has to be handled anyway. Written as two statements rather
        // than one, because a try/catch expression followed by an elvis operator
        // is the kind of line that reads fine and parses badly.
        val draft: DogProfileDraft? = try {
            builder.build()
                .prompt()
                .system(systemPrompt)
                .user(withHistory(conversations.history(ownerSubject), input))
                .call()
                .entity(DogProfileDraft::class.java)
        } catch (failure: Exception) {
            // Structured output is best effort and the parser is strict: a model
            // that wraps its JSON in a sentence throws here. That is a bad
            // minute for one owner, not an outage, so it answers rather than 500s.
            log.warn("the profile chat could not read the model's answer: {}", failure.message)
            null
        }
        if (draft == null) return ChatOutcome(UNREADABLE_REPLY, null)

        val reply = text(draft.reply, MAX_REPLY_CHARACTERS) ?: DEFAULT_REPLY
        conversations.record(ownerSubject, input, reply)

        // The model's own out-of-scope flag is believed only in the direction
        // that costs nothing: it can refuse to fill the profile, it cannot ask
        // for anything extra. Counted, because a refusal nobody counts is not a
        // metric.
        if (draft.outOfScope == true) {
            log.info("profile chat refused an out-of-scope message")
            return ChatOutcome(reply, null)
        }

        val dog = dogs.findByOwnerSubject(ownerSubject) ?: Dog(ownerSubject = ownerSubject)
        val changed = merge(dog, draft, ownerFallbackName)
        if (!changed) return ChatOutcome(reply, dog.takeIf { it.id != null })

        return ChatOutcome(reply, dogs.save(dog))
    }

    /** Returns whether anything was actually taken from the draft. */
    private fun merge(dog: Dog, draft: DogProfileDraft, ownerFallbackName: String?): Boolean {
        var changed = false

        text(draft.ownerName, MAX_NAME_CHARACTERS)?.let { dog.ownerName = it; changed = true }
        if (dog.ownerName == null && ownerFallbackName != null) dog.ownerName = ownerFallbackName

        text(draft.name, MAX_NAME_CHARACTERS)?.let { dog.name = it; changed = true }
        text(draft.breed, MAX_NAME_CHARACTERS)?.let { dog.breed = it; changed = true }
        match(draft.gender, Gender.entries)?.let { dog.gender = it; changed = true }
        match(draft.size, DogSize.entries)?.let { dog.size = it; changed = true }
        match(draft.temperament, Temperament.entries)?.let { dog.temperament = it; changed = true }
        age(draft.age)?.let { dog.age = it; changed = true }
        preferences(draft.preferences)?.let { dog.preferences = it.toMutableSet(); changed = true }

        return changed
    }

    /**
     * Everything the model is told, in one string: what has already been said,
     * then the new message, clearly separated.
     *
     * Without this the feature loops, and the loop is not the model being bad.
     * One word arrives with no question attached, so "maschietto" is not an
     * answer about gender, it is the entire input, and the only sensible thing
     * to do with it is ask something else.
     *
     * Note what replaying the transcript does NOT do: it gives those earlier
     * messages no more authority than they had the first time. They were
     * untrusted text in the same single channel then and they are untrusted text
     * in the same single channel now, which is the honest reason this costs
     * nothing in risk and quite a lot in tokens.
     */
    private fun withHistory(history: List<ChatTurn>, message: String): String {
        if (history.isEmpty()) return message
        val transcript = history.joinToString("\n") { turn ->
            "owner: " + turn.owner + "\nyou: " + turn.assistant
        }
        return "CONVERSATION SO FAR\n" + transcript + "\n\nTHE OWNER'S NEW MESSAGE\n" + message
    }

    /**
     * Curly braces out, before anything else happens to the message.
     *
     * Prompt text is rendered by StringTemplate, whose delimiters are single
     * braces. An owner who pastes one gets a render failure instead of an
     * answer, for a character they did not type on purpose. This is the same
     * sharp edge that makes a JSON example unsafe to paste into a system prompt,
     * arriving from the other direction.
     */
    private fun sanitise(message: String): String =
        message.replace('{', ' ').replace('}', ' ').trim()

    private fun text(raw: String?, max: Int): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.take(max)

    /**
     * The closed vocabulary, enforced where it can actually be enforced. An
     * unrecognised value is dropped rather than approximated: "quite big" is
     * not evidence that the dog is LARGE.
     */
    private fun <T : Enum<T>> match(raw: String?, options: List<T>): T? {
        val candidate = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return options.firstOrNull { it.name.equals(candidate, ignoreCase = true) }
    }

    private fun age(raw: Int?): Int? = raw?.takeIf { it in MIN_AGE..MAX_AGE }

    private fun preferences(raw: List<String>?): Set<String>? =
        raw?.mapNotNull { text(it, MAX_PREFERENCE_CHARACTERS) }
            ?.take(MAX_PREFERENCES)
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }

    private companion object {
        const val MAX_INPUT_CHARACTERS = 4000
        const val MAX_REPLY_CHARACTERS = 500
        const val MAX_NAME_CHARACTERS = 100
        const val MAX_PREFERENCE_CHARACTERS = 50
        const val MAX_PREFERENCES = 10
        const val MIN_AGE = 0
        const val MAX_AGE = 30

        const val EMPTY_REPLY = "Tell me about your dog and I will fill the profile in."
        const val DEFAULT_REPLY = "I have updated the profile with what you told me."
        const val UNREADABLE_REPLY =
            "I could not read that properly. Could you tell me about your dog in one or two sentences?"
        const val NO_MODEL_REPLY =
            "The profile assistant is not configured on this installation."
    }
}
