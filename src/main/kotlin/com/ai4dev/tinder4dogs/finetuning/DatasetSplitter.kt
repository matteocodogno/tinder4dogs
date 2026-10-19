package com.ai4dev.tinder4dogs.finetuning

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Step 5. Train / validation / test, per intent. */
@Component
class DatasetSplitter {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Stratified split: each intent is split proportionally across train/val/test.
     * Default: 80% train, 10% val, 10% test.
     */
    fun split(
        examples: List<TrainingExample>,
        trainRatio: Double = 0.80,
        valRatio: Double = 0.10,
    ): DatasetSplit {
        val byIntent = examples.groupBy { it.intent }

        val train = mutableListOf<TrainingExample>()
        val valid = mutableListOf<TrainingExample>()
        val test = mutableListOf<TrainingExample>()

        byIntent.values.forEach { group ->
            val shuffled = group.shuffled()
            val trainEnd = (shuffled.size * trainRatio).toInt().coerceAtLeast(1)
            val valEnd =
                trainEnd +
                    (shuffled.size * valRatio)
                        .toInt()
                        .coerceAtLeast(1)
                        .coerceAtMost(shuffled.size - trainEnd)

            log.info(
                "✂️ Splitting intent '{}' into {} training, {} validation and {} test examples",
                group.first().intent, trainEnd, valEnd - trainEnd, shuffled.size - valEnd,
            )

            train += shuffled.subList(0, trainEnd)
            valid += shuffled.subList(trainEnd, valEnd.coerceAtMost(shuffled.size))
            test += shuffled.subList(valEnd.coerceAtMost(shuffled.size), shuffled.size)
        }

        return DatasetSplit(train = train.shuffled(), validation = valid, test = test)
    }
}
