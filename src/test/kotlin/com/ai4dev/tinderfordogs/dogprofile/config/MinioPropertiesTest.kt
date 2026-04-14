package com.ai4dev.tinderfordogs.dogprofile.config

import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MinioPropertiesTest {

    private val yaml: Map<String, Any> by lazy {
        val stream = Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("application.yaml")
            ?: error("application.yaml not found on classpath")
        @Suppress("UNCHECKED_CAST")
        Yaml().load(stream) as Map<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun minioSection(): Map<String, Any> =
        yaml["minio"] as? Map<String, Any>
            ?: error("minio section missing from application.yaml")

    @Test
    fun `application yaml contains minio url placeholder`() {
        val url = minioSection()["url"] as? String
        assertNotNull(url, "minio.url must be declared in application.yaml")
        assertTrue(url.contains("\${MINIO_URL"), "minio.url must use env-var substitution")
    }

    @Test
    fun `application yaml contains minio access-key placeholder`() {
        val key = minioSection()["access-key"] as? String
        assertNotNull(key, "minio.access-key must be declared in application.yaml")
        assertTrue(key.contains("\${MINIO_ACCESS_KEY"), "minio.access-key must use env-var substitution")
    }

    @Test
    fun `application yaml contains minio secret-key placeholder`() {
        val key = minioSection()["secret-key"] as? String
        assertNotNull(key, "minio.secret-key must be declared in application.yaml")
        assertTrue(key.contains("\${MINIO_SECRET_KEY"), "minio.secret-key must use env-var substitution")
    }

    @Test
    fun `application yaml contains minio bucket placeholder`() {
        val bucket = minioSection()["bucket"] as? String
        assertNotNull(bucket, "minio.bucket must be declared in application.yaml")
        assertTrue(bucket.contains("\${MINIO_BUCKET"), "minio.bucket must use env-var substitution")
    }
}
