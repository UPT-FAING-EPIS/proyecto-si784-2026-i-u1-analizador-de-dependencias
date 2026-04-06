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
            if (verbose) System.err.println("ℹ️  Maven: Project directory doesn't exist or is not a directory")
            return null
        }

        val pomFile = File(projectDir, "pom.xml")
        if (!pomFile.exists()) {
            if (verbose) System.err.println("ℹ️  Maven: pom.xml not found in $projectDir")
            return null
        }

        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val command = if (isWindows) "mvn.cmd" else "mvn"

        if (verbose) System.err.println("ℹ️  Maven: Executing 'mvn dependency:tree' in $projectDir")

        val process = ProcessBuilder(command, "dependency:tree")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()

        val completed = process.waitFor(timeout.inWholeSeconds, java.util.concurrent.TimeUnit.SECONDS)

        if (!completed) {
            process.destroyForcibly()
            if (verbose) System.err.println("⚠️  Maven: Execution timeout after ${timeout.inWholeSeconds} seconds")
            return null
        }

        val output = process.inputStream.bufferedReader().use { reader ->
            reader.readText()
        }

        val exitCode = process.exitValue()
        
        if (verbose) {
            System.err.println("ℹ️  Maven: Execution completed with exit code $exitCode")
            System.err.println("ℹ️  Maven: Output length: ${output.length} characters")
        }

        if (output.isBlank()) {
            if (verbose) System.err.println("⚠️  Maven: No output received from dependency:tree")
            if (exitCode != 0) {
                if (verbose) System.err.println("⚠️  Maven: Exit code was $exitCode and output is empty")
            }
            return null
        }

        if (exitCode != 0 && verbose) {
            System.err.println("ℹ️  Maven: Non-zero exit code ($exitCode), but returning output anyway (likely warnings)")
        }
        
        output
    } catch (e: Exception) {
        System.err.println("⚠️  Maven: Execution failed: ${e.message}")
        null
    }
}
