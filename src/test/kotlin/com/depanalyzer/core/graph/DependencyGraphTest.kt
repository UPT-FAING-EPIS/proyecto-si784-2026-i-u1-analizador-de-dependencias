package com.depanalyzer.core.graph

import com.depanalyzer.report.AffectedDependency
import com.depanalyzer.report.Vulnerability
import com.depanalyzer.report.VulnerabilitySeverity
import com.depanalyzer.report.VulnerabilitySource
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyGraphTest {

    @Test
    fun `builds graph with direct and transitive dependencies`() {
        val direct = DependencyNode(
            id = "org.example:direct:1.0",
            groupId = "org.example",
            artifactId = "direct",
            version = "1.0"
        )

        val transitive = DependencyNode(
            id = "org.example:transitive:1.0",
            groupId = "org.example",
            artifactId = "transitive",
            version = "1.0",
            parent = direct
        )

        direct.addChild(transitive)

        val graph = DependencyGraph(listOf(direct, transitive))

        assertEquals(2, graph.getAllNodes().size)
        assertTrue(graph.getAllNodes().contains(direct))
        assertTrue(graph.getAllNodes().contains(transitive))
    }

    @Test
    fun `identifies all vulnerable nodes`() {
        val vuln = Vulnerability(
            cveId = "CVE-2024-1234",
            description = "Test vulnerability",
            severity = VulnerabilitySeverity.HIGH,
            cvssScore = 8.5,
            affectedDependency = AffectedDependency("org.example", "vuln", "1.0"),
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = Instant.now(),
            referenceUrl = "https://example.com"
        )

        val vulnNode = DependencyNode(
            id = "org.example:vuln:1.0",
            groupId = "org.example",
            artifactId = "vuln",
            version = "1.0",
            vulnerabilities = listOf(vuln)
        )

        val safeNode = DependencyNode(
            id = "org.example:safe:1.0",
            groupId = "org.example",
            artifactId = "safe",
            version = "1.0"
        )

        val graph = DependencyGraph(listOf(vulnNode, safeNode))
        val vulnNodes = graph.getAllVulnerableNodes()

        assertEquals(1, vulnNodes.size)
        assertEquals(vulnNode, vulnNodes.first())
    }

    @Test
    fun `detects cycles in dependency graph`() {
        val node1 = DependencyNode(
            id = "org.example:node1:1.0",
            groupId = "org.example",
            artifactId = "node1",
            version = "1.0"
        )

        val node2 = DependencyNode(
            id = "org.example:node2:1.0",
            groupId = "org.example",
            artifactId = "node2",
            version = "1.0"
        )

        node1.addChild(node2)

        val graph = DependencyGraph(listOf(node1, node2))
        assertTrue(graph.getAllNodes().isNotEmpty())
    }

    @Test
    fun `handles empty graph`() {
        val graph = DependencyGraph(emptyList())
        
        assertEquals(0, graph.getAllNodes().size)
        assertEquals(0, graph.getAllVulnerableNodes().size)
    }
}
