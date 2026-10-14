package com.ai4dev.tinder4dogs.knowledge

/**
 * The YAML front matter at the top of a corpus document, parsed once.
 *
 * Extracted from [FrontmatterTransformer] because the index-time gate needs the
 * SAME parse of the SAME bytes, and a second implementation would be a gate
 * that checks something slightly different from what gets indexed.
 *
 * It reads the raw file rather than a transformed [org.springframework.ai.document.Document]
 * for one reason that matters: the transformer fills a missing `source` in from
 * the file name, which is the right thing for an index and the wrong thing for a
 * gate. After that fallback, "this document declares no id" is no longer
 * observable. The gate has to see the file as it was written.
 */
object Frontmatter {

    data class Parsed(val keys: Map<String, String>, val body: String) {
        operator fun get(key: String): String? = keys[key]?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns the front matter keys and the body without them. A file with no
     * front matter parses to no keys and its whole text as the body, which is
     * the right outcome for a page somebody pasted in without the header: the
     * gate then rejects it for having no `source`, instead of this throwing.
     *
     * Deliberately not a YAML parser. The front matter in this corpus is flat
     * `key: value` lines, and a dependency to read five of them would be a
     * dependency to review, pin and upgrade for ever.
     */
    fun parse(text: String): Parsed {
        val normalised = text.replace("\r\n", "\n")
        if (!normalised.startsWith("---\n")) return Parsed(emptyMap(), text)

        val end = normalised.indexOf("\n---", startIndex = 3)
        if (end < 0) return Parsed(emptyMap(), text)

        val block = normalised.substring(4, end)
        val body = normalised.substring(end + 4).trimStart('\n')

        val keys = block.lineSequence()
            .mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) {
                    null
                } else {
                    line.substring(0, colon).trim() to line.substring(colon + 1).trim()
                }
            }
            .toMap()

        return Parsed(keys, body)
    }
}
