package com.depanalyzer.parser.maven

data class TreeLine(
    val depth: Int,
    val line: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val scope: String? = null,
    val isDirect: Boolean = depth == 0,
    val isExcluded: Boolean = false
)
