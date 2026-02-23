package com.ai4dev.tinderfordogs.ai.finetuning.service

import org.springframework.http.HttpHeaders
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import java.io.File

data class FileUploadResponse(
    val id: String,
    val filename: String,
    val bytes: Long,
)

data class FineTuningJob(
    val id: String,
    val status: String,
    val model: String,
    val fineTunedModel: String?,
)

@Service
class FineTuningJobService(
    private val openAIService: OpenAIService,
) {
    /**
     * Uploads a JSONL file to OpenAI and returns the file ID.
     */
    suspend fun uploadFile(jsonlFile: File): String {
        val builder = MultipartBodyBuilder()
        builder.part("purpose", "fine-tune")
        builder
            .part("file", jsonlFile.readBytes())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "form-data; name=\"file\"; filename=\"${jsonlFile.name}\"",
            ).header(HttpHeaders.CONTENT_TYPE, "application/jsonl")

        val response = openAIService.uploadFiles(builder.build())

        println("✅ Uploaded ${jsonlFile.name} → file ID: ${response.id}")
        return response.id
    }

    /**
     * Creates a fine-tuning job.
     * Model: "gpt-4o-mini-2024-07-18" is the smallest / cheapest option.
     */
    suspend fun createJob(
        trainingFileId: String,
        validationFileId: String,
        baseModel: String = "gpt-4o-mini-2024-07-18",
        suffix: String = "tinder4dogs-support",
    ): FineTuningJob {
        val body =
            mapOf(
                "training_file" to trainingFileId,
                "validation_file" to validationFileId,
                "model" to baseModel,
                "suffix" to suffix,
                "hyperparameters" to
                    mapOf(
                        "n_epochs" to "auto",
                        "batch_size" to "auto",
                        "learning_rate_multiplier" to "auto",
                    ),
            )

        val job = openAIService.createFineTuningJob(body)

        println(
            """
            ✅ Fine-tuning job created!
            Job ID : ${job.id}
            Status : ${job.status}
            Model  : ${job.model}
            Track  : https://platform.openai.com/fine-tuning
            """.trimIndent(),
        )
        return job
    }
}
