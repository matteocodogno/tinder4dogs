package com.ai4dev.tinder4dogs.knowledge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * The fourth rule, and the number it needs.
 *
 * ```
 * mvn -q test -Dtest=CorpusSimilarityTest
 * ```
 *
 * Near-duplicate detection costs one embedding call per document and puts a
 * network dependency inside the gate, so it lives apart from the three rules
 * that block a merge. Fourteen documents are ninety-one pairs and one batched
 * call, which is cheap; the dependency is the expensive part, not the tokens.
 *
 * ## Read the distribution before you believe the threshold
 *
 * The table below is every pair, most similar first. A threshold is only
 * defensible if you can see the gap it sits in: on a corpus with three versions
 * of the same page there is usually a cliff between "the same document twice"
 * and "two documents about the same feature", and the number belongs in that
 * cliff. If there is no cliff, say so, because then no threshold separates them
 * and the rule needs a human rather than a constant.
 *
 * A failure here is a decision, not a defect: either two documents really are
 * the same and one should go, or the threshold is in the wrong place. Both are
 * answered by looking at the two lines the report prints.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
    named = "AI4DEV_API_KEY",
    matches = ".+",
    disabledReason = "the near-duplicate rule embeds every document through the gateway",
)
class CorpusSimilarityTest {

    @Autowired
    private lateinit var gate: CorpusGate

    @Test
    fun `no two documents in the corpus are the same document`() {
        val pairs = gate.similarities()
        val violations = gate.inspect().filter { it.rule == CorpusRule.DUPLICATE }

        report(pairs, violations)

        assertThat(pairs)
            .describedAs("the similarity table is empty, so no embedding model answered")
            .isNotEmpty()
        assertThat(violations)
            .describedAs("either one of the two documents should go, or the threshold is wrong")
            .isEmpty()
    }

    private fun report(pairs: List<Triple<String, String, Double>>, violations: List<CorpusViolation>) {
        println()
        println("── how similar the documents are to each other ───────────────────────────")
        pairs.take(TOP_PAIRS).forEach { (left, right, similarity) ->
            println("  %.4f  %-34s %s".format(similarity, left, right))
        }
        if (pairs.size > TOP_PAIRS) println("  ... ${pairs.size - TOP_PAIRS} more pairs, less similar")
        println()
        println("verdict    : " + if (violations.isEmpty()) "no near-duplicates" else "REFUSED, ${violations.size}")
        violations.forEach { println("  - $it") }
        println("──────────────────────────────────────────────────────────────────────────")
        println("Find the gap in that column and put the threshold in it. If the top of")
        println("the list runs smoothly into the middle with no gap, there is no threshold")
        println("that separates a copy from a neighbour, and saying that out loud is worth")
        println("more than a number you picked because it looked round.")
        println()
    }

    private companion object {
        const val TOP_PAIRS = 12
    }
}
