package com.depanalyzer.core.graph

import com.depanalyzer.report.Vulnerability

data class DependencyNode(
    val id: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val parent: DependencyNode? = null,
    val children: MutableList<DependencyNode> = mutableListOf(),
    val vulnerabilities: List<Vulnerability> = emptyList()
) {

    val coordinate: String
        get() = "$groupId:$artifactId:$version"

    fun isDirectDependency(): Boolean = parent == null

    fun isVulnerable(): Boolean = vulnerabilities.isNotEmpty()

    fun hasVulnerableTransitive(): Boolean = children.any { child ->
        child.isVulnerable() || child.hasVulnerableTransitive()
    }

    fun addChild(child: DependencyNode) {
        if (!children.contains(child)) {
            children.add(child)
        }
    }

    override fun toString(): String {
        val parentStr = if (parent != null) " ← ${parent.groupId}:${parent.artifactId}" else ""
        return "$coordinate$parentStr"
    }
}
