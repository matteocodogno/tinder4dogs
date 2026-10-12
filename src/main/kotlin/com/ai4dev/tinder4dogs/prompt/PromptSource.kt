package com.ai4dev.tinder4dogs.prompt

/**
 * A prompt, and which version of it this is.
 *
 * The version is not decoration. When an answer is wrong, the first question is
 * which sentence produced it, and a prompt that cannot say which version it is
 * cannot answer that.
 */
data class LoadedPrompt(
    val text: String,
    val version: String,
    val origin: String,
)

/**
 * Where the running application reads a prompt from.
 *
 * The interface is this narrow on purpose. Git is where a prompt is WRITTEN and
 * REVIEWED and that is not in question; the open decision is where the running
 * process READS it, and the honest way to hold a decision open is one method.
 */
interface PromptSource {

    /** @param label which deployed version to serve, for sources that have versions. */
    fun load(name: String, label: String): LoadedPrompt
}
