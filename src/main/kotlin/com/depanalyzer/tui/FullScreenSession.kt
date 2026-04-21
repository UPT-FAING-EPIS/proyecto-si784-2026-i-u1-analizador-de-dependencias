package com.depanalyzer.tui

import com.github.ajalt.mordant.terminal.Terminal

class FullScreenSession(private val terminal: Terminal) : AutoCloseable {
    private var entered = false

    fun enter() {
        if (entered) return
        terminal.rawPrint("\u001b[?1049h")
        terminal.cursor.hide(showOnExit = false)
        entered = true
    }

    override fun close() {
        if (!entered) return
        terminal.cursor.show()
        terminal.rawPrint("\u001b[?1049l")
        entered = false
    }
}
