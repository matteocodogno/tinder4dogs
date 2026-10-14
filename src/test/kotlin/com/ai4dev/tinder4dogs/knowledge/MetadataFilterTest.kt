package com.ai4dev.tinder4dogs.knowledge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser
import org.springframework.ai.vectorstore.pgvector.PgVectorFilterExpressionConverter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * A retrieval problem becomes a WHERE clause.
 *
 * ```
 * mvn -q test -Dtest=MetadataFilterTest
 * AI4DEV_FRESH_SINCE=2026-06-01 mvn -q test -Dtest=MetadataFilterTest
 * ```
 *
 * The corpus has a page that is simply out of date: the guide to the "cane del
 * mese" badge describes a feature the Q3 release notes removed. Retrieval has no
 * opinion about that. The stale page is well written, it is about the badge, and
 * it wins on similarity, so the honest answer about a feature that no longer
 * exists is the answer the system gives.
 *
 * Nothing in the embedding, the chunking or the prompt fixes this. A date does.
 * Every document in this corpus carries `updated` in its front matter, the
 * transformer puts it in the metadata, and the metadata is a column: so "do not
 * answer from pages older than X" stops being a retrieval problem and becomes a
 * condition in the query. That is the mechanism the later lab reuses for stale
 * documents, duplicates and anything else the corpus is guilty of.
 *
 * This is also the capability the previous store did not have in any usable
 * form. `SimpleVectorStore` can evaluate a filter, in Java, over every document
 * in the map, after it has scored all of them. PostgreSQL evaluates it as part
 * of the query, next to the data, with the rest of the product's indexes
 * available to it. Same API, different thing entirely.
 *
 * ## The two surprises, and both are measured here
 *
 * **A document with no `updated` disappears.** Filtering on `updated` removes it
 * silently, because a JSONPath predicate over a key that is not there is not
 * true. A filter on metadata is therefore also a filter on metadata QUALITY, and
 * nothing anywhere reports which of the two excluded a document.
 *
 * The corpus used to contain exactly that case: `faq-foto-v3.md` with no date.
 * The index-time gate now refuses to index an undated document at all, so the
 * fix has moved upstream and this filter is what catches the next one before
 * anybody runs the gate.
 *
 * **The filter is not SQL you wrote.** It is parsed from a portable expression
 * language and translated by the store into a PostgreSQL JSONPath predicate
 * against the `metadata` column. The test prints the translation, because the
 * thing that eventually appears in a slow-query log is that, not this.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
    named = "AI4DEV_API_KEY",
    matches = ".+",
    disabledReason = "the filter is measured against a real search, which needs real embeddings",
)
class MetadataFilterTest {

    @Autowired
    private lateinit var index: KnowledgeIndex

    @Autowired
    private lateinit var vectorStore: VectorStore

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `one metadata condition, and what it removes`() {
        val probe = VectorStoreProbe(jdbc)
        if (probe.chunkCount() == 0) index.build()

        val cutoff = System.getProperty("freshSince") ?: System.getenv("AI4DEV_FRESH_SINCE") ?: DEFAULT_CUTOFF
        val filter = "updated >= '$cutoff'"
        val questions = RetrievalSet.load()

        val unfiltered = questions.associate { it.id to search(it.question, null) }
        val filtered = questions.associate { it.id to search(it.question, filter) }

        report(cutoff, filter, questions, unfiltered, filtered)

        // The invariant, and it is the whole claim: after the filter, nothing
        // retrieved is older than the cutoff. Written against the cutoff rather
        // than against a date, so overriding the cutoff changes what is measured
        // and not whether the measurement is valid.
        assertThat(filtered.values.flatten())
            .describedAs("nothing retrieved may be older than %s", cutoff)
            .allMatch { hit -> hit.updated != null && hit.updated >= cutoff }

        // THE TWO NAMED CASES THIS TEST USED TO PIN ARE GONE, and that is the
        // most interesting thing in this file now.
        //
        // It asserted that `faq-foto-v3` vanished under the filter because its
        // front matter carried no `updated`, and that the stale badge guide
        // vanished because it was older than the cutoff. Then the index-time
        // gate arrived: the undated file was dated, and the guide was deleted
        // because it documents a feature the release notes removed.
        //
        // So those two assertions have nothing left to catch. The filter still
        // works and the invariant above still holds. What the day revealed is
        // that this filter was COMPENSATING FOR A CORPUS NOBODY HAD GATED: fix
        // the cause at index time and the runtime workaround goes quiet. It
        // stays in the product anyway, because the next document somebody adds
        // without a date will arrive before anyone runs the gate, and a filter
        // that costs nothing is worth keeping for that day.

        // A filter must not empty the index. If it does, the cutoff is wrong and
        // the feature would answer "I do not know" to everything.
        assertThat(filtered.values.filter { it.isEmpty() })
            .describedAs("every question must still retrieve something after filtering")
            .isEmpty()
    }

