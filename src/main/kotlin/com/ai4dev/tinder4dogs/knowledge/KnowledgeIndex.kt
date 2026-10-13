package com.ai4dev.tinder4dogs.knowledge

import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.reader.TextReader
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component

/**
 * Builds the search index over the application's own documentation.
 *
 * The whole pipeline is four lines long and every one of them is one of the
 * ETL pipeline's roles: read, transform, split, write. Resisting the urge to
 * put anything clever in here is the point of the first lab.
 *
 * ## The store is the database now, and that changes this class once
 *
 * Nothing in here names pgvector, and that is the design working: this class
 * depends on [VectorStore], the bean behind it moved from a
 * `SimpleVectorStore` in the application's heap to a `PgVectorStore` over a
 * `vector` column created by changeset 006, and the only line that had to
 * change is the one below that forgets before it writes.
 *
 * It had to change because the old store could not remember. Every run started
 * from an empty map, so writing the corpus twice was impossible. A store in the
 * database remembers across runs, across restarts and across instances, so
 * writing the corpus twice is not only possible, it is the default: the second
 * run leaves TWO copies of every chunk, the top three fills up with duplicates
 * of the same page, and recall drops for a reason that has nothing to do with
 * retrieval. The measurement would have blamed the chunking.
 *
 * So a build REPLACES what it indexes. It deletes the chunks of the documents
 * it is about to write, by `docId`, and then writes them. That is the ordinary
 * re-indexing strategy for a corpus whose documents are edited in place, and it
 * is the behaviour the recall harness needs in order to be re-runnable at all.
 *
 * Two things it deliberately does not do. It does not empty the table: chunks of
 * documents that are no longer in the corpus survive, which is a real problem in
 * production and the subject of a later lab rather than a line here. And it does
 * not try to be clever about what actually changed: fourteen documents are cheap
 * to re-embed, a hundred thousand are not, and the strategy for that is content
 * hashes and a decision nobody in this room has data for yet.
 *
 * ## The chunking strategy is an argument
 *
 * It was hard-coded to the splitter's defaults in the first lab, and making it a
 * parameter is an ADDED overload with a default value rather than a rewrite: the
 * call that measured 800 tokens still reads the same and still means the same.
 * That is what parameterising something should cost.
 *
 * The target store is an argument for the same reason. Measuring three
 * strategies means three indexes, and three indexes in one JVM means not
 * sharing one store between them: `SimpleVectorStore` has no way to forget
 * what it was told, so comparing strategies in a single store would measure
 * all three at once and call it the third.
 *
 * The index is not built at startup. An index build is an embedding call per
 * chunk, and a product that pays that bill every time somebody restarts it on
 * a laptop is a product nobody will run twice. It is built when something asks
 * for it: today that is a test. Now that the index outlives the process, "ask
 * for it" also stopped meaning "every run" — the chunks from this morning are
 * still in the table this afternoon.
 */
@Component
class KnowledgeIndex(
    private val vectorStore: VectorStore,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Reads every document in the corpus, chunks it with [strategy], and writes
     * the chunks to [target]. Returns what it did, because a build that reports
     * nothing cannot be checked.
     *
     * With [replaceExisting] the chunks already stored for these documents are
     * deleted first, which makes the build idempotent and is what you want
     * against a store that remembers. Pass `false` only for a store you created
     * yourself and know to be empty: it saves a round trip and nothing else.
     */
    fun build(
        strategy: ChunkingStrategy = ChunkingStrategy.TOKEN_DEFAULT,
        target: VectorStore = vectorStore,
        replaceExisting: Boolean = true,
    ): IndexStats {
        val documents = read()
        if (replaceExisting) forget(documents, target)
        val chunks = strategy.split(documents)

        target.write(chunks)

        val stats = IndexStats(
            documents = documents.size,
            chunks = chunks.size,
            chunksWithSection = chunks.count { it.metadata[SECTION] != null },
            strategy = strategy,
        )
        log.info(
            "knowledge index built with {}: {} documents, {} chunks, {} chunks per document on average",
            strategy.label,
            stats.documents,
            stats.chunks,
            "%.1f".format(stats.chunksPerDocument),
        )
        return stats
    }

    /** The corpus, read and with its front matter promoted to metadata. */
    fun read(): List<Document> {
        val resources = PathMatchingResourcePatternResolver()
            .getResources("classpath:$CORPUS_PATTERN")
            .sortedBy { it.filename }

        require(resources.isNotEmpty()) {
            "no documents matched classpath:$CORPUS_PATTERN — is the corpus still in the jar?"
        }

        val documents = resources.flatMap { TextReader(it).get() }
        return FrontmatterTransformer().apply(documents)
    }

    /**
     * Drops whatever is stored for these documents, by `docId`.
     *
     * The filter is built rather than written as a string. Both work — the
     * string form of this one is `docId in ['faq-generale.md', ...]` and the
     * framework parses it with its own grammar — but a built expression cannot
     * be broken by a file name that contains a quote, and the store translates
     * it to its own dialect either way. Against pgvector that dialect is a
     * PostgreSQL JSONPath predicate over the `metadata` column; against the
     * in-memory store it is a predicate over a map. Neither is this class's
     * business, which is the point of expressing it this way.
     */
    private fun forget(documents: List<Document>, target: VectorStore) {
        val docIds = documents.mapNotNull { it.metadata[DOC_ID] as? String }.distinct()
        if (docIds.isEmpty()) return

        target.delete(FilterExpressionBuilder().`in`(DOC_ID, *docIds.toTypedArray()).build())
        log.debug("dropped the existing chunks of {} documents before re-indexing", docIds.size)
    }

    data class IndexStats(
        val documents: Int,
        val chunks: Int,
        /** How many chunks can name the section they came from. */
        val chunksWithSection: Int = 0,
        val strategy: ChunkingStrategy = ChunkingStrategy.TOKEN_DEFAULT,
    ) {
        val chunksPerDocument: Double
            get() = if (documents == 0) 0.0 else chunks.toDouble() / documents
    }

    companion object {
        const val CORPUS_PATTERN = "knowledge/*.md"

        /** The metadata key holding the id written inside the document itself. */
        const val DOC_ID = "docId"

        /** The heading a chunk came from. Only [ChunkingStrategy.BY_HEADING] sets it. */
        const val SECTION = "section"
    }
}
