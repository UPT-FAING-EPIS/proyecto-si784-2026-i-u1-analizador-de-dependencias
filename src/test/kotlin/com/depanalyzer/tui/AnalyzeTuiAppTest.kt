package com.depanalyzer.tui

import com.depanalyzer.report.*
import com.depanalyzer.update.UpdateTargetType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnalyzeTuiAppTest {
    @Test
    fun `build entries includes direct update suggestion when latest version exists`() {
        val report = DependencyReport(
            projectName = "demo",
            dependencyTree = listOf(
                DependencyTreeNode(
                    groupId = "org.sample",
                    artifactId = "demo-lib",
                    currentVersion = "1.0.0",
                    latestVersion = "1.2.0",
                    isDirectDependency = true
                )
            )
        )

        val entries = AnalyzeTuiApp().buildEntries(report)

        assertEquals(1, entries.size)
        val suggestion = assertNotNull(entries.first().updateSuggestion)
        assertEquals("org.sample", suggestion.groupId)
        assertEquals("demo-lib", suggestion.artifactId)
        assertEquals("1.0.0", suggestion.currentVersion)
        assertEquals("1.2.0", suggestion.newVersion)
        assertEquals(UpdateTargetType.DIRECT, suggestion.targetType)
    }

    @Test
    fun `build entries marks vulnerable chain in tree lines`() {
        val vulnerableChild = DependencyTreeNode(
            groupId = "org.transitive",
            artifactId = "child-lib",
            currentVersion = "2.0.0",
            vulnerabilities = listOf(
                Vulnerability(
                    cveId = "CVE-2026-9999",
                    severity = VulnerabilitySeverity.HIGH,
                    cvssScore = 8.2,
                    description = "demo vulnerability",
                    affectedDependency = AffectedDependency("org.transitive", "child-lib", "2.0.0"),
                    source = VulnerabilitySource.OSS_INDEX,
                    retrievedAt = null,
                    referenceUrl = null
                )
            )
        )

        val report = DependencyReport(
            projectName = "demo",
            dependencyTree = listOf(
                DependencyTreeNode(
                    groupId = "org.direct",
                    artifactId = "root-lib",
                    currentVersion = "1.0.0",
                    isDirectDependency = true,
                    children = listOf(vulnerableChild)
                )
            )
        )

        val entry = AnalyzeTuiApp().buildEntries(report).first()

        assertTrue(entry.transitiveTreeLines.any { it.contains("[CHAIN]") })
        assertTrue(entry.transitiveTreeLines.any { it.contains("! CVE-2026-9999") })
    }

    @Test
    fun `merge loading entries keeps existing table when partial snapshot is empty`() {
        val app = AnalyzeTuiApp()
        val current = listOf(
            TuiDependencyEntry(coordinate = "org.sample:demo", currentVersion = "1.0.0")
        )

        val merged = app.mergeLoadingEntries(
            currentEntries = current,
            incomingEntries = emptyList(),
            isLoading = true
        )

        assertEquals(current, merged)
    }

    @Test
    fun `merge loading entries replaces matching coordinates and keeps stable order`() {
        val app = AnalyzeTuiApp()
        val current = listOf(
            TuiDependencyEntry(coordinate = "a:one", currentVersion = "1.0.0"),
            TuiDependencyEntry(coordinate = "b:two", currentVersion = "1.0.0")
        )
        val incoming = listOf(
            TuiDependencyEntry(coordinate = "b:two", currentVersion = "2.0.0"),
            TuiDependencyEntry(coordinate = "c:three", currentVersion = "1.0.0")
        )

        val merged = app.mergeLoadingEntries(
            currentEntries = current,
            incomingEntries = incoming,
            isLoading = true
        )

        assertEquals(listOf("a:one", "b:two", "c:three"), merged.map { it.coordinate })
        assertEquals("2.0.0", merged[1].currentVersion)
    }
}
