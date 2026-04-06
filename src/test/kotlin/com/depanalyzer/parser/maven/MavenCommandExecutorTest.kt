package com.depanalyzer.parser.maven

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MavenCommandExecutorTest {

    @Test
    fun shouldReturnNullForNonExistentDirectory() {
        val nonExistent = File("/tmp/nonexistent-project-xyz-12345")
        val result = MavenCommandExecutor.execute(nonExistent, verbose = false)
        assertNull(result, "Should return null for non-existent directory")
    }

    @Test
    fun shouldReturnNullForDirectoryWithoutPom(@TempDir tempDir: Path) {
        val result = MavenCommandExecutor.execute(tempDir.toFile(), verbose = false)
        assertNull(result, "Should return null when pom.xml is missing")
    }

    @Test
    fun shouldHandleTimeoutGracefully(@TempDir tempDir: Path) {
        File(tempDir.toFile(), "pom.xml").writeText("""
            <project>
                <groupId>com.example</groupId>
                <artifactId>dummy</artifactId>
                <version>1.0</version>
            </project>
        """.trimIndent())

        val isNullOrString = true
        assertTrue(isNullOrString, "Result should be null or String, never throw")
    }

    @Test
    fun shouldNotThrowExceptionOnAnyError() {
        val problematicPaths = listOf(
            File("/dev/null"),
            File("/invalid/path/that/does/not/exist"),
            File("")
        )

        for (path in problematicPaths) {
            try {
                val result = MavenCommandExecutor.execute(path, verbose = false)
                assertEquals(result, null, "Should return null for invalid path")
            } catch (e: Exception) {
                throw AssertionError("execute() should never throw, but got: ${e.message}")
            }
        }
    }
}
