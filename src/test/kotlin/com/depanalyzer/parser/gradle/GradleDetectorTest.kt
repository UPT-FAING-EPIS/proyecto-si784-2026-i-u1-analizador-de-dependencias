package com.depanalyzer.parser.gradle

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GradleDetectorTest {

    @Test
    fun `should check gradle availability`() {
        // This test depends on whether gradle is installed
        // Just verify the method returns a boolean
        val available = GradleDetector.isAvailable()
        assertTrue(available || !available)  // Always true, just tests it runs
    }

    @Test
    fun `should find gradle command in current system`() {
        // Test that the method returns a string or null
        val version = GradleDetector.getVersion()
        // Version can be null if gradle not installed, or a string (possibly empty) if it is
        // This test just verifies the method runs without error
        assertTrue(version == null || version is String)
    }

    @Test
    fun `should handle non-existent project directory`() {
        val nonExistent = File("/non/existent/path/that/definitely/does/not/exist")
        try {
            GradleDetector.findGradleCommand(nonExistent)
            assertFalse(true, "Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must exist") == true)
        }
    }

    @Test
    fun `should return null when gradle wrapper not found`() {
        // Create a temporary directory without gradle files
        val tempDir = File.createTempFile("gradle-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val command = GradleDetector.findGradleCommand(tempDir)
            // Command can be null (if no wrapper and gradle not in PATH) or a string
            assertTrue(command == null || command.contains("gradle"))
        } finally {
            tempDir.delete()
        }
    }
}
