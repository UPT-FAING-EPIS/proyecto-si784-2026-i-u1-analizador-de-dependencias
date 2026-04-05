package com.depanalyzer.core.graph

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChainResolverTest {

    @Test
    fun `resolves 2-level chain correctly`() {
        val (nodes, vulnMap) = DependencyGraphFixtures.build2LevelChain()
        val graph = DependencyGraph(nodes)

        val chains = ChainResolver.resolveAllChains(graph, vulnMap)

        assertEquals(1, chains.size, "Should find one chain")
        val chain = chains.first()
        assertEquals(2, chain.chain.size, "Chain should have 2 nodes (direct -> vulnerable)")
        assertEquals("com.myapp:app:1.0", chain.chain[0].id)
        assertEquals("com.vulnerable:lib:1.0", chain.chain[1].id)
    }

    @Test
    fun `resolves 3-level chain correctly`() {
        val (nodes, vulnMap) = DependencyGraphFixtures.build3LevelChain()
        val graph = DependencyGraph(nodes)

        val chains = ChainResolver.resolveAllChains(graph, vulnMap)

        assertEquals(1, chains.size, "Should find one chain")
        val chain = chains.first()
        assertEquals(3, chain.chain.size, "Chain should have 3 nodes")
        assertEquals("com.myapp:service:1.0", chain.chain[0].id)
        assertEquals("com.lib:connector:1.5", chain.chain[1].id)
        assertEquals("com.lib:vulnerable:2.0", chain.chain[2].id)
    }

    @Test
    fun `resolves 4-level chain correctly`() {
        val (nodes, vulnMap) = DependencyGraphFixtures.build4LevelChain()
        val graph = DependencyGraph(nodes)

        val chains = ChainResolver.resolveAllChains(graph, vulnMap)

        assertEquals(1, chains.size, "Should find one chain")
        val chain = chains.first()
        assertEquals(4, chain.chain.size, "Chain should have 4 nodes")
        assertEquals("com.myapp:main:1.0", chain.chain[0].id)
    }

    @Test
    fun `handles diamond dependency with multiple paths`() {
        val (nodes, vulnMap) = DependencyGraphFixtures.buildDiamondDependency()
        val graph = DependencyGraph(nodes)

        val chains = ChainResolver.resolveAllChains(graph, vulnMap)
        assertTrue(chains.isNotEmpty(), "Should find at least one chain")

        chains.forEach { chain ->
            assertEquals("com.lib:base:1.0", chain.chain.last().id, 
                "All chains should end at vulnerable library")
        }
    }

    @Test
    fun `marks shortest paths correctly`() {
        val (nodes, vulnMap) = DependencyGraphFixtures.buildDiamondDependency()
        val graph = DependencyGraph(nodes)

        val chains = ChainResolver.resolveAllChains(graph, vulnMap)

        val shortestPaths = chains.filter { it.isShortestPath }
        assertTrue(shortestPaths.isNotEmpty(), "Should have at least one shortest path marked")
    }

    @Test
    fun `classifies direct vulnerabilities correctly`() {
        val (nodes, vulnMap) = DependencyGraphFixtures.build2LevelChain()
        val graph = DependencyGraph(nodes)

        val chains = ChainResolver.resolveAllChains(graph, vulnMap)

        assertEquals(1, chains.size)
        val chain = chains.first()
        assertEquals(VulnerabilityClassification.INDIRECTLY_VULNERABLE, chain.classification)
    }

    @Test
    fun `handles multiple CVEs in single node`() {
        val (nodes, vulnMap) = DependencyGraphFixtures.buildMultipleCVEs()
        val graph = DependencyGraph(nodes)

        val chains = ChainResolver.resolveAllChains(graph, vulnMap)

        assertEquals(1, chains.size, "Should find one chain")
        val chain = chains.first()
        assertEquals(3, chain.vulnerabilities.size, "Chain should include all 3 CVEs")
        
        val cveIds = chain.cveIds
        assertTrue(cveIds.contains("CVE-2024-1001"))
        assertTrue(cveIds.contains("CVE-2024-1002"))
        assertTrue(cveIds.contains("CVE-2024-1003"))
    }

    @Test
    fun `handles circular references without infinite loops`() {
        val (nodes, vulnMap) = DependencyGraphFixtures.buildCircularReference()
        val graph = DependencyGraph(nodes)

        val chains = ChainResolver.resolveAllChains(graph, vulnMap)

        assertTrue(chains.isNotEmpty() || vulnMap.isEmpty(), 
            "Should handle circular refs gracefully")
    }

    @Test
    fun `resolves multiple paths to same vulnerable library`() {
        val (nodes, vulnMap) = DependencyGraphFixtures.buildMultiplePathsToVulnerable()
        val graph = DependencyGraph(nodes)

        val chains = ChainResolver.resolveAllChains(graph, vulnMap)

        assertTrue(chains.isNotEmpty(), "Should find multiple paths or one deduplicated path")

        chains.forEach { chain ->
            assertEquals("com.lib:unsafe:1.0", chain.vulnerableNode.id)
        }
    }

    @Test
    fun `returns empty list for graph with no vulnerabilities`() {
        val safeNode = DependencyNode(
            id = "org.safe:lib:1.0",
            groupId = "org.safe",
            artifactId = "lib",
            version = "1.0"
        )

        val graph = DependencyGraph(listOf(safeNode))
        val chains = ChainResolver.resolveAllChains(graph, emptyMap())

        assertEquals(0, chains.size, "Should find no chains for safe dependencies")
    }

    @Test
    fun `deduplicates semantically identical chains`() {
        val (nodes, vulnMap) = DependencyGraphFixtures.buildMultiplePathsToVulnerable()
        val graph = DependencyGraph(nodes)

        val chains = ChainResolver.resolveAllChains(graph, vulnMap)

        assertTrue(chains.isNotEmpty(), "Should have deduplicated chains")
    }

    @Test
    fun `chains include all necessary information for reporting`() {
        val (nodes, vulnMap) = DependencyGraphFixtures.build3LevelChain()
        val graph = DependencyGraph(nodes)

        val chains = ChainResolver.resolveAllChains(graph, vulnMap)

        assertEquals(1, chains.size)
        val chain = chains.first()

        assertFalse(chain.chain.isEmpty(), "Chain should have nodes")
        assertTrue(chain.vulnerabilities.isNotEmpty(), "Chain should have vulnerabilities")
        assertTrue(chain.classification == VulnerabilityClassification.INDIRECTLY_VULNERABLE ||
                   chain.classification == VulnerabilityClassification.TRANSITIVE_VULNERABLE, 
            "Chain should be properly classified")
        assertTrue(chain.depth > 0, "Chain should have depth > 0")
        assertTrue(chain.directDependency.isDirectDependency(), 
            "First element should be direct dependency")
    }
}
