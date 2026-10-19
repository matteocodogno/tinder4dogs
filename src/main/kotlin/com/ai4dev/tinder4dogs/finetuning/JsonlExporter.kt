package com.ai4dev.tinder4dogs.finetuning

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

data class ExportResult(
    val trainFile: File,
    val validationFile: File,
    val testFile: File,
    val stats: Map<String, Any>,
)

/** Step 6. One JSON object per line, `{"messages": [...]}`: the OpenAI chat format. */
@Component
class JsonlExporter {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()

    fun export(
        split: DatasetSplit,
        outputDir: String = "target/fine-tuning",
    ): ExportResult {
        val dir = File(outputDir).also { it.mkdirs() }

        val trainFile = writeJsonl(split.train, File(dir, "train.jsonl"))
        val valFile = writeJsonl(split.validation, File(dir, "validation.jsonl"))
        val testFile = writeJsonl(split.test, File(dir, "test.jsonl"))

        val stats =
            mapOf(
                "total" to split.total,
                "train" to split.train.size,
                "validation" to split.validation.size,
                "test" to split.test.size,
                "by_intent" to split.train.groupingBy { it.intent }.eachCount(),
                "by_source" to split.train.groupingBy { it.source }.eachCount(),
            )

        log.info(
            """
            ─── Dataset Export Summary ───
            Total examples : ${split.total}
            Train          : ${split.train.size} (${trainFile.absolutePath})
            Validation     : ${split.validation.size} (${valFile.absolutePath})
            Test           : ${split.test.size} (${testFile.absolutePath})
            By intent      : ${stats["by_intent"]}
            By source      : ${stats["by_source"]}
            """.trimIndent(),
        )

        return ExportResult(trainFile, valFile, testFile, stats)
    }

    private fun writeJsonl(
        examples: List<TrainingExample>,
        file: File,
    ): File {
        file.bufferedWriter().use { writer ->
            examples.forEach { example ->
                // Write only the messages field (standard JSONL format)
                val obj = mapOf("messages" to example.messages)
                writer.write(mapper.writeValueAsString(obj))
                writer.newLine()
            }
        }
        return file
    }
}
