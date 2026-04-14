package com.depanalyzer.parser.maven

import com.depanalyzer.parser.CommandFinder
import java.io.File

object MavenDetector {
    private const val MVN_COMMAND = "mvn"
    private const val MVN_COMMAND_WINDOWS = "mvn.cmd"
    private const val MVN_WRAPPER = "mvnw"
    private const val MVN_WRAPPER_WINDOWS = "mvnw.cmd"

    fun isAvailable(verbose: Boolean = false): Boolean {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val command = if (isWindows) MVN_COMMAND_WINDOWS else MVN_COMMAND
        return CommandFinder.isCommandAvailable(command, verbose)
    }

    fun getVersion(verbose: Boolean = false): String? {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val command = if (isWindows) MVN_COMMAND_WINDOWS else MVN_COMMAND
        return CommandFinder.getCommandVersion(command, verbose)
    }

    fun findMavenCommand(projectDir: File, verbose: Boolean = false): String? {
        return CommandFinder.findCommand(
            projectDir = projectDir,
            wrapperName = MVN_WRAPPER,
            wrapperNameWindows = MVN_WRAPPER_WINDOWS,
            globalCommand = MVN_COMMAND,
            globalCommandWindows = MVN_COMMAND_WINDOWS,
            verbose = verbose
        )
    }
}
