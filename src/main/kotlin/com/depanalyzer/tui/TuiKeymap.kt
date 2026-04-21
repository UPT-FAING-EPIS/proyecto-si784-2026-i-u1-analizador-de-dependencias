package com.depanalyzer.tui

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.isCtrlC

enum class TuiAction {
    MOVE_UP,
    MOVE_DOWN,
    UPDATE_SELECTED,
    UPDATE_ALL,
    APPLY_PENDING,
    DISCARD_PENDING,
    FILTER,
    NEXT_TAB,
    PREVIOUS_TAB,
    QUIT,
    NONE
}

data class TuiShortcut(
    val key: String,
    val description: String
)

object TuiKeymap {
    private val shortcuts = listOf(
        TuiShortcut("↑", "Navegar arriba"),
        TuiShortcut("↓", "Navegar abajo"),
        TuiShortcut("u", "Actualizar seleccionado"),
        TuiShortcut("U", "Actualizar todo"),
        TuiShortcut("a", "Aplicar pendientes"),
        TuiShortcut("x", "Descartar pendientes"),
        TuiShortcut("f", "Filtrar"),
        TuiShortcut("q", "Salir")
    )

    fun shortcuts(): List<TuiShortcut> = shortcuts

    fun registeredShortcutKeys(): Set<String> = shortcuts.map { it.key }.toSet()

    fun resolve(event: KeyboardEvent): TuiAction {
        if (event.isCtrlC) return TuiAction.QUIT

        val key = event.key.lowercase()
        return when {
            key == "arrowup" -> TuiAction.MOVE_UP
            key == "arrowdown" -> TuiAction.MOVE_DOWN
            key == "tab" || key == "arrowright" -> TuiAction.NEXT_TAB
            key == "arrowleft" -> TuiAction.PREVIOUS_TAB
            key == "q" -> TuiAction.QUIT
            key == "f" -> TuiAction.FILTER
            key == "u" && (event.shift || event.key == "U") -> TuiAction.UPDATE_ALL
            key == "u" -> TuiAction.UPDATE_SELECTED
            key == "a" -> TuiAction.APPLY_PENDING
            key == "x" -> TuiAction.DISCARD_PENDING
            else -> TuiAction.NONE
        }
    }
}
