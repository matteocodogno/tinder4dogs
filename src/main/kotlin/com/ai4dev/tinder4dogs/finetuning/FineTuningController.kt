package com.ai4dev.tinder4dogs.finetuning

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FineTuningController(
    private val pipeline: FineTuningPipelineService,
) {
    @PostMapping("/api/fine-tuning/run")
    fun runPipeline(): PipelineReport = pipeline.run()
}
