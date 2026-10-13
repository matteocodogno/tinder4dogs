package com.ai4dev.tinder4dogs.knowledge

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * The same twenty questions against three chunkings.
 *
 * ```
 * mvn -q test -Dtest=ChunkingComparisonTest
 * ```
 *
 * Nothing about the prompt changes here, because there still is no prompt. One
 * variable moves, the strategy, and everything else is held still: same corpus,
 * same questions, same embedding model, same k. That is the whole design of the
 * lab and it is the only way the three rows can be compared at all.
 *
 * ## The four numbers, and why the last one is the one a PO reads
 *
 *  - **chunks**: how many pieces the corpus became
 *  - **recall@3** and **recall@1**: how often the right document is in the top
 *    three, and how often it is first. The gap between them is a diagnosis: a
 *    high recall@3 with a low recall@1 is a ranking problem, both low is an
 *    embedding, chunking or corpus problem, and the two want different fixes
 *  - **context tokens**: the average size of what would be sent to the model
 *    for one question. This is paid on every request for ever, so a strategy
 *    that buys half a point of recall by doubling the context is a decision
 *    about the bill and not about quality
 *
 * The tokens are counted with CL100K_BASE, which is the same tokenizer the
 * splitter counts with, through the library the splitter itself uses. It is
 * therefore honest about what the splitter did, and still only an estimate of
 * what another vendor's model will charge for the same text.
 *
 * ## Three indexes, three stores
 *
 * `SimpleVectorStore` cannot forget. Measuring three strategies in one store
 * would measure all three at once and report the third, so each strategy gets a
 * store of its own, built from the same `EmbeddingModel` bean.
 *
 * Cost and duration: three builds plus sixty query embeddings. On this corpus
 * that is a few hundred calls of a few hundred tokens, which is fractions of a
 * cent and a couple of minutes of waiting, because the toy store embeds one
 * document per call and does not batch. The store that replaces it does batch,
 * through a `BatchingStrategy` its starter auto-configures, so the same build
 * against pgvector is a handful of calls rather than a hundred. That is not a
 * detail if your gateway is rate limited.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
    named = "AI4DEV_API_KEY",
    matches = ".+",
    disabledReason = "three index builds and sixty queries, all real embedding calls",
)
class ChunkingComparisonTest {

    @Autowired
    private lateinit var index: KnowledgeIndex

    @Autowired
    private lateinit var embeddingModel: EmbeddingModel

    private val encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE)

    private data class Row(
        val strategy: ChunkingStrategy,
        val chunks: Int,
        val recallAt3: Double,
        val recallAt1: Double,
        val contextTokens: Double,
        val hits: Map<String, Boolean>,
        val sections: Int,
    )

    @Test
    fun `three chunkings, one question set`() {
        val questions = RetrievalSet.load()
        val rows = ChunkingStrategy.entries.map { strategy -> measure(strategy, questions) }

        table(rows)
        moved(rows, questions)

        assertThat(rows).hasSize(ChunkingStrategy.entries.size)
        assertThat(rows).allSatisfy { assertThat(it.chunks).isGreaterThan(0) }

        // The heading strategy has one job the other two cannot do, and it is
        // the job Part 5 turns into a release condition: a chunk that can name
        // the part of the page it came from.
        val byHeading = rows.single { it.strategy == ChunkingStrategy.BY_HEADING }
        assertThat(byHeading.sections)
            .describedAs("every chunk cut at a heading must carry its section")
            .isEqualTo(byHeading.chunks)
    }

    private fun measure(strategy: ChunkingStrategy, questions: List<RetrievalSet.Question>): Row {
        val store = SimpleVectorStore.builder(embeddingModel).build()
        // Nothing to forget: this store was created a line ago. The build
        // deletes what it is about to write by default, which is what a store
        // that remembers across runs needs and what this one cannot have.
        val stats = index.build(strategy, store, replaceExisting = false)

        var hitAt3 = 0
        var hitAt1 = 0
        var tokens = 0L
        val hits = mutableMapOf<String, Boolean>()

        questions.forEach { q ->
            val found = store.similaritySearch(
                SearchRequest.builder().query(q.question).topK(TOP_K).similarityThreshold(0.0).build(),
            ).orEmpty()

            val ids = found.map { it.metadata[KnowledgeIndex.DOC_ID] as? String ?: "(no docId)" }
            val hit = q.expectedSource in ids
            hits[q.id] = hit
            if (hit) hitAt3++
            if (ids.firstOrNull() == q.expectedSource) hitAt1++
            tokens += found.sumOf { encoding.countTokens(it.text.orEmpty()).toLong() }
        }

        // The context is counted over the chunks that were RETRIEVED rather than
        // over the index, because what you pay for is what you send and not what
        // you stored.
        return Row(
            strategy = strategy,
            chunks = stats.chunks,
            recallAt3 = hitAt3.toDouble() / questions.size,
            recallAt1 = hitAt1.toDouble() / questions.size,
            contextTokens = tokens.toDouble() / questions.size,
            hits = hits,
            sections = stats.chunksWithSection,
        )
    }

    private fun table(rows: List<Row>) {
        println()
        println("── three chunkings, twenty questions ─────────────────────────────────────")
        println("%-46s %7s %9s %9s %9s".format("strategy", "chunks", "recall@3", "recall@1", "ctx tok"))
        rows.forEach { r ->
            println(
                "%-46s %7d %8.0f%% %8.0f%% %9.0f".format(
                    r.strategy.label, r.chunks, r.recallAt3 * 100, r.recallAt1 * 100, r.contextTokens,
                ),
            )
        }
        println("──────────────────────────────────────────────────────────────────────────")
        println("Say which one ships in ONE sentence that mentions recall and context both.")
        println()
    }

    private fun moved(rows: List<Row>, questions: List<RetrievalSet.Question>) {
        val changed = questions.filter { q -> rows.map { it.hits[q.id] }.distinct().size > 1 }
        if (changed.isEmpty()) {
            println("No question changed outcome between the three strategies.")
            println("That is a finding: on a corpus this small the boundaries may all fall")
            println("in harmless places. Say what would change at a hundred thousand chunks.")
            println()
            return
        }
        println("Questions whose outcome CHANGED with the strategy:")
        changed.forEach { q ->
            val per = rows.joinToString("  ") { r ->
                "${r.strategy.name}=${if (r.hits[q.id] == true) "hit" else "miss"}"
            }
            println("  ${q.id}  \"${q.question}\"")
            println("      expected ${q.expectedSource}   $per")
        }
        println()
        println("Explain the mechanism for one of them WITHOUT using the word better.")
        println()
    }

    private companion object {
        const val TOP_K = 3
    }
}
