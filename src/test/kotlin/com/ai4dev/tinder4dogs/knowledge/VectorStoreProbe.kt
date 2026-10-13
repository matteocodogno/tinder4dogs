package com.ai4dev.tinder4dogs.knowledge

import org.springframework.jdbc.core.JdbcTemplate

/**
 * The index, looked at with SQL instead of through the framework.
 *
 * Everything in this class is a question you could not ask of the previous
 * store, and that is the whole reason it exists. `SimpleVectorStore` was a map
 * in the application's heap: there was no row to count, no index to inspect, no
 * session setting to read and nothing to look at after the process exited. Now
 * there is a table, and a table can be interrogated by anybody with psql and no
 * knowledge of Spring AI at all. That is a property of the storage choice and
 * not a convenience of this class.
 *
 * It is test code on purpose. The product has no reason to ship an endpoint that
 * reads `pg_indexes`; a lab measuring what an index costs has every reason to
 * know whether the index is there.
 */
class VectorStoreProbe(private val jdbc: JdbcTemplate) {

    /** Chunks stored. One row is one chunk, not one document. */
    fun chunkCount(): Int = jdbc.queryForObject("SELECT count(*) FROM $TABLE", Int::class.javaObjectType) ?: 0

    /** Documents represented, read out of the JSON metadata column. */
    fun documentCount(): Int =
        jdbc.queryForObject("SELECT count(DISTINCT metadata->>'docId') FROM $TABLE", Int::class.javaObjectType) ?: 0

    /**
     * Every index on the table, as PostgreSQL itself describes it.
     *
     * `filterNotNull` because `queryForList` promises a `List<String?>`: a
     * column can be null and the framework has no way to know this one cannot.
     * Kotlin is right to insist.
     */
    fun indexDefinitions(): List<String> = jdbc.queryForList(
        "SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND tablename = ? ORDER BY indexname",
        String::class.java,
        SCHEMA,
        TABLE,
    ).filterNotNull()

    /**
     * The approximate-search index, if there is one. The primary key's index
     * does not count: it is on `id` and no similarity query will ever use it.
     */
    fun annIndexDefinition(): String? =
        indexDefinitions().firstOrNull { it.contains("USING hnsw") || it.contains("USING ivfflat") }

    /**
     * `hnsw.ef_search` as the current session sees it.
     *
     * Three outcomes, and all three are informative. A number means pgvector's
     * library is loaded in this session and that is the value a query would use.
     * An empty string means the setting exists only as an unrecognised
     * placeholder, which is what PostgreSQL does with a dotted name no loaded
     * extension has claimed: read it BEFORE any vector query has touched the
     * table and this is what you get, because the extension's library is loaded
     * lazily. Null means the statement failed outright.
     *
     * Which is why the measurement reads this AFTER running its queries.
     */
    fun efSearch(): String? =
        runCatching { jdbc.queryForObject("SHOW hnsw.ef_search", String::class.java) }.getOrNull()

    /**
     * One stored chunk, as one line, for looking at.
     *
     * The same row `SELECT * FROM vector_store LIMIT 1` would show you in psql,
     * which is the version of this to run in the lab: the value here is that
     * there IS a row to select, somewhere outside the process that wrote it.
     */
    fun sampleChunk(): String? = runCatching {
        jdbc.queryForObject(
            """
            SELECT id::text || '  ' || metadata::text || '  ' || left(content, 100)
            FROM $TABLE ORDER BY id LIMIT 1
            """.trimIndent(),
            String::class.java,
        )
    }.getOrNull()

    companion object {
        const val SCHEMA = "public"
        const val TABLE = "vector_store"

        /**
         * The similarity query, copied verbatim from `PgVectorStore`'s
         * `COSINE_DISTANCE` template with its two placeholders filled in
         * (`%s` table name, `%s` metadata filter clause).
         *
         * It is reproduced here for one reason: a measurement of what an index
         * costs has to time the DATABASE, and `vectorStore.similaritySearch`
         * also makes an HTTP call to embed the question. On this corpus that
         * call is two orders of magnitude slower than the query, so timing the
         * framework's method measures the gateway and reports it as the index.
         *
         * Reproducing the SQL is a liability and it is declared: if the
         * framework changes its template, this diverges silently. The
         * measurement therefore checks, for one question, that this query and
         * `similaritySearch` return the same top three.
         *
         * The parameters, in order: the query vector (twice, once for the
         * projection and once for the predicate), the maximum distance, and k.
         * The distance is `1 - similarityThreshold`, so the framework's default
         * threshold of 0.0 is a maximum distance of 1.0 here: cosine distance
         * below 1 is cosine similarity above 0.
         */
        val SIMILARITY_SQL = """
            SELECT *, embedding <=> ?::vector AS distance
            FROM $SCHEMA.$TABLE
            WHERE embedding <=> ?::vector < ?
            ORDER BY distance
            LIMIT ?
        """.trimIndent()

        /**
         * A float array as a PostgreSQL vector literal.
         *
         * `?::vector` with a string parameter rather than the driver's own
         * `PGvector` type, so that nothing in this file depends on a client
         * library: this is the wire format, and seeing it once is worth more
         * than hiding it.
         */
        fun vectorLiteral(embedding: FloatArray): String =
            embedding.joinToString(separator = ",", prefix = "[", postfix = "]")
    }
}
