package com.ai4dev.tinderfordogs.ai.common.config

import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.finetuning.service.OpenAIService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.HttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

/**
 * Configures HTTP clients for the LiteLLM and OpenAI service groups, injecting base URLs,
 * API keys, and default headers required by each provider's REST API.
 *
 * Registers a [RestClientHttpServiceGroupConfigurer] bean that wires the externally
 * configured credentials into the underlying [RestClient.Builder] for each named group,
 * ensuring that every declarative HTTP call made via [LiteLLMService] or [OpenAIService]
 * carries the correct `Authorization` and `Content-Type` headers without duplicating
 * that boilerplate in individual service methods.
 *
 * The LiteLLM base URL and API key are resolved from `litellm.url` and `litellm.key`
 * properties respectively, while the OpenAI client always targets the canonical
 * `https://api.openai.com/v1` endpoint, keyed by `openai.api-key`.
 */
@Configuration
@ImportHttpServices(group = "litellm", types = [LiteLLMService::class])
@ImportHttpServices(group = "openai", types = [OpenAIService::class])
class HttpClientConfig(
    @Value("\${litellm.url}") private val litellmBaseUrl: String,
    @Value("\${litellm.key}") private val llitellmApiKey: String,
    @Value("\${openai.api-key}") private val openaiApiKey: String,
) {
    companion object {
        private const val OPENAI_BASEURL = "https://api.openai.com/v1"
    }

    @Bean
    fun groupConfigurer(): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups: HttpServiceGroupConfigurer.Groups<RestClient.Builder> ->
            groups.filterByName("litellm").forEachClient { _, builder ->
                builder
                    .baseUrl(litellmBaseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $llitellmApiKey")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            }

            groups.filterByName("openai").forEachClient { _, builder ->
                builder
                    .baseUrl(OPENAI_BASEURL)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $openaiApiKey")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            }
        }
}
