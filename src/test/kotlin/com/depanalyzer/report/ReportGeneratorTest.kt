package com.depanalyzer.report

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ReportGeneratorTest {

    private val generator = ReportGenerator()

    @Test
    fun `generates text report correctly`() {
        val report = DependencyReport(
            projectName = "TestProject",
            upToDate = listOf(DependencyInfo("org.slf4j", "slf4j-api", "2.0.13")),
            outdated = listOf(OutdatedDependency("junit", "junit", "4.12", "4.13.2")),
            directVulnerable = listOf(
                VulnerableDependency(
                    "com.h2database", "h2", "1.4.199",
                    listOf(Vulnerability("CVE-2021-23463", "Remote Code Execution", "...", 9.8, VulnerabilitySeverity.CRITICAL))
                )
            ),
            transitiveVulnerable = listOf(
                VulnerableDependency(
                    "org.yaml", "snakeyaml", "1.26",
                    listOf(Vulnerability("CVE-2022-25857", "Denial of Service", "...", 7.5, VulnerabilitySeverity.HIGH)),
                    dependencyChain = listOf("direct-dep", "snakeyaml")
                )
            )
        )

        val text = generator.toText(report)

        assertTrue(text.contains("TestProject"))
        assertTrue(text.contains("VULNERABILIDADES DETECTADAS"))
        assertTrue(text.contains("CVE-2021-23463"))
        assertTrue(text.contains("junit:junit: 4.12 -> 4.13.2"))
        assertTrue(text.contains("Ruta: direct-dep -> snakeyaml"))
        assertTrue(text.contains("Al día: 1"))
    }

    @Test
    fun `generates json report correctly`() {
        val report = DependencyReport(
            projectName = "TestProject",
            upToDate = listOf(DependencyInfo("g", "a", "1.0"))
        )

        val json = generator.toJson(report)

        assertTrue(json.contains("\"projectName\" : \"TestProject\""))
        assertTrue(json.contains("\"upToDate\" : ["))
    }
}
