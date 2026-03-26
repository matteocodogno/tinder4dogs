package com.ai4dev.tinderfordogs.ai.finetuning.service

import com.ai4dev.tinderfordogs.ai.finetuning.model.FineTuningJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class FineTuningPipelineService(
    private val loader: RawDataLoader,
    private val cleaner: DataCleaner,
) {
    suspend fun run(submitJob: Boolean = false): FineTuningJob? {
        logger.info { "🚀 Starting fine-tuning pipeline for Tinder for Dogs..." }

        // 1. Load
        val seeds = loader.loadSeedExamples()
        val raw = loader.toTrainingExamples(seeds, RawDataLoader.SYSTEM_PROMPT)
        logger.info { "📥 Loaded ${raw.size} seed examples" }

        // 2. Clean
        val (cleaned, report) = cleaner.clean(raw)
        logger.info { "🧹 Cleaning report: $report" }

        return null
    }
}
