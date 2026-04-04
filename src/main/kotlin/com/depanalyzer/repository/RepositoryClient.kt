package com.depanalyzer.repository

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.module.kotlin.KotlinModule
import java.io.IOException
import java.util.concurrent.TimeUnit

class RepositoryClient(
    connectTimeoutSeconds: Long = 10,
    readTimeoutSeconds: Long = 10,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()
) {
    private val xmlMapper = XmlMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    fun getLatestVersion(repository: ProjectRepository, groupId: String, artifactId: String): String? {
        val metadata = fetchMetadata(repository, groupId, artifactId) ?: return null
        return metadata.versioning?.release ?: metadata.versioning?.latest
        ?: metadata.versioning?.versions?.versionList?.lastOrNull()
    }

    private fun fetchMetadata(repository: ProjectRepository, groupId: String, artifactId: String): MavenMetadata? {
        val url = buildMetadataUrl(repository.url, groupId, artifactId)
        val requestBuilder = Request.Builder().url(url)

        if (repository.username != null && repository.password != null) {
            requestBuilder.header("Authorization", Credentials.basic(repository.username, repository.password))
        }

        val request = requestBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                return xmlMapper.readValue(body, MavenMetadata::class.java)
            }
        } catch (_: IOException) {
            return null
        }
    }

    private fun buildMetadataUrl(baseUrl: String, groupId: String, artifactId: String): String {
        val groupPath = groupId.replace('.', '/')
        val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return "$cleanBaseUrl$groupPath/$artifactId/maven-metadata.xml"
    }
}
