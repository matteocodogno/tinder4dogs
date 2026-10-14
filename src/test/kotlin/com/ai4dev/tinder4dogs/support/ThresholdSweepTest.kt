package com.ai4dev.tinder4dogs.support

import com.ai4dev.tinder4dogs.knowledge.KnowledgeIndex
import com.ai4dev.tinder4dogs.knowledge.RetrievalSet
import com.ai4dev.tinder4dogs.knowledge.VectorStoreProbe
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * The floor, and what it costs. Run this BEFORE choosing a number.
 *
 * ```
 * mvn -q test -Dtest=ThresholdSweepTest
 * ```
 *
 * `similarityThreshold` defaults to `SIMILARITY_THRESHOLD_ACCEPT_ALL`, which is
 * 0.0, which means there is no floor: ask about something the corpus has never
 * heard of and the search returns the *k* least irrelevant chunks, with scores,
 * cheerfully. The refusal path your prompt politely describes is unreachable by
 * construction, because the context is never empty.
 *
 * Raising the floor makes that path reachable and takes answers away. This test
 * prints the whole trade in one table so that the number you put in the code is
 * one you chose rather than one you guessed, and so that the cost of choosing
 * it is written down next to it.
 *
 * ## The two numbers that decide everything
 *
 * Under the table are the only two numbers that matter:
 *
 *  - the **lowest** top-1 score among the twenty questions the corpus answers.
 *    A floor above it silences a question you could have answered.
 *  - the **highest** top-1 score among the questions the corpus does not
 *    answer. A floor below it leaves the bot with relevant context and no
 *    answer in it, which is where a confident wrong answer comes from.
 *
 * If the second is below the first there is a window, and any number inside it
 * does both jobs. **If it is not, there is no threshold that separates them**,
 * and that is a finding rather than a failure: it says the two populations
 * overlap in this embedding space, on this corpus, and that no single number
 * can tell "close to a document" from "answered by a document". The honest
 * responses are then a second signal (a model asked to judge its own context, a
 * reranker, an answer the application refuses to serve without a citation) or
 * an accepted cost, declared out loud. What is not honest is picking a round
 * number and calling it tuned.
 *
 * ## One pass of embeddings, every threshold
 *
 * The sweep makes ONE search per question, at threshold 0.0, asking for ten
 * hits, and then applies every threshold to the scores it got back. That is
 * exact, not an approximation, and the reason is worth following: the store
 * filters by distance and then orders by distance, so raising the floor can
 * only remove hits from the top of the list, never reorder them and never
 * introduce one the first search did not see. The only case where ten would not
 * be enough is one where all ten pass the floor, and then the top three are
 * already there. Twenty-four embedding calls measure fifteen thresholds.
 *
 * The comparison is strict (`score > threshold`), because that is what pgvector
 * does: its SQL is `WHERE embedding <=> ? < 1 - threshold`. The in-memory store
 * used in the earlier labs compares with `>=`. The two differ only exactly at
 * the boundary, and this test follows the store the product actually runs.
 *
 * ## It depends on the index, which is why the index is printed
 *
 * An approximate index changes which chunk is first and therefore changes the
 * scores this test reads. A threshold chosen with the HNSW index from LAB 3 is
 * not the same threshold as one chosen with an exact scan, and nothing will
 * tell you that later. The header says which one you measured under.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
    named = "AI4DEV_API_KEY",
    matches = ".+",
    disabledReason = "sweeping the threshold needs real embeddings for every question",
)
class ThresholdSweepTest {

    @Autowired
    private lateinit var index: KnowledgeIndex

    @Autowired
    private lateinit var vectorStore: VectorStore

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    /** One question, and the ten hits the search returned with no floor at all. */
    private data class Probe(
        val id: String,
        val question: String,
        val expectedSource: String?,
        val hits: List<Hit>,
    ) {
        /** The hits that survive [threshold], in order, capped at [TOP_K]. */
        fun surviving(threshold: Double): List<Hit> =
            hits.filter { it.score > threshold }.take(TOP_K)

        val topScore: Double get() = hits.firstOrNull()?.score ?: 0.0
    }

    private data class Hit(val docId: String, val score: Double)

