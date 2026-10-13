package com.ai4dev.tinder4dogs.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.ClassPathResource

/**
 * The gold set: twenty questions, each with the document that SHOULD answer it.
 *
 * "Should" is the whole value of this file. The label was written by a person
 * who read the corpus, not produced by the system being measured, which is the
 * only arrangement under which a number means anything.
 *
 * Two rules it follows, and both are decisions rather than conveniences:
 *
 *  - **only answerable questions.** A question the corpus cannot answer does
 *    not measure recall, it measures refusal, and that is a different test with
 *    a different expected outcome.
 *  - **no ambiguous gold.** The documents that contradict each other, the three
 *    near-identical versions of the same page and the stale one are never the
 *    expected answer. Measuring recall against a contradiction measures nothing.
 *
 * At work this file does not exist and somebody has to write it: a sample of
 * real support questions, read against the documentation, labelled by hand. It
 * is an afternoon of one person who knows the product, and it is the line
 * nobody puts in the estimate for a retrieval project.
 */
object RetrievalSet {

    data class Question(val id: String, val question: String, val expectedSource: String)

    const val PATH = "retrieval/retrieval-set.json"

    fun load(): List<Question> {
        val mapper = ObjectMapper()
        val root = ClassPathResource(PATH).inputStream.use { mapper.readTree(it) }
        return root["questions"].map { node ->
            Question(
                id = node["id"].asText(),
                question = node["question"].asText(),
                expectedSource = node["expectedSource"].asText(),
            )
        }
    }
}
