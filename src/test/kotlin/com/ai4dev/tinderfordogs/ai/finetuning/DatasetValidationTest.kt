package com.ai4dev.tinderfordogs.ai.finetuning

import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

class DatasetValidationTest {
    private val mapper = ObjectMapper()

    @Test
    fun `train jsonl is valid and has enough examples`() {
        validateJsonl("build/fine-tuning/train.jsonl", minExamples = 10)
    }

    @Test
    fun `validation jsonl is valid`() {
        validateJsonl("build/fine-tuning/validation.jsonl", minExamples = 1)
    }

    @Test
    fun `no PII in dataset`() {
        listOf("train", "validation", "test").forEach { split ->
            val file = File("build/fine-tuning/$split.jsonl")
            if (!file.exists()) return@forEach
            file.readLines().forEachIndexed { i, line ->
                val lineNum = i + 1
                assertTrue(
                    !line.contains(Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")),
                    "Line $lineNum in $split.jsonl contains an email address",
                )
            }
        }
    }

    private fun validateJsonl(
        path: String,
        minExamples: Int,
    ) {
        val file = File(path)
        assertTrue(file.exists(), "$path not found — run the pipeline first")

        val errors = mutableListOf<String>()
        var count = 0

        file.readLines().forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            count++
            val lineNum = index + 1

            val obj =
                runCatching { mapper.readTree(line) }
                    .getOrElse {
                        errors += "Line $lineNum: invalid JSON"
                        return@forEachIndexed
                    }

            val messages =
                obj.get("messages")
                    ?: run {
                        errors += "Line $lineNum: missing 'messages'"
                        return@forEachIndexed
                    }

            val roles = messages.map { it.get("role")?.asString() }
            if (roles.firstOrNull() != "system") errors += "Line $lineNum: first role must be 'system'"
            if ("user" !in roles) errors += "Line $lineNum: missing 'user' turn"
            if ("assistant" !in roles) errors += "Line $lineNum: missing 'assistant' turn"

            messages.forEach { msg ->
                if (msg.get("content")?.asString().isNullOrBlank()) {
                    errors += "Line $lineNum: empty content in ${msg.get("role")?.asString()} message"
                }
            }
        }

        assertTrue(count >= minExamples, "Expected at least $minExamples examples, found $count")
        if (errors.isNotEmpty()) fail("Validation failed:\n" + errors.joinToString("\n") { "  - $it" })
        println("✅ $path is valid ($count examples)")
    }
}
