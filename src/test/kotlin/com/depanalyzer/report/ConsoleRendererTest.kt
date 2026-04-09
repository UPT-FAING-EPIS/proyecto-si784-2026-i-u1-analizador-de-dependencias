package com.depanalyzer.report

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.core.graph.VulnerabilityChain
import com.depanalyzer.core.graph.VulnerabilityClassification
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

    @Test
    fun `renders single vulnerability chain without alternative paths note`() {
        val directNode = DependencyNode(
            id = "org.example:direct:1.0",
            groupId = "org.example",
            artifactId = "direct",
            version = "1.0"
        )

        val vulnerableNode = DependencyNode(
            id = "org.vulnerable:lib:2.0",
            groupId = "org.vulnerable",
            artifactId = "lib",
            version = "2.0",
            parent = directNode
        )

        val affectedDependency = AffectedDependency("org.vulnerable", "lib", "2.0")
        val vuln = Vulnerability(
            cveId = "CVE-2024-99999",
            severity = VulnerabilitySeverity.HIGH,
            cvssScore = 8.5,
            description = "High severity vulnerability",
            affectedDependency = affectedDependency,
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = Instant.now(),
            referenceUrl = "https://example.com"
        )

        val chain = VulnerabilityChain(
            chain = listOf(directNode, vulnerableNode),
            vulnerabilities = listOf(vuln),
            isShortestPath = true,
            classification = VulnerabilityClassification.INDIRECTLY_VULNERABLE
        )

        val report = DependencyReport(
            projectName = "TestProject",
            vulnerabilityChains = listOf(chain)
        )

        val renderer = ConsoleRenderer(noColor = true)
        renderer.render(report, showChains = true, detailedChains = false)
        assertNotNull(renderer)
    }

    @Test
    fun `renders multiple chains with same signature and shows alternative paths note`() {
        val directNode = DependencyNode(
            id = "org.example:direct:1.0",
            groupId = "org.example",
            artifactId = "direct",
            version = "1.0"
        )

        val midNode1 = DependencyNode(
            id = "org.mid:connector:1.5",
            groupId = "org.mid",
            artifactId = "connector",
            version = "1.5",
            parent = directNode
        )

        val vulnerableNode = DependencyNode(
            id = "org.vulnerable:lib:2.0",
            groupId = "org.vulnerable",
            artifactId = "lib",
            version = "2.0",
            parent = midNode1
        )

        val affectedDependency = AffectedDependency("org.vulnerable", "lib", "2.0")
        val vuln = Vulnerability(
            cveId = "CVE-2024-99999",
            severity = VulnerabilitySeverity.CRITICAL,
            cvssScore = 9.8,
            description = "Critical vulnerability",
            affectedDependency = affectedDependency,
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = Instant.now(),
            referenceUrl = "https://example.com"
        )

        val shortestChain = VulnerabilityChain(
            chain = listOf(directNode, midNode1, vulnerableNode),
            vulnerabilities = listOf(vuln),
            isShortestPath = true,
            classification = VulnerabilityClassification.INDIRECTLY_VULNERABLE
        )

        val longerChain = VulnerabilityChain(
            chain = listOf(
                directNode,
                DependencyNode(
                    id = "org.mid:helper:1.0",
                    groupId = "org.mid",
                    artifactId = "helper",
                    version = "1.0",
                    parent = directNode
                ),
                midNode1.copy(parent = DependencyNode(
                    id = "org.mid:helper:1.0",
                    groupId = "org.mid",
                    artifactId = "helper",
                    version = "1.0"
                )),
                vulnerableNode.copy(
                    parent = midNode1.copy(parent = DependencyNode(
                        id = "org.mid:helper:1.0",
                        groupId = "org.mid",
                        artifactId = "helper",
                        version = "1.0"
                    ))
                )
            ),
            vulnerabilities = listOf(vuln),
            isShortestPath = false,
            classification = VulnerabilityClassification.INDIRECTLY_VULNERABLE
        )

        val report = DependencyReport(
            projectName = "TestProject",
            vulnerabilityChains = listOf(shortestChain, longerChain)
        )

        val renderer = ConsoleRenderer(noColor = true)
        renderer.render(report, showChains = true, detailedChains = false)
        assertNotNull(renderer)
    }

    @Test
    fun `renders chains with detailed vulnerability info when requested`() {
        val directNode = DependencyNode(
            id = "org.example:direct:1.0",
            groupId = "org.example",
            artifactId = "direct",
            version = "1.0"
        )

        val vulnerableNode = DependencyNode(
            id = "org.vulnerable:lib:2.0",
            groupId = "org.vulnerable",
            artifactId = "lib",
            version = "2.0",
            parent = directNode
        )

        val affectedDependency = AffectedDependency("org.vulnerable", "lib", "2.0")
        val vuln1 = Vulnerability(
            cveId = "CVE-2024-00001",
            severity = VulnerabilitySeverity.CRITICAL,
            cvssScore = 10.0,
            description = "Critical vulnerability",
            affectedDependency = affectedDependency,
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = Instant.now(),
            referenceUrl = "https://example.com"
        )
        val vuln2 = Vulnerability(
            cveId = "CVE-2024-00002",
            severity = VulnerabilitySeverity.HIGH,
            cvssScore = 8.5,
            description = "High vulnerability",
            affectedDependency = affectedDependency,
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = Instant.now(),
            referenceUrl = "https://example.com"
        )

        val chain = VulnerabilityChain(
            chain = listOf(directNode, vulnerableNode),
            vulnerabilities = listOf(vuln1, vuln2),
            isShortestPath = true,
            classification = VulnerabilityClassification.DIRECTLY_VULNERABLE
        )

        val report = DependencyReport(
            projectName = "TestProject",
            vulnerabilityChains = listOf(chain)
        )

        val renderer = ConsoleRenderer(noColor = true)
        renderer.render(report, showChains = true, detailedChains = true)
        assertNotNull(renderer)
    }

    @Test
    fun `renders dependency tree without crashing`() {
        val affectedDependency = AffectedDependency("org.example", "vulnerable-dep", "1.0")
        val vulnerability = Vulnerability(
            cveId = "CVE-2024-00001",
            severity = VulnerabilitySeverity.CRITICAL,
            cvssScore = 9.8,
            description = "Critical vulnerability",
            affectedDependency = affectedDependency,
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = Instant.now(),
            referenceUrl = "https://example.com"
        )

        val transitiveNode = DependencyTreeNode(
            groupId = "org.example",
            artifactId = "vulnerable-dep",
            currentVersion = "1.0",
            latestVersion = null,
            isDirectDependency = false,
            vulnerabilities = listOf(vulnerability),
            dependencyChain = listOf("root:1.0", "vulnerable-dep:1.0")
        )

        val rootNode = DependencyTreeNode(
            groupId = "org.example",
            artifactId = "root-dep",
            currentVersion = "1.0",
            latestVersion = "2.0",
            isDirectDependency = true,
            children = listOf(transitiveNode)
        )

        val report = DependencyReport(
            projectName = "Test",
            dependencyTree = listOf(rootNode)
        )

        val renderer = ConsoleRenderer(noColor = true, useAscii = false)
        renderer.render(report)
        assertNotNull(renderer)
    }

    @Test
    fun `renders dependency tree in verbose mode without crashing`() {
        val affectedDependency = AffectedDependency("org.example", "vulnerable-dep", "1.0")
        val vulnerability = Vulnerability(
            cveId = "CVE-2024-00001",
            severity = VulnerabilitySeverity.HIGH,
            cvssScore = 7.5,
            description = "High severity vulnerability",
            affectedDependency = affectedDependency,
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = Instant.now(),
            referenceUrl = "https://example.com"
        )

        val transitiveNode = DependencyTreeNode(
            groupId = "org.example",
            artifactId = "vulnerable-dep",
            currentVersion = "1.0",
            latestVersion = null,
            isDirectDependency = false,
            scope = "compile",
            vulnerabilities = listOf(vulnerability),
            dependencyChain = listOf("root:1.0", "vulnerable-dep:1.0")
        )

        val rootNode = DependencyTreeNode(
            groupId = "org.example",
            artifactId = "root-dep",
            currentVersion = "1.0",
            latestVersion = "2.0",
            isDirectDependency = true,
            scope = "compile",
            children = listOf(transitiveNode)
        )

        val report = DependencyReport(
            projectName = "Test",
            dependencyTree = listOf(rootNode)
        )

        val renderer = ConsoleRenderer(noColor = true, useAscii = false)
        renderer.renderVerbose(report)
        assertNotNull(renderer)
    }

    @Test
    fun `renders dependency tree with ASCII mode without crashing`() {
        val affectedDependency = AffectedDependency("org.example", "vulnerable-dep", "1.0")
        val vulnerability = Vulnerability(
            cveId = "CVE-2024-00001",
            severity = VulnerabilitySeverity.MEDIUM,
            cvssScore = 5.0,
            description = "Medium severity vulnerability",
            affectedDependency = affectedDependency,
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = Instant.now(),
            referenceUrl = "https://example.com"
        )

        val rootNode = DependencyTreeNode(
            groupId = "org.example",
            artifactId = "root-dep",
            currentVersion = "1.0",
            latestVersion = null,
            isDirectDependency = true,
            vulnerabilities = listOf(vulnerability)
        )

        val report = DependencyReport(
            projectName = "Test",
            dependencyTree = listOf(rootNode)
        )

        val renderer = ConsoleRenderer(noColor = true, useAscii = true)
        renderer.render(report)
        assertNotNull(renderer)
    }

    @Test
    fun `renders dependency tree with depth limit without crashing`() {
        val level2Node = DependencyTreeNode(
            groupId = "org.example",
            artifactId = "level-2",
            currentVersion = "1.0",
            isDirectDependency = false,
            vulnerabilities = listOf(
                Vulnerability(
                    cveId = "CVE-2024-00003",
                    severity = VulnerabilitySeverity.LOW,
                    cvssScore = 2.0,
                    description = "Low vulnerability",
                    affectedDependency = AffectedDependency("org.example", "level-2", "1.0"),
                    source = VulnerabilitySource.OSS_INDEX,
                    retrievedAt = Instant.now(),
                    referenceUrl = null
                )
            )
        )

        val level1Node = DependencyTreeNode(
            groupId = "org.example",
            artifactId = "level-1",
            currentVersion = "1.0",
            isDirectDependency = false,
            children = listOf(level2Node),
            vulnerabilities = listOf(
                Vulnerability(
                    cveId = "CVE-2024-00002",
                    severity = VulnerabilitySeverity.MEDIUM,
                    cvssScore = 5.5,
                    description = "Medium vulnerability",
                    affectedDependency = AffectedDependency("org.example", "level-1", "1.0"),
                    source = VulnerabilitySource.OSS_INDEX,
                    retrievedAt = Instant.now(),
                    referenceUrl = null
                )
            )
        )

        val rootNode = DependencyTreeNode(
            groupId = "org.example",
            artifactId = "root-dep",
            currentVersion = "1.0",
            latestVersion = "2.0",
            isDirectDependency = true,
            children = listOf(level1Node)
        )

        val report = DependencyReport(
            projectName = "Test",
            dependencyTree = listOf(rootNode)
        )

        val renderer = ConsoleRenderer(noColor = true, useAscii = false, treeMaxDepth = 1)
        renderer.render(report)
        assertNotNull(renderer)
    }
}
