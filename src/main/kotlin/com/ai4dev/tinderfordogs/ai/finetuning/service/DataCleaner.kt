package com.ai4dev.tinderfordogs.ai.finetuning.service

import com.ai4dev.tinderfordogs.ai.finetuning.model.CleaningReport
import com.ai4dev.tinderfordogs.ai.finetuning.model.TrainingExample
import org.springframework.stereotype.Component

@Component
class DataCleaner {
    companion object {
        private const val MIN_TOKENS = 30 // rough: chars / 4
        private const val MAX_TOKENS = 2048
        private val EMAIL_RE = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
        private val PHONE_RE = Regex("""\+?[\d\s\-().]{7,20}""")
    }

    fun clean(examples: List<TrainingExample>): Pair<List<TrainingExample>, CleaningReport> {
        var removedDup = 0
        var removedShort = 0
        var removedLong = 0
        var removedFormat = 0
        var piiRedacted = 0

        // 1. Format validation
        val validFormat =
            examples.filter { ex ->
                val roles = ex.messages.map { it.role }
                val valid =
                    "system" in roles && "user" in roles && "assistant" in roles &&
                        ex.messages.all { it.content.isNotBlank() }
                if (!valid) removedFormat++
                valid
            }

        // 2. Length filtering (approximate token count: chars / 4)
        val validLength =
            validFormat.filter { ex ->
                val totalChars = ex.messages.sumOf { it.content.length }
                val tokens = totalChars / 4
                when {
                    tokens < MIN_TOKENS -> {
                        removedShort++
                        false
                    }

                    tokens > MAX_TOKENS -> {
                        removedLong++
                        false
                    }

                    else -> {
                        true
                    }
                }
            }

        // 3. Deduplication (by user message hash)
        val seen = mutableSetOf<Int>()
        val deduped =
            validLength.filter { ex ->
                val key =
                    ex.messages
                        .first { it.role == "user" }
                        .content
                        .trim()
                        .lowercase()
                        .hashCode()
                if (key in seen) {
                    removedDup++
                    false
                } else {
                    seen += key
                    true
                }
            }

        // 4. PII redaction (mutate content in place)
        val cleaned =
            deduped.map { ex ->
                val redactedMessages =
                    ex.messages.map { msg ->
                        var text = msg.content
                        val before = text
                        text = EMAIL_RE.replace(text, "<EMAIL>")
                        text = PHONE_RE.replace(text, "<PHONE>")
                        if (text != before) piiRedacted++
                        msg.copy(content = text)
                    }
                ex.copy(messages = redactedMessages)
            }

        val report =
            CleaningReport(
                inputCount = examples.size,
                removedDuplicates = removedDup,
                removedTooShort = removedShort,
                removedTooLong = removedLong,
                removedInvalidFormat = removedFormat,
                piiRedacted = piiRedacted,
                outputCount = cleaned.size,
            )
        return cleaned to report
    }
}
