package com.ai4dev.tinder4dogs.knowledge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Measures the search, with no language model in the loop.
 *
 * ## Why this is a test and not a script
 *
 * Retrieval is a pure function. Given a corpus, a chunking, an embedding model
 * and a question, either the right document is in the top k or it is not. No
 * sampling, no temperature, no judge, nothing that can answer differently on a
 * Tuesday. That makes it the half of a RAG system you can pin, and pinning it
 * is what tells you whether a bad answer is the retrieval's fault or the
 * prompt's.
 *
 * The number this prints is a ceiling. No prompt and no model can answer from
 * text it was never given, so whatever recall@3 says here is the best the
 * finished feature can do. Measuring it before writing a prompt is the whole
 * argument for doing this lab first.
 *
 * ## What it is honest about
 *
 * **It is not a unit test yet, whatever the comment above claims.** It needs
 * the gateway, a key, and a database to start the context, so it costs real
 * embedding calls on every run and it cannot go into a pipeline as it stands.
 * The fix is known and is deliberately not here: `SimpleVectorStore` has
 * `save(File)` and `load(File)`, so an index built once and committed as a
 * fixture would make this hermetic and free. That is a conversation about
 * caching, and caching an index is also the thing that would hide the index
 * build's cost from the room's dashboard this afternoon.
 *
 * **Nothing here asserts a recall level**, because nobody has run it on this
 * corpus. Asserting a number somebody guessed is worse than asserting none:
 * the first person to see it red would lower the number instead of looking at
 * the misses. Measure your corpus, agree on a floor, then uncomment the
 * assertion at the bottom and let it block the pipeline.
 *
 * It is skipped with no API key, so `mise run test` stays green on a laptop
 * that has not been handed a key yet.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
    named = "AI4DEV_API_KEY",
    matches = ".+",
    disabledReason = "the retrieval measurement makes real embedding calls through the gateway",
)
class RetrievalRecallTest {

    @Autowired
    private lateinit var index: KnowledgeIndex

    @Autowired
    private lateinit var vectorStore: VectorStore

    private data class Outcome(
        val question: RetrievalSet.Question,
        val retrieved: List<String>,
    ) {
        val hitAt3: Boolean get() = question.expectedSource in retrieved
        val hitAt1: Boolean get() = retrieved.firstOrNull() == question.expectedSource
    }

    @Test
    fun `the search finds the document that answers the question`() {
        val stats = index.build()
        val questions = RetrievalSet.load()

        val outcomes = questions.map { q ->
            val hits = vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(q.question)
                    .topK(TOP_K)
                    // Spelt out even though it is the default, because the
                    // default is SIMILARITY_THRESHOLD_ACCEPT_ALL = 0.0 and a
                    // floor of zero is a decision whether or not you made it.
                    .similarityThreshold(0.0)
                    .build(),
            ).orEmpty()

            Outcome(q, hits.map { it.metadata[KnowledgeIndex.DOC_ID] as? String ?: "(no docId)" })
        }

        report(stats, outcomes)

        // What can be asserted without having run this once.
        assertThat(outcomes).hasSize(questions.size)
        assertThat(outcomes).allSatisfy { assertThat(it.retrieved).isNotEmpty() }

        // The assumption that would otherwise fail silently: a chunk keeps the
        // metadata of the document it was cut from. Nothing in the splitter's
        // documentation promises it, and without it every answer in this
        // module is uncitable.
        assertThat(outcomes.flatMap { it.retrieved })
            .describedAs("every chunk must carry the id of the document it came from")
            .doesNotContain("(no docId)")

        // Uncomment once you have a measured baseline for YOUR corpus, and
        // then treat a drop as a regression rather than as a surprise.
        // assertThat(recall(outcomes) { it.hitAt3 }).isGreaterThanOrEqualTo(0.80)
    }

    private fun report(stats: KnowledgeIndex.IndexStats, outcomes: List<Outcome>) {
        val at3 = recall(outcomes) { it.hitAt3 }
        val at1 = recall(outcomes) { it.hitAt1 }

        println()
        println("── retrieval ─────────────────────────────────────────────────")
        println("corpus      : ${stats.documents} documents, ${stats.chunks} chunks")
        println("recall@$TOP_K    : ${percent(at3)}  (${outcomes.count { it.hitAt3 }}/${outcomes.size})")
        println("recall@1    : ${percent(at1)}  (${outcomes.count { it.hitAt1 }}/${outcomes.size})")

        val misses = outcomes.filterNot { it.hitAt3 }
        if (misses.isEmpty()) {
            println("misses      : none")
        } else {
            println("misses      : ${misses.size}")
            misses.forEach { m ->
                println("  ${m.question.id}  \"${m.question.question}\"")
                println("      expected: ${m.question.expectedSource}")
                println("      got     : ${m.retrieved.joinToString(", ")}")
            }
        }
        println("──────────────────────────────────────────────────────────────")
        println("Read the misses, not the number. For each one: chunking,")
        println("wording, or corpus? The next lab only fixes the first.")
        println()
    }

    private fun recall(outcomes: List<Outcome>, hit: (Outcome) -> Boolean): Double =
        if (outcomes.isEmpty()) 0.0 else outcomes.count(hit).toDouble() / outcomes.size

    private fun percent(value: Double): String = "%.0f%%".format(value * 100)

    private companion object {
        const val TOP_K = 3
    }
}
