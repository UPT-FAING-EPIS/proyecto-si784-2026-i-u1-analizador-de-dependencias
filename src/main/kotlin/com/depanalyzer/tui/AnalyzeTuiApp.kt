package com.depanalyzer.tui

import com.depanalyzer.cli.ProgressTracker
import com.depanalyzer.report.DependencyReport
import com.depanalyzer.report.DependencyTreeNode
import com.depanalyzer.update.UpdateReason
import com.depanalyzer.update.UpdateResult
import com.depanalyzer.update.UpdateSuggestion
import com.depanalyzer.update.UpdateTargetType
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.terminal.Terminal
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

class AnalyzeTuiApp(
    private val terminal: Terminal = Terminal(),
    private val layout: TuiLayout = TuiLayout()
) {
    private enum class ConfirmationType {
        QUEUE_SELECTED,
        QUEUE_ALL,
        APPLY_PENDING,
        DISCARD_PENDING
    }

    private data class PendingConfirmation(
        val type: ConfirmationType,
        val message: String,
        val suggestions: List<UpdateSuggestion> = emptyList()
    )

    fun run(report: DependencyReport) {
        runAsync(
            initialStatus = "Interfaz interactiva activa",
            progressHint = null,
            scanProvider = { _ -> report },
            initialReport = report,
            applyUpdates = null
        )
    }

    fun runAsync(
        initialStatus: String,
        progressHint: String?,
        scanProvider: ((DependencyReport) -> Unit) -> DependencyReport,
        initialReport: DependencyReport? = null,
        applyUpdates: ((List<UpdateSuggestion>) -> List<UpdateResult>)? = null
    ): DependencyReport? {
        var viewportRows = layout.contentRows(terminal)
        var analyzedReport: DependencyReport? = null
        val initialEntries = initialReport?.let(::buildEntries).orEmpty()
        val initialProjectName = initialReport?.projectName ?: "escaneo-dinamico"
        val hasInitialTreeData = hasTransitiveTreeData(initialEntries)

        var state = TuiState(
            entries = initialEntries,
            summary = buildSummary(initialProjectName, initialEntries),
            statusLine = initialStatus,
            isTreeTabEnabled = hasInitialTreeData,
            treeUnavailableMessage = if (hasInitialTreeData) {
                null
            } else {
                "Arbol transitivo no disponible: no se pudo cargar la estructura transitiva del proyecto"
            },
            isLoading = true,
            loadingMessage = progressHint ?: "Escaneo en progreso...",
            loadingFrame = 0
        )

        val partialReport = AtomicReference<DependencyReport?>(null)
        val partialVersion = AtomicLong(0)
        val progressMessage = AtomicReference<String?>(null)
        val executor = Executors.newSingleThreadExecutor()
        ProgressTracker.setListener { message ->
            val normalized = message.trim()
            if (normalized.isNotEmpty()) {
                progressMessage.set(normalized)
            }
        }
        val future = executor.submit(
            Callable {
                scanProvider { snapshot ->
                    partialReport.set(snapshot)
                    partialVersion.incrementAndGet()
                }
            }
        )

        try {
            FullScreenSession(terminal).use { fullScreen ->
                fullScreen.enter()
                layout.render(terminal, state)

                terminal.enterRawMode(MouseTracking.Off).use { rawMode ->
                    var appliedPartialVersion = 0L
                    var pendingConfirmation: PendingConfirmation? = null
                    var lastPartialApplyMs = 0L

                    while (true) {
                        var stateChanged = false

                        if (state.isLoading) {
                            val latestMessage = progressMessage.getAndSet(null)
                            if (!latestMessage.isNullOrBlank() && latestMessage != state.loadingMessage) {
                                state = state.copy(loadingMessage = latestMessage)
                                stateChanged = true
                            }
                        }

                        val pendingVersion = partialVersion.get()
                        if (pendingVersion > appliedPartialVersion) {
                            val now = System.currentTimeMillis()
                            val shouldApplySnapshot =
                                !state.isLoading || future.isDone || (now - lastPartialApplyMs) >= 180
                            if (shouldApplySnapshot) {
                                val snapshot = partialReport.get()
                                if (snapshot != null) {
                                    val snapshotEntries = buildEntries(snapshot)
                                    val entries = mergeLoadingEntries(
                                        currentEntries = state.entries,
                                        incomingEntries = snapshotEntries,
                                        isLoading = state.isLoading
                                    )
                                    val treeTabEnabled = hasTransitiveTreeData(entries)
                                    val pendingUpdates = prunePendingUpdates(state.pendingUpdates, entries)
                                    pendingConfirmation = null
                                    val updatedState = state.copy(
                                        entries = entries,
                                        summary = buildSummary(snapshot.projectName, entries),
                                        isTreeTabEnabled = treeTabEnabled,
                                        treeUnavailableMessage = if (treeTabEnabled) {
                                            null
                                        } else {
                                            "Arbol transitivo no disponible: no se pudo cargar la estructura transitiva del proyecto"
                                        },
                                        statusLine = "Escaneo en progreso...",
                                        confirmationPrompt = null,
                                        pendingUpdates = pendingUpdates
                                    ).ensureCursorBounds().ensureScrollVisible(viewportRows)
                                    if (updatedState != state) {
                                        state = updatedState
                                        stateChanged = true
                                    }
                                }
                                appliedPartialVersion = pendingVersion
                                lastPartialApplyMs = now
                            }
                        }

                        if (state.isLoading) {
                            if (future.isDone) {
                                val report = try {
                                    future.get()
                                } catch (e: Exception) {
                                    state = state.copy(
                                        isLoading = false,
                                        loadError = "${e.javaClass.simpleName}: ${e.message ?: "error desconocido"}",
                                        statusLine = "Escaneo dinámico falló",
                                        confirmationPrompt = null
                                    )
                                    layout.render(terminal, state)
                                    null
                                }

                                if (report != null) {
                                    analyzedReport = report
                                    val entries = buildEntries(report)
                                    val treeTabEnabled = hasTransitiveTreeData(entries)
                                    val pendingUpdates = prunePendingUpdates(state.pendingUpdates, entries)
                                    pendingConfirmation = null
                                    val updatedState = state.copy(
                                        entries = entries,
                                        summary = buildSummary(report.projectName, entries),
                                        statusLine = "Escaneo dinámico completado",
                                        confirmationPrompt = null,
                                        isTreeTabEnabled = treeTabEnabled,
                                        treeUnavailableMessage = if (treeTabEnabled) {
                                            null
                                        } else {
                                            "Arbol transitivo no disponible: no se pudo cargar la estructura transitiva del proyecto"
                                        },
                                        pendingUpdates = pendingUpdates,
                                        isLoading = false,
                                        loadingMessage = "",
                                        loadingFrame = 0,
                                        loadError = null
                                    ).ensureCursorBounds().ensureScrollVisible(viewportRows)
                                    if (updatedState != state) {
                                        state = updatedState
                                        stateChanged = true
                                    }
                                    appliedPartialVersion = partialVersion.get()
                                }
                            }
                        }

                        val event = try {
                            rawMode.readEventOrNull(120.milliseconds)
                        } catch (error: RuntimeException) {
                            if (isRecoverableConsoleReadError(error)) {
                                null
                            } else {
                                throw error
                            }
                        }
                        if (event is KeyboardEvent) {
                            if (event.isCtrlC) {
                                if (!future.isDone) {
                                    future.cancel(true)
                                }
                                break
                            }

                            val key = event.key.lowercase()
                            if (pendingConfirmation != null) {
                                when {
                                    key == "s" || key == "y" -> {
                                        state = acceptConfirmation(
                                            state = state,
                                            confirmation = pendingConfirmation,
                                            applyUpdates = applyUpdates
                                        ).ensureCursorBounds().ensureScrollVisible(viewportRows)
                                        pendingConfirmation = null
                                        stateChanged = true
                                    }

                                    key == "n" || key == "escape" -> {
                                        pendingConfirmation = null
                                        state = state.copy(
                                            confirmationPrompt = null,
                                            statusLine = "Acción cancelada"
                                        )
                                            .ensureCursorBounds()
                                            .ensureScrollVisible(viewportRows)
                                        stateChanged = true
                                    }
                                }

                                if (stateChanged) {
                                    layout.render(terminal, state)
                                }
                                continue
                            }

                            val action = TuiKeymap.resolve(event)
                            if (action == TuiAction.QUIT) {
                                if (!future.isDone) {
                                    future.cancel(true)
                                }
                                break
                            }

                            if (state.loadError == null && (!state.isLoading || state.entries.isNotEmpty())) {
                                viewportRows = layout.contentRows(terminal)
                                val previousState = state
                                val handled = handleAction(state, action)
                                state = handled.first.ensureCursorBounds().ensureScrollVisible(viewportRows)
                                pendingConfirmation = handled.second
                                stateChanged = state != previousState || pendingConfirmation != null
                            }
                        }

                        if (stateChanged) {
                            layout.render(terminal, state)
                        }
                    }
                }
            }
        } finally {
            ProgressTracker.setListener(null)
            if (!future.isDone) {
                future.cancel(true)
            }
            executor.shutdownNow()
        }

        return analyzedReport
    }

    private fun handleAction(state: TuiState, action: TuiAction): Pair<TuiState, PendingConfirmation?> {
        return when (action) {
            TuiAction.MOVE_UP -> Pair(state.moveCursor(-1), null)
            TuiAction.MOVE_DOWN -> Pair(state.moveCursor(1), null)
            TuiAction.UPDATE_SELECTED -> {
                val selected = state.selectedEntry
                val suggestion = selected?.updateSuggestion
                when {
                    selected == null -> Pair(state.copy(statusLine = "No hay dependencia seleccionada"), null)
                    suggestion == null -> Pair(
                        state.copy(statusLine = "${selected.coordinate}: no hay actualización directa sugerida"),
                        null
                    )

                    state.pendingUpdates.containsKey(selected.coordinate) -> Pair(
                        state.copy(statusLine = "${selected.coordinate} ya está pendiente"),
                        null
                    )

                    else -> {
                        val prompt =
                            "Confirmar: agregar ${selected.coordinate} ${suggestion.currentVersion} -> ${suggestion.newVersion} [s/n]"
                        Pair(
                            state.copy(statusLine = prompt, confirmationPrompt = prompt),
                            PendingConfirmation(
                                type = ConfirmationType.QUEUE_SELECTED,
                                message = prompt,
                                suggestions = listOf(suggestion)
                            )
                        )
                    }
                }
            }

            TuiAction.UPDATE_ALL -> {
                val suggestions = state.entries.mapNotNull { entry ->
                    entry.updateSuggestion?.takeUnless { state.pendingUpdates.containsKey(entry.coordinate) }
                }
                if (suggestions.isEmpty()) {
                    Pair(state.copy(statusLine = "No hay actualizaciones directas nuevas para agregar"), null)
                } else {
                    val prompt = "Confirmar: agregar ${suggestions.size} actualizaciones a pendientes [s/n]"
                    Pair(
                        state.copy(statusLine = prompt, confirmationPrompt = prompt),
                        PendingConfirmation(
                            type = ConfirmationType.QUEUE_ALL,
                            message = prompt,
                            suggestions = suggestions
                        )
                    )
                }
            }

            TuiAction.APPLY_PENDING -> {
                val suggestions = state.pendingUpdates.values.toList()
                if (suggestions.isEmpty()) {
                    Pair(state.copy(statusLine = "No hay actualizaciones pendientes para aplicar"), null)
                } else {
                    val prompt = "Confirmar: aplicar ${suggestions.size} actualizaciones pendientes [s/n]"
                    Pair(
                        state.copy(statusLine = prompt, confirmationPrompt = prompt),
                        PendingConfirmation(
                            type = ConfirmationType.APPLY_PENDING,
                            message = prompt,
                            suggestions = suggestions
                        )
                    )
                }
            }

            TuiAction.DISCARD_PENDING -> {
                if (state.pendingUpdates.isEmpty()) {
                    Pair(state.copy(statusLine = "No hay actualizaciones pendientes para descartar"), null)
                } else {
                    val prompt = "Confirmar: descartar ${state.pendingUpdates.size} pendientes [s/n]"
                    Pair(
                        state.copy(statusLine = prompt, confirmationPrompt = prompt),
                        PendingConfirmation(type = ConfirmationType.DISCARD_PENDING, message = prompt)
                    )
                }
            }

            TuiAction.FILTER -> {
                val updated = state.cycleFilter()
                Pair(updated.copy(statusLine = "Filtro activo: ${updated.activeFilter.label()}"), null)
            }

            TuiAction.NEXT_TAB -> {
                if (!state.isTreeTabEnabled) {
                    val message = state.treeUnavailableMessage ?: "Arbol transitivo deshabilitado"
                    return Pair(state.copy(statusLine = message), null)
                }
                val updated = state.nextTab()
                Pair(updated.copy(statusLine = "Pestaña: ${updated.activeTab.label()}"), null)
            }

            TuiAction.PREVIOUS_TAB -> {
                if (!state.isTreeTabEnabled) {
                    val message = state.treeUnavailableMessage ?: "Arbol transitivo deshabilitado"
                    return Pair(state.copy(statusLine = message), null)
                }
                val updated = state.previousTab()
                Pair(updated.copy(statusLine = "Pestaña: ${updated.activeTab.label()}"), null)
            }

            TuiAction.QUIT,
            TuiAction.NONE -> Pair(state, null)
        }
    }

    private fun acceptConfirmation(
        state: TuiState,
        confirmation: PendingConfirmation,
        applyUpdates: ((List<UpdateSuggestion>) -> List<UpdateResult>)?
    ): TuiState {
        return when (confirmation.type) {
            ConfirmationType.QUEUE_SELECTED,
            ConfirmationType.QUEUE_ALL -> {
                val pending = LinkedHashMap(state.pendingUpdates)
                confirmation.suggestions.forEach { suggestion ->
                    pending[suggestion.coordinate] = suggestion
                }

                val addedCount = confirmation.suggestions.count { suggestion ->
                    suggestion.coordinate in pending
                }
                state.copy(
                    pendingUpdates = pending,
                    statusLine = "Pendientes: ${pending.size} (${addedCount} agregadas)",
                    confirmationPrompt = null
                )
            }

            ConfirmationType.APPLY_PENDING -> {
                if (applyUpdates == null) {
                    return state.copy(
                        statusLine = "Aplicación de cambios no disponible en esta ejecución",
                        confirmationPrompt = null
                    )
                }

                val results = runCatching { applyUpdates(confirmation.suggestions) }
                    .getOrElse { error ->
                        return state.copy(
                            statusLine = "Error aplicando cambios: ${error.message ?: "desconocido"}",
                            confirmationPrompt = null
                        )
                    }

                val appliedSuggestions = results.filter { it.applied }.map { it.suggestion }
                if (appliedSuggestions.isEmpty()) {
                    return state.copy(
                        statusLine = "No se aplicaron cambios (sin coincidencia editable)",
                        confirmationPrompt = null
                    )
                }

                val appliedCoordinates = appliedSuggestions.map { it.coordinate }.toSet()
                val pending = LinkedHashMap(state.pendingUpdates)
                appliedCoordinates.forEach { pending.remove(it) }
                val updatedEntries = applySuccessfulUpdates(state.entries, appliedSuggestions)

                state.copy(
                    entries = updatedEntries,
                    summary = buildSummary(state.summary.projectName, updatedEntries),
                    pendingUpdates = pending,
                    statusLine = "Aplicadas ${appliedSuggestions.size}; fallidas ${results.size - appliedSuggestions.size}; pendientes ${pending.size}",
                    confirmationPrompt = null
                )
            }

            ConfirmationType.DISCARD_PENDING -> {
                state.copy(
                    pendingUpdates = emptyMap(),
                    statusLine = "Pendientes descartadas",
                    confirmationPrompt = null
                )
            }
        }
    }

    private fun applySuccessfulUpdates(
        entries: List<TuiDependencyEntry>,
        appliedSuggestions: List<UpdateSuggestion>
    ): List<TuiDependencyEntry> {
        val appliedByCoordinate = appliedSuggestions.associateBy { it.coordinate }
        return entries.map { entry ->
            val applied = appliedByCoordinate[entry.coordinate] ?: return@map entry
            val oldCoordinateWithVersion = "${applied.groupId}:${applied.artifactId}:${applied.currentVersion}"
            val newCoordinateWithVersion = "${applied.groupId}:${applied.artifactId}:${applied.newVersion}"

            entry.copy(
                currentVersion = applied.newVersion,
                latestVersion = null,
                outdatedCount = 0,
                updateSuggestion = null,
                chainPreview = entry.chainPreview.map { node ->
                    if (node == oldCoordinateWithVersion) newCoordinateWithVersion else node
                },
                transitiveTreeLines = entry.transitiveTreeLines.map { line ->
                    line.replace(oldCoordinateWithVersion, newCoordinateWithVersion)
                }
            )
        }
    }

    private fun prunePendingUpdates(
        pendingUpdates: Map<String, UpdateSuggestion>,
        entries: List<TuiDependencyEntry>
    ): Map<String, UpdateSuggestion> {
        if (pendingUpdates.isEmpty()) return pendingUpdates
        val validCoordinates = entries.map { it.coordinate }.toSet()
        return pendingUpdates.filterKeys { it in validCoordinates }
    }

    internal fun mergeLoadingEntries(
        currentEntries: List<TuiDependencyEntry>,
        incomingEntries: List<TuiDependencyEntry>,
        isLoading: Boolean
    ): List<TuiDependencyEntry> {
        if (!isLoading) {
            return incomingEntries
        }
        if (incomingEntries.isEmpty()) {
            return currentEntries
        }
        if (currentEntries.isEmpty()) {
            return incomingEntries
        }

        val incomingByCoordinate = incomingEntries.associateBy { it.coordinate }.toMutableMap()
        val merged = mutableListOf<TuiDependencyEntry>()

        currentEntries.forEach { current ->
            val replacement = incomingByCoordinate.remove(current.coordinate)
            merged += replacement ?: current
        }
        merged += incomingByCoordinate.values
        return merged
    }

    private fun buildSummary(projectName: String, entries: List<TuiDependencyEntry>): TuiSummary {
        return TuiSummary(
            projectName = projectName,
            outdatedCount = entries.count { it.outdatedCount > 0 || it.latestVersion != null },
            vulnerableCount = entries.count { it.vulnerabilityCount > 0 },
            totalEntries = entries.size
        )
    }

    internal fun buildEntries(report: DependencyReport): List<TuiDependencyEntry> {
        val roots = report.dependencyTree.orEmpty()
        val directRoots = roots.filter { it.isDirectDependency }.ifEmpty { roots }
        if (directRoots.isNotEmpty()) {
            val entries = directRoots.map { root ->
                val vulnerabilities = collectSubtreeVulnerabilities(root)
                val chain = firstVulnerabilityPath(root)
                val outdatedCount = countOutdatedNodes(root)
                val reason = if (vulnerabilities.isNotEmpty()) UpdateReason.CVE else UpdateReason.OUTDATED
                val updateSuggestion = root.latestVersion
                    ?.takeIf { it != root.currentVersion }
                    ?.let { latest ->
                        UpdateSuggestion(
                            groupId = root.groupId,
                            artifactId = root.artifactId,
                            currentVersion = root.currentVersion,
                            newVersion = latest,
                            reason = reason,
                            targetType = UpdateTargetType.DIRECT
                        )
                    }

                TuiDependencyEntry(
                    coordinate = "${root.groupId}:${root.artifactId}",
                    currentVersion = root.currentVersion,
                    latestVersion = root.latestVersion,
                    vulnerabilityCount = vulnerabilities.size,
                    outdatedCount = outdatedCount,
                    maxSeverity = vulnerabilities.maxByOrNull { it.severity.ordinal }?.severity,
                    source = "direct",
                    vulnerabilities = vulnerabilities,
                    chainPreview = chain,
                    transitiveTreeLines = buildTransitiveTreeLines(root, chain.toSet()),
                    updateSuggestion = updateSuggestion
                )
            }

            return entries.sortedWith(
                compareByDescending<TuiDependencyEntry> { it.maxSeverity?.priority() ?: 0 }
                    .thenByDescending { it.vulnerabilityCount }
                    .thenByDescending { it.outdatedCount }
                    .thenBy { it.coordinate }
            )
        }

        val fallback = linkedMapOf<String, TuiDependencyEntry>()
        report.upToDate.forEach { dep ->
            val coordinate = "${dep.groupId}:${dep.artifactId}"
            fallback[coordinate] = TuiDependencyEntry(
                coordinate = coordinate,
                currentVersion = dep.version,
                source = "direct"
            )
        }
        report.outdated.forEach { dep ->
            val coordinate = "${dep.groupId}:${dep.artifactId}"
            val existing = fallback[coordinate]
            val reason = if (
                report.directVulnerable.any { it.groupId == dep.groupId && it.artifactId == dep.artifactId }
            ) {
                UpdateReason.CVE
            } else {
                UpdateReason.OUTDATED
            }
            val suggestion = UpdateSuggestion(
                groupId = dep.groupId,
                artifactId = dep.artifactId,
                currentVersion = dep.currentVersion,
                newVersion = dep.latestVersion,
                reason = reason,
                targetType = UpdateTargetType.DIRECT
            )

            fallback[coordinate] = TuiDependencyEntry(
                coordinate = coordinate,
                currentVersion = dep.currentVersion,
                latestVersion = dep.latestVersion,
                vulnerabilityCount = existing?.vulnerabilityCount ?: 0,
                outdatedCount = 1,
                maxSeverity = existing?.maxSeverity,
                source = "direct",
                vulnerabilities = existing?.vulnerabilities ?: emptyList(),
                chainPreview = existing?.chainPreview ?: emptyList(),
                transitiveTreeLines = existing?.transitiveTreeLines ?: emptyList(),
                updateSuggestion = suggestion
            )
        }
        report.directVulnerable.forEach { dep ->
            val coordinate = "${dep.groupId}:${dep.artifactId}"
            val existing = fallback[coordinate]
            val mappedVulnerabilities = dep.vulnerabilities.map {
                TuiVulnerability(
                    cveId = it.cveId,
                    severity = it.severity,
                    cvssScore = it.cvssScore,
                    description = it.description
                )
            }

            val suggestion = existing?.updateSuggestion ?: report.outdated
                .firstOrNull { outdated -> outdated.groupId == dep.groupId && outdated.artifactId == dep.artifactId }
                ?.let { outdated ->
                    UpdateSuggestion(
                        groupId = dep.groupId,
                        artifactId = dep.artifactId,
                        currentVersion = outdated.currentVersion,
                        newVersion = outdated.latestVersion,
                        reason = UpdateReason.CVE,
                        targetType = UpdateTargetType.DIRECT
                    )
                }

            fallback[coordinate] = TuiDependencyEntry(
                coordinate = coordinate,
                currentVersion = existing?.currentVersion ?: dep.version,
                latestVersion = existing?.latestVersion,
                vulnerabilityCount = mappedVulnerabilities.size,
                outdatedCount = existing?.outdatedCount ?: 0,
                maxSeverity = mappedVulnerabilities.maxByOrNull { it.severity.ordinal }?.severity,
                source = "direct",
                vulnerabilities = mappedVulnerabilities,
                chainPreview = dep.dependencyChain.orEmpty(),
                transitiveTreeLines = existing?.transitiveTreeLines ?: emptyList(),
                updateSuggestion = suggestion
            )
        }

        return fallback.values.toList()
    }

    private fun hasTransitiveTreeData(entries: List<TuiDependencyEntry>): Boolean {
        return entries.any { entry -> entry.transitiveTreeLines.any { it.startsWith("  +") } }
    }

    internal fun isRecoverableConsoleReadError(error: Throwable?): Boolean {
        if (error == null) return false
        val message = error.message.orEmpty()
        if (message.contains("waitResult=258")) {
            val fromWindowsNativeInput = error.stackTrace.any { frame ->
                frame.className.contains("TerminalInterfaceNativeImageWindows") ||
                        frame.fileName?.contains("TerminalInterface.nativeimage.windows.kt", ignoreCase = true) == true
            }
            if (fromWindowsNativeInput) {
                return true
            }
        }
        return isRecoverableConsoleReadError(error.cause)
    }

    private fun collectSubtreeVulnerabilities(root: DependencyTreeNode): List<TuiVulnerability> {
        val result = mutableListOf<TuiVulnerability>()

        fun visit(node: DependencyTreeNode) {
            node.vulnerabilities.forEach { vuln ->
                result += TuiVulnerability(
                    cveId = vuln.cveId,
                    severity = vuln.severity,
                    cvssScore = vuln.cvssScore,
                    description = vuln.description
                )
            }
            node.children.forEach(::visit)
        }

        visit(root)
        return result.distinctBy { it.cveId }
    }

    private fun countOutdatedNodes(root: DependencyTreeNode): Int {
        var count = 0
        fun visit(node: DependencyTreeNode) {
            if (node.latestVersion != null) count++
            node.children.forEach(::visit)
        }
        visit(root)
        return count
    }

    private fun firstVulnerabilityPath(root: DependencyTreeNode): List<String> {
        val path = mutableListOf<String>()

        fun dfs(node: DependencyTreeNode, currentPath: MutableList<String>): Boolean {
            currentPath += "${node.groupId}:${node.artifactId}:${node.currentVersion}"
            if (node.vulnerabilities.isNotEmpty()) {
                path.clear()
                path += currentPath
                return true
            }
            for (child in node.children) {
                if (dfs(child, currentPath)) {
                    return true
                }
            }
            currentPath.removeAt(currentPath.lastIndex)
            return false
        }

        dfs(root, mutableListOf())
        return path
    }

    private fun buildTransitiveTreeLines(root: DependencyTreeNode, vulnerableChain: Set<String>): List<String> {
        val lines = mutableListOf<String>()

        fun visit(node: DependencyTreeNode, depth: Int) {
            val indent = "  ".repeat(depth)
            val coordinate = "${node.groupId}:${node.artifactId}:${node.currentVersion}"
            val chainBadge = if (coordinate in vulnerableChain) " [CHAIN]" else ""
            val outdatedBadge = node.latestVersion?.let { " desactualizada -> $it" }.orEmpty()
            lines += "$indent+ $coordinate$chainBadge$outdatedBadge"

            node.vulnerabilities.forEach { vuln ->
                val cvss = vuln.cvssScore?.let { " CVSS %.1f".format(it) } ?: ""
                val cveChainBadge = if (coordinate in vulnerableChain) " [CHAIN]" else ""
                lines += "$indent  ! ${vuln.cveId}$cvss$cveChainBadge"
            }

            node.children.forEach { child -> visit(child, depth + 1) }
        }

        visit(root, 0)
        return lines
    }
}