    @Test
    fun `what a floor costs, at every floor`() {
        val probe = VectorStoreProbe(jdbc)
        val built = if (probe.chunkCount() == 0) index.build() else null

        val answerable = RetrievalSet.load().map { q ->
            search(q.id, q.question, q.expectedSource)
        }
        val unanswerable = SupportSet.unanswerable().map { q ->
            search(q.id, q.question, expectedSource = null)
        }

        report(probe, built, answerable, unanswerable)
        happyPath()
        byHand()

        // Nothing here asserts a threshold. The number is a decision and this
        // test is the evidence for it, so the assertions protect the evidence
        // and not the decision.

        // A question the corpus answers must retrieve something with no floor
        // at all. If it does not, the sweep is measuring a broken index and
        // every row of the table is a row of zeroes that says so nowhere.
        assertThat(answerable)
            .describedAs("every answerable question must retrieve something at threshold 0")
            .allSatisfy { p -> assertThat(p.hits).isNotEmpty() }

        // The other assumption: the store returns hits in descending score
        // order. The sweep takes the FIRST three that survive a floor, which is
        // only the right three if that order is the store's own ranking.
        assertThat(answerable + unanswerable).allSatisfy { p ->
            val scores = p.hits.map { it.score }
            assertThat(scores)
                .describedAs("hits for %s must be ordered by score, best first", p.id)
                .isEqualTo(scores.sortedDescending())
        }
    }

    /**
     * The four questions of step 3, and the documents they would cite.
     *
     * This is also what keeps `answerable.json` honest: its `expectedSources`
     * are claims about the corpus, and a claim nothing checks is a comment.
     */
    private fun happyPath() {
        val questions = SupportSet.answerable()
        println()
        println("── the four questions of step 3 ──────────────────────────────────────────")
        val outcomes = questions.map { q ->
            val retrieved = search(q.id, q.question, expectedSource = null)
                .surviving(0.0)
                .map { it.docId }
            println("${q.id}  \"${q.question}\"")
            println("    expected : ${q.expectedSources.joinToString(", ")}")
            println("    retrieved: ${retrieved.joinToString(", ")}")
            q to retrieved
        }
        println("──────────────────────────────────────────────────────────────────────────")
        println("The sources are checkable and the ANSWER is not: read the four answers")
        println("yourself and say whether each one carries the facts in answerable.json.")
        println()

        outcomes.forEach { (question, retrieved) ->
            assertThat(retrieved)
                .describedAs("%s must retrieve at least one of its expected sources", question.id)
                .containsAnyElementsOf(question.expectedSources)
        }
    }

    /** The two cases nothing here can measure, named so they are not forgotten. */
    private fun byHand() {
        println("── still to do by hand, steps 5 and 6 ────────────────────────────────────")
        SupportSet.cases().forEach { case ->
            println("${case.id}  ${case.kind}")
            println("    \"${case.question}\"")
        }
        println("adversarial.json says, for each, what must not happen and where the")
        println("mitigation can live. No test here decides either: step 6 is a decision")
        println("about the application, not an assertion about a string.")
        println("──────────────────────────────────────────────────────────────────────────")
        println()
    }

