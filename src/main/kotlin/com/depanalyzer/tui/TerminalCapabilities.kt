package com.depanalyzer.tui

import com.github.ajalt.mordant.rendering.AnsiLevel

data class TerminalCapabilities(
    val ansiLevel: AnsiLevel,
    val isTty: Boolean,
    val isCi: Boolean,
    val supportsInteractiveTui: Boolean
)

class TerminalCapabilitiesDetector(
    private val envProvider: (String) -> String? = { System.getenv(it) },
    private val hasConsole: () -> Boolean = { System.console() != null }
) {
    fun detect(noColor: Boolean = false): TerminalCapabilities {
        val isTty = hasConsole()
        val isCi = isCiEnvironment()
        val noColorEnv = envProvider("NO_COLOR") != null
        val dumbTerm = envProvider("TERM").equals("dumb", ignoreCase = true)

        val ansiLevel = if (noColor || noColorEnv || dumbTerm || isCi || !isTty) {
            AnsiLevel.NONE
        } else {
            AnsiLevel.TRUECOLOR
        }

        return TerminalCapabilities(
            ansiLevel = ansiLevel,
            isTty = isTty,
            isCi = isCi,
            supportsInteractiveTui = isTty && !isCi
        )
    }

    private fun isCiEnvironment(): Boolean {
        val ci = envProvider("CI")
        if (ci != null && ci != "0" && !ci.equals("false", ignoreCase = true)) {
            return true
        }

        if (envProvider("GITHUB_ACTIONS").equals("true", ignoreCase = true)) {
            return true
        }

        return envProvider("JENKINS_URL") != null || envProvider("BUILD_NUMBER") != null
    }
}
