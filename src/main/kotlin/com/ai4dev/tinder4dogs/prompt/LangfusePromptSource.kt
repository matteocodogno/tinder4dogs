package com.ai4dev.tinder4dogs.prompt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

/**
 * The prompt as deployed in Langfuse, with the jar as the floor underneath it.
 *
 * What this buys: a version is deployed by moving a label, rolled back by moving
 * it back, and changed by somebody who cannot merge code. What it costs is the
 * whole of the rest of this class.
 *
 * **There is no official Langfuse SDK for the JVM.** Python and TypeScript have
 * one and it does the caching, the stale-while-revalidate and the fallback for
 * you. On the JVM, Langfuse's own documentation points you at the REST API, so
 * every one of those behaviours below is a decision somebody had to make on
 * purpose rather than inherit. That is the actual lesson of this file: a prompt
 * fetched over the network at request time has turned one sentence into a
 * dependency with an availability requirement.
 *
 * The policy, in the order it is applied:
 *
 *  1. a fresh cache entry is served without touching the network
 *  2. a stale one triggers a fetch, and the fresh answer replaces it
 *  3. if that fetch fails, **the stale prompt is served anyway**, because a
 *     slightly old prompt is a far better answer than an error
 *  4. with nothing cached at all and the fetch failing, the jar answers
 *
 * Step 3 is the one people leave out, and it is the one that decides whether a
 * Langfuse outage is a graph on a dashboard or an incident in your product.
 */
class LangfusePromptSource(
    private val fallback: PromptSource,
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    publicKey: String,
    secretKey: String,
    private val cacheTtlMillis: Long,
) : PromptSource {

    private val log = LoggerFactory.getLogger(javaClass)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private val authorization: String = "Basic " + Base64.getEncoder()
        .encodeToString((publicKey + ":" + secretKey).toByteArray(StandardCharsets.UTF_8))

    private data class CacheEntry(val prompt: LoadedPrompt, val fetchedAtMillis: Long)

    override fun load(name: String, label: String): LoadedPrompt {
        val key = name + "/" + label
        val cached = cache[key]

        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMillis < cacheTtlMillis) {
            return cached.prompt
        }

        val fetched = fetch(name, label)
        if (fetched != null) {
            cache[key] = CacheEntry(fetched, System.currentTimeMillis())
            return fetched
        }

        if (cached != null) {
            log.warn("could not refresh prompt {} ({}), serving the cached version {}", name, label, cached.prompt.version)
            return cached.prompt
        }

        log.warn("could not reach the prompt registry for {} ({}), falling back to the classpath", name, label)
        return fallback.load(name, label)
    }

    private fun fetch(name: String, label: String): LoadedPrompt? =
        try {
            val body = restClient.get()
                .uri("/api/public/v2/prompts/{name}?label={label}", name, label)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .body(String::class.java)
            body?.let { parse(it) }
        } catch (failure: Exception) {
            log.warn("prompt registry call failed for {} ({}): {}", name, label, failure.message)
            null
        }

    /**
     * Read defensively, with a JSON tree rather than a mapped type.
     *
     * The response carries more fields than this needs and will carry more again
     * next year. Binding it to a data class would make an added field somebody
     * else's deployment decision into this application's startup failure, for no
     * benefit: three field names are all that is used, and they are the three
     * the format is built around.
     */
    private fun parse(body: String): LoadedPrompt? {
        val root: JsonNode = objectMapper.readTree(body)
        val version = root.path("version").asText("unknown")
        val prompt = root.path("prompt")

        val text = when {
            prompt.isTextual -> prompt.asText()
            // A chat prompt is an array of role and content. This application
            // sends one system message, so that is the part it takes.
            prompt.isArray -> prompt.joinToString("\n\n") { it.path("content").asText("") }.trim()
            else -> return null
        }

        if (text.isEmpty()) return null
        return LoadedPrompt(text = text, version = version, origin = "langfuse")
    }
}
