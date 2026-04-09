package com.depanalyzer.report

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DependencyTreeNode(
    val groupId: String,
    val artifactId: String,
    val currentVersion: String,
    val latestVersion: String? = null,
    val isDirectDependency: Boolean = false,
    val isDependencyManagement: Boolean = false,
    val scope: String? = null,
    val vulnerabilities: List<Vulnerability> = emptyList(),
    val children: List<DependencyTreeNode> = emptyList(),
    val dependencyChain: List<String>? = null
) {
    val coordinate: String get() = "$groupId:$artifactId:$currentVersion"
    val hasOutdated: Boolean get() = latestVersion != null
    val hasVulnerabilities: Boolean get() = vulnerabilities.isNotEmpty()
    val hasProblems: Boolean get() = hasOutdated || hasVulnerabilities

    val maxSeverity: VulnerabilitySeverity?
        get() = vulnerabilities.maxByOrNull { it.severity.ordinal }?.severity

    val depth: Int
        get() = if (children.isEmpty()) 0 else 1 + children.maxOf { it.depth }

    fun hasVulnerabilityWithSeverityOrHigher(severity: VulnerabilitySeverity): Boolean {
        if (vulnerabilities.any { it.severity.ordinal >= severity.ordinal }) {
            return true
        }
        return children.any { it.hasVulnerabilityWithSeverityOrHigher(severity) }
    }

    fun getProblematicDescendants(): List<DependencyTreeNode> {
        val result = mutableListOf<DependencyTreeNode>()
        if (hasProblems) {
            result.add(this)
        }
        children.forEach { child ->
            result.addAll(child.getProblematicDescendants())
        }
        return result
    }
}

enum class TreeExpandMode {
    COLLAPSED,
    CRITICAL,
    HIGH,
    MEDIUM,
    ALL
}
