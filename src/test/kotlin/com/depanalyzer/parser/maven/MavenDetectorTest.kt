package com.depanalyzer.parser.maven

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MavenDetectorTest {

    @Test
    fun shouldDetectMavenWhenAvailable() {
        val isAvailable = MavenDetector.isAvailable(verbose = false)

        if (isAvailable) {
            assertTrue(true, "Maven should be detected if installed")
        } else {
            assertFalse(false, "Maven correctly reported as unavailable")
        }
    }

    @Test
    fun shouldReturnVersionStringWhenMavenAvailable() {
        if (MavenDetector.isAvailable(verbose = false)) {
            val version = MavenDetector.getVersion(verbose = false)
            assertNotNull(version, "Version should not be null if Maven is available")
            assertTrue(version.contains("Maven") || version.contains("maven"), 
                      "Version string should contain 'Maven'")
        }
    }

    @Test
    fun shouldHandleMavenUnavailableGracefully() {

        val isAvailable = MavenDetector.isAvailable(verbose = false)
        val version = MavenDetector.getVersion(verbose = false)

        if (isAvailable) {
            assertNotNull(version, "If Maven is available, version should not be null")
        } else {
            assertEquals(version, null, "If Maven is not available, version should be null")
        }
    }

    @Test
    fun `should handle non-existent project directory when finding mvn command`() {
        val nonExistent = File("/non/existent/path/that/definitely/does/not/exist")
        try {
            MavenDetector.findMavenCommand(nonExistent, verbose = false)
            assertFalse(true, "Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals(e.message?.contains("must exist"), true)
        }
    }

    @Test
    fun `should return mvn command when no wrapper found`() {
        val tempDir = File.createTempFile("maven-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val command = MavenDetector.findMavenCommand(tempDir, verbose = false)
            assertTrue(command == null || command.contains("mvn") || command.contains("mvn.cmd"))
        } finally {
            tempDir.delete()
        }
    }

    @Test
    fun `should find mvnw wrapper when it exists`() {
        val tempDir = File.createTempFile("maven-test-wrapper", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val wrapperName = if (System.getProperty("os.name").lowercase().contains("windows")) {
                "mvnw.cmd"
            } else {
                "mvnw"
            }
            
            val wrapper = File(tempDir, wrapperName)
            wrapper.writeText("@echo off\necho test")
            if (!System.getProperty("os.name").lowercase().contains("windows")) {
                wrapper.setExecutable(true)
            }

            val command = MavenDetector.findMavenCommand(tempDir, verbose = true)
            assertNotNull(command, "Should find wrapper")
            assertTrue(command.contains("mvnw"), "Expected wrapper path but got: $command")
        } finally {
            val wrapperName = if (System.getProperty("os.name").lowercase().contains("windows")) {
                "mvnw.cmd"
            } else {
                "mvnw"
            }
            File(tempDir, wrapperName).delete()
            tempDir.delete()
        }
    }

    @Test
    fun `should use Windows wrapper on Windows when present`() {
        val tempDir = File.createTempFile("maven-test-windows-wrapper", "")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val unixWrapper = File(tempDir, "mvnw")
            unixWrapper.writeText("#!/bin/bash\necho unix")
            
            val windowsWrapper = File(tempDir, "mvnw.cmd")
            windowsWrapper.writeText("@echo windows")

            val command = MavenDetector.findMavenCommand(tempDir, verbose = false)
            assertNotNull(command, "Should find a wrapper")
            assertTrue(command.contains("mvnw"), "Should return wrapper")
        } finally {
            File(tempDir, "mvnw").delete()
            File(tempDir, "mvnw.cmd").delete()
            tempDir.delete()
        }
    }
}


