package com.ai4dev.tinder4dogs.knowledge

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Where the vectors live, for now.
 *
 * [SimpleVectorStore] is documented as "good only for testing purposes" and the
 * reference carries an explicit callout against using it in production. It is
 * here on purpose and it is here temporarily.
 *
 * On purpose, because for this lab everything about it is an advantage: the
 * index rebuilds in seconds, there is nothing to install, and `cosineSimilarity`
 * is a public method in its own source that anybody curious can go and read. A
 * lab that spends its first twenty minutes on a database extension teaches a
 * database extension.
 *
 * Temporarily, because it lives in this process's heap. The index is gone at
 * every restart, it is not shared between two instances, and it cannot filter
 * by metadata inside the search. All three are things the product needs on its
 * first day in front of users, and all three are why this bean is replaced
 * later in the module by pgvector in the database the product already runs.
 *
 * The replacement is a one-line change here precisely because everything else
 * depends on [VectorStore] and not on this class. That is also what makes the
 * swap measurable: the same recall harness runs against both.
 */
@Configuration
class KnowledgeConfiguration {

    @Bean
    fun vectorStore(embeddingModel: EmbeddingModel): VectorStore =
        SimpleVectorStore.builder(embeddingModel).build()
}
