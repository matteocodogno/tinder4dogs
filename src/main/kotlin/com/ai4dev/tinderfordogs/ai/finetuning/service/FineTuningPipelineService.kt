package com.ai4dev.tinderfordogs.ai.finetuning.service

import com.ai4dev.tinderfordogs.ai.finetuning.model.FineTuningJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class FineTuningPipelineService(
    private val loader: RawDataLoader,
    private val cleaner: DataCleaner,
    private val augmentor: DataAugmentor,
    private val splitter: DatasetSplitter,
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

        // 3. Augment
        val augmented = augmentor.augment(cleaned, variantsPerExample = 10)
        logger.info { "🔧 Augmented to ${augmented.size} examples" }

        // 4. Clean again (augmented data may have issues)
        val (finalExamples, _) = cleaner.clean(augmented)
        logger.info { "✅ Final clean dataset: ${finalExamples.size} examples" }

        // 5. Split
        val split = splitter.split(finalExamples)
        logger.info {
            "✂️ Split into ${split.train.size} training, ${split.validation.size} validation examples and ${split.test.size} test examples"
        }

        return null
    }
}
