package com.depanalyzer.report

import com.depanalyzer.core.graph.DependencyNode

class DependencyTreeBuilder(
    private val vulnerabilities: Map<String, List<Vulnerability>> = emptyMap(),
    private val outdatedMap: Map<String, OutdatedDependency> = emptyMap()
) {

    fun buildTree(
        rootNodes: List<DependencyNode>,
        maxDepth: Int? = null,
        expandMode: TreeExpandMode = TreeExpandMode.ALL
    ): List<DependencyTreeNode> {
        return rootNodes.mapNotNull { rootNode ->
            nodeToTreeNode(
                node = rootNode,
                isDirectDep = true,
                currentDepth = 0,
                maxDepth = maxDepth,
                expandMode = expandMode,
                chain = listOf(rootNode.coordinate)
            )
        }
    }

    private fun nodeToTreeNode(
        node: DependencyNode,
        isDirectDep: Boolean,
        currentDepth: Int,
        maxDepth: Int?,
        expandMode: TreeExpandMode,
        chain: List<String>
    ): DependencyTreeNode? {
        val coordinate = node.coordinate

        val vulns = vulnerabilities[coordinate] ?: emptyList()
        val outdated = outdatedMap[coordinate]
        val latestVersion = outdated?.latestVersion

        val hasProblems = vulns.isNotEmpty() || latestVersion != null

        val children = mutableListOf<DependencyTreeNode>()

        if (maxDepth == null || currentDepth < maxDepth) {
            for (child in node.children) {
                val childTreeNode = nodeToTreeNode(
                    node = child,
                    isDirectDep = false,
                    currentDepth = currentDepth + 1,
                    maxDepth = maxDepth,
                    expandMode = expandMode,
                    chain = chain + child.coordinate
                )
                if (childTreeNode != null) {
                    children.add(childTreeNode)
                }
            }
        }

        val shouldInclude = when (expandMode) {
            TreeExpandMode.COLLAPSED -> {
                isDirectDep && hasProblems
            }

            TreeExpandMode.CRITICAL -> {
                hasProblems || children.isNotEmpty()
            }

            TreeExpandMode.HIGH -> {
                hasProblems || children.isNotEmpty()
            }

            TreeExpandMode.MEDIUM -> {
                hasProblems || children.isNotEmpty()
            }

            TreeExpandMode.ALL -> {
                hasProblems || children.isNotEmpty()
            }
        }

        if (!shouldInclude) {
            return null
        }

        return DependencyTreeNode(
            groupId = node.groupId,
            artifactId = node.artifactId,
            currentVersion = node.version,
            latestVersion = latestVersion,
            isDirectDependency = isDirectDep,
            isDependencyManagement = node.isDependencyManagement,
            scope = node.scope,
            vulnerabilities = vulns.sortedBy { it.severity.ordinal }.reversed(),
            children = children.sortByDependencyType(),
            dependencyChain = if (isDirectDep) null else chain
        )
    }

    private fun List<DependencyTreeNode>.sortByDependencyType(): List<DependencyTreeNode> {
        return this.sortedWith(compareBy<DependencyTreeNode> { node ->
            !node.hasOutdated
        }.thenBy { node ->
            -(node.maxSeverity?.ordinal ?: Int.MAX_VALUE)
        })
    }
}
