package com.depanalyzer.parser

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandFinderTest {

    @Test
    fun `should handle non-existent project directory`() {
        val nonExistent = File("/non/existent/path/that/definitely/does/not/exist")
        try {
            CommandFinder.findCommand(
                nonExistent,
                "gradlew",
                "gradlew.bat",
                "gradle",
                "gradle.cmd"
            )
            assertTrue(false, "Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals(e.message?.contains("must exist"), true)
        }
    }

    @Test
    fun `should return null when no wrapper and global command not available`() {
        val tempDir = File.createTempFile("command-finder-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val command = CommandFinder.findCommand(
                tempDir,
                "nonexistent-wrapper",
                "nonexistent-wrapper.bat",
                "nonexistent-global-command-xyz",
                "nonexistent-global-command-xyz.cmd",
                verbose = false
            )
            assertNull(command, "Should return null when neither wrapper nor global command exist")
        } finally {
            tempDir.delete()
        }
    }

    @Test
    fun `should detect command availability`() {
        val result = CommandFinder.isCommandAvailable("java", verbose = false)
        assertTrue(result, "Java should be available")
    }

    @Test
    fun `should handle verbose mode without exceptions`() {
        val tempDir = File.createTempFile("command-finder-verbose-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val command = CommandFinder.findCommand(
                tempDir,
                "nonexistent-wrapper",
                "nonexistent-wrapper.bat",
                "nonexistent-global-command",
                "nonexistent-global-command.cmd",
                verbose = true
            )
            assertNull(command)
        } finally {
            tempDir.delete()
        }
    }

    @Test
    fun `should find wrapper when it exists in project directory`() {
        val tempDir = File.createTempFile("command-finder-wrapper-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val wrapperName = if (System.getProperty("os.name").lowercase().contains("windows")) {
                "mywrapper.bat"
            } else {
                "mywrapper"
            }

            val wrapperFile = File(tempDir, wrapperName)
            wrapperFile.writeText("@echo off\necho mock")
            if (!System.getProperty("os.name").lowercase().contains("windows")) {
                wrapperFile.setExecutable(true)
            }

            val command = CommandFinder.findCommand(
                tempDir,
                "mywrapper",
                "mywrapper.bat",
                "nonexistent-global-cmd-xyz",
                "nonexistent-global-cmd-xyz.cmd",
                verbose = false
            )

            assertNotNull(command, "Should find wrapper")
            assertTrue(command.contains("mywrapper"), "Should return wrapper path but got: $command")
        } finally {
            val wrapperName = if (System.getProperty("os.name").lowercase().contains("windows")) {
                "mywrapper.bat"
            } else {
                "mywrapper"
            }
            File(tempDir, wrapperName).delete()
            tempDir.delete()
        }
    }

    @Test
    fun `should get command version`() {
        val version = CommandFinder.getCommandVersion("java", verbose = false)
        assertNotNull(version, "Java version should be available")
        assertTrue(
            version.contains("java") || version.contains("java") || version.isNotEmpty(),
            "Version string should contain java or be non-empty"
        )
    }
}

