package com.ai4dev.tinder4dogs.knowledge

import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.reader.TextReader
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component

/**
 * Builds the search index over the application's own documentation.
 *
 * The whole pipeline is four lines long and every one of them is one of the
 * ETL pipeline's roles: read, transform, split, write. Resisting the urge to
 * put anything clever in here is the point of the first lab.
 *
 * ## The chunking strategy is an argument
 *
 * It was hard-coded to the splitter's defaults in the previous lab, and making
 * it a parameter is an ADDED overload with a default value rather than a
 * rewrite: the call that measured 800 tokens still reads the same and still
 * means the same. That is what parameterising something should cost.
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
 * for it: today that is a test.
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
     */
    fun build(
        strategy: ChunkingStrategy = ChunkingStrategy.TOKEN_DEFAULT,
        target: VectorStore = vectorStore,
    ): IndexStats {
        val documents = read()
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
