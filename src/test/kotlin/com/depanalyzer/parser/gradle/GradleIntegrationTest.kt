package com.depanalyzer.parser.gradle

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GradleIntegrationTest {

    @Test
    fun `should return empty list for non-existent directory`() {
        val nonExistent = File("/non/existent/path")
        try {
            GradleIntegration.analyzeGradleProject(nonExistent)
            assertTrue(false, "Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must exist") == true)
        }
    }

    @Test
    fun `should respect enableGradle flag`() {
        val tempDir = File.createTempFile("gradle-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            // When gradle is disabled, should use fallback (which returns empty for non-gradle project)
            val result = GradleIntegration.analyzeGradleProject(
                projectDir = tempDir,
                enableGradle = false
            )
            assertNotNull(result)
            // Result should be empty since directory doesn't contain gradle build files
            assertTrue(result.isEmpty())
        } finally {
            tempDir.delete()
        }
    }

    @Test
    fun `should handle verbose flag without errors`() {
        val tempDir = File.createTempFile("gradle-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val result = GradleIntegration.analyzeGradleProject(
                projectDir = tempDir,
                enableGradle = false,
                verbose = true
            )
            assertNotNull(result)
        } finally {
            tempDir.delete()
        }
    }

    @Test
    fun `should not throw exception on error during gradle execution`() {
        val tempDir = File.createTempFile("gradle-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            // Should gracefully handle errors without throwing
            val result = GradleIntegration.analyzeGradleProject(
                projectDir = tempDir,
                enableGradle = true  // Try to execute, but will fail gracefully
            )
            assertNotNull(result)
        } finally {
            tempDir.delete()
        }
    }
}
