package com.depanalyzer.report

import com.depanalyzer.parser.Ecosystem
import com.fasterxml.jackson.annotation.JsonIgnore
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
    val dependencyChain: List<String>? = null,
    val ecosystem: Ecosystem = Ecosystem.MAVEN
) {
    @get:JsonIgnore
    val coordinate: String get() = "$groupId:$artifactId:$currentVersion"

    @get:JsonIgnore
    val hasOutdated: Boolean get() = latestVersion != null

    @get:JsonIgnore
    val hasVulnerabilities: Boolean get() = vulnerabilities.isNotEmpty()

    @get:JsonIgnore
    val hasProblems: Boolean get() = hasOutdated || hasVulnerabilities

    @get:JsonIgnore
    val maxSeverity: VulnerabilitySeverity?
        get() = vulnerabilities.maxByOrNull { it.severity.ordinal }?.severity

    @get:JsonIgnore
    val depth: Int
        get() = if (children.isEmpty()) 0 else 1 + children.maxOf { it.depth }

    @JsonIgnore
    fun hasVulnerabilityWithSeverityOrHigher(severity: VulnerabilitySeverity): Boolean {
        if (vulnerabilities.any { it.severity.ordinal >= severity.ordinal }) {
            return true
        }
        return children.any { it.hasVulnerabilityWithSeverityOrHigher(severity) }
    }

    @JsonIgnore
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
