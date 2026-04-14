package com.depanalyzer.parser

import java.io.File
import java.util.concurrent.TimeUnit

object CommandFinder {
    private const val VERSION_TIMEOUT_SECONDS = 5L

    fun findCommand(
        projectDir: File,
        wrapperName: String,
        wrapperNameWindows: String,
        globalCommand: String,
        globalCommandWindows: String,
        verbose: Boolean = false
    ): String? {
        require(projectDir.exists() && projectDir.isDirectory) { "Project directory must exist" }

        val isWindows = isWindowsOS()

        val wrapperFileName = if (isWindows) wrapperNameWindows else wrapperName
        val wrapperFile = File(projectDir, wrapperFileName)

        val wrapperExists = if (isWindows) {
            wrapperFile.exists()
        } else {
            wrapperFile.exists() && wrapperFile.canExecute()
        }

        if (wrapperExists) {
            if (verbose) {
                System.err.println("[CommandFinder] Found project wrapper: ${wrapperFile.absolutePath}")
            }
            return wrapperFile.absolutePath
        }

        if (verbose) {
            System.err.println("[CommandFinder] Project wrapper not found: $wrapperFileName")
        }

        val globalCmd = if (isWindows) globalCommandWindows else globalCommand

        if (isCommandAvailable(globalCmd, verbose)) {
            if (verbose) {
                System.err.println("[CommandFinder] Found global command: $globalCmd")
            }
            return globalCmd
        }

        if (verbose) {
            System.err.println("[CommandFinder] Global command not found: $globalCmd")
        }

        return null
    }

    fun isCommandAvailable(command: String, verbose: Boolean = false): Boolean {
        return try {
            val process = ProcessBuilder(command, "--version")
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (completed) {
                val success = process.exitValue() == 0
                if (verbose && !success) {
                    System.err.println("[CommandFinder] Command '$command --version' exited with code ${process.exitValue()}")
                }
                success
            } else {
                process.destroyForcibly()
                if (verbose) {
                    System.err.println("[CommandFinder] Command '$command --version' timed out")
                }
                false
            }
        } catch (e: Exception) {
            if (verbose) {
                System.err.println("[CommandFinder] Exception checking command '$command': ${e.message}")
            }
            false
        }
    }

    fun getCommandVersion(command: String, verbose: Boolean = false): String? {
        return try {
            val process = ProcessBuilder(command, "--version")
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
                if (verbose) {
                    System.err.println("[CommandFinder] Command '$command --version' exited with code ${process.exitValue()}")
                }
                null
            }
        } catch (e: Exception) {
            if (verbose) {
                System.err.println("[CommandFinder] Exception getting version for '$command': ${e.message}")
            }
            null
        }
    }

    private fun isWindowsOS(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }
}
