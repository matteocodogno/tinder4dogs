package com.ai4dev.tinder4dogs.knowledge

import org.springframework.ai.document.Document
import org.springframework.ai.document.DocumentTransformer

/**
 * Takes the YAML front matter out of the text and puts it into the metadata.
 *
 * This is a [DocumentTransformer], which is the ETL pipeline's own role for
 * "change the documents between reading them and splitting them", so it costs
 * nothing to put it where the framework expects it.
 *
 * It exists for two reasons, and the first one is easy to miss.
 *
 * The front matter is text. Left in place it gets embedded along with the
 * prose: the first chunk of every document carries five lines of YAML into its
 * vector, which moves that chunk's coordinates towards every other document's
 * front matter and away from what the page is actually about. Nothing fails,
 * the recall is just quietly worse.
 *
 * The second is that metadata is what makes an answer citable. A chunk that
 * cannot say which document it came from cannot be cited, and Module 13 makes
 * citation a release condition rather than a courtesy.
 *
 * ## Why the id is `docId` and not `source`
 *
 * `source` is taken. `TextReader.get()` writes the resource's file name into
 * the `source` key AFTER applying any custom metadata, so a `source` of our
 * own is silently overwritten by `faq-generale.md`. That is reasonable
 * behaviour and it is also invisible, so this class uses a key the framework
 * does not own and leaves `source` to mean what the framework means by it: the
 * file. `docId` is the id written in the document itself, which survives a
 * rename; `source` is where it was found today.
 */
class FrontmatterTransformer : DocumentTransformer {

    override fun apply(documents: List<Document>): List<Document> = documents.map(::transformOne)

    private fun transformOne(document: Document): Document {
        val text = document.text ?: return document
        val parsed = Frontmatter.parse(text)
        val frontmatter = parsed.keys
        val body = parsed.body

        // Start from what the reader already put there (source, charset) so
        // nothing is lost, then add ours on top.
        val metadata = document.metadata.toMutableMap()
        frontmatter["source"]?.let { metadata["docId"] = it }
        frontmatter["title"]?.let { metadata["title"] = it }
        frontmatter["updated"]?.let { metadata["updated"] = it }

        // A document with no id gets one from its file name rather than none at
        // all: a chunk that cannot be traced back is worse than an ugly id.
        metadata.putIfAbsent("docId", metadata["source"] ?: "unknown")

        return Document(body, metadata)
    }
}
