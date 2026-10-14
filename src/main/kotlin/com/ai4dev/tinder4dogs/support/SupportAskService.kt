package com.ai4dev.tinder4dogs.support

import com.ai4dev.tinder4dogs.knowledge.KnowledgeIndex
import com.ai4dev.tinder4dogs.prompt.PromptSource
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Answers a support question from the product's own documentation, or refuses.
 *
 * Read the order of the steps before anything else, because the order is the
 * design: retrieve, decide whether there is anything to answer from, and only
 * then call a model. **A question that retrieves nothing never reaches the
 * gateway.** The refusal costs nothing, cannot be talked out of itself, and
 * happens before the part of the system that can be persuaded.
 *
 * ## The two numbers, and why neither has a default in the code
 *
 * `topK` and `similarityThreshold` are both explicit, both configurable, and
 * both carry a reason:
 *
 * **topK = 3.** Every chunk retrieved is tokens on every request, for ever. The
 * splitter chosen in the chunking lab decided how big each one is, so this
 * number multiplies that decision by itself. Three is what the earlier labs
 * measured recall at, so the recall number this product was built on is the
 * recall number it actually runs with. Changing it to five without re-running
 * the harness changes the cost immediately and the quality unmeasurably.
 *
 * **similarityThreshold.** The framework's default is
 * `SIMILARITY_THRESHOLD_ACCEPT_ALL`, which is 0.0, which means there is no floor
 * at all: ask about something the documentation has never heard of and the
 * search returns the three least irrelevant chunks, with scores, cheerfully.
 * The refusal path above would then be unreachable by construction, because the
 * context is never empty.
 *
 * **The shipped value of the threshold is 0.0 and that is not a recommendation.**
 * It is the framework's own default, left in place so that nobody inherits a
 * number somebody guessed. The right value is measured against your own corpus,
 * your own embedding model and your own index with `ThresholdSweepTest`, and
 * this service says so in the log, loudly, every time it starts with no floor.
 *
 * ## Where the mitigation lives
 *
 * The prompt tells the model that the context is data and not instructions.
 * That is a hint and the previous module established why. Three things here are
 * not hints:
 *
 *  - **the sources are computed, never parsed.** [Citation]s come from what the
 *    retrieval returned. No sentence a model writes can add a document, remove
 *    one, or rename one.
 *  - **the answer is checked before it is served.** The prompt carries a canary
 *    phrase; if it ever appears in the output, the output is discarded and the
 *    request is refused. A leak becomes a refusal and a log line instead of a
 *    response body.
 *  - **the model does nothing but produce text.** It has no tools, no
 *    repository, no write path. The worst a persuaded model can do here is
 *    write a wrong paragraph, which is the point of giving it nothing else.
 *
 * None of the three fixes the real defect, which is that a document nobody
 * reviewed became part of a prompt. That fix is a gate at index time, and it is
 * the next lab.
 */
