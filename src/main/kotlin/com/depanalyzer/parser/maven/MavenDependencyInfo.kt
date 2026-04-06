package com.depanalyzer.parser.maven

data class MavenDependencyInfo(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val scope: String? = null,
    val isDirect: Boolean = false,
    val depth: Int = 0,
    val children: MutableList<MavenDependencyInfo> = mutableListOf()
) {

    val coordinates: String
        get() = "$groupId:$artifactId:$version"

    val id: String
        get() = "$groupId:$artifactId"
}
