package com.depanalyzer.tui

import com.github.ajalt.mordant.rendering.AnsiLevel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TerminalCapabilitiesDetectorTest {
    @Test
    fun `returns no ansi and no interactive mode in ci`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { name -> if (name == "CI") "true" else null },
            hasConsole = { true }
        )

        val capabilities = detector.detect()

        assertEquals(AnsiLevel.NONE, capabilities.ansiLevel)
        assertEquals(false, capabilities.supportsInteractiveTui)
    }

    @Test
    fun `returns no ansi when there is no tty`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { null },
            hasConsole = { false }
        )

        val capabilities = detector.detect()

        assertEquals(AnsiLevel.NONE, capabilities.ansiLevel)
        assertEquals(false, capabilities.supportsInteractiveTui)
    }
}
