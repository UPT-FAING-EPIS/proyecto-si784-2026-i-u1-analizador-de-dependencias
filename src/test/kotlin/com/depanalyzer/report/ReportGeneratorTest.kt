package com.depanalyzer.report

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.core.graph.VulnerabilityChain
import tools.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportGeneratorTest {

    private val generator = ReportGenerator()

    @Test
    fun `generates text report correctly`() {
        val affectedDep1 = AffectedDependency("com.h2database", "h2", "1.4.199")
        val affectedDep2 = AffectedDependency("org.yaml", "snakeyaml", "1.26")

        val report = DependencyReport(
            projectName = "TestProject",
            upToDate = listOf(DependencyInfo("org.slf4j", "slf4j-api", "2.0.13")),
            outdated = listOf(OutdatedDependency("junit", "junit", "4.12", "4.13.2")),
            directVulnerable = listOf(
                VulnerableDependency(
                    "com.h2database", "h2", "1.4.199",
                    listOf(
                        Vulnerability(
                            cveId = "CVE-2021-23463",
                            severity = VulnerabilitySeverity.CRITICAL,
                            cvssScore = 9.8,
                            description = "Remote Code Execution",
                            affectedDependency = affectedDep1,
                            source = VulnerabilitySource.OSS_INDEX,
                            retrievedAt = Instant.now(),
                            referenceUrl = null
                        )
                    )
                )
            ),
            transitiveVulnerable = listOf(
                VulnerableDependency(
                    "org.yaml", "snakeyaml", "1.26",
                    listOf(
                        Vulnerability(
                            cveId = "CVE-2022-25857",
                            severity = VulnerabilitySeverity.HIGH,
                            cvssScore = 7.5,
                            description = "Denial of Service",
                            affectedDependency = affectedDep2,
                            source = VulnerabilitySource.OSS_INDEX,
                            retrievedAt = Instant.now(),
                            referenceUrl = null
                        )
                    ),
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

    @Test
    fun `json report omits dependency tree derived getters`() {
        val report = DependencyReport(
            projectName = "TreeProject",
            dependencyTree = listOf(
                DependencyTreeNode(
                    groupId = "org.example",
                    artifactId = "direct",
                    currentVersion = "1.0.0",
                    latestVersion = "1.1.0",
                    isDirectDependency = true,
                    children = listOf(
                        DependencyTreeNode(
                            groupId = "org.example",
                            artifactId = "transitive",
                            currentVersion = "2.0.0"
                        )
                    )
                )
            )
        )

        val json = generator.toJson(report)

        assertTrue(json.contains("\"dependencyTree\" : ["))
        assertTrue(!json.contains("problematicDescendants"))
        assertTrue(!json.contains("hasProblems"))
        assertTrue(!json.contains("depth"))
    }

    @Test
    fun `serializes schema metadata tree chains and locations without cycles`() {
        val vulnerability = Vulnerability(
            cveId = "CVE-2026-0001",
            severity = VulnerabilitySeverity.HIGH,
            cvssScore = 8.1,
            description = "Quoted \"description\"",
            affectedDependency = AffectedDependency("org.example", "child", "1.0.0"),
            source = VulnerabilitySource.BOTH,
            retrievedAt = Instant.parse("2026-06-26T12:00:00Z"),
            referenceUrl = "https://example.test/CVE-2026-0001"
        )
        val rootNode = DependencyNode("root", "org.example", "root", "2.0.0")
        val childNode = DependencyNode(
            "child",
            "org.example",
            "child",
            "1.0.0",
            parent = rootNode,
            vulnerabilities = listOf(vulnerability)
        )
        rootNode.addChild(childNode)

        val report = DependencyReport(
            projectName = "full",
            directVulnerable = listOf(
                VulnerableDependency(
                    groupId = "org.example",
                    artifactId = "child",
                    version = "1.0.0",
                    vulnerabilities = listOf(vulnerability),
                    sourceLocation = DependencySourceLocation("pom.xml", 10, 5, 20)
                )
            ),
            vulnerabilityChains = listOf(
                VulnerabilityChain(
                    chain = listOf(rootNode, childNode),
                    vulnerabilities = listOf(vulnerability),
                    isShortestPath = true
                )
            ),
            dependencyTree = listOf(
                DependencyTreeNode(
                    groupId = "org.example",
                    artifactId = "root",
                    currentVersion = "2.0.0",
                    isDirectDependency = true,
                    children = listOf(
                        DependencyTreeNode(
                            groupId = "org.example",
                            artifactId = "child",
                            currentVersion = "1.0.0",
                            vulnerabilities = listOf(vulnerability)
                        )
                    )
                )
            ),
            analysis = AnalysisMetadata(
                requestedMode = AnalysisMode.DYNAMIC,
                actualMode = AnalysisMode.DYNAMIC,
                projectType = "MAVEN",
                ecosystems = listOf("MAVEN"),
                durationMs = 1250,
                providers = ProviderAnalysisMetadata(
                    requested = "AUTO",
                    used = listOf("OSS_INDEX", "NVD"),
                    statuses = mapOf("OSS_INDEX" to "AVAILABLE", "NVD" to "AVAILABLE")
                )
            )
        )

        val root = JsonMapper.builder().build().readTree(generator.toJson(report))

        assertEquals("1.1", root.path("schemaVersion").asText())
        assertEquals("pom.xml", root.path("directVulnerable").get(0).path("sourceLocation").path("file").asText())
        assertEquals("child", root.path("vulnerabilityChains").get(0).path("chain").get(1).path("id").asText())
        assertEquals("child", root.path("dependencyTree").get(0).path("children").get(0).path("artifactId").asText())
        assertEquals("DYNAMIC", root.path("analysis").path("actualMode").asText())
        assertEquals("MAVEN", root.path("analysis").path("ecosystems").get(0).asText())
        assertEquals("OSS_INDEX", root.path("analysis").path("providers").path("used").get(0).asText())
        assertEquals("AVAILABLE", root.path("analysis").path("providers").path("statuses").path("OSS_INDEX").asText())
    }
}
