package com.depanalyzer.report

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class ConsoleRendererTest {

    @Test
    fun `does not crash when rendering report`() {
        val report = DependencyReport(
            projectName = "Test",
            upToDate = listOf(DependencyInfo("g", "a", "1")),
            outdated = listOf(OutdatedDependency("g2", "a2", "1", "2")),
            directVulnerable = listOf(
                VulnerableDependency("g3", "a3", "1", listOf(
                    Vulnerability("V1", "Title", "Desc", 10.0, VulnerabilitySeverity.CRITICAL)
                ))
            )
        )
        
        val renderer = ConsoleRenderer(noColor = true)
        renderer.render(report)
        assertNotNull(renderer)
    }
}
