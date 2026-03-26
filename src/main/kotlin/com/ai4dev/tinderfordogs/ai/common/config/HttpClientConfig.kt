package com.ai4dev.tinderfordogs.ai.common.config

import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.HttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

@Configuration
@ImportHttpServices(group = "litellm", types = [LiteLLMService::class])
class HttpClientConfig(
    @Value("\${litellm.url}") private val litellmBaseUrl: String,
    @Value("\${litellm.key}") private val llitellmApiKey: String,
) {
    @Bean
    fun groupConfigurer(): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups: HttpServiceGroupConfigurer.Groups<RestClient.Builder> ->
            groups.filterByName("litellm").forEachClient { _, builder ->
                builder
                    .baseUrl(litellmBaseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $llitellmApiKey")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            }
        }
}
