package com.ai4dev.tinderfordogs.ai.finetuning.service

import org.springframework.stereotype.Service

@Service
class FineTuningPipelineService(
    private val loader: RawDataLoader,
    private val cleaner: DataCleaner,
    private val augmentor: DataAugmentor,
    private val splitter: DatasetSplitter,
    private val exporter: JsonlExporter,
    private val jobService: FineTuningJobService,
) {
    suspend fun run(submitJob: Boolean = false): FineTuningJob? {
        println("🚀 Starting fine-tuning pipeline for Tinder for Dogs...")

        // 1. Load
        val seeds = loader.loadSeedExamples()
        val raw = loader.toTrainingExamples(seeds, RawDataLoader.SYSTEM_PROMPT)
        println("📥 Loaded ${raw.size} seed examples")

        // 2. Clean
        val (cleaned, report) = cleaner.clean(raw)
        println("🧹 Cleaning report: $report")

        // 3. Augment
        val augmented = augmentor.augment(cleaned, variantsPerExample = 4)
        println("🔧 Augmented to ${augmented.size} examples")

        // 4. Clean again (augmented data may have issues)
        val (finalExamples, _) = cleaner.clean(augmented)
        println("✅ Final clean dataset: ${finalExamples.size} examples")

        // 5. Split
        val split = splitter.split(finalExamples)

        // 6. Export
        val export = exporter.export(split)

        // 7. Submit (optional — requires valid OpenAI API key)
        if (!submitJob) {
            println("⏸️ Skipping job submission (submitJob=false). Files ready at: ${export.trainFile.parent}")
            return null
        }

        val trainFileId = jobService.uploadFile(export.trainFile)
        val valFileId = jobService.uploadFile(export.validationFile)

        return jobService.createJob(trainFileId, valFileId)
    }
}
