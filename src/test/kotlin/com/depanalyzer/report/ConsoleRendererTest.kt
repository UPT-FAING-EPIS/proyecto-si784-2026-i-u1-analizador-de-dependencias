package com.depanalyzer.report

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertNotNull

class ConsoleRendererTest {

    @Test
    fun `does not crash when rendering report`() {
        val affectedDependency = AffectedDependency("g3", "a3", "1")
        val report = DependencyReport(
            projectName = "Test",
            upToDate = listOf(DependencyInfo("g", "a", "1")),
            outdated = listOf(OutdatedDependency("g2", "a2", "1", "2")),
            directVulnerable = listOf(
                VulnerableDependency(
                    "g3", "a3", "1", listOf(
                        Vulnerability(
                            cveId = "CVE-2024-00001",
                            severity = VulnerabilitySeverity.CRITICAL,
                            cvssScore = 10.0,
                            description = "Critical vulnerability",
                            affectedDependency = affectedDependency,
                            source = VulnerabilitySource.OSS_INDEX,
                            retrievedAt = Instant.now(),
                            referenceUrl = "https://example.com"
                        )
                    )
                )
            )
        )

        val renderer = ConsoleRenderer(noColor = true)
        renderer.render(report)
        assertNotNull(renderer)
    }

    @Test
    fun `does not crash when rendering verbose report`() {
        val affectedDependency = AffectedDependency("g3", "a3", "1")
        val report = DependencyReport(
            projectName = "Test",
            upToDate = listOf(DependencyInfo("g", "a", "1")),
            outdated = listOf(OutdatedDependency("g2", "a2", "1", "2")),
            directVulnerable = listOf(
                VulnerableDependency(
                    "g3", "a3", "1", listOf(
                        Vulnerability(
                            cveId = "CVE-2024-00001",
                            severity = VulnerabilitySeverity.CRITICAL,
                            cvssScore = 10.0,
                            description = "Critical vulnerability",
                            affectedDependency = affectedDependency,
                            source = VulnerabilitySource.OSS_INDEX,
                            retrievedAt = Instant.now(),
                            referenceUrl = "https://example.com"
                        )
                    )
                )
            )
        )

        val renderer = ConsoleRenderer(noColor = true)
        renderer.renderVerbose(report)
        assertNotNull(renderer)
    }
}
