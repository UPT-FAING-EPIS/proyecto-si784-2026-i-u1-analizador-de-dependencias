package com.depanalyzer.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ProjectDetectorTest {

    private val detector = ProjectDetector()

    @Test
    fun `should detect Maven when pom xml exists`(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("pom.xml"))
        assertEquals(ProjectType.MAVEN, detector.detect(tempDir))
    }

    @Test
    fun `should detect Gradle Groovy when build gradle exists`(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("build.gradle"))
        assertEquals(ProjectType.GRADLE_GROOVY, detector.detect(tempDir))
    }

    @Test
    fun `should detect Gradle Kotlin when build gradle kts exists`(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("build.gradle.kts"))
        assertEquals(ProjectType.GRADLE_KOTLIN, detector.detect(tempDir))
    }

    @Test
    fun `should detect Gradle Kotlin when settings gradle kts exists`(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("settings.gradle.kts"))
        assertEquals(ProjectType.GRADLE_KOTLIN, detector.detect(tempDir))
    }

    @Test
    fun `should detect Gradle Groovy when settings gradle exists`(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("settings.gradle"))
        assertEquals(ProjectType.GRADLE_GROOVY, detector.detect(tempDir))
    }

    @Test
    fun `should prioritize Gradle Kotlin over Groovy if both exist`(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("build.gradle"))
        Files.createFile(tempDir.resolve("build.gradle.kts"))
        assertEquals(ProjectType.GRADLE_KOTLIN, detector.detect(tempDir))
    }

    @Test
    fun `should prioritize Gradle over Maven if both exist`(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("pom.xml"))
        Files.createFile(tempDir.resolve("build.gradle"))
        assertEquals(ProjectType.GRADLE_GROOVY, detector.detect(tempDir))
    }

    @Test
    fun `should throw error if no build file exists`(@TempDir tempDir: Path) {
        val exception = assertThrows(IllegalStateException::class.java) {
            detector.detect(tempDir)
        }
        assertTrue(exception.message!!.contains("No known build files"))
    }

    @Test
    fun `should throw error if path is not a directory`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("somefile.txt")
        Files.createFile(file)
        val exception = assertThrows(IllegalArgumentException::class.java) {
            detector.detect(file)
        }
        assertTrue(exception.message!!.contains("not a valid directory"))
    }
}
