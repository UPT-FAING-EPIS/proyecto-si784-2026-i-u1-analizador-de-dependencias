package com.depanalyzer.report

import com.depanalyzer.core.graph.DependencyNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyTreeBuilderTest {

    @Test
    fun `builds tree with only nodes that have problems`() {
        val rootNode = DependencyNode(
            id = "org.example:root:1.0",
            groupId = "org.example",
            artifactId = "root",
            version = "1.0"
        )

        val childWithProblem = DependencyNode(
            id = "org.example:child-vulnerable:1.0",
            groupId = "org.example",
            artifactId = "child-vulnerable",
            version = "1.0",
            parent = rootNode
        )

        val childClean = DependencyNode(
            id = "org.example:child-clean:1.0",
            groupId = "org.example",
            artifactId = "child-clean",
            version = "1.0",
            parent = rootNode
        )

        rootNode.addChild(childWithProblem)
        rootNode.addChild(childClean)

        val vulnerability = Vulnerability(
            cveId = "CVE-2024-12345",
            severity = VulnerabilitySeverity.CRITICAL,
            cvssScore = 9.8,
            description = "Test vulnerability",
            affectedDependency = AffectedDependency("org.example", "child-vulnerable", "1.0"),
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = null,
            referenceUrl = null
        )

        val vulnerabilities = mapOf(
            "org.example:child-vulnerable:1.0" to listOf(vulnerability)
        )

        val directDeps = setOf("org.example:root:1.0")

        val builder = DependencyTreeBuilder(
            vulnerabilities = vulnerabilities,
            outdatedMap = emptyMap()
        )

        val tree = builder.buildTree(listOf(rootNode))

        assertEquals(1, tree.size)
        val rootTreeNode = tree[0]
        assertEquals("org.example", rootTreeNode.groupId)
        assertEquals("root", rootTreeNode.artifactId)
        assertEquals("1.0", rootTreeNode.currentVersion)
        assertTrue(rootTreeNode.isDirectDependency)

        assertEquals(1, rootTreeNode.children.size)
        val childTreeNode = rootTreeNode.children[0]
        assertEquals("org.example", childTreeNode.groupId)
        assertEquals("child-vulnerable", childTreeNode.artifactId)
        assertEquals(1, childTreeNode.vulnerabilities.size)
    }

    @Test
    fun `orders nodes with outdated versions first`() {
        val rootNode = DependencyNode(
            id = "org.example:root:1.0",
            groupId = "org.example",
            artifactId = "root",
            version = "1.0"
        )

        val outdatedChild = DependencyNode(
            id = "org.example:outdated:1.0",
            groupId = "org.example",
            artifactId = "outdated",
            version = "1.0",
            parent = rootNode
        )

        val vulnerableChild = DependencyNode(
            id = "org.example:vulnerable:1.0",
            groupId = "org.example",
            artifactId = "vulnerable",
            version = "1.0",
            parent = rootNode
        )

        rootNode.addChild(outdatedChild)
        rootNode.addChild(vulnerableChild)

        val vulnerability = Vulnerability(
            cveId = "CVE-2024-12345",
            severity = VulnerabilitySeverity.HIGH,
            cvssScore = 7.5,
            description = "Test vulnerability",
            affectedDependency = AffectedDependency("org.example", "vulnerable", "1.0"),
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = null,
            referenceUrl = null
        )

        val vulnerabilities = mapOf(
            "org.example:vulnerable:1.0" to listOf(vulnerability)
        )

        val outdated = mapOf(
            "org.example:outdated:1.0" to OutdatedDependency("org.example", "outdated", "1.0", "2.0")
        )

        val directDeps = setOf("org.example:root:1.0")

        val builder = DependencyTreeBuilder(
            vulnerabilities = vulnerabilities,
            outdatedMap = outdated
        )

        val tree = builder.buildTree(listOf(rootNode))

        assertEquals(1, tree.size)
        val rootTreeNode = tree[0]
        assertEquals(2, rootTreeNode.children.size)

        assertEquals("outdated", rootTreeNode.children[0].artifactId)
        assertEquals("vulnerable", rootTreeNode.children[1].artifactId)
    }

    @Test
    fun `respects tree depth limit`() {
        val level0 = DependencyNode(
            id = "org.example:level0:1.0",
            groupId = "org.example",
            artifactId = "level0",
            version = "1.0"
        )

        val level1 = DependencyNode(
            id = "org.example:level1:1.0",
            groupId = "org.example",
            artifactId = "level1",
            version = "1.0",
            parent = level0,
            vulnerabilities = listOf(
                Vulnerability(
                    cveId = "CVE-2024-12345",
                    severity = VulnerabilitySeverity.MEDIUM,
                    cvssScore = 5.0,
                    description = "Test",
                    affectedDependency = AffectedDependency("org.example", "level1", "1.0"),
                    source = VulnerabilitySource.OSS_INDEX,
                    retrievedAt = null,
                    referenceUrl = null
                )
            )
        )

        val level2 = DependencyNode(
            id = "org.example:level2:1.0",
            groupId = "org.example",
            artifactId = "level2",
            version = "1.0",
            parent = level1,
            vulnerabilities = listOf(
                Vulnerability(
                    cveId = "CVE-2024-67890",
                    severity = VulnerabilitySeverity.CRITICAL,
                    cvssScore = 9.0,
                    description = "Test",
                    affectedDependency = AffectedDependency("org.example", "level2", "1.0"),
                    source = VulnerabilitySource.OSS_INDEX,
                    retrievedAt = null,
                    referenceUrl = null
                )
            )
        )

        level0.addChild(level1)
        level1.addChild(level2)

        val vulnerabilities = mapOf(
            "org.example:level1:1.0" to level1.vulnerabilities,
            "org.example:level2:1.0" to level2.vulnerabilities
        )

        val directDeps = setOf("org.example:level0:1.0")

        val builder = DependencyTreeBuilder(
            vulnerabilities = vulnerabilities,
            outdatedMap = emptyMap()
        )

        val tree = builder.buildTree(listOf(level0), maxDepth = 1)

        assertEquals(1, tree.size)
        val rootNode = tree[0]
        assertEquals(1, rootNode.children.size)
        assertEquals("level1", rootNode.children[0].artifactId)
        assertEquals(0, rootNode.children[0].children.size)
    }

    @Test
    fun `handles empty dependency tree`() {
        val directDeps = setOf("org.example:root:1.0")

        val builder = DependencyTreeBuilder(
            vulnerabilities = emptyMap(),
            outdatedMap = emptyMap()
        )

        val tree = builder.buildTree(emptyList())

        assertEquals(0, tree.size)
    }
}
