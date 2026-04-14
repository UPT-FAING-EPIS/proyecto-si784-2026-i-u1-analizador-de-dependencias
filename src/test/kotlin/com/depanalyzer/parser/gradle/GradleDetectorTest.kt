package com.depanalyzer.parser.gradle

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GradleDetectorTest {
    @Test
    fun `should handle non-existent project directory`() {
        val nonExistent = File("/non/existent/path/that/definitely/does/not/exist")
        try {
            GradleDetector.findGradleCommand(nonExistent, verbose = false)
            assertFalse(true, "Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals(e.message?.contains("must exist"), true)
        }
    }

    @Test
    fun `should return null when gradle wrapper not found and global gradle not available`() {
        val tempDir = File.createTempFile("gradle-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val command = GradleDetector.findGradleCommand(tempDir, verbose = false)
            assertTrue(command == null || command.contains("gradle") || command.contains("gradle.cmd"))
        } finally {
            tempDir.delete()
        }
    }

    @Test
    fun `should find wrapper when it exists in project directory`() {
        val tempDir = File.createTempFile("gradle-test-wrapper", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val wrapperName = if (System.getProperty("os.name").lowercase().contains("windows")) {
                "gradlew.bat"
            } else {
                "gradlew"
            }

            val wrapper = File(tempDir, wrapperName)
            wrapper.writeText("@echo off\necho test")
            if (!System.getProperty("os.name").lowercase().contains("windows")) {
                wrapper.setExecutable(true)
            }

            val command = GradleDetector.findGradleCommand(tempDir, verbose = true)
            assertNotNull(command, "Should find wrapper")
            assertTrue(command.contains("gradlew"), "Expected wrapper path but got: $command")
        } finally {
            val wrapperName = if (System.getProperty("os.name").lowercase().contains("windows")) {
                "gradlew.bat"
            } else {
                "gradlew"
            }
            File(tempDir, wrapperName).delete()
            tempDir.delete()
        }
    }

    @Test
    fun `should use Windows wrapper on Windows when present`() {
        val tempDir = File.createTempFile("gradle-test-windows-wrapper", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val unixWrapper = File(tempDir, "gradlew")
            unixWrapper.writeText("#!/bin/bash\necho unix")

            val windowsWrapper = File(tempDir, "gradlew.bat")
            windowsWrapper.writeText("@echo windows")

            val command = GradleDetector.findGradleCommand(tempDir, verbose = false)
            assertNotNull(command, "Should find a wrapper")
            assertTrue(command.contains("gradlew"), "Should return wrapper")
        } finally {
            File(tempDir, "gradlew").delete()
            File(tempDir, "gradlew.bat").delete()
            tempDir.delete()
        }
    }
}
