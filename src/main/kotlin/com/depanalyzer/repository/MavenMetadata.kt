package com.depanalyzer.repository

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonRootName
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty

@JsonRootName(value = "metadata")
@JsonIgnoreProperties(ignoreUnknown = true)
data class MavenMetadata(
    val groupId: String? = null,
    val artifactId: String? = null,
    val versioning: MavenVersioning? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MavenVersioning(
    val latest: String? = null,
    val release: String? = null,
    val versions: MavenVersions? = null,
    val lastUpdated: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MavenVersions(
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "version")
    val versionList: List<String> = emptyList()
)
