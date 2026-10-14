package com.ai4dev.tinder4dogs.knowledge

import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component

/** Which rule a document broke. */
enum class CorpusRule {
    /** No `source`, or no `updated`. No date, no index. */
    METADATA,

    /** Older than the stated cutoff, and not allow-listed. */
    STALE,

    /** Byte-identical body, or too similar to another document to be a separate one. */
    DUPLICATE,

    /** An email address or an Italian mobile number in the body. */
    PII,
}

/** One document, one rule, one sentence that names the file. */
data class CorpusViolation(
    val file: String,
    val rule: CorpusRule,
    val detail: String,
) {
    override fun toString(): String = "$file: [$rule] $detail"
}

/** Thrown instead of indexing. The message is the report. */
class CorpusRejected(val violations: List<CorpusViolation>) : IllegalStateException(
    "the corpus was refused by the index-time gate, ${violations.size} violations:\n" +
        violations.joinToString("\n") { "  - $it" },
)

/**
 * Refuses to index a corpus that is not fit to answer from.
 *
 * ## It refuses. It does not clean, fix, or do its best.
 *
 * That is the whole design and it is the only part worth arguing about. A build
 * that fails with "faq-foto-v3.md has no updated field" costs somebody twenty
 * minutes. An answer that quotes a removed feature to a paying customer costs a
 * support ticket, a trust problem and a meeting about whether to turn the bot
 * off. The second is not more expensive by a little.
 *
 * A gate that silently repaired its input would be worse than no gate: it would
 * hide the day somebody added a document with no date, and nobody would find out
 * for a month. Same move as typed model output in the previous module: what can
 * be checked mechanically is checked mechanically, and the failure is loud.
 *
 * ## The four rules, and what each one actually costs
 *
 * **METADATA and STALE** are free and deterministic. They read the front matter
 * and a date. These two belong in continuous integration on Monday morning with
 * no further discussion.
 *
 * **PII** is free, deterministic and *crude*. Two regular expressions, and the
 * group is expected to say out loud what they miss: a name with no email next to
 * it, an address, a tax code, a customer id, a phone number written with dots,
 * anything inside an image. It also over-matches, which is the more instructive
 * half: the published support address in a FAQ is an email address and is not
 * personal data. That is what the allow-list is for, and why an allow-list entry
 * has to carry a reason.
 *
 * **DUPLICATE** has two tiers. A SHA-256 of the normalised body catches exact
 * copies for nothing. Near-copies need the embedding model, which means one call
 * per document and a network dependency inside your gate, so this tier is the
 * one to think about before putting it in CI. The middle option this class
 * deliberately does NOT have is a text-shingle similarity: free, deterministic,
 * no network, and a different kind of wrong. Naming what is missing is better
 * than implying the three tiers are one.
 *
 * ## Why the staleness cutoff is a date and not a rolling window
 *
 * `stale-before` is an explicit date rather than "older than N months". A
 * rolling window means the gate's verdict changes on a morning when nobody
 * changed anything, which is the kind of build failure that teaches a team to
 * ignore the gate. A date is a decision somebody made, reviewable in a diff, and
 * moving it is a deliberate act. A rolling window is the other legitimate
 * choice; it is just not the one that survives contact with a team.
 */
