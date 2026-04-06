package com.ai4dev.tinderfordogs.ai.observability.config

import com.langfuse.client.LangfuseClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LangfuseConfig {
    @Bean
    fun langfuseClient(
        @Value($$"${langfuse.url}") langfuseUrl: String,
        @Value($$"${langfuse.public_key}") publicKey: String,
        @Value($$"${langfuse.secret_key}") secretKey: String,
    ): LangfuseClient =
        LangfuseClient
            .builder()
            .url(langfuseUrl)
            .credentials(publicKey, secretKey)
            .build()
}
