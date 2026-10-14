package com.ai4dev.tinder4dogs.support

/**
 * One document the answer was built from.
 *
 * Every field here comes from the index, not from the model. That is the whole
 * design: a citation the model wrote is a claim about a document, and a
 * citation the application wrote is a fact about what was retrieved.
 */
data class Citation(
    val docId: String,
    val title: String?,
    val section: String?,
    val updated: String?,
    val score: Double?,
)

/**
 * What the support bot produced, as two cases that cannot be confused.
 *
 * ## Why this is a sealed type and not a nullable field
 *
 * The lab's release condition is that an answer with no sources is impossible
 * **by construction rather than by convention**. A data class with
 * `answer: String` and `sources: List<Citation>` makes it possible by
 * construction and forbidden by convention: every future call site has to
 * remember, and one of them will not.
 *
 * Here there is no shape that carries an answer without sources. [Answered]
 * requires a non-empty list in its constructor, [Refused] carries no answer at
 * all, and a caller that wants the text has to say which case it is handling
 * first. The compiler enforces the product rule, and the `require` below catches
 * the one path the compiler cannot see.
 *
 * ## Refusal is a result, not an error
 *
 * [Refused] is not an exception and it is not an HTTP error. A support bot that
 * refuses fifteen percent of questions and is right on the rest is a product;
 * one that answers everything and is right eighty-five percent of the time is a
 * liability, and the difference is invisible in an accuracy average. So a
 * refusal is a first-class outcome with a reason attached, countable next to
 * the answers rather than hidden among the failures.
 */
sealed interface SupportAnswer {

    data class Answered(
        val answer: String,
        val sources: List<Citation>,
        val promptVersion: String,
    ) : SupportAnswer {
        init {
            require(sources.isNotEmpty()) {
                "an answered response must carry its sources: this is the release condition, not a precondition"
            }
        }
    }

    data class Refused(
        val reason: Reason,
        val message: String,
    ) : SupportAnswer

    /**
     * Why the bot did not answer. Four of these are good news and one is not.
     *
     * [NO_CONTEXT] is the one the product is built around: nothing in the
     * documentation was close enough to the question, so there was nothing to
     * answer from and no model was called. [BLOCKED] is the one to watch: the
     * model produced something the application refused to serve, which means a
     * guard fired and the reason it fired is in the log.
     */
    enum class Reason {
        /** Nothing cleared the similarity floor. The documentation does not cover this. */
        NO_CONTEXT,

        /** No gateway configured on this installation. A setup problem, not an answer. */
        MODEL_UNAVAILABLE,

        /** Nothing was asked. */
        EMPTY_QUESTION,

        /** The model returned nothing usable. */
        UNREADABLE,

        /** The application refused to serve what the model produced. */
        BLOCKED,
    }
}
