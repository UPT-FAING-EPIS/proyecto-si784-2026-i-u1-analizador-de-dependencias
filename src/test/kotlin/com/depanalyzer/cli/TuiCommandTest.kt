package com.depanalyzer.cli

import com.depanalyzer.report.DependencyReport
import com.depanalyzer.tui.TerminalCapabilitiesDetector
import com.github.ajalt.clikt.core.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TuiCommandTest {
    @Test
    fun `does not launch tui command in non tty environments`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { name -> if (name == "CI") "true" else null },
            hasConsole = { false }
        )

        var tuiLaunched = false
        val command = Tui(
            analyzeExecutor = { DependencyReport(projectName = "tui-no-tty") },
            terminalCapabilitiesDetector = detector,
            tuiRunner = { _, _ ->
                tuiLaunched = true
                null
            }
        )

        command.parse(emptyList())

        assertEquals(false, tuiLaunched)
    }

    @Test
    fun `launches tui command in tty environments`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { null },
            hasConsole = { true }
        )

        var tuiLaunched = false
        val command = Tui(
            analyzeExecutor = { DependencyReport(projectName = "tui-tty") },
            terminalCapabilitiesDetector = detector,
            tuiRunner = { config, _ ->
                tuiLaunched = true
                config.scanProvider { }
            }
        )

        command.parse(emptyList())

        assertEquals(true, tuiLaunched)
    }

    @Test
    fun `forces dynamic analysis in tui mode even with static flags`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { null },
            hasConsole = { true }
        )

        var capturedRequest: AnalyzeExecutionRequest? = null
        val command = Tui(
            analyzeExecutor = { request ->
                capturedRequest = request
                DependencyReport(projectName = "dynamic-forced")
            },
            terminalCapabilitiesDetector = detector,
            tuiRunner = { config, _ -> config.scanProvider { } }
        )

        command.parse(listOf("--offline", "--disable-maven", "--disable-gradle"))

        val request = assertNotNull(capturedRequest)
        assertFalse(request.disableMaven)
        assertFalse(request.disableGradle)
        assertTrue(request.includeChains)
    }

    @Test
    fun `passes command output flag in tui mode`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { null },
            hasConsole = { true }
        )

        var capturedRequest: AnalyzeExecutionRequest? = null
        val command = Tui(
            analyzeExecutor = { request ->
                capturedRequest = request
                DependencyReport(projectName = "tui-command-output")
            },
            terminalCapabilitiesDetector = detector,
            tuiRunner = { config, _ -> config.scanProvider { } }
        )

        command.parse(listOf("--command-output"))

        assertTrue(capturedRequest?.showCommandOutput == true)
    }
}
