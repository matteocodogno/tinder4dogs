package com.ai4dev.tinderfordogs.support.model

/**
 * Represents the result of an AI-powered compatibility evaluation between two dog breeds.
 *
 * This response is produced by the breed compatibility service after retrieving relevant
 * knowledge chunks and generating a behaviour-focused summary via a language model.
 * It carries both the breed identifiers from the original request and the generated output,
 * including the compatibility advice and the knowledge base sources that informed it.
 *
 * The advice is grounded exclusively in the retrieved context, covering compatibility risks,
 * special considerations for the specific breed pair, and a practical first-meeting tip.
 * The sources list reflects the distinct knowledge base entries that were used to construct
 * the advice, allowing consumers to trace the origin of the generated content.
 *
 * @param myDogBreed The breed of the user's own dog, echoed from the original request.
 * @param matchBreed The breed of the potential match dog, echoed from the original request.
 * @param advice The AI-generated compatibility summary for the given breed pair, including
 * any behavioural risks, special considerations, and a first-meeting tip.
 * @param sources The distinct knowledge base sources that were retrieved and used as context
 * when generating the compatibility advice.
 */
data class CompatibilityResponse(
    val myDogBreed: String,
    val matchBreed: String,
    val advice: String,
    val sources: List<String>,
)
