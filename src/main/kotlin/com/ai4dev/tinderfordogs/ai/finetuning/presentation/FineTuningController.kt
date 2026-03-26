package com.ai4dev.tinderfordogs.ai.finetuning.presentation

import com.ai4dev.tinderfordogs.ai.finetuning.service.FineTuningPipelineService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/fine-tuning")
class FineTuningController(
    private val pipeline: FineTuningPipelineService,
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
}
