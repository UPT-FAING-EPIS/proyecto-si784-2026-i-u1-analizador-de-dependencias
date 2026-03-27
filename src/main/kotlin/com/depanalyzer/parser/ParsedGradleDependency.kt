package com.depanalyzer.parser

data class ParsedGradleDependency(
    val groupId: String,
    val artifactId: String,
    val version: String?,
    val configuration: String
)
