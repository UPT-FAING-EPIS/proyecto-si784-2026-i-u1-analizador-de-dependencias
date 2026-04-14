package com.depanalyzer.parser.gradle

import com.depanalyzer.parser.CommandFinder
import java.io.File

object GradleDetector {
    private const val GRADLE_COMMAND = "gradle"
    private const val GRADLE_COMMAND_WINDOWS = "gradle.cmd"
    private const val GRADLE_WRAPPER = "gradlew"
    private const val GRADLE_WRAPPER_WINDOWS = "gradlew.bat"

    fun findGradleCommand(projectDir: File, verbose: Boolean = false): String? {
        return CommandFinder.findCommand(
            projectDir = projectDir,
            wrapperName = GRADLE_WRAPPER,
            wrapperNameWindows = GRADLE_WRAPPER_WINDOWS,
            globalCommand = GRADLE_COMMAND,
            globalCommandWindows = GRADLE_COMMAND_WINDOWS,
            verbose = verbose
        )
    }
}
