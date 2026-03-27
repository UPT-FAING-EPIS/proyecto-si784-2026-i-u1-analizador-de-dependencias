package com.depanalyzer.repository

data class ProjectRepository(
    val id: String,
    val url: String,
    val releases: Boolean = true,
    val snapshots: Boolean = false,
    val username: String? = null,
    val password: String? = null
) {
    companion object {
        val MAVEN_CENTRAL = ProjectRepository(
            id = "central", url = "https://repo1.maven.org/maven2", releases = true, snapshots = false
        )
    }
}
