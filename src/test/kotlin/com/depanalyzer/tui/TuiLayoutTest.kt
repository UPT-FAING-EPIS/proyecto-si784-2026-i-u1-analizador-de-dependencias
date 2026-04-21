package com.depanalyzer.tui

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class TuiLayoutTest {
    @Test
    fun `composes two panel layout with dependency list and detail`() {
        val layout = TuiLayout()
        val state = TuiState(
            entries = listOf(
                TuiDependencyEntry(
                    coordinate = "org.sample:demo",
                    currentVersion = "1.0.0",
                    latestVersion = "1.1.0",
                    vulnerabilityCount = 2,
                    source = "outdated"
                )
            ),
            summary = TuiSummary(
                projectName = "sample",
                outdatedCount = 1,
                vulnerableCount = 1,
                totalEntries = 1
            )
        )

        val frame = layout.composeFrame(state)

        assertTrue(frame.any { it.contains("DEPENDENCIAS") })
        assertTrue(frame.any { it.contains("· 1 CVE · 1 desact.") })
        assertTrue(frame.any { it.contains("Detalle") })
        assertTrue(frame.any { it.contains("Filtro (f):") })
        assertTrue(frame.any { it.contains("org.sample:demo") })
        assertTrue(frame.any { it.contains("DEPENDENCIA SELECCIONADA") })
        assertTrue(frame.none { it.contains("Escaneo") })
    }

    @Test
    fun `calculates responsive dimensions from terminal size`() {
        val layout = TuiLayout()

        val dimensions = layout.calculateDimensions(width = 100, height = 30)

        assertTrue(dimensions.leftInnerWidth > 0)
        assertTrue(dimensions.rightInnerWidth > 0)
        assertTrue(dimensions.contentRows > 0)
    }

    @Test
    fun `shows disabled tree tab message when transitive tree is unavailable`() {
        val layout = TuiLayout()
        val state = TuiState(
            entries = listOf(
                TuiDependencyEntry(
                    coordinate = "org.sample:demo",
                    currentVersion = "1.0.0"
                )
            ),
            summary = TuiSummary(
                projectName = "sample",
                outdatedCount = 0,
                vulnerableCount = 0,
                totalEntries = 1
            ),
            isTreeTabEnabled = false,
            treeUnavailableMessage = "No se pudo cargar el arbol transitivo"
        )

        val frame = layout.composeFrame(state)

        assertTrue(frame.any { it.contains("desactivado") })
        assertTrue(frame.any { it.contains("No se pudo cargar el arbol transitivo") })
    }

    @Test
    fun `keeps main table layout while loading with empty entries`() {
        val layout = TuiLayout()
        val state = TuiState(
            entries = emptyList(),
            summary = TuiSummary(
                projectName = "sample",
                outdatedCount = 0,
                vulnerableCount = 0,
                totalEntries = 0
            ),
            isLoading = true,
            loadingMessage = "Escaneo en progreso..."
        )

        val frame = layout.composeFrame(state)

        assertTrue(frame.any { it.contains("DEPENDENCIAS") })
        assertTrue(frame.any { it.contains("No hay dependencias") })
        assertTrue(frame.none { it.contains("Presiona q para salir") })
    }
}
