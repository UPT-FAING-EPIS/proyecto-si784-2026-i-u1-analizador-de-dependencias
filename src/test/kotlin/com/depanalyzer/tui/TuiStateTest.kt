package com.depanalyzer.tui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TuiStateTest {
    @Test
    fun `adjusts scroll offset to keep cursor visible`() {
        val entries = (1..20).map {
            TuiDependencyEntry(coordinate = "g:lib$it", currentVersion = "1.0.0")
        }

        val state = TuiState(
            entries = entries,
            summary = TuiSummary(
                projectName = "test",
                outdatedCount = 0,
                vulnerableCount = 0,
                totalEntries = entries.size
            ),
            cursor = 15,
            scrollOffset = 0
        )
            .ensureScrollVisible(windowSize = 8)

        assertEquals(8, state.scrollOffset)
        assertEquals(15, state.cursor)
    }

    @Test
    fun `cycles quick filter to transitive and filters correctly`() {
        val entries = listOf(
            TuiDependencyEntry(
                coordinate = "g:direct-a",
                currentVersion = "1.0.0",
                transitiveTreeLines = listOf("+ g:direct-a:1.0.0", "  + g:child:1.1.0")
            ),
            TuiDependencyEntry(
                coordinate = "g:direct-b",
                currentVersion = "1.0.0",
                transitiveTreeLines = listOf("+ g:direct-b:1.0.0")
            )
        )

        val state = TuiState(
            entries = entries,
            summary = TuiSummary(
                projectName = "test",
                outdatedCount = 0,
                vulnerableCount = 0,
                totalEntries = entries.size
            )
        )

        val transitiveFilterState = state
            .cycleFilter() // CVE
            .cycleFilter() // OUTDATED
            .cycleFilter() // TRANSITIVE

        assertEquals(TuiQuickFilter.TRANSITIVE, transitiveFilterState.activeFilter)
        assertEquals(1, transitiveFilterState.filteredIndexes.size)
        assertTrue(transitiveFilterState.filteredIndexes.contains(0))
    }
}
