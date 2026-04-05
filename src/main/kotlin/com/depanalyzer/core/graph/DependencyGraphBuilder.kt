package com.depanalyzer.core.graph

import com.depanalyzer.parser.ParsedDependency
import com.depanalyzer.report.Vulnerability

class DependencyGraphBuilder {

    fun buildGraph(
        directDependencies: List<ParsedDependency>,
        allDependencies: List<ParsedDependency>,
        vulnerabilities: Map<String, List<Vulnerability>> = emptyMap()
    ): DependencyGraph {
        val directCoordinates = directDependencies.map { "${it.groupId}:${it.artifactId}:${it.version}" }.toSet()

        val nodeMap = mutableMapOf<String, DependencyNode>()

        allDependencies.forEach { dep ->
            val coordinate = "${dep.groupId}:${dep.artifactId}:${dep.version}"
            val vulns = vulnerabilities[coordinate] ?: emptyList()
            
            val node = DependencyNode(
                id = coordinate,
                groupId = dep.groupId,
                artifactId = dep.artifactId,
                version = dep.version ?: "unknown",
                vulnerabilities = vulns
            )
            nodeMap[coordinate] = node
        }

        val rootNodes = mutableListOf<DependencyNode>()

        directDependencies.forEach { dep ->
            val coordinate = "${dep.groupId}:${dep.artifactId}:${dep.version}"
            nodeMap[coordinate]?.let { rootNodes.add(it) }
        }

        val transitiveDeps = allDependencies.filterNot { dep ->
            directCoordinates.contains("${dep.groupId}:${dep.artifactId}:${dep.version}")
        }

        transitiveDeps.forEach { transitiv ->
            val transitiveCoord = "${transitiv.groupId}:${transitiv.artifactId}:${transitiv.version}"
            val transitiveNode = nodeMap[transitiveCoord] ?: return@forEach

            directDependencies.forEach { direct ->
                val directCoord = "${direct.groupId}:${direct.artifactId}:${direct.version}"
                nodeMap[directCoord]?.addChild(transitiveNode)
            }
        }

        return DependencyGraph(rootNodes)
    }

}
