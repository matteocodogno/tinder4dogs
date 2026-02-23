package com.ai4dev.tinderfordogs.ai.finetuning.service

import com.ai4dev.tinderfordogs.ai.finetuning.model.DatasetSplit
import com.ai4dev.tinderfordogs.ai.finetuning.model.TrainingExample
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.io.File

data class ExportResult(
    val trainFile: File,
    val validationFile: File,
    val testFile: File,
    val stats: Map<String, Any>,
)

@Component
class JsonlExporter(
    private val mapper: ObjectMapper,
) {
    fun export(
        split: DatasetSplit,
        outputDir: String = "build/fine-tuning",
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

        // Print summary
        println(
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
