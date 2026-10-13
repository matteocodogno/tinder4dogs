package com.ai4dev.tinder4dogs.knowledge

import org.springframework.ai.document.Document
import org.springframework.ai.transformer.splitter.TokenTextSplitter

/**
 * How the corpus gets cut, as an argument rather than an edit.
 *
 * LAB 1 ran on [TOKEN_DEFAULT] because that is what the framework hands you
 * when you do not choose. This enum exists so that the next lab can measure
 * three strategies against the same twenty questions without touching the
 * index build, which is the difference between an experiment and an opinion.
 *
 * ## The splitter this project is pinned to has no overlap
 *
 * Read from the source of the version in this pom, Spring AI **2.0.1**, and not
 * from the current branch, which is a different splitter: `TokenTextSplitter`
 * takes `chunkSize` (default **800** tokens), `minChunkSizeChars`,
 * `minChunkLengthToEmbed`, `maxNumChunks`, `keepSeparator`, `punctuationMarks`
 * and `encodingType`. **There is no overlap: not a default, not a parameter,
 * not a field.** A later version adds `withChunkOverlap` to the builder; this
 * one cannot express it at all.
 *
 * That matters more than a wrong default would, because the playbook asks for
 * 50 tokens of overlap and **the framework the course ships cannot give it**.
 * So the protection against an answer that straddles a boundary has to be
 * structural rather than parametric: cut where a human already cut, which is
 * what [BY_HEADING] does. In this version that strategy is not a refinement,
 * it is the only one of the three that protects a crossing answer.
 *
 * The honest options, if overlap is what you want: upgrade the BOM and pay the
 * upgrade, write your own splitter, or choose boundaries that do not need it.
 * This lab measures the third.
 */
enum class ChunkingStrategy(val label: String) {

    /** What LAB 1 measured: 800 tokens, nobody's decision. */
    TOKEN_DEFAULT("TokenTextSplitter defaults (800 tokens)"),

    /**
     * The playbook's size, as close as 2.0.1 gets to its recommendation: the
     * 300-to-500 range is expressible, the 50 tokens of overlap are not.
     */
    TOKEN_400("400 tokens, no overlap available"),

    /**
     * Headings first, size second. Documentation is written in sections and a
     * section is usually the unit that answers a question, so a boundary a
     * human chose beats a boundary arithmetic chose. It also produces the
     * `section` metadata that a citation needs, and on this version it is the
     * only answer to the overlap problem.
     */
    BY_HEADING("by heading, then 400 inside long sections"),
    ;

    fun split(documents: List<Document>): List<Document> = when (this) {
        TOKEN_DEFAULT -> TokenTextSplitter.builder().build().split(documents)
        TOKEN_400 -> tokens400().split(documents)
        BY_HEADING -> HeadingSplitter(longSectionSplitter = tokens400()).split(documents)
    }

    private fun tokens400(): TokenTextSplitter = TokenTextSplitter.builder()
        .withChunkSize(400)
        .build()
}
