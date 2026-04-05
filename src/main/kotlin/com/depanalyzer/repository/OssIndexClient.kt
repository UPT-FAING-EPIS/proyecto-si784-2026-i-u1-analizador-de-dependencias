package com.depanalyzer.repository

import com.depanalyzer.parser.ParsedDependency
import com.depanalyzer.report.AffectedDependency
import com.depanalyzer.report.Vulnerability
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class OssIndexClient(
    connectTimeoutSeconds: Long = 15,
    readTimeoutSeconds: Long = 30,
    private val token: String? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build(),
    baseUrl: HttpUrl = "https://api.guide.sonatype.com/".toHttpUrl()
) {
    companion object {
        private const val BATCH_SIZE = 128
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
    }

    private val componentReportUrl: HttpUrl = baseUrl.newBuilder()
        .addPathSegments("api/v3/component-report")
        .build()

    private val jsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    fun getVulnerabilities(dependencies: List<ParsedDependency>): Map<String, List<Vulnerability>> {
        if (dependencies.isEmpty()) {
            return emptyMap()
        }

        val result = mutableMapOf<String, List<Vulnerability>>()

        val componentCoordinates = dependencies.mapNotNull { dep ->
            if (dep.version != null && !isVariableVersion(dep.version)) {
                "pkg:maven/${dep.groupId}/${dep.artifactId}@${dep.version}"
            } else {
                null
            }
        }.distinct()

        if (componentCoordinates.isEmpty()) {
            return emptyMap()
        }

        componentCoordinates.chunked(BATCH_SIZE).forEach { batch ->
            try {
                val reports = queryBatch(batch)
                reports.forEach { report ->
                    if (report.vulnerabilities.isNotEmpty()) {
                        // Parse Maven coordinates to extract groupId, artifactId, version
                        val affectedDependency = parseAffectedDependency(report.coordinates)

                        val vulnerabilities = report.vulnerabilities.map { ossVuln ->
                            ossVuln.toVulnerability(
                                affectedDependency = affectedDependency,
                                retrievedAt = java.time.Instant.now()
                            )
                        }

                        var coordinateKey = report.coordinates
                        if (coordinateKey.startsWith("pkg:maven/")) {
                            val cleanPurl = coordinateKey.substringBefore("?")
                            val parts = cleanPurl.substringAfter("pkg:maven/").split("/", "@")
                            if (parts.size == 3) {
                                coordinateKey = "${parts[0]}:${parts[1]}:${parts[2]}"
                            }
                        }

                        result[coordinateKey] = vulnerabilities
                    }
                }
            } catch (e: Exception) {
                System.err.println("⚠️  OSS Index no disponible. Análisis de vulnerabilidades omitido.")
                System.err.println("   Detalle: ${e.message}")
            }
        }

        return result
    }

    private fun queryBatch(componentCoordinates: List<String>): List<ComponentReportResponse> {
        val requestBody = jsonMapper.writeValueAsString(
            mapOf("coordinates" to componentCoordinates)
        ).toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(componentReportUrl)
            .post(requestBody)

        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        return performRequest(request, retries = 0)
    }

    private fun performRequest(request: Request, retries: Int = 0): List<ComponentReportResponse> {
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body.string()
                        jsonMapper.readValue(body, Array<ComponentReportResponse>::class.java).toList()
                    }

                    response.code == 429 && retries < MAX_RETRIES -> {
                        val backoffMs = INITIAL_BACKOFF_MS * (2.0.pow(retries.toDouble())).toLong()
                        System.err.println("⏳ Rate limit (429). Esperando ${backoffMs}ms antes de reintentar...")
                        Thread.sleep(backoffMs)
                        performRequest(request, retries + 1)
                    }

                    response.code == 429 -> {
                        throw IOException("OSS Index rate limit (429) - reintentos agotados")
                    }

                    else -> {
                        throw IOException("OSS Index error: HTTP ${response.code}")
                    }
                }
            }
        } catch (e: IOException) {
            throw e
        }
    }

    private fun isVariableVersion(version: String): Boolean {
        val dollarSign = "$"
        return version.startsWith(dollarSign) || version.startsWith(dollarSign + "{")
    }

    private fun parseAffectedDependency(coordinates: String): AffectedDependency {
        try {
            val cleanCoords = coordinates.substringBefore("?")

            // Try PURL format first
            if (cleanCoords.startsWith("pkg:maven/")) {
                val parts = cleanCoords.substringAfter("pkg:maven/").split("/", "@")
                if (parts.size == 3) {
                    return AffectedDependency(
                        groupId = parts[0],
                        artifactId = parts[1],
                        version = parts[2]
                    )
                }
            }

            // Try Maven Central format (groupId:artifactId:version)
            val parts = cleanCoords.split(":")
            if (parts.size == 3) {
                return AffectedDependency(
                    groupId = parts[0],
                    artifactId = parts[1],
                    version = parts[2]
                )
            }

            throw IllegalArgumentException("Unable to parse coordinates: $coordinates")
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse Maven coordinates from: $coordinates", e)
        }
    }
}
