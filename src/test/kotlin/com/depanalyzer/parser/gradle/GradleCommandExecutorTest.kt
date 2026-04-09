package com.depanalyzer.parser.gradle

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradleCommandExecutorTest {

    @BeforeEach
    fun setUp() {
        mockkObject(GradleDetector)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(GradleDetector)
    }

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
        val tempDir = File.createTempFile("gradle-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            every { GradleDetector.findGradleCommand(tempDir) } returns null
            val result = GradleCommandExecutor.execute(tempDir)
            assertNull(result)
        } finally {
            tempDir.delete()
        }
    }

    @Test
    fun `should not throw exception on error`() {
        val tempDir = File.createTempFile("gradle-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            every { GradleDetector.findGradleCommand(tempDir) } returns null
            val result = GradleCommandExecutor.execute(tempDir)
            assertNull(result)
        } finally {
            tempDir.delete()
        }
    }
}
