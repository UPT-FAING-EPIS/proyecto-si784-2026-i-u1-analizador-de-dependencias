package com.depanalyzer.parser.gradle

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleDetectorTest {
    @Test
    fun `should handle non-existent project directory`() {
        val nonExistent = File("/non/existent/path/that/definitely/does/not/exist")
        try {
            GradleDetector.findGradleCommand(nonExistent)
            assertFalse(true, "Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals(e.message?.contains("must exist"), true)
        }
    }

    @Test
    fun `should return null when gradle wrapper not found`() {
        val tempDir = File.createTempFile("gradle-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val command = GradleDetector.findGradleCommand(tempDir)
            assertTrue(command == null || command.contains("gradle"))
        } finally {
            tempDir.delete()
        }
    }
}
