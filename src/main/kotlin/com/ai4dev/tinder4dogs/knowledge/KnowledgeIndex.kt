package com.ai4dev.tinder4dogs.knowledge

import org.slf4j.LoggerFactory
import org.springframework.ai.reader.TextReader
import org.springframework.ai.transformer.splitter.TokenTextSplitter
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
 * ## What is NOT here, on purpose
 *
 * The chunking strategy is hard-coded to the splitter's defaults. The next lab
 * turns it into a parameter and measures three of them against the same twenty
 * questions, and it can only do that honestly if this lab ran on the default
 * nobody chose.
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
     * Reads every document in the corpus, chunks it, and writes the chunks to
     * the store. Returns what it did, because a build that reports nothing
     * cannot be checked.
     */
    fun build(): IndexStats {
        val resources = PathMatchingResourcePatternResolver()
            .getResources("classpath:$CORPUS_PATTERN")
            .sortedBy { it.filename }

        require(resources.isNotEmpty()) {
            "no documents matched classpath:$CORPUS_PATTERN — is the corpus still in the jar?"
        }

        val documents = resources.flatMap { TextReader(it).get() }
        val withMetadata = FrontmatterTransformer().apply(documents)
        val chunks = TokenTextSplitter.builder().build().split(withMetadata)

        vectorStore.write(chunks)

        val stats = IndexStats(documents = documents.size, chunks = chunks.size)
        log.info(
            "knowledge index built: {} documents, {} chunks, {} chunks per document on average",
            stats.documents,
            stats.chunks,
            "%.1f".format(stats.chunksPerDocument),
        )
        return stats
    }

    data class IndexStats(val documents: Int, val chunks: Int) {
        val chunksPerDocument: Double
            get() = if (documents == 0) 0.0 else chunks.toDouble() / documents
    }

    companion object {
        const val CORPUS_PATTERN = "knowledge/*.md"

        /** The metadata key holding the id written inside the document itself. */
        const val DOC_ID = "docId"
    }
}