    /** What came back: the document, and the date the filter is about. */
    private data class Hit(val docId: String, val updated: String?)

    private fun search(question: String, filter: String?): List<Hit> {
        val request = SearchRequest.builder().query(question).topK(TOP_K).similarityThreshold(0.0)
        if (filter != null) request.filterExpression(filter)
        return vectorStore.similaritySearch(request.build())
            .orEmpty()
            .map {
                Hit(
                    docId = it.metadata[KnowledgeIndex.DOC_ID] as? String ?: "(no docId)",
                    updated = it.metadata["updated"] as? String,
                )
            }
    }

    private fun report(
        cutoff: String,
        filter: String,
        questions: List<RetrievalSet.Question>,
        unfiltered: Map<String, List<Hit>>,
        filtered: Map<String, List<Hit>>,
    ) {
        val translated = runCatching {
            PgVectorFilterExpressionConverter()
                .convertExpression(FilterExpressionTextParser().parse(filter))
        }.getOrElse { "could not be translated: ${it.message}" }

        val reachable = { results: Map<String, List<Hit>> -> results.values.flatten().map { it.docId }.toSet() }
        val gone = (reachable(unfiltered) - reachable(filtered)).sorted()
        val changed = questions.filter { unfiltered[it.id] != filtered[it.id] }
        val recall = { results: Map<String, List<Hit>> ->
            questions.count { q -> q.expectedSource in results.getValue(q.id).map { it.docId } }
                .toDouble() / questions.size
        }
        val recallAt1 = { results: Map<String, List<Hit>> ->
            questions.count { q -> results.getValue(q.id).firstOrNull()?.docId == q.expectedSource }
                .toDouble() / questions.size
        }

        println()
        println("── one WHERE clause ──────────────────────────────────────────────────────")
        println("filter      : $filter")
        println("as SQL      : $translated")
        println("recall@$TOP_K    : ${percent(recall(unfiltered))} without it, ${percent(recall(filtered))} with it")
        println("recall@1    : ${percent(recallAt1(unfiltered))} without it, ${percent(recallAt1(filtered))} with it")
        println("no longer reachable, anywhere in the top $TOP_K:")
        if (gone.isEmpty()) println("  nothing") else gone.forEach { println("  $it") }
        println()
        println("questions whose top $TOP_K changed: ${changed.size}")
        changed.forEach { q ->
            println("  ${q.id}  \"${q.question}\"   expected ${q.expectedSource}")
            println("      without: ${unfiltered.getValue(q.id).joinToString(", ") { it.docId }}")
            println("      with   : ${filtered.getValue(q.id).joinToString(", ") { it.docId }}")
        }
        println("──────────────────────────────────────────────────────────────────────────")
        println("Look at recall@1 and not recall@$TOP_K. A stale page that wins FIRST")
        println("place still leaves the right document in the top $TOP_K, so recall@$TOP_K")
        println("cannot see the problem the filter solves. recall@1 can.")
        println()
        println("Two questions for the group. Did the filter FIX an answer, and which one?")
        println("And which document did it remove for a reason that has nothing to do with")
        println("$cutoff? Then decide who is responsible for that document's front matter.")
        println()
    }

    private fun percent(value: Double): String = "%.0f%%".format(value * 100)

    private companion object {
        const val TOP_K = 3

        /**
         * Before this date the corpus contains a guide the release notes have
         * since contradicted. It is a decision about the product's freshness and
         * it belongs to whoever owns the documentation, not to this test, which
         * is why it is overridable from the environment.
         */
        const val DEFAULT_CUTOFF = "2026-01-01"

    }
}
