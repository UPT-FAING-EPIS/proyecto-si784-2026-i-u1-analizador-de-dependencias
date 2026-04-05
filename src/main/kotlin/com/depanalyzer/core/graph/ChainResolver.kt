package com.depanalyzer.core.graph

import com.depanalyzer.report.Vulnerability

object ChainResolver {

    private const val MAX_DEPTH = 50


    fun resolveAllChains(
        graph: DependencyGraph,
        vulnerabilities: Map<String, List<Vulnerability>>
    ): List<VulnerabilityChain> {
        if (vulnerabilities.isEmpty()) {
            return emptyList()
        }

        val vulnerableNodes = graph.getAllVulnerableNodes()
        val allChains = mutableListOf<VulnerabilityChain>()

        vulnerableNodes.forEach { vulnNode ->
            val nodeVulns = vulnerabilities[vulnNode.id] ?: return@forEach
            if (nodeVulns.isEmpty()) return@forEach

            val allPaths = findAllPaths(vulnNode, graph, mutableSetOf())

            allPaths.forEach { path ->
                val chain = VulnerabilityChain(
                    chain = path,
                    vulnerabilities = nodeVulns,
                    isShortestPath = false,
                    classification = classifyVulnerability(path)
                )
                allChains.add(chain)
            }
        }

        markShortestPaths(allChains)

        return deduplicateChains(allChains)
    }

    private fun findAllPaths(
        vulnerableNode: DependencyNode,
        graph: DependencyGraph,
        visited: MutableSet<String>
    ): List<List<DependencyNode>> {
        val paths = mutableListOf<List<DependencyNode>>()

        if (vulnerableNode.isDirectDependency()) {
            paths.add(listOf(vulnerableNode))
            return paths
        }

        dfsBackwardsToRoots(
            vulnerableNode,
            graph,
            mutableListOf(vulnerableNode),
            visited.toMutableSet(),
            paths
        )

        return paths
    }

    private fun dfsBackwardsToRoots(
        current: DependencyNode,
        graph: DependencyGraph,
        currentPath: MutableList<DependencyNode>,
        visited: MutableSet<String>,
        results: MutableList<List<DependencyNode>>
    ) {
        if (currentPath.size > MAX_DEPTH) {
            return
        }

        if (current.isDirectDependency()) {
            results.add(currentPath.reversed())
            return
        }

        if (visited.contains(current.id)) {
            return
        }

        visited.add(current.id)

        if (current.parent != null) {
            val parent = current.parent
            currentPath.add(parent)
            dfsBackwardsToRoots(parent, graph, currentPath, visited.toMutableSet(), results)
            currentPath.removeAt(currentPath.size - 1)
        } else {
            results.add(currentPath.reversed())
        }
    }

    private fun markShortestPaths(chains: MutableList<VulnerabilityChain>) {
        val groupedByKey = chains.groupBy { chain ->
            Triple(
                chain.directDependency.id,
                chain.vulnerableNode.id,
                chain.cveIds.joinToString(",")
            )
        }

        chains.replaceAll { original ->
            val groupKey = Triple(
                original.directDependency.id,
                original.vulnerableNode.id,
                original.cveIds.joinToString(",")
            )
            val group = groupedByKey[groupKey] ?: return@replaceAll original
            val shortest = group.minByOrNull { it.depth }
            if (original.depth == shortest?.depth) original.copy(isShortestPath = true) else original
        }
    }

    private fun classifyVulnerability(path: List<DependencyNode>): VulnerabilityClassification {
        val directDep = path.first()
        val vulnerableNode = path.last()

        return when {
            directDep.id == vulnerableNode.id -> 
                VulnerabilityClassification.DIRECTLY_VULNERABLE

            directDep.isDirectDependency() && directDep.id != vulnerableNode.id ->
                VulnerabilityClassification.INDIRECTLY_VULNERABLE

            else ->
                VulnerabilityClassification.TRANSITIVE_VULNERABLE
        }
    }

    private fun deduplicateChains(chains: List<VulnerabilityChain>): List<VulnerabilityChain> {
        val grouped = chains.groupBy { chain ->
            Triple(
                chain.directDependency.id,
                chain.vulnerableNode.id,
                chain.cveIds.toSet()
            )
        }

        return grouped.values.map { group ->
            group.minByOrNull { it.depth } ?: group.first()
        }
    }
}
