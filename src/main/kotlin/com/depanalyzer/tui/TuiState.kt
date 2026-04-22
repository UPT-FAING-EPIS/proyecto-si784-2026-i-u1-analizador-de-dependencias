package com.depanalyzer.tui

import com.depanalyzer.report.VulnerabilitySeverity
import com.depanalyzer.update.UpdateSuggestion

enum class TuiTab {
    DETAIL,
    TREE;

    fun label(): String = when (this) {
        DETAIL -> "Detalle"
        TREE -> "Arbol transitivo"
    }

    fun next(): TuiTab = when (this) {
        DETAIL -> TREE
        TREE -> DETAIL
    }

    fun previous(): TuiTab = when (this) {
        DETAIL -> TREE
        TREE -> DETAIL
    }
}

enum class TuiQuickFilter {
    DIRECT,
    CVE,
    OUTDATED,
    TRANSITIVE,
    ALL;

    fun label(): String = when (this) {
        DIRECT -> "Dependencias"
        ALL -> "Todas"
        CVE -> "Solo CVE"
        OUTDATED -> "Solo desact."
        TRANSITIVE -> "Solo transitivas"
    }

    fun next(): TuiQuickFilter = when (this) {
        DIRECT -> CVE
        CVE -> OUTDATED
        OUTDATED -> TRANSITIVE
        TRANSITIVE -> ALL
        ALL -> DIRECT
    }
}

data class TuiSummary(
    val projectName: String,
    val outdatedCount: Int,
    val vulnerableCount: Int,
    val totalEntries: Int
)

data class TuiVulnerability(
    val cveId: String,
    val severity: VulnerabilitySeverity,
    val cvssScore: Double?,
    val description: String?
)

data class TuiDependencyEntry(
    val coordinate: String,
    val currentVersion: String,
    val latestVersion: String? = null,
    val vulnerabilityCount: Int = 0,
    val outdatedCount: Int = 0,
    val maxSeverity: VulnerabilitySeverity? = null,
    val source: String = "report",
    val vulnerabilities: List<TuiVulnerability> = emptyList(),
    val chainPreview: List<String> = emptyList(),
    val transitiveTreeLines: List<String> = emptyList(),
    val updateSuggestion: UpdateSuggestion? = null
)

data class TuiState(
    val entries: List<TuiDependencyEntry>,
    val summary: TuiSummary,
    val cursor: Int = 0,
    val scrollOffset: Int = 0,
    val activeFilter: TuiQuickFilter = TuiQuickFilter.DIRECT,
    val activeTab: TuiTab = TuiTab.DETAIL,
    val statusLine: String = "Listo",
    val confirmationPrompt: String? = null,
    val isTreeTabEnabled: Boolean = true,
    val treeUnavailableMessage: String? = null,
    val pendingUpdates: Map<String, UpdateSuggestion> = emptyMap(),
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val loadingFrame: Int = 0,
    val loadError: String? = null
) {
    val filteredIndexes: List<Int>
        get() {
            return entries.mapIndexedNotNull { index, entry ->
                if (matchesQuickFilter(entry)) index else null
            }
        }

    val selectedEntry: TuiDependencyEntry?
        get() {
            if (filteredIndexes.isEmpty()) return null
            val selectedIndex = filteredIndexes[cursor.coerceIn(0, filteredIndexes.lastIndex)]
            return entries.getOrNull(selectedIndex)
        }

    fun moveCursor(delta: Int): TuiState {
        if (filteredIndexes.isEmpty()) return copy(cursor = 0)
        return copy(cursor = (cursor + delta).coerceIn(0, filteredIndexes.lastIndex))
    }

    fun ensureCursorBounds(): TuiState {
        if (filteredIndexes.isEmpty()) return copy(cursor = 0)
        return copy(cursor = cursor.coerceIn(0, filteredIndexes.lastIndex))
    }

    fun cycleFilter(): TuiState {
        return copy(activeFilter = activeFilter.next(), cursor = 0, scrollOffset = 0)
    }

    fun nextTab(): TuiState = copy(activeTab = activeTab.next())

    fun previousTab(): TuiState = copy(activeTab = activeTab.previous())

    fun ensureScrollVisible(windowSize: Int): TuiState {
        if (filteredIndexes.isEmpty()) {
            return copy(scrollOffset = 0)
        }

        if (windowSize <= 0) {
            return copy(scrollOffset = 0)
        }

        val boundedCursor = cursor.coerceIn(0, filteredIndexes.lastIndex)
        val maxScroll = (filteredIndexes.size - windowSize).coerceAtLeast(0)

        val adjustedOffset = when {
            boundedCursor < scrollOffset -> boundedCursor
            boundedCursor >= scrollOffset + windowSize -> boundedCursor - windowSize + 1
            else -> scrollOffset
        }.coerceIn(0, maxScroll)

        return copy(cursor = boundedCursor, scrollOffset = adjustedOffset)
    }

    private fun matchesQuickFilter(entry: TuiDependencyEntry): Boolean {
        return when (activeFilter) {
            TuiQuickFilter.DIRECT -> !entry.source.equals("transitive", ignoreCase = true)
            TuiQuickFilter.ALL -> true
            TuiQuickFilter.CVE -> entry.vulnerabilityCount > 0
            TuiQuickFilter.OUTDATED -> entry.outdatedCount > 0 || entry.latestVersion != null
            TuiQuickFilter.TRANSITIVE -> entry.transitiveTreeLines.any { it.startsWith("  +") }
        }
    }
}
