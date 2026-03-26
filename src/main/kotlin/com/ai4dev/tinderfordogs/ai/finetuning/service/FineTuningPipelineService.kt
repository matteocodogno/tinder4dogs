package com.ai4dev.tinderfordogs.ai.finetuning.service

import com.ai4dev.tinderfordogs.ai.finetuning.model.FineTuningJob
import org.springframework.stereotype.Service

@Service
class FineTuningPipelineService(
    private val loader: RawDataLoader,
) {
    suspend fun run(submitJob: Boolean = false): FineTuningJob? {
        println("🚀 Starting fine-tuning pipeline for Tinder for Dogs...")

        // 1. Load
        val seeds = loader.loadSeedExamples()
        val raw = loader.toTrainingExamples(seeds, RawDataLoader.SYSTEM_PROMPT)
        println("📥 Loaded ${raw.size} seed examples")

        return null
    }
}
