package com.depanalyzer.repository

import com.depanalyzer.security.InputSafety
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
    private val trustedCredentialHosts: Set<String> =
        InputSafety.parseTrustedCredentialHosts(System.getenv(InputSafety.CREDENTIAL_HOST_ALLOWLIST_ENV)),
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
        val candidates = buildList {
            metadata.versioning?.release?.let(::add)
            metadata.versioning?.latest?.let(::add)
            metadata.versioning?.versions?.versionList.orEmpty().asReversed().forEach(::add)
        }

        return candidates
            .map(String::trim)
            .firstOrNull(InputSafety::isSafeVersion)
    }

    private fun fetchMetadata(repository: ProjectRepository, groupId: String, artifactId: String): MavenMetadata? {
        if (!InputSafety.isAllowedRepositoryUrl(repository.url)) return null

        val url = buildMetadataUrl(repository.url, groupId, artifactId)
        if (!InputSafety.isAllowedRepositoryUrl(url)) return null

        val requestBuilder = runCatching { Request.Builder().url(url) }.getOrNull() ?: return null

        val allowCredentials = InputSafety.isTrustedCredentialDestination(url, trustedCredentialHosts)
        if (allowCredentials && repository.username != null && repository.password != null) {
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