@Component
class CorpusGate(
    private val embeddingModels: ObjectProvider<EmbeddingModel>,
    @Value("\${app.knowledge.gate.enabled:true}") private val enabled: Boolean,
    @Value("\${app.knowledge.gate.stale-before:2026-01-01}") private val staleBefore: String,
    @Value("\${app.knowledge.gate.duplicate-similarity:0.95}") private val duplicateSimilarity: Double,
    @Value("\${app.knowledge.gate.check-near-duplicates:true}") private val checkNearDuplicates: Boolean,
    @Value("\${app.knowledge.gate.allow:}") private val allowRaw: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** One allowed exception: a file, a rule, and why. */
    private data class Exemption(val file: String, val rule: CorpusRule, val reason: String)

    /**
     * Checks the corpus and throws [CorpusRejected] if anything is wrong.
     *
     * Reads the files itself rather than taking the documents the index is about
     * to write, because the transformer fills a missing `source` in from the
     * file name and the gate has to see the file as it was written.
     */
    fun enforce() {
        if (!enabled) {
            log.warn("the corpus gate is DISABLED (app.knowledge.gate.enabled=false), indexing whatever is there")
            return
        }
        val violations = inspect()
        if (violations.isNotEmpty()) throw CorpusRejected(violations)
        log.info("corpus gate passed: {} documents", corpus().size)
    }

    /** Every violation, for a report. Does not throw. */
    fun inspect(): List<CorpusViolation> {
        val files = corpus().map { it to Frontmatter.parse(text(it)) }
        val exemptions = exemptions()
        val cutoff = LocalDate.parse(staleBefore)

        val found = buildList {
            files.forEach { (resource, parsed) -> addAll(metadata(resource, parsed)) }
            files.forEach { (resource, parsed) -> addAll(stale(resource, parsed, cutoff)) }
            files.forEach { (resource, parsed) -> addAll(pii(resource, parsed)) }
            addAll(duplicates(files))
        }

        return found.filterNot { violation ->
            exemptions.any { it.file == violation.file && it.rule == violation.rule }
        }
    }

    /**
     * Similarity of every pair, highest first.
     *
     * Printed by the lab and never used by the gate, because a threshold has to
     * be chosen from the distribution rather than from a round number. Fourteen
     * documents are ninety-one pairs and one batched embedding call.
     */
    fun similarities(): List<Triple<String, String, Double>> {
        val model = embeddingModels.ifAvailable ?: return emptyList()
        val files = corpus().map { name(it) to Frontmatter.parse(text(it)).body }
        val names = files.map { it.first }
        val vectors = model.embed(files.map { it.second })
        return buildList {
            for (i in names.indices) {
                for (j in i + 1 until names.size) {
                    add(Triple(names[i], names[j], cosine(vectors[i], vectors[j])))
                }
            }
        }.sortedByDescending { it.third }
    }

    // ─── the rules ───────────────────────────────────────────────────────────

    private fun metadata(resource: Resource, parsed: Frontmatter.Parsed): List<CorpusViolation> = buildList {
        val file = name(resource)
        if (parsed["source"] == null) {
            add(CorpusViolation(file, CorpusRule.METADATA, "no `source` in the front matter, so no chunk from it can be cited"))
        }
        if (parsed["updated"] == null) {
            add(CorpusViolation(file, CorpusRule.METADATA, "no `updated` in the front matter, so staleness cannot be judged at all"))
        } else if (runCatching { LocalDate.parse(parsed["updated"]) }.isFailure) {
            add(CorpusViolation(file, CorpusRule.METADATA, "`updated: ${parsed["updated"]}` is not an ISO date (yyyy-MM-dd)"))
        }
    }

    private fun stale(resource: Resource, parsed: Frontmatter.Parsed, cutoff: LocalDate): List<CorpusViolation> {
        val raw = parsed["updated"] ?: return emptyList()
        val updated = try {
            LocalDate.parse(raw)
        } catch (_: DateTimeParseException) {
            return emptyList() // already reported by the metadata rule
        }
        if (!updated.isBefore(cutoff)) return emptyList()
        return listOf(
            CorpusViolation(
                name(resource),
                CorpusRule.STALE,
                "updated $raw, before the cutoff of $staleBefore. Refresh it, delete it, or allow-list it with a reason",
            ),
        )
    }

    private fun pii(resource: Resource, parsed: Frontmatter.Parsed): List<CorpusViolation> = buildList {
        val file = name(resource)
        EMAIL.find(parsed.body)?.let {
            add(CorpusViolation(file, CorpusRule.PII, "contains what looks like an email address (${it.value})"))
        }
        PHONE_IT.find(parsed.body)?.let {
            add(CorpusViolation(file, CorpusRule.PII, "contains what looks like an Italian mobile number (${it.value})"))
        }
    }

    private fun duplicates(files: List<Pair<Resource, Frontmatter.Parsed>>): List<CorpusViolation> = buildList {
        // Tier one: exact, free. A normalised body hashed, so two files that
        // differ only in trailing whitespace are still the same document.
        val byHash = files.groupBy { sha256(normalise(it.second.body)) }
        byHash.values.filter { it.size > 1 }.forEach { group ->
            val names = group.map { name(it.first) }
            names.forEach { file ->
                add(
                    CorpusViolation(
                        file,
                        CorpusRule.DUPLICATE,
                        "byte-identical body to ${names.filterNot { it == file }.joinToString(", ")}",
                    ),
                )
            }
        }

        if (!checkNearDuplicates) return@buildList
        val model = embeddingModels.ifAvailable ?: run {
            // Said out loud rather than skipped silently: a gate that quietly
            // drops a rule when a dependency is missing is a gate that passes
            // on the day it matters.
            log.warn("no embedding model available, so the near-duplicate rule did NOT run")
            return@buildList
        }

        // Tier two: one embedding call per document, and a network dependency
        // inside the gate. This is the tier to argue about before CI.
        val names = files.map { name(it.first) }
        val vectors = model.embed(files.map { it.second.body })
        for (i in names.indices) {
            for (j in i + 1 until names.size) {
                val similarity = cosine(vectors[i], vectors[j])
                if (similarity <= duplicateSimilarity) continue
                val detail = "%.3f cosine similarity to %s, above the %.2f threshold".format(
                    similarity, names[j], duplicateSimilarity,
                )
                add(CorpusViolation(names[i], CorpusRule.DUPLICATE, detail))
                add(
                    CorpusViolation(
                        names[j],
                        CorpusRule.DUPLICATE,
                        "%.3f cosine similarity to %s, above the %.2f threshold".format(
                            similarity, names[i], duplicateSimilarity,
                        ),
                    ),
                )
            }
        }
    }

    // ─── the allow-list ──────────────────────────────────────────────────────

    /**
     * Parses `file:RULE:reason` entries, and refuses the ones with no reason.
     *
     * The reason is mandatory on purpose. An allow-list without reasons becomes
     * a list nobody dares to shorten, because no one remembers whether the entry
     * is a considered exception or somebody silencing a build in a hurry.
     */
    private fun exemptions(): List<Exemption> = allowRaw.split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { entry ->
            val parts = entry.split(':', limit = 3).map { it.trim() }
            require(parts.size == 3 && parts[2].isNotEmpty()) {
                "allow-list entry \"$entry\" must be file:RULE:reason — an exception without a written reason is not an exception"
            }
            val rule = runCatching { CorpusRule.valueOf(parts[1].uppercase()) }.getOrElse {
                throw IllegalArgumentException(
                    "allow-list entry \"$entry\" names no rule. Known rules: ${CorpusRule.entries.joinToString()}",
                )
            }
            Exemption(parts[0], rule, parts[2])
        }

    // ─── plumbing ────────────────────────────────────────────────────────────

    private fun corpus(): List<Resource> = PathMatchingResourcePatternResolver()
        .getResources("classpath:${KnowledgeIndex.CORPUS_PATTERN}")
        .sortedBy { it.filename }

    private fun text(resource: Resource): String =
        resource.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun name(resource: Resource): String = resource.filename ?: "(unnamed)"

    private fun normalise(body: String): String = body.replace(Regex("\\s+"), " ").trim().lowercase()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    /**
     * Written out rather than imported. Spring AI has one, as a nested class of
     * the store this module deleted in LAB 3, and six lines that anybody can
     * check beat an import whose home moved once already.
     */
    private fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i].toDouble()
            normA += a[i].toDouble() * a[i].toDouble()
            normB += b[i].toDouble() * b[i].toDouble()
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (Math.sqrt(normA) * Math.sqrt(normB))
    }

    private companion object {
        /**
         * Crude on purpose, and the group has to say what it misses. It does not
         * know that a published support address is not personal data either,
         * which is what the allow-list is for.
         */
        val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

        /** Italian mobile numbers, with or without the country code. */
        val PHONE_IT = Regex("(\\+39[ .-]?)?3\\d{2}[ .-]?\\d{6,7}")
    }
}
