package com.ai4dev.tinder4dogs.finetuning

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Load → clean → augment → clean again → split → export.
 *
 * It stops at the JSONL files. Submitting a training job needs a provider that
 * trains (the course gateway does not), and the room uses a model that was
 * fine-tuned before the day.
 */
@Service
class FineTuningPipelineService(
    private val loader: RawDataLoader,
    private val cleaner: DataCleaner,
    private val augmentor: DataAugmentor,
    private val splitter: DatasetSplitter,
    private val exporter: JsonlExporter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun run(): PipelineReport {
        log.info("🚀 Starting fine-tuning pipeline for Tinder for Dogs...")

        // 1. Load
        val seeds = loader.loadSeedExamples()
        val raw = loader.toTrainingExamples(seeds, RawDataLoader.SYSTEM_PROMPT)
        log.info("📥 Loaded {} seed examples", raw.size)

        // 2. Clean
        val (cleaned, firstReport) = cleaner.clean(raw)
        log.info("🧹 Cleaning report: {}", firstReport)

        // 3. Augment
        val augmented = augmentor.augment(cleaned, variantsPerExample = 10)
        log.info("🔧 Augmented to {} examples", augmented.size)

        // 4. Clean again (augmented data may have issues)
        val (finalExamples, finalReport) = cleaner.clean(augmented)
        log.info("✅ Final clean dataset: {} examples", finalExamples.size)

        // 5. Split
        val split = splitter.split(finalExamples)
        log.info(
            "✂️ Split into {} training, {} validation examples and {} test examples",
            split.train.size, split.validation.size, split.test.size,
        )

        // 6. Export
        val export = exporter.export(split)
        log.info("📤 Files ready at: {}", export.trainFile.parentFile.absolutePath)

        return PipelineReport(
            loaded = raw.size,
            firstClean = firstReport,
            augmented = augmented.size,
            finalClean = finalReport,
            train = split.train.size,
            validation = split.validation.size,
            test = split.test.size,
            files =
                mapOf(
                    "train" to export.trainFile.path,
                    "validation" to export.validationFile.path,
                    "test" to export.testFile.path,
                ),
        )
    }
}