    private fun search(id: String, question: String, expectedSource: String?): Probe {
        val hits = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(SAMPLE)
                // Deliberately the accept-everything default: this call is the
                // measurement of what IS there, and the floor is applied after.
                .similarityThreshold(0.0)
                .build(),
        ).orEmpty().map { hit ->
            Hit(
                docId = hit.metadata[KnowledgeIndex.DOC_ID] as? String ?: "(no docId)",
                score = hit.score ?: 0.0,
            )
        }
        return Probe(id, question, expectedSource, hits)
    }

    private fun report(
        probe: VectorStoreProbe,
        built: KnowledgeIndex.IndexStats?,
        answerable: List<Probe>,
        unanswerable: List<Probe>,
    ) {
        val ann = probe.annIndexDefinition()

        println()
        println("── the floor, and what it costs ──────────────────────────────────────────")
        println("stored      : ${probe.chunkCount()} chunks over ${probe.documentCount()} documents")
        println("built now   : " + (built?.let { "yes, ${it.strategy.label}" } ?: "no, already in the database"))
        println(
            "index       : " + when {
                ann == null -> "none on the embedding column, so these scores are exact"
                ann.contains("USING hnsw") -> "HNSW, so these scores are what an APPROXIMATE search returned"
                else -> "IVFFlat, so these scores are what an APPROXIMATE search returned"
            },
        )
        println("questions   : ${answerable.size} the corpus answers, ${unanswerable.size} it does not")
        println()

        val ceiling = answerable.maxOfOrNull { it.topScore } ?: 0.0
        val step = if (ceiling <= 0.0) 0.05 else ceiling / (ROWS - 1)

        println(
            "%9s %9s %9s %14s %14s".format(
                "threshold", "recall@3", "recall@1", "no context", "refusals",
            ),
        )
        (0 until ROWS).forEach { row ->
            val threshold = row * step
            val kept = answerable.map { it to it.surviving(threshold) }
            val recallAt3 = kept.count { (q, hits) -> hits.any { it.docId == q.expectedSource } }
            val recallAt1 = kept.count { (q, hits) -> hits.firstOrNull()?.docId == q.expectedSource }
            val starved = kept.count { (_, hits) -> hits.isEmpty() }
            val silenced = unanswerable.count { it.surviving(threshold).isEmpty() }

            println(
                "%9.3f %8.0f%% %8.0f%% %8d/%-5d %8d/%-5d".format(
                    threshold,
                    recallAt3 * 100.0 / answerable.size,
                    recallAt1 * 100.0 / answerable.size,
                    starved, answerable.size,
                    silenced, unanswerable.size,
                ),
            )
        }
        println()
        println("'no context' is a question the corpus ANSWERS that the floor just silenced:")
        println("a refusal the user did not deserve. 'refusals' is a question the corpus")
        println("does NOT answer that the floor silenced: the ones you are buying.")
        println()

        window(answerable, unanswerable)
    }

    /** The two numbers, and whether there is anything between them. */
    private fun window(answerable: List<Probe>, unanswerable: List<Probe>) {
        val weakest = answerable.minByOrNull { it.topScore }
        val loudest = unanswerable.maxByOrNull { it.topScore }

        println("── the window ────────────────────────────────────────────────────────────")
        if (weakest == null || loudest == null) {
            println("not enough questions on one side to say anything.")
            println("──────────────────────────────────────────────────────────────────────────")
            return
        }

        println("lowest answerable top-1  : %.4f  %s \"%s\"".format(weakest.topScore, weakest.id, weakest.question))
        println("highest unanswerable top-1: %.4f  %s \"%s\"".format(loudest.topScore, loudest.id, loudest.question))
        println()

        if (loudest.topScore < weakest.topScore) {
            val midpoint = (loudest.topScore + weakest.topScore) / 2
            println("There IS a window: %.4f to %.4f.".format(loudest.topScore, weakest.topScore))
            println("Any floor strictly inside it answers all %d and refuses all %d."
                .format(answerable.size, unanswerable.size))
            println("The midpoint is %.4f. Put THAT number in the code, with this".format(midpoint))
            println("output's date next to it, because it is a measurement and not a constant.")
        } else {
            println("There is NO window. The two populations overlap: any floor high enough")
            println("to silence ${loudest.id} also silences ${weakest.id}, which the corpus answers.")
            println()
            println("This is a finding, not a failure. It says that on this corpus, in this")
            println("embedding space, one number cannot tell \"close to a document\" from")
            println("\"answered by a document\". Say so, then choose: accept the false")
            println("refusals and write down how many, accept the invented answers and write")
            println("down which, or add a second signal that is not a distance. What is not")
            println("available is a round number and the word tuned.")
        }
        println("──────────────────────────────────────────────────────────────────────────")
    }

    private companion object {
        /** What the product will send to the model. */
        const val TOP_K = 3

        /**
         * How many hits one search asks for. More than [TOP_K] so that a floor
         * which removes the first hits can still be answered exactly from the
         * same search, and small enough that the sweep stays one pass.
         */
        const val SAMPLE = 10

        /** Rows in the table, spanning 0 to the highest answerable score. */
        const val ROWS = 15
    }
}
