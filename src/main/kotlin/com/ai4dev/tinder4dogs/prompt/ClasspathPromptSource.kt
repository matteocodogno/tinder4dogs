package com.ai4dev.tinder4dogs.prompt

import java.nio.charset.StandardCharsets
import org.springframework.core.io.ResourceLoader

/**
 * The prompt that shipped inside the jar.
 *
 * Its "version" is the build it was packaged in, which is the honest answer: a
 * classpath prompt has exactly the version of the code around it, and that is
 * the property a registry trades away in exchange for changing it without a
 * deploy.
 *
 * This source has one job that matters more than serving the normal case: it is
 * the thing that still works when everything else is down.
 */
class ClasspathPromptSource(
    private val resources: ResourceLoader,
) : PromptSource {

    override fun load(name: String, label: String): LoadedPrompt {
        val resource = resources.getResource("classpath:/prompts/$name.md")
        val text = resource.inputStream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
        return LoadedPrompt(text = text, version = "classpath", origin = "classpath")
    }
}
