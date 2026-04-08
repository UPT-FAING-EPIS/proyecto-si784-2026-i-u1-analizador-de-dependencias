package com.depanalyzer.parser.gradle

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object GradleCommandExecutor {
    private const val DEFAULT_TIMEOUT_SECONDS = 300L  // 5 minutes
    private const val GRADLE_TASK = "dependencies"
    private const val GRADLE_INFO_FLAG = "--info"

    fun execute(
        projectDir: File,
        timeout: Duration = DEFAULT_TIMEOUT_SECONDS.seconds,
        verbose: Boolean = false
    ): String? {
        require(projectDir.exists() && projectDir.isDirectory) { "Project directory must exist" }

        val gradleCommand = GradleDetector.findGradleCommand(projectDir)
            ?: run {
                if (verbose) {
                    System.err.println("[GradleCommandExecutor] Gradle not found in PATH or project")
                }
                return null
            }

        return try {
            if (verbose) {
                System.err.println("[GradleCommandExecutor] Executing: $gradleCommand $GRADLE_TASK $GRADLE_INFO_FLAG")
                System.err.println("[GradleCommandExecutor] Working directory: ${projectDir.absolutePath}")
                System.err.println("[GradleCommandExecutor] Timeout: ${timeout.inWholeSeconds}s")
            }

            val processBuilder = ProcessBuilder(gradleCommand, GRADLE_TASK, GRADLE_INFO_FLAG)
                .directory(projectDir)
                .redirectErrorStream(true)  // Combine stdout and stderr

            val process = processBuilder.start()
            val completed = process.waitFor(timeout.inWholeSeconds, java.util.concurrent.TimeUnit.SECONDS)

            if (!completed) {
                if (verbose) {
                    System.err.println("[GradleCommandExecutor] Command timed out after ${timeout.inWholeSeconds}s")
                }
                process.destroyForcibly()
                return null
            }

            val output = process.inputStream.bufferedReader().use { it.readText() }

            if (verbose) {
                System.err.println("[GradleCommandExecutor] Command completed with exit code: ${process.exitValue()}")
                System.err.println("[GradleCommandExecutor] Output length: ${output.length} characters")
            }

            output.ifEmpty { null }
        } catch (e: Exception) {
            if (verbose) {
                System.err.println("[GradleCommandExecutor] Exception during command execution:")
                e.printStackTrace(System.err)
            }
            null
        }
    }
}
