package com.depanalyzer.parser.gradle

import java.io.File
import java.util.concurrent.TimeUnit

object GradleDetector {
    private const val GRADLE_COMMAND = "gradle"
    private const val GRADLE_WRAPPER = "gradlew"
    private const val GRADLE_WRAPPER_WINDOWS = "gradlew.bat"
    private const val VERSION_TIMEOUT_SECONDS = 5L

    fun isAvailable(): Boolean {
        return try {
            val command = if (isWindows()) arrayOf("cmd", "/c", "gradle --version") else arrayOf("sh", "-c", "gradle --version")
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (completed) {
                process.exitValue() == 0
            } else {
                process.destroyForcibly()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getVersion(): String? {
        return try {
            val command = if (isWindows()) arrayOf("cmd", "/c", "gradle --version") else arrayOf("sh", "-c", "gradle --version")
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return null
            }

            if (process.exitValue() == 0) {
                process.inputStream.bufferedReader().readLine()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun findGradleCommand(projectDir: File): String? {
        require(projectDir.exists() && projectDir.isDirectory) { "Project directory must exist" }

        // Check for Gradle Wrapper
        val wrapperName = if (isWindows()) GRADLE_WRAPPER_WINDOWS else GRADLE_WRAPPER
        val wrapperFile = File(projectDir, wrapperName)
        if (wrapperFile.exists() && wrapperFile.canExecute()) {
            return wrapperFile.absolutePath
        }

        // Check for Windows batch file if not already checked
        if (!isWindows()) {
            val windowsWrapper = File(projectDir, GRADLE_WRAPPER_WINDOWS)
            if (windowsWrapper.exists() && windowsWrapper.canExecute()) {
                return windowsWrapper.absolutePath
            }
        }

        // Fallback to global gradle if available
        return if (isAvailable()) GRADLE_COMMAND else null
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }
}
