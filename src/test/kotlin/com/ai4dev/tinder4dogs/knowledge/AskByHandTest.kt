package com.ai4dev.tinder4dogs.knowledge

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Ask the index one question and look at what comes back.
 *
 * ```
 * AI4DEV_Q="il mio cane non compare nella ricerca" mvn -q test -Dtest=AskByHandTest
 * ```
 *
 * The question arrives in an ENVIRONMENT VARIABLE rather than a `-D` property
 * because Surefire runs the tests in a forked JVM: the fork inherits the
 * environment for certain, and whether it inherits an arbitrary `-D` depends on
 * the plugin's configuration. `-Dq=` is accepted too, for whoever has it
 * working, but the documented form is the one that cannot fail for a reason
 * nobody wants to debug in front of twenty people.
 *
 * ## What to look at, and it is not the answer
 *
 * There is no answer. Nothing generates text in this lab. What this prints is
 * the three or four chunks a model WOULD have been given, each with its
 * similarity score, and the score is the point:
 *
 *  - two questions that share no words can score high, which is the reason
 *    embeddings are here at all
 *  - two questions that share every word can score high on the WRONG document,
 *    which is the reason retrieval has to be measured rather than trusted
 *  - the gap between the first and the fourth score is what a similarity
 *    threshold has to separate, and in Part 4 you will have to pick one. Look
 *    at real numbers now and that choice stops being arbitrary
 *
 * It asks for five rather than three on purpose. The harness measures at three,
 * because three is what the product will send to the model; this one shows five
 * because you are looking at the neighbourhood and not scoring it. Among those
 * extra two you will often find the same answer three times, which is a defect
 * of the corpus and is somebody else's lab.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
    named = "AI4DEV_API_KEY",
    matches = ".+",
    disabledReason = "asking the index makes a real embedding call through the gateway",
)
class AskByHandTest {

    @Autowired
    private lateinit var index: KnowledgeIndex

    @Autowired
    private lateinit var vectorStore: VectorStore

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `what comes back for the question you typed`() {
        val question = System.getProperty("q")
            ?: System.getenv("AI4DEV_Q")
            ?: DEFAULT_QUESTION

        // The index used to be rebuilt here on every run, because the store was
        // a map in this process's heap and there was nothing else it could be.
        // Since the index became a table it survives the JVM, so rebuilding it
        // to ask one question would be paying to embed fourteen documents in
        // order to embed one sentence. Ask the index that is there; build it
        // only if there is none.
        val probe = VectorStoreProbe(jdbc)
        val built = if (probe.chunkCount() == 0) index.build() else null

        val hits = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(NEIGHBOURHOOD)
                .similarityThreshold(0.0)
                .build(),
        ).orEmpty()

        println()
        println("── asked ─────────────────────────────────────────────────────")
        println("question : $question")
        println(
            "index    : " + (
                built?.let { "${it.documents} documents, ${it.chunks} chunks, built just now" }
                    ?: "${probe.documentCount()} documents, ${probe.chunkCount()} chunks, already in the database"
                ),
        )
        println()
        hits.forEachIndexed { position, hit ->
            val score = hit.score?.let { "%.4f".format(it) } ?: "n/a"
            val docId = hit.metadata[KnowledgeIndex.DOC_ID] ?: "(no docId)"
            val title = hit.metadata["title"] ?: ""
            println("${position + 1}. score $score  $docId")
            if (title != "") println("   $title")
            println("   ${preview(hit.text)}")
            println()
        }
        println("──────────────────────────────────────────────────────────────")
        println("Is the first one the document YOU would have cited? If not,")
        println("say whether the one that won is about the same thing in other")
        println("words, or about something else in the same words.")
        println()
    }

    private fun preview(text: String?): String {
        val flat = text.orEmpty().replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= PREVIEW_CHARS) flat else flat.take(PREVIEW_CHARS) + "..."
    }

    private companion object {
        /**
         * The pair from Part 1: nearly every word is shared with "come cancello
         * il mio account", and the two are answered by different documents. A
         * bare run of the suite lands on the interesting case rather than on a
         * boring one.
         */
        const val DEFAULT_QUESTION = "Come cancello il mio cane?"
        const val NEIGHBOURHOOD = 5
        const val PREVIEW_CHARS = 140
    }
}
