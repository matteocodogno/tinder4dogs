package com.ai4dev.tinder4dogs.knowledge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * What the index IS, and what it costs you.
 *
 * ```
 * mvn -q test -Dtest=IndexCostTest                        # whatever is in the database
 * AI4DEV_EF_SEARCH=100 mvn -q test -Dtest=IndexCostTest   # with the search widened
 * ```
 *
 * This test does not configure anything. It reports the configuration it found
 * and measures that, which is the only arrangement that works here: the index
 * is a Liquibase changeset and the search is widened by a session setting, so
 * neither is a property a test can flip. Run it three times, in three states,
 * and the three rows of the table in the lab fill themselves in:
 *
 *  1. after changeset 006 only, with no index on the embedding column
 *  2. after changeset 007, with HNSW and `hnsw.ef_search` left at its default
 *  3. after changeset 007, with `AI4DEV_EF_SEARCH=100`
 *
 * Every run prints the state it ran under next to the numbers, so a row that
 * was measured in the wrong state is visible rather than plausible.
 *
 * ## It does not rebuild the index, and that is the lesson
 *
 * It builds the corpus only if the table is EMPTY. The index is in the database
 * now: it survived the JVM that created it, it will still be there tomorrow, and
 * paying to embed fourteen documents again on every run would be exactly the
 * mistake the previous store forced on us. If you want it rebuilt, say so:
 * `ai4dev db new` and run the harness, or call `KnowledgeIndex.build()` yourself.
 *
 * ## `SET LOCAL`, and why the measurement is inside a transaction
 *
 * `hnsw.ef_search` is a session setting, and a connection pool hands out
 * sessions. A bare `SET` on a `JdbcTemplate` lands on whichever connection the
 * pool felt like giving it, and the query that was supposed to be affected may
 * run on a different one: the test would then report a setting it did not
 * actually use, which is worse than not measuring it.
 *
 * Inside a transaction there is exactly one connection, Spring hands the same
 * one to everything that asks, and `SET LOCAL` reverts at commit. That makes the
 * measurement self-contained and leaves nothing behind for the next test.
 *
 * ## What the numbers mean and what they do not
 *
 * `recall@3` is the same measurement as the first lab's, by the same definition,
 * against the same twenty questions. It is comparable across the three rows
 * because nothing else moves.
 *
 * `query` is the DATABASE's time for one similarity search: the SQL only, with
 * the question already embedded. The framework's `similaritySearch` also makes
 * an HTTP call to the gateway to embed the question, and on this corpus that
 * call is two orders of magnitude larger than the query, so timing the
 * framework's method would measure the gateway and label it the index. Which is
 * itself the finding to say out loud: on a corpus this size the index cannot
 * make the feature feel faster, because the database was never the slow part.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
    named = "AI4DEV_API_KEY",
    matches = ".+",
    disabledReason = "measuring the index needs real embeddings for the twenty questions",
)
class IndexCostTest {

    @Autowired
    private lateinit var index: KnowledgeIndex

    @Autowired
    private lateinit var vectorStore: VectorStore

    @Autowired
    private lateinit var embeddingModel: EmbeddingModel

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private data class Timed(val retrieved: List<String>, val medianMicros: Long)

    @Test
    fun `the index, and what it cost`() {
        val probe = VectorStoreProbe(jdbc)
        val questions = RetrievalSet.load()

        val built = if (probe.chunkCount() == 0) index.build() else null

        // One batched call for all twenty questions. The store batches its
        // writes the same way, through a BatchingStrategy the starter
        // auto-configures — which is a second thing the storage swap changed
        // for free: the toy store embedded one document per call.
        val embeddings = embeddingModel.embed(questions.map { it.question })

        val requested = (System.getProperty("efSearch") ?: System.getenv("AI4DEV_EF_SEARCH"))?.toIntOrNull()

        val template = TransactionTemplate(transactionManager)
        val measurement = template.execute {
            // Interpolated rather than bound: SET takes no parameters. It is an
            // Int parsed from the environment and nothing else can reach here.
            if (requested != null) jdbc.execute("SET LOCAL hnsw.ef_search = $requested")

            val timed = questions.indices.map { i -> search(embeddings[i]) }
            val agreement = agreesWithFramework(questions.first(), timed.first())

            // Read AFTER the queries: before them, pgvector's library may not be
            // loaded in this session yet and the setting reads as a placeholder.
            Measurement(
                efSearchInEffect = probe.efSearch(),
                timed = timed,
                frameworkAgrees = agreement,
            )
        }!!

        report(probe, questions, measurement, built, requested)

        assertThat(probe.chunkCount()).describedAs("the index must have rows in it").isGreaterThan(0)
        assertThat(measurement.timed).hasSize(questions.size)
        assertThat(measurement.timed.flatMap { it.retrieved })
            .describedAs("every stored chunk must still carry the id of its document")
            .doesNotContain(NO_DOC_ID)
    }

    private data class Measurement(
        val efSearchInEffect: String?,
        val timed: List<Timed>,
        val frameworkAgrees: Boolean,
    )

    /**
     * Only `docId` is read out of each row. The query selects everything the
     * framework's own query selects, embedding column included, because the
     * point is to time what the framework pays for and not a leaner query
     * nobody runs.
     */
    private val docIdFromMetadata = RowMapper<String> { rs, _ -> docIdOf(rs.getString("metadata")) }

