package com.ai4dev.tinderfordogs.ai.finetuning.presentation

import com.ai4dev.tinderfordogs.ai.finetuning.service.FineTuningJob
import com.ai4dev.tinderfordogs.ai.finetuning.service.FineTuningPipelineService
import com.ai4dev.tinderfordogs.ai.finetuning.service.OpenAIService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/fine-tuning")
class FineTuningController(
    private val pipeline: FineTuningPipelineService,
    private val openAIService: OpenAIService,
) {
    @PostMapping("/run")
    suspend fun runPipeline(
        @RequestParam(defaultValue = "false") submitJob: Boolean,
    ): Map<String, Any?> {
        val job = pipeline.run(submitJob = submitJob)
        return mapOf(
            "status" to "completed",
            "jobId" to job?.id,
            "jobStatus" to job?.status,
            "fineTunedModel" to job?.fineTunedModel,
        )
    }

    @GetMapping("/jobs/{jobId}")
    suspend fun getJob(
        @PathVariable jobId: String,
    ): FineTuningJob = openAIService.getFineTuningJob(jobId)
}
