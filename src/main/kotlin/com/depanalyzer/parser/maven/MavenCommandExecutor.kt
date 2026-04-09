package com.depanalyzer.parser.maven

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object MavenCommandExecutor {
    private const val DEFAULT_TIMEOUT_SECONDS = 180L

    fun execute(
        projectDir: File,
        timeout: Duration = DEFAULT_TIMEOUT_SECONDS.seconds,
        verbose: Boolean = false
    ): String? = try {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            if (verbose) System.err.println("[MavenCommandExecutor] Project directory doesn't exist or is not a directory")
            return null
        }

        val pomFile = File(projectDir, "pom.xml")
        if (!pomFile.exists()) {
            if (verbose) System.err.println("[MavenCommandExecutor] pom.xml not found in $projectDir")
            return null
        }

        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val command = if (isWindows) "mvn.cmd" else "mvn"

        if (verbose) System.err.println("[MavenCommandExecutor] Executing 'mvn dependency:tree' in $projectDir")

        val process = ProcessBuilder(command, "dependency:tree")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()

        val completed = process.waitFor(timeout.inWholeSeconds, java.util.concurrent.TimeUnit.SECONDS)

        if (!completed) {
            process.destroyForcibly()
            if (verbose) System.err.println("[MavenCommandExecutor] Execution timeout after ${timeout.inWholeSeconds} seconds")
            return null
        }

        val output = process.inputStream.bufferedReader().use { reader ->
            reader.readText()
        }

        val exitCode = process.exitValue()
        
        if (verbose) {
            System.err.println("[MavenCommandExecutor] Execution completed with exit code $exitCode")
            System.err.println("[MavenCommandExecutor] Output length: ${output.length} characters")
        }

        if (output.isBlank()) {
            if (verbose) System.err.println("[MavenCommandExecutor] No output received from dependency:tree")
            if (exitCode != 0) {
                if (verbose) System.err.println("[MavenCommandExecutor] Exit code was $exitCode and output is empty")
            }
            return null
        }

        if (exitCode != 0 && verbose) {
            System.err.println("[MavenCommandExecutor] Non-zero exit code ($exitCode), but returning output anyway (likely warnings)")
        }
        
        output
    } catch (e: Exception) {
        if (verbose) {
            System.err.println("[MavenCommandExecutor] Exception during command execution: ${e.message}")
            e.printStackTrace(System.err)
        }
        null
    }
}
