package com.ai4dev.tinder4dogs.knowledge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * The gate you can put in continuous integration on Monday.
 *
 * ```
 * mvn -q test -Dtest=CorpusGateTest
 * ```
 *
 * **No API key, no gateway, no network.** This is the only test in the module
 * that can say that, and it is why the near-duplicate rule is switched off here:
 * three of the four rules read a file and a date, and a gate that needs an
 * embedding endpoint is a gate that goes red when a vendor has an incident.
 *
 * `CorpusSimilarityTest` runs the fourth rule. Keeping them in two files is the
 * lab's decision made visible: this one blocks a merge, that one is a review
 * step somebody looks at.
 *
 * ## What a failure here means
 *
 * Exactly what it says: a named file broke a named rule. The fix is in the
 * corpus, not in this test and not in the code. Lowering the bar to make the
 * build green is the one response that defeats the whole point.
 *
 * On the corpus as it was shipped at the start of the module this test FAILS,
 * with six documents rejected. That was the lab.
 */
@SpringBootTest(
    properties = [
        // The free tier only. See the class comment: this is the deliberate half
        // of the gate, not a convenience.
        "app.knowledge.gate.check-near-duplicates=false",
    ],
)
class CorpusGateTest {

    @Autowired
    private lateinit var gate: CorpusGate

    @Test
    fun `the corpus is fit to index`() {
        val violations = gate.inspect()
        report(violations)

        assertThat(violations)
            .describedAs("every violation is a file to fix, not a rule to relax")
            .isEmpty()
    }

    private fun report(violations: List<CorpusViolation>) {
        println()
        println("── the gate ──────────────────────────────────────────────────────────────")
        println("rules run  : METADATA, STALE, PII  (near-duplicate needs embeddings)")
        if (violations.isEmpty()) {
            println("verdict    : the corpus passes")
            println("──────────────────────────────────────────────────────────────────────────")
            println("Now say what the PII rule MISSES. Open ticket-esempio-escalation.md:")
            println("the email and the phone number are gone and a first name is still")
            println("there, with a dog's name next to it. A regular expression cannot see")
            println("a person, only a pattern. That is the honest limit of a free rule,")
            println("and the reason this one is a gate and not a guarantee.")
            println()
            return
        }
        println("verdict    : REFUSED, ${violations.size} violations")
        println()
        violations.groupBy { it.file }.forEach { (file, forFile) ->
            println("  $file")
            forFile.forEach { println("      [${it.rule}] ${it.detail}") }
        }
        println("──────────────────────────────────────────────────────────────────────────")
        println("Each line names a file and a rule. Fix the CORPUS: delete the duplicates,")
        println("date the undated, redact the personal data, and allow-list what has to")
        println("stay with a written reason. Then re-run the retrieval harness and look at")
        println("what happened to recall.")
        println()
    }
}