@Service
class SupportAskService(
    private val vectorStore: VectorStore,
    private val chatClients: ObjectProvider<ChatClient.Builder>,
    private val prompts: PromptSource,
    @Value("\${app.prompts.label:production}") private val promptLabel: String,
    @Value("\${app.support.top-k:3}") private val topK: Int,
    @Value("\${app.support.similarity-threshold:0.0}") private val similarityThreshold: Double,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * The pair, always together.
     *
     * An evaluation that rewards answering and penalises refusing builds a
     * machine optimised for confident wrong answers, and it will deliver one.
     * So the two numbers are kept side by side and logged side by side: a
     * refusal is not a miss, and the miss is the confident answer that is wrong.
     */
    private val answered = AtomicLong()
    private val refused = AtomicLong()

    private val lastPromptVersion = AtomicReference<String>()

    init {
        if (similarityThreshold <= 0.0) {
            log.warn(
                "the support bot has NO similarity floor (app.support.similarity-threshold={}), " +
                    "so every question retrieves context and the refusal path is unreachable. " +
                    "Measure a floor with ThresholdSweepTest before this answers anybody.",
                similarityThreshold,
            )
        }
    }

    fun isAvailable(): Boolean = chatClients.ifAvailable != null

    fun ask(rawQuestion: String): SupportAnswer {
        val question = sanitise(rawQuestion).take(MAX_QUESTION_CHARACTERS)
        if (question.isEmpty()) return refuse(SupportAnswer.Reason.EMPTY_QUESTION, EMPTY_QUESTION)

        val builder = chatClients.ifAvailable
            ?: return refuse(SupportAnswer.Reason.MODEL_UNAVAILABLE, NO_MODEL)

        // Step one, and it is not the model. Both numbers are spelled out even
        // where one of them is the framework's default, because a floor of zero
        // is a decision whether or not anybody made it.
        val chunks = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build(),
        ).orEmpty()

        if (chunks.isEmpty()) {
            // The whole point of the floor. No gateway call, no tokens, no
            // chance for a well-written prompt to be talked into an answer that
            // the documentation does not support.
            return refuse(SupportAnswer.Reason.NO_CONTEXT, NO_ANSWER_IN_DOCS)
        }

        val prompt = prompts.load(PROMPT_NAME, promptLabel)
        if (lastPromptVersion.getAndSet(prompt.version) != prompt.version) {
            log.info(
                "the support bot is now using prompt {} version {}, from {}",
                PROMPT_NAME, prompt.version, prompt.origin,
            )
        }

        val text = try {
            builder.build()
                .prompt()
                .system(prompt.text)
                .user(contextAndQuestion(chunks, question))
                .call()
                .content()
                ?.trim()
        } catch (failure: Exception) {
            log.warn("the support bot could not get an answer from the model: {}", failure.message)
            null
        }

        if (text.isNullOrEmpty()) return refuse(SupportAnswer.Reason.UNREADABLE, UNREADABLE)

        // The guard that is not a hint. If the canary is in the output then the
        // system prompt has leaked, whatever else the answer says, and no amount
        // of it is safe to serve.
        if (text.contains(CANARY, ignoreCase = true)) {
            log.error(
                "the support bot's answer contained the prompt canary and was discarded. " +
                    "Question: \"{}\". Documents in context: {}",
                question,
                chunks.joinToString(", ") { docIdOf(it) },
            )
            return refuse(SupportAnswer.Reason.BLOCKED, BLOCKED)
        }

        val outcome = SupportAnswer.Answered(
            answer = text.take(MAX_ANSWER_CHARACTERS),
            sources = chunks.map(::citation),
            promptVersion = prompt.version,
        )
        log.info(
            "support question answered from {} documents. answered={} refused={}",
            outcome.sources.size, answered.incrementAndGet(), refused.get(),
        )
        return outcome
    }

    private fun refuse(reason: SupportAnswer.Reason, message: String): SupportAnswer.Refused {
        log.info(
            "support question refused ({}). answered={} refused={}",
            reason, answered.get(), refused.incrementAndGet(),
        )
        return SupportAnswer.Refused(reason, message)
    }

    /**
     * Everything the model is given, in one string.
     *
     * Each extract carries the identifier, the section and the date of the
     * document it came from, for three different reasons: the identifier is
     * what the answer may cite, the section is what makes a citation precise
     * enough to check in under a minute, and the date is what lets the model
     * say which of two contradictory extracts is the newer one. The corpus
     * contains a pair that disagrees, so this is not hypothetical.
     *
     * The markers are a convention and they are breakable, which is why they
     * are not the mitigation. They are here because a model that cannot tell
     * where a document ends cannot cite it either.
     */
    private fun contextAndQuestion(chunks: List<Document>, question: String): String {
        val context = chunks.joinToString("\n\n") { chunk ->
            val header = buildList {
                add("document: " + docIdOf(chunk))
                chunk.metadata[KnowledgeIndex.SECTION]?.let { add("section: $it") }
                chunk.metadata[UPDATED]?.let { add("updated: $it") }
            }.joinToString(" | ")
            "--- $header ---\n" + chunk.text.orEmpty().trim()
        }
        return "CONTEXT\n" + context + "\n--- end of context ---\n\nQUESTION\n" + question
    }

    private fun citation(chunk: Document): Citation = Citation(
        docId = docIdOf(chunk),
        title = chunk.metadata["title"] as? String,
        section = chunk.metadata[KnowledgeIndex.SECTION] as? String,
        updated = chunk.metadata[UPDATED] as? String,
        score = chunk.score,
    )

    private fun docIdOf(chunk: Document): String =
        chunk.metadata[KnowledgeIndex.DOC_ID] as? String ?: "(no docId)"

    /**
     * Curly braces out, for the same reason as the profile chat: prompt text is
     * rendered by StringTemplate and a user who pastes a brace would get a
     * render failure instead of an answer.
     */
    private fun sanitise(question: String): String =
        question.replace('{', ' ').replace('}', ' ').trim()

    private companion object {
        const val PROMPT_NAME = "support-ask"

        /** The metadata key the front matter transformer writes the date into. */
        const val UPDATED = "updated"

        /**
         * The phrase the prompt says must never be written. Kept here as well as
         * in the prompt on purpose: a canary that lives only in the prompt is a
         * canary nothing checks.
         */
        const val CANARY = "marmalade eleven kingfisher"

        const val MAX_QUESTION_CHARACTERS = 1000
        const val MAX_ANSWER_CHARACTERS = 2000

        const val NO_ANSWER_IN_DOCS =
            "Non trovo questa informazione nella documentazione di Tinder4Dogs."
        const val EMPTY_QUESTION = "Scrivi una domanda e cerco la risposta nella documentazione."
        const val UNREADABLE = "Non sono riuscito a formulare una risposta. Riprova."
        const val BLOCKED = "Non posso rispondere a questa domanda."
        const val NO_MODEL = "L'assistenza automatica non e' configurata su questa installazione."
    }
}
