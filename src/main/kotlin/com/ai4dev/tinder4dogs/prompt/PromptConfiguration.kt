package com.ai4dev.tinder4dogs.prompt

import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Picks where prompts are read from, once, at startup.
 *
 * There is exactly one [PromptSource] bean and the rest of the application
 * cannot tell which one it got, which is the point: the decision is an operating
 * one and it belongs here rather than in the code that uses a prompt.
 *
 * With no registry configured the jar answers, so a fresh clone works before
 * anybody has created a Langfuse project, exactly like Google sign-in.
 */
@Configuration
class PromptConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun promptSource(
        resources: ResourceLoader,
        @Value("\${app.langfuse.host:}") host: String,
        @Value("\${app.langfuse.public-key:}") publicKey: String,
        @Value("\${app.langfuse.secret-key:}") secretKey: String,
        @Value("\${app.prompts.cache-ttl-seconds:60}") cacheTtlSeconds: Long,
    ): PromptSource {
        val classpath = ClasspathPromptSource(resources)

        // Naming the missing properties, rather than reporting that something is
        // missing, because this line is read by somebody who has just set three
        // environment variables and is wondering which one did not arrive. The
        // values are never logged, only whether each one is there.
        val missing = buildList {
            if (host.isBlank()) add("app.langfuse.host (LANGFUSE_HOST)")
            if (publicKey.isBlank()) add("app.langfuse.public-key (LANGFUSE_PUBLIC_KEY)")
            if (secretKey.isBlank()) add("app.langfuse.secret-key (LANGFUSE_SECRET_KEY)")
        }

        if (missing.size == 3) {
            log.info("no prompt registry configured, prompts are read from the jar")
            return classpath
        }

        if (missing.isNotEmpty()) {
            // Half configured is almost always a typo in one variable name, and
            // falling back silently is how that typo survives until somebody
            // wonders why a prompt change had no effect.
            log.warn(
                "the prompt registry is only half configured, so prompts are read from the jar. Missing: {}",
                missing.joinToString(", "),
            )
            return classpath
        }

        // Timeouts, because this is the line that decides what a registry outage
        // does to the product. Without them a hung Langfuse hangs the request
        // that is waiting for a prompt, the fallback never runs, and the thing
        // that was supposed to make the feature resilient is what takes it down.
        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(Duration.ofSeconds(2))
        requestFactory.setReadTimeout(Duration.ofSeconds(3))

        log.info("prompts are read from the registry at {}, with the jar as the fallback", host)
        return LangfusePromptSource(
            fallback = classpath,
            restClient = RestClient.builder().baseUrl(host).requestFactory(requestFactory).build(),
            publicKey = publicKey,
            secretKey = secretKey,
            cacheTtlMillis = cacheTtlSeconds * 1000,
        )
    }
}