    /** One similarity search, run [REPEATS] times, timed, median taken. */
    private fun search(embedding: FloatArray): Timed {
        val literal = VectorStoreProbe.vectorLiteral(embedding)
        var retrieved: List<String> = emptyList()
        val samples = (1..REPEATS).map {
            val started = System.nanoTime()
            retrieved = jdbc.query(
                VectorStoreProbe.SIMILARITY_SQL,
                docIdFromMetadata,
                literal,
                literal,
                MAX_DISTANCE,
                TOP_K,
            )
            (System.nanoTime() - started) / 1_000
        }.sorted()
        return Timed(retrieved, samples[samples.size / 2])
    }

    /**
     * Does the SQL above still return what the framework returns?
     *
     * The copied query is the only thing in this test that can rot without
     * saying so, so one question is run both ways and the top three compared.
     * It costs one extra embedding call and it is the difference between a
     * measurement and a guess about a measurement.
     */
    private fun agreesWithFramework(question: RetrievalSet.Question, timed: Timed): Boolean {
        val throughFramework = vectorStore.similaritySearch(
            SearchRequest.builder().query(question.question).topK(TOP_K).similarityThreshold(0.0).build(),
        ).orEmpty().map { it.metadata[KnowledgeIndex.DOC_ID] as? String ?: NO_DOC_ID }
        return throughFramework == timed.retrieved
    }

    /** `docId` out of the raw JSON of the metadata column. */
    private fun docIdOf(metadata: String?): String {
        val match = DOC_ID_IN_JSON.find(metadata.orEmpty())
        return match?.groupValues?.get(1) ?: NO_DOC_ID
    }

    private fun report(
        probe: VectorStoreProbe,
        questions: List<RetrievalSet.Question>,
        measurement: Measurement,
        built: KnowledgeIndex.IndexStats?,
        requested: Int?,
    ) {
        val recallAt3 = questions.indices.count { i ->
            questions[i].expectedSource in measurement.timed[i].retrieved
        }.toDouble() / questions.size

        // recall@1 is here because recall@3 is already 100% on this corpus, and
        // a column that reads 100% in all three rows measures nothing. Ranking
        // is what an approximate index disturbs first: the right document stays
        // in the top three and stops being first. Measure the number that can
        // move, or you will conclude the index was free.
        val recallAt1 = questions.indices.count { i ->
            measurement.timed[i].retrieved.firstOrNull() == questions[i].expectedSource
        }.toDouble() / questions.size
        val medians = measurement.timed.map { it.medianMicros }.sorted()
        val ann = probe.annIndexDefinition()
        val configuration = when {
            ann == null -> "no index on embedding (exact)"
            requested != null -> "${indexKind(ann)}, ef_search set to $requested"
            else -> "${indexKind(ann)}, ef_search default"
        }

        println()
        println("── the index ─────────────────────────────────────────────────────────────")
        println("stored      : ${probe.chunkCount()} chunks over ${probe.documentCount()} documents")
        println(
            "built now   : " + (
                built?.let { "yes, with ${it.strategy.label}" }
                    ?: "no, the table already had rows (this is the point of pgvector)"
                ),
        )
        probe.indexDefinitions().forEach { println("index       : $it") }
        if (ann == null) println("index       : none on the embedding column, so the search is exact")
        println("ef_search   : ${measurement.efSearchInEffect?.ifBlank { "unset placeholder" } ?: "could not be read"}")
        println("same as API : ${if (measurement.frameworkAgrees) "yes" else "NO — the copied SQL has diverged"}")
        probe.sampleChunk()?.let { println("one row     : $it") }
        println()
        println("Row for the table, measured in THIS state:")
        println("%-44s %9s %9s %11s".format("configuration", "recall@3", "recall@1", "query"))
        println(
            "%-44s %8.0f%% %8.0f%% %8.2f ms".format(
                configuration, recallAt3 * 100, recallAt1 * 100, medians[medians.size / 2] / 1000.0,
            ),
        )
        println()
        println("slowest query: %.2f ms    fastest: %.2f ms".format(medians.last() / 1000.0, medians.first() / 1000.0))
        println("──────────────────────────────────────────────────────────────────────────")
        println("Compare recall@1 across the three rows before recall@3. On a corpus")
        println("this size recall@3 is saturated and cannot show you what the index cost.")
        println("If NEITHER number moved, say what would change at a hundred thousand")
        println("chunks and why you cannot measure it here. That is a better answer than")
        println("a number you half believe.")
        println()
    }

    private fun indexKind(definition: String): String =
        if (definition.contains("USING hnsw")) "HNSW" else "IVFFlat"

    private companion object {
        const val TOP_K = 3

        /** `1 - similarityThreshold`, for the framework's default threshold of 0.0. */
        const val MAX_DISTANCE = 1.0

        /** Enough runs for a median to mean something, few enough to stay instant. */
        const val REPEATS = 7

        const val NO_DOC_ID = "(no docId)"

        /**
         * The metadata column is read as raw JSON here rather than parsed: the
         * one key this test needs is a string, and a JSON mapper in a row mapper
         * would be one more thing between the measurement and the database.
         */
        val DOC_ID_IN_JSON = Regex("\"docId\"\\s*:\\s*\"([^\"]*)\"")
    }
}
