package com.ai4dev.tinder4dogs.knowledge

import org.springframework.ai.document.Document
import org.springframework.ai.transformer.splitter.TokenTextSplitter

/**
 * Cuts a markdown document at its `##` headings, and only then by size.
 *
 * Twenty lines of our own instead of a library, for a reason worth saying out
 * loud: a fixed token count knows nothing about headings, and the thing that
 * makes documentation answerable is that somebody already divided it into the
 * units that answer questions. Respecting that division is free.
 *
 * Two things come out of here that arithmetic cannot produce:
 *
 *  - **the `section` metadata**, which is what lets an answer cite not just a
 *    page but the part of the page it came from. Part 5 makes citation a
 *    release condition, and "it is in the FAQ somewhere" is not a citation
 *  - **boundaries that do not fall mid-sentence**, so an answer that spans a
 *    cause and its remedy stays in one piece when those are one section, and
 *    is split cleanly in two when they are not. On Spring AI 2.0.1 this is the
 *    only protection available, because that version's `TokenTextSplitter` has
 *    no overlap to fall back on
 *
 * A section longer than the delegate's own chunk size is handed to
 * [longSectionSplitter] (400 tokens, no overlap, because there is none to be had), and the sub-chunks keep this section's metadata
 * because the splitter copies a document's metadata onto its chunks. That
 * assumption is pinned by a test rather than trusted.
 */
class HeadingSplitter(
    private val longSectionSplitter: TokenTextSplitter,
) {

    fun split(documents: List<Document>): List<Document> = documents.flatMap(::splitOne)

    private fun splitOne(document: Document): List<Document> {
        val text = document.text ?: return listOf(document)
        val sections = sectionsOf(text)
        if (sections.isEmpty()) return listOf(document)

        return sections.flatMap { section ->
            val metadata = document.metadata.toMutableMap()
            metadata[KnowledgeIndex.SECTION] = section.heading
            val piece = Document(section.text, metadata)

            // Only the long ones get cut further, so a short section stays a
            // single chunk with a single clean boundary.
            if (section.text.length <= LONG_SECTION_CHARS) {
                listOf(piece)
            } else {
                longSectionSplitter.split(listOf(piece))
            }
        }
    }

    private data class Section(val heading: String, val text: String)

    /**
     * Everything before the first `##` is the document's own opening, and it is
     * kept only when it carries enough text to be worth a vector of its own.
     * Below that it is folded into the first section, because a chunk that is
     * three words long is noise with coordinates.
     */
    private fun sectionsOf(text: String): List<Section> {
        val lines = text.lines()
        val sections = mutableListOf<Section>()
        var heading = OPENING
        val buffer = StringBuilder()

        fun flush() {
            val body = buffer.toString().trim()
            if (body.isNotEmpty()) sections += Section(heading, body)
            buffer.setLength(0)
        }

        for (line in lines) {
            if (line.startsWith("## ")) {
                flush()
                heading = line.removePrefix("## ").trim()
            }
            buffer.appendLine(line)
        }
        flush()

        if (sections.size >= 2 && sections.first().heading == OPENING &&
            sections.first().text.length < MIN_OPENING_CHARS
        ) {
            val opening = sections.removeAt(0)
            val first = sections[0]
            sections[0] = first.copy(text = opening.text + "\n\n" + first.text)
        }
        return sections
    }

    private companion object {
        const val OPENING = "(opening)"

        /** About 400 tokens of Italian prose, which is the delegate's target. */
        const val LONG_SECTION_CHARS = 1600

        const val MIN_OPENING_CHARS = 200
    }
}
