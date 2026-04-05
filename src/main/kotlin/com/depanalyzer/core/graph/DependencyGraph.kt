package com.depanalyzer.core.graph

class DependencyGraph(
    rootNodes: List<DependencyNode> = emptyList()
) {

    private val nodeIndex: MutableMap<String, DependencyNode> = mutableMapOf()

    init {
        rootNodes.forEach { node ->
            indexNode(node)
        }
    }

    private fun indexNode(node: DependencyNode) {
        nodeIndex[node.id] = node
        node.children.forEach { child ->
            indexNode(child)
        }
    }

    private fun calculateMaxDepth(node: DependencyNode): Int {
        if (node.children.isEmpty()) return 0
        return 1 + node.children.maxOf { calculateMaxDepth(it) }
    }

    fun getAllNodes(): List<DependencyNode> = nodeIndex.values.toList()

    fun getAllVulnerableNodes(): List<DependencyNode> {
        return nodeIndex.values.filter { it.isVulnerable() }
    }

    private fun detectCycle(node: DependencyNode, visited: MutableSet<String>): Boolean {
        if (visited.contains(node.id)) {
            return true
        }
        visited.add(node.id)

        for (child in node.children) {
            if (detectCycle(child, visited.toMutableSet())) {
                return true
            }
        }

        return false
    }

}
