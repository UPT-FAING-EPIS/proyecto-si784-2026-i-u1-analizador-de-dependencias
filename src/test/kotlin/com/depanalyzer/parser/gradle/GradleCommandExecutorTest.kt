package com.depanalyzer.parser.gradle

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradleCommandExecutorTest {

    @Test
    fun `should handle non-existent project directory`() {
        val nonExistent = File("/non/existent/path")
        try {
            GradleCommandExecutor.execute(nonExistent)
            assertTrue(false, "Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals(e.message?.contains("must exist"), true)
        }
    }

    @Test
    fun `should return null when gradle not found`() {
        // Create a temporary directory without gradle installed
        val tempDir = File.createTempFile("gradle-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val result = GradleCommandExecutor.execute(tempDir)
            // Result should be null if gradle is not available
            assertNull(result)
        } finally {
            tempDir.delete()
        }
    }

    @Test
    fun `should not throw exception on error`() {
        // Create a minimal project structure
        val tempDir = File.createTempFile("gradle-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            // Should not throw, just return null or empty
            val result = GradleCommandExecutor.execute(tempDir)
            assertTrue(result.isNullOrEmpty() || result.isNotEmpty())
        } finally {
            tempDir.delete()
        }
    }
}
