package com.ai4dev.tinder4dogs.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.ClassPathResource

/**
 * The questions LAB 4 is built on, in three files, for three different jobs.
 *
 *  - `answerable.json` — four questions the corpus genuinely answers, with the
 *    facts the answer has to carry and the sources it has to cite.
 *  - `unanswerable.json` — four questions it does not, each one close enough to
 *    a real document that the search returns that document with a high score.
 *    This is the file the refusal path exists for.
 *  - `adversarial.json` — the contradiction and the document that gives the
 *    assistant instructions.
 *
 * ## Why so much prose inside JSON
 *
 * Most of what is in those files is read by a person, not by a program: what
 * the answer must say, what an invented answer looks like, which of three
 * outcomes is acceptable. It is in the fixture rather than in a facilitator's
 * notes because it has to stay next to the question it is about, and because a
 * group that disagrees with a judgement can see exactly what was claimed and
 * argue with it.
 *
 * This loader deliberately exposes only the machine-readable part of each file.
 * The `about`, `why` and `ask` fields are for the room; `question`,
 * `expectedSources` and `nearestDocument` are for the code.
 *
 * At work this file does not exist either, and somebody has to write it. The
 * unanswerable half is the part nobody budgets for: four questions whose
 * answer is "we do not know" are worth more than forty more questions you can
 * already answer, and they cannot be mined from a log of successful sessions.
 */
object SupportSet {

    /** A question the corpus answers, with the documents that answer it. */
    data class Answerable(
        val id: String,
        val goldSetId: String,
        val question: String,
        val expectedSources: List<String>,
    )

    /**
     * A question the corpus does not answer.
     *
     * [nearestDocument] is the document the search will hand you anyway, which
     * is the whole difficulty: the context is not empty, it is relevant and it
     * does not contain the answer. [pii] marks the one where a grounded,
     * obedient, well-written answer is also the worst outcome of the day.
     */
    data class Unanswerable(
        val id: String,
        val question: String,
        val nearestDocument: String,
        val pii: Boolean,
    )

    /** A case from `adversarial.json`, with its prose left in the file. */
    data class Case(
        val id: String,
        val kind: String,
        val question: String,
    )

    fun answerable(): List<Answerable> = read("support/answerable.json", "questions") { node ->
        Answerable(
            id = node["id"].asText(),
            goldSetId = node["goldSetId"].asText(),
            question = node["question"].asText(),
            expectedSources = node["expectedSources"].map { it.asText() },
        )
    }

    fun unanswerable(): List<Unanswerable> = read("support/unanswerable.json", "questions") { node ->
        Unanswerable(
            id = node["id"].asText(),
            question = node["question"].asText(),
            nearestDocument = node["nearestDocument"].asText(),
            pii = node["pii"]?.asBoolean() ?: false,
        )
    }

    fun cases(): List<Case> = read("support/adversarial.json", "cases") { node ->
        Case(
            id = node["id"].asText(),
            kind = node["kind"].asText(),
            question = node["question"].asText(),
        )
    }

    private fun <T> read(path: String, arrayField: String, map: (JsonNode) -> T): List<T> {
        val root = ClassPathResource(path).inputStream.use { ObjectMapper().readTree(it) }
        return root[arrayField].map(map)
    }
}
