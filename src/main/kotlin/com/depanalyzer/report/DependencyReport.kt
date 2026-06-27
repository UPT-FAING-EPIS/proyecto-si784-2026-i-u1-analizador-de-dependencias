package com.depanalyzer.report

import com.depanalyzer.core.graph.VulnerabilityChain
import com.depanalyzer.parser.Ecosystem
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DependencyReport(
    val schemaVersion: String = "1.1",
    val projectName: String,
    val upToDate: List<DependencyInfo> = emptyList(),
    val outdated: List<OutdatedDependency> = emptyList(),
    val directVulnerable: List<VulnerableDependency> = emptyList(),
    val transitiveVulnerable: List<VulnerableDependency> = emptyList(),
    val vulnerabilityChains: List<VulnerabilityChain> = emptyList(),
    val dependencyTree: List<DependencyTreeNode>? = null,
    val analysis: AnalysisMetadata? = null
)

enum class AnalysisMode {
    DYNAMIC,
    STATIC,
    STATIC_FALLBACK
}

data class ProviderAnalysisMetadata(
    val requested: String,
    val used: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val statuses: Map<String, String> = emptyMap()
)

data class AnalysisMetadata(
    val requestedMode: AnalysisMode,
    val actualMode: AnalysisMode,
    val projectType: String,
    val ecosystems: List<String> = emptyList(),
    val durationMs: Long,
    val warnings: List<String> = emptyList(),
    val providers: ProviderAnalysisMetadata
)

data class DependencyInfo(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val ecosystem: Ecosystem = Ecosystem.MAVEN,
    val sourceLocation: DependencySourceLocation? = null
)

data class OutdatedDependency(
    val groupId: String,
    val artifactId: String,
    val currentVersion: String,
    val latestVersion: String,
    val ecosystem: Ecosystem = Ecosystem.MAVEN,
    val sourceLocation: DependencySourceLocation? = null
)

data class VulnerableDependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val vulnerabilities: List<Vulnerability>,
    val dependencyChain: List<String>? = null,
    val ecosystem: Ecosystem = Ecosystem.MAVEN,
    val sourceLocation: DependencySourceLocation? = null
)

data class DependencySourceLocation(
    val file: String,
    val line: Int,
    val startColumn: Int,
    val endColumn: Int
)
