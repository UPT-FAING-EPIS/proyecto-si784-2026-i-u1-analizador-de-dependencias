package com.depanalyzer.tui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TuiKeymapTest {
    @Test
    fun `registers required shortcuts`() {
        assertEquals(setOf("↑", "↓", "u", "U", "a", "x", "f", "q"), TuiKeymap.registeredShortcutKeys())
    }
}
