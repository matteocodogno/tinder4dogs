package com.ai4dev.tinderfordogs.support.model

/**
 * Represents a request to evaluate the compatibility between two dog breeds.
 *
 * This request is used as the input payload for the breed compatibility endpoint,
 * carrying the breed identifiers of both the user's dog and the potential match.
 * The provided breed names are used to generate AI-powered compatibility advice
 * along with relevant supporting sources.
 *
 * @param myDogBreed The breed of the user's own dog.
 * @param matchBreed The breed of the potential match dog to evaluate compatibility against.
 */
data class CompatibilityRequest(
    val myDogBreed: String,
    val matchBreed: String,
)
