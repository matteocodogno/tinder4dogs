package com.ai4dev.tinderfordogs.dogprofile.config

import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import kotlin.reflect.full.findAnnotation
import kotlin.test.assertNotNull

class AsyncConfigTest {

    @Test
    fun `AsyncConfig is a Spring configuration class`() {
        assertNotNull(AsyncConfig::class.findAnnotation<Configuration>())
    }

    @Test
    fun `AsyncConfig enables async execution`() {
        assertNotNull(AsyncConfig::class.findAnnotation<EnableAsync>())
    }
}
