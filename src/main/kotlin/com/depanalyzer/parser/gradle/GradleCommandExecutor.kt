package com.depanalyzer.parser.gradle

import com.depanalyzer.cli.ProgressTracker
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object GradleCommandExecutor {
    private const val DEFAULT_TIMEOUT_SECONDS = 1800L  // 30 minutos
    private const val GRADLE_TASK = "dependencies"
    private const val GRADLE_INFO_FLAG = "--info"
    private const val MAX_RETRIES = 2

    private var lastErrorInfo: GradleErrorInfo? = null

    fun getLastErrorInfo(): GradleErrorInfo? = lastErrorInfo

    fun execute(
        projectDir: File,
        timeout: Duration = DEFAULT_TIMEOUT_SECONDS.seconds,
        verbose: Boolean = false,
        isDefaultTimeout: Boolean = true,
        onOutputLine: ((String) -> Unit)? = null
    ): String? {
        require(projectDir.exists() && projectDir.isDirectory) { "Project directory must exist" }

        val gradleCommand = GradleDetector.findGradleCommand(projectDir, verbose)
            ?: run {
                if (verbose) {
                    System.err.println("[GradleCommandExecutor] Gradle not found (no wrapper and no global gradle)")
                }
                return null
            }

        return executeWithRetry(
            gradleCommand = gradleCommand,
            projectDir = projectDir,
            timeout = timeout,
            verbose = verbose,
            isDefaultTimeout = isDefaultTimeout,
            onOutputLine = onOutputLine,
            additionalFlags = emptyList(),
            retryCount = 0
        )
    }

    private fun executeWithRetry(
        gradleCommand: String,
        projectDir: File,
        timeout: Duration,
        verbose: Boolean,
        isDefaultTimeout: Boolean,
        onOutputLine: ((String) -> Unit)?,
        additionalFlags: List<String>,
        retryCount: Int
    ): String? {
        return try {
            if (retryCount == 0) {
                ProgressTracker.logProcessing("Descargando dependencias (puede tardar varios minutos)...")
                if (isDefaultTimeout) {
                    ProgressTracker.logStep("   ⏳ Se cancelará en: ${timeout.inWholeSeconds}s (30 minutos)")
                } else {
                    ProgressTracker.logStep("   ⏳ Timeout: ${timeout.inWholeSeconds}s")
                }
            } else {
                if (verbose) {
                    System.err.println("[GradleCommandExecutor] Retry attempt $retryCount with flags: $additionalFlags")
                }
            }

            val command = mutableListOf(gradleCommand)
            command.addAll(additionalFlags)
            command.add(GRADLE_TASK)
            command.add(GRADLE_INFO_FLAG)

            if (verbose) {
                System.err.println("[GradleCommandExecutor] Executing: ${command.joinToString(" ")}")
                System.err.println("[GradleCommandExecutor] Working directory: ${projectDir.absolutePath}")
                System.err.println("[GradleCommandExecutor] Timeout: ${timeout.inWholeSeconds}s")
            }

            val processBuilder = ProcessBuilder(command)
                .directory(projectDir)

            val process = processBuilder.start()
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val stdoutReader = consumeStream(
                inputStream = process.inputStream,
                sink = stdout,
                onOutputLine = onOutputLine
            )
            val stderrReader = consumeStream(
                inputStream = process.errorStream,
                sink = stderr,
                onOutputLine = onOutputLine
            )
            val completed = process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)

            if (!completed) {
                if (verbose) {
                    System.err.println("[GradleCommandExecutor] Command timed out after ${timeout.inWholeSeconds}s")
                }
                process.destroyForcibly()
                stdoutReader.join(1000)
                stderrReader.join(1000)
                lastErrorInfo = null
                return null
            }

            stdoutReader.join(3000)
            stderrReader.join(3000)

            val stdoutText = stdout.toString()
            val stderrText = stderr.toString()
            val fullOutput = stdoutText + "\n" + stderrText

            val exitCode = process.exitValue()
            val hasBuildFailure = fullOutput.contains("BUILD FAILED", ignoreCase = true) || exitCode != 0

            if (verbose) {
                System.err.println("[GradleCommandExecutor] Command completed with exit code: $exitCode")
                System.err.println("[GradleCommandExecutor] Output length: ${stdoutText.length} characters")
                if (stderrText.isNotEmpty()) {
                    System.err.println("[GradleCommandExecutor] Stderr length: ${stderrText.length} characters")
                }
            }

            if (hasBuildFailure && retryCount < MAX_RETRIES) {
                val errorInfo = GradleErrorDetector.detectError(fullOutput)
                if (errorInfo != null) {
                    lastErrorInfo = errorInfo
                    if (verbose) {
                        System.err.println("[GradleCommandExecutor] Detected ${errorInfo.type}: ${errorInfo.message}")
                        System.err.println("[GradleCommandExecutor] Will retry with flags: ${errorInfo.suggestedFlags}")
                    }

                    if (errorInfo.suggestedFlags.isNotEmpty()) {
                        return executeWithRetry(
                            gradleCommand = gradleCommand,
                            projectDir = projectDir,
                            timeout = timeout,
                            verbose = verbose,
                            isDefaultTimeout = isDefaultTimeout,
                            onOutputLine = onOutputLine,
                            additionalFlags = errorInfo.suggestedFlags,
                            retryCount = retryCount + 1
                        )
                    }
                }
            }

            if (hasBuildFailure) {
                if (verbose) {
                    System.err.println("[GradleCommandExecutor] Build failed and no retry strategy available")
                }
                lastErrorInfo = null
                return null
            }

            ProgressTracker.logSuccess("Árbol de dependencias resuelto (${stdoutText.length} chars)")
            lastErrorInfo = null
            stdoutText.ifEmpty { null }
        } catch (e: Exception) {
            if (verbose) {
                System.err.println("[GradleCommandExecutor] Exception during command execution:")
                e.printStackTrace(System.err)
            }
            lastErrorInfo = null
            null
        }
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
