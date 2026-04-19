package com.depanalyzer.cli

import com.depanalyzer.core.ProjectAnalyzer
import com.depanalyzer.parser.ProjectType
import com.depanalyzer.repository.OssIndexClient
import com.depanalyzer.update.*
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.SelectList
import com.github.ajalt.mordant.widgets.Text
import java.nio.file.Path

class Update(
    private val plannerFactory: (String?) -> UpdatePlanner = { token ->
        AnalyzerUpdatePlanner(
            analyzer = ProjectAnalyzer(ossIndexClient = OssIndexClient(token = token))
        )
    },
    private val updaterFactory: (ProjectType) -> BuildFileUpdater = { type ->
        when (type) {
            ProjectType.MAVEN -> PomBuildFileUpdater()
            ProjectType.GRADLE_GROOVY -> GradleGroovyBuildFileUpdater()
            ProjectType.GRADLE_KOTLIN -> GradleKotlinBuildFileUpdater()
        }
    },
    private val selectionProvider: (Terminal, List<UpdateSuggestion>) -> Set<UpdateSuggestion> = ::defaultSelectionProvider
) : CliktCommand(name = "update") {
    override fun help(context: Context): String = "Actualiza dependencias con confirmación interactiva"

    private val path: Path by argument(help = "Ruta al directorio del proyecto").path(
        mustExist = true,
        canBeFile = false
    )
    private val ossIndexToken: String? by option(
        "-t",
        "--oss-index-token",
        help = "Token de autenticación para OSS Index API"
    )
    private val dynamic: Boolean by option(
        "--dynamic",
        help = "Fuerza análisis dinámico (más preciso, más lento). Por defecto: análisis estático"
    ).flag(default = false)

    override fun run() {
        val terminal = if (System.getenv("NO_COLOR") != null) {
            Terminal(ansiLevel = AnsiLevel.NONE)
        } else {
            Terminal(ansiLevel = AnsiLevel.TRUECOLOR)
        }
        val results = executeUpdate(path, terminal)
        val appliedCount = results.count { it.applied }
        val omittedCount = results.size - appliedCount
        echo("Resumen final: aplicadas=$appliedCount, omitidas=$omittedCount")
    }

    internal fun executeUpdate(targetPath: Path, terminal: Terminal = Terminal()): List<UpdateResult> {
        val planner = plannerFactory(getTokenFromCliOrEnv())
        val plan = planner.plan(
            targetPath,
            UpdateAnalysisOptions(dynamic = getDynamicFromCli())
        )
        val updater = updaterFactory(plan.projectType)
        val orderedSuggestions = plan.suggestions.sortedWith(
            compareBy<UpdateSuggestion> { if (it.reason == UpdateReason.CVE) 0 else 1 }
                .thenBy { it.coordinate }
        )

        if (orderedSuggestions.isEmpty()) {
            echo("No se encontraron dependencias desactualizadas para actualizar.")
            return emptyList()
        }

        terminal.println(bold("Actualizaciones sugeridas para ${plan.buildFile.name}"))
        terminal.println(bold("Formato: dependencia | actual -> nueva | razón"))

        val selectedSuggestions = selectionProvider(terminal, orderedSuggestions)
        val results = mutableListOf<UpdateResult>()

        if (selectedSuggestions.isEmpty()) {
            orderedSuggestions.forEach { suggestion ->
                results.add(UpdateResult(suggestion, applied = false, note = "no seleccionada"))
            }
            renderSummary(terminal, results)
            return results
        }

        val backup = BuildFileBackup.ensureBackup(plan.buildFile)
        terminal.println("Backup creado: ${backup.name}")

        for (suggestion in orderedSuggestions) {
            if (suggestion in selectedSuggestions) {
                val applied = updater.applyUpdate(plan.buildFile, suggestion)
                val note = if (applied) "aplicada" else "sin coincidencia editable"
                results.add(UpdateResult(suggestion, applied, note))
            } else {
                results.add(UpdateResult(suggestion, applied = false, note = "no seleccionada"))
            }
        }

        renderSummary(terminal, results)
        return results
    }

    private fun getTokenFromCliOrEnv(): String? {
        val cliToken = runCatching { ossIndexToken }.getOrNull()
        return cliToken ?: System.getenv("OSS_INDEX_TOKEN")
    }

    private fun getDynamicFromCli(): Boolean {
        return runCatching { dynamic }.getOrDefault(false)
    }

    private fun renderSummary(terminal: Terminal, results: List<UpdateResult>) {
        val applied = results.filter { it.applied }
        val omitted = results.filterNot { it.applied }

        terminal.println()
        terminal.println(bold("Resumen de actualizaciones"))
        val summaryTable = table {
            header { row("Estado", "Cantidad") }
            body {
                row("Aplicadas", applied.size.toString())
                row("Omitidas", omitted.size.toString())
            }
        }
        terminal.println(summaryTable)

        if (applied.isNotEmpty()) {
            terminal.println(bold("Cambios aplicados"))
            val appliedTable = table {
                header { row("Dependencia", "Cambio", "Razón", "Tipo", "Vía") }
                body {
                    applied.forEach { result ->
                        row(
                            result.suggestion.coordinate,
                            "${result.suggestion.currentVersion} -> ${result.suggestion.newVersion}",
                            result.suggestion.reason.label(),
                            result.suggestion.targetType.label(),
                            result.suggestion.viaDirectCoordinate ?: "-"
                        )
                    }
                }
            }
            terminal.println(appliedTable)
        }

        if (omitted.isNotEmpty()) {
            terminal.println(bold("Cambios omitidos"))
            val omittedTable = table {
                header { row("Dependencia", "Cambio", "Razón", "Tipo", "Vía", "Nota") }
                body {
                    omitted.forEach { result ->
                        row(
                            result.suggestion.coordinate,
                            "${result.suggestion.currentVersion} -> ${result.suggestion.newVersion}",
                            result.suggestion.reason.label(),
                            result.suggestion.targetType.label(),
                            result.suggestion.viaDirectCoordinate ?: "-",
                            result.note
                        )
                    }
                }
            }
            terminal.println(omittedTable)
        }
    }

    companion object {
        private data class SelectionState(
            val items: List<SelectList.Entry>,
            val filterText: String = "",
            val isFiltering: Boolean = false,
            val cursor: Int = 0,
        ) {
            val filteredIndexes: List<Int>
                get() {
                    if (filterText.isBlank()) {
                        return items.indices.toList()
                    }

                    return items.mapIndexedNotNull { index, entry ->
                        if (entry.title.contains(filterText, ignoreCase = true)) index else null
                    }
                }
        }

        private fun defaultSelectionProvider(
            terminal: Terminal,
            suggestions: List<UpdateSuggestion>
        ): Set<UpdateSuggestion> {
            val labels = suggestions.map(::formatSelectionLabel)
            val initialState = SelectionState(items = labels.map { SelectList.Entry(it) })
            var state = initialState

            val animation = terminal.animation<SelectionState> { current ->
                val filteredItems = current.filteredIndexes.map { current.items[it] }
                val cursorIndex = if (filteredItems.isEmpty()) -1 else current.cursor

                SelectList(
                    entries = filteredItems,
                    title = Text(
                        if (current.isFiltering) {
                            "Buscar: ${current.filterText}"
                        } else {
                            "Selecciona dependencias para actualizar"
                        }
                    ),
                    cursorIndex = cursorIndex,
                    styleOnHover = true,
                    cursorMarker = ">",
                    selectedMarker = "[x]",
                    unselectedMarker = "[ ]",
                    selectedStyle = green + bold,
                    cursorStyle = cyan + bold,
                    unselectedTitleStyle = gray,
                    unselectedMarkerStyle = gray,
                    captionBottom = Text(
                        dim("space/x marcar • a todo • A limpiar • j/k o ↑/↓ mover • / o f buscar • enter confirmar • esc/q cancelar")
                    )
                )
            }
            animation.update(state)

            terminal.enterRawMode(MouseTracking.Off).use { rawMode ->
                while (true) {
                    val event = rawMode.readEvent()
                    if (event !is KeyboardEvent) {
                        continue
                    }

                    if (event.isCtrlC || (!state.isFiltering && keyIs(event, "q"))) {
                        animation.clear()
                        return emptySet()
                    }

                    if (state.isFiltering && keyIs(event, "escape")) {
                        state = state.copy(filterText = "", isFiltering = false, cursor = 0)
                        animation.update(state.ensureCursorBounds())
                        continue
                    }

                    if (!state.isFiltering && keyIs(event, "escape")) {
                        animation.clear()
                        return emptySet()
                    }

                    if (!event.ctrl && !event.alt && state.isFiltering) {
                        state = when {
                            keyIs(event, "enter") -> state.copy(isFiltering = false)
                            keyIs(event, "backspace") -> state.copy(
                                filterText = state.filterText.dropLast(1),
                                cursor = 0
                            )

                            event.key.length == 1 -> state.copy(filterText = state.filterText + event.key, cursor = 0)
                            keyIs(event, "arrowup") || keyIs(event, "k") -> state.moveCursor(-1)
                            keyIs(event, "arrowdown") || keyIs(event, "j") -> state.moveCursor(1)
                            else -> state
                        }
                        animation.update(state.ensureCursorBounds())
                        state = state.ensureCursorBounds()
                        continue
                    }

                    state = when {
                        isFilterTrigger(event) -> state.copy(isFiltering = true, filterText = "", cursor = 0)
                        !state.isFiltering && keyIs(event, "a") && event.shift -> state.setAllSelected(false)
                        !state.isFiltering && keyIs(event, "a") -> state.setAllSelected(true)
                        keyIs(event, "arrowup") || keyIs(event, "k") -> state.moveCursor(-1)
                        keyIs(event, "arrowdown") || keyIs(event, "j") -> state.moveCursor(1)
                        keyIs(event, " ") || keyIs(event, "space") || keyIs(event, "spacebar") || keyIs(
                            event,
                            "x"
                        ) -> state.toggleCurrent()

                        keyIs(event, "enter") -> {
                            val selectedLabels = state.items.filter { it.selected }.map { it.title }.toSet()
                            animation.clear()
                            return suggestions.filterIndexed { index, _ -> labels[index] in selectedLabels }.toSet()
                        }

                        else -> state
                    }

                    state = state.ensureCursorBounds()
                    animation.update(state)
                }
            }
        }

        private fun SelectionState.moveCursor(delta: Int): SelectionState {
            if (filteredIndexes.isEmpty()) return copy(cursor = 0)
            val newCursor = (cursor + delta).coerceIn(0, filteredIndexes.lastIndex)
            return copy(cursor = newCursor)
        }

        private fun SelectionState.toggleCurrent(): SelectionState {
            val indexes = filteredIndexes
            if (indexes.isEmpty()) return this

            val itemIndex = indexes[cursor]
            val updatedItems = items.toMutableList()
            val current = updatedItems[itemIndex]
            updatedItems[itemIndex] = current.copy(selected = !current.selected)
            return copy(items = updatedItems)
        }

        private fun SelectionState.ensureCursorBounds(): SelectionState {
            val indexes = filteredIndexes
            if (indexes.isEmpty()) {
                return copy(cursor = 0)
            }

            return copy(cursor = cursor.coerceIn(0, indexes.lastIndex))
        }

        private fun SelectionState.setAllSelected(selected: Boolean): SelectionState {
            return copy(items = items.map { it.copy(selected = selected) })
        }

        private fun formatSelectionLabel(suggestion: UpdateSuggestion): String {
            val base =
                "${suggestion.coordinate} | ${suggestion.currentVersion} -> ${suggestion.newVersion} | ${suggestion.reason.label()} | ${suggestion.targetType.label()}"
            return suggestion.viaDirectCoordinate?.let { "$base | via $it" } ?: base
        }

        private fun isFilterTrigger(event: KeyboardEvent): Boolean {
            if (event.ctrl || event.alt) return false
            val key = event.key.lowercase()
            return key in setOf("/", "slash", "?", "numpaddivide", "divide", "f", "&") ||
                    (event.shift && key in setOf("7", "digit7"))
        }

        private fun keyIs(event: KeyboardEvent, expected: String): Boolean {
            return event.key.equals(expected, ignoreCase = true)
        }
    }
}
