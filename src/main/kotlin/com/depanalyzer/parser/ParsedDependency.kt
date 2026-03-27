package com.depanalyzer.parser

enum class DependencySection {
    DEPENDENCIES,
    DEPENDENCY_MANAGEMENT
}

data class ParsedDependency(
    val groupId: String,
    val artifactId: String,
    val version: String?,
    val scope: String,
    val section: DependencySection
)
