package com.depanalyzer.parser

import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GradleRepositoryParserTest {

    @Test
    fun `extracts repositories from build gradle groovy`() {
        val mockEnv = mockk<(String) -> String?>()
        every { mockEnv("NEXUS_USER") } returns "user-groovy"
        every { mockEnv("NEXUS_PASS") } returns "pass-groovy"
        
        val parser = GradleRepositoryParser(envProvider = mockEnv)
        val file = resourceFile("gradles/with-repositories/build.gradle")
        val repos = parser.parse(file)

        assertEquals(4, repos.size)
        assertTrue(repos.any { it.url == "https://repo1.maven.org/maven2" })
        assertTrue(repos.any { it.url == "https://maven.google.com" })
        assertTrue(repos.any { it.url == "https://jitpack.io" })
        
        val nexus = repos.find { it.url.contains("nexus.example.com") }
        assertNotNull(nexus)
        assertEquals("user-groovy", nexus.username)
        assertEquals("pass-groovy", nexus.password)
    }

    @Test
    fun `extracts repositories from build gradle kts`() {
        val mockEnv = mockk<(String) -> String?>()
        every { mockEnv("NEXUS_USER") } returns "user-kts"
        every { mockEnv("NEXUS_PASS") } returns "pass-kts"
        
        val parser = GradleRepositoryParser(envProvider = mockEnv)
        val file = resourceFile("gradles/with-repositories/build.gradle.kts")
        val repos = parser.parse(file)

        assertEquals(4, repos.size)
        assertTrue(repos.any { it.url == "https://repo1.maven.org/maven2" })
        assertTrue(repos.any { it.url == "https://maven.google.com" })
        assertTrue(repos.any { it.url == "https://jitpack.io" })
        
        val nexus = repos.find { it.url.contains("nexus.example.com") }
        assertNotNull(nexus)
        assertEquals("user-kts", nexus.username)
        assertEquals("pass-kts", nexus.password)
    }

    @Test
    fun `fallbacks to maven central if no repositories block exists`() {
        val parser = GradleRepositoryParser()
        val tempFile = File.createTempFile("empty", ".gradle")
        tempFile.writeText("dependencies { implementation 'org.slf4j:slf4j-api:2.0.13' }")
        
        val repos = parser.parse(tempFile)
        assertEquals(1, repos.size)
        assertEquals("https://repo1.maven.org/maven2", repos[0].url)
        
        tempFile.delete()
    }

    private fun resourceFile(path: String): File {
        val url = this::class.java.classLoader.getResource(path)
        requireNotNull(url) { "Missing test resource: $path" }
        return File(url.toURI())
    }
}
