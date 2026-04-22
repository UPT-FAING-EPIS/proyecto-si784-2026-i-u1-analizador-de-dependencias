package com.depanalyzer.parser.maven

import com.depanalyzer.cli.ProgressTracker
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object MavenCommandExecutor {
    private const val DEFAULT_TIMEOUT_SECONDS = 1800L

    fun execute(
        projectDir: File,
        timeout: Duration = DEFAULT_TIMEOUT_SECONDS.seconds,
        verbose: Boolean = false,
        isDefaultTimeout: Boolean = true,
        onOutputLine: ((String) -> Unit)? = null
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

        val mavenCommand = MavenDetector.findMavenCommand(projectDir, verbose)
            ?: run {
                if (verbose) System.err.println("[MavenCommandExecutor] Maven not found (no wrapper and no global mvn)")
                return null
            }

        ProgressTracker.logProcessing("Descargando dependencias (puede tardar varios minutos)...")
        if (isDefaultTimeout) {
            ProgressTracker.logStep("   ⏳ Se cancelará en: ${timeout.inWholeSeconds}s (30 minutos)")
        } else {
            ProgressTracker.logStep("   ⏳ Timeout: ${timeout.inWholeSeconds}s")
        }

        if (verbose) System.err.println("[MavenCommandExecutor] Executing 'mvn dependency:tree' in $projectDir using: $mavenCommand")

        val process = ProcessBuilder(mavenCommand, "dependency:tree")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()

        val outputBuffer = StringBuilder()
        val outputReader = consumeStream(
            inputStream = process.inputStream,
            sink = outputBuffer,
            onOutputLine = onOutputLine
        )

        val completed = process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)

        if (!completed) {
            process.destroyForcibly()
            outputReader.join(1000)
            if (verbose) System.err.println("[MavenCommandExecutor] Execution timeout after ${timeout.inWholeSeconds} seconds")
            return null
        }

        outputReader.join(3000)
        val output = outputBuffer.toString()

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

        ProgressTracker.logSuccess("Árbol de dependencias resuelto (${output.length} chars)")
        output
    } catch (e: Exception) {
        if (verbose) {
            System.err.println("[MavenCommandExecutor] Exception during command execution: ${e.message}")
            e.printStackTrace(System.err)
        }
        null
    }

    private fun consumeStream(
        inputStream: InputStream,
        sink: StringBuilder,
        onOutputLine: ((String) -> Unit)?
    ): Thread {
        return Thread {
            inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    synchronized(sink) {
                        sink.appendLine(line)
                    }
                    onOutputLine?.invoke(line)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
}
