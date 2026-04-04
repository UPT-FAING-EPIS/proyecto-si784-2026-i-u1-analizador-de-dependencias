package com.depanalyzer.report

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DependencyReport(
    val projectName: String,
    val upToDate: List<DependencyInfo> = emptyList(),
    val outdated: List<OutdatedDependency> = emptyList(),
    val directVulnerable: List<VulnerableDependency> = emptyList(),
    val transitiveVulnerable: List<VulnerableDependency> = emptyList()
)

data class DependencyInfo(
    val groupId: String,
    val artifactId: String,
    val version: String
)

data class OutdatedDependency(
    val groupId: String,
    val artifactId: String,
    val currentVersion: String,
    val latestVersion: String
)

data class VulnerableDependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val vulnerabilities: List<Vulnerability>,
    val dependencyChain: List<String>? = null
)

data class Vulnerability(
    val id: String,
    val title: String,
    val description: String?,
    val cvssScore: Double?,
    val severity: VulnerabilitySeverity
)

enum class VulnerabilitySeverity {
    LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN
}
