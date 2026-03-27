package com.depanalyzer.parser

data class VersionCatalog(
    val versions: Map<String, String> = emptyMap(),
    val libraries: Map<String, LibraryInfo> = emptyMap()
)

data class LibraryInfo(
    val group: String,
    val name: String,
    val versionRef: String? = null,
    val version: String? = null
)
