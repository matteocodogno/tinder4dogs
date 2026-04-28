package com.ai4dev.tinderfordogs.agent.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AgentConfig {
    @Bean
    fun dogMatchingChatClient(builder: ChatClient.Builder): ChatClient = builder.build()
}
