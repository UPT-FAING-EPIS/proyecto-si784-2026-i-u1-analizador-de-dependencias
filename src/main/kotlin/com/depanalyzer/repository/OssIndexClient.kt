package com.depanalyzer.repository

import com.depanalyzer.parser.Ecosystem
import com.depanalyzer.parser.ParsedDependency
import com.depanalyzer.report.AffectedDependency
import com.depanalyzer.report.Vulnerability
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
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

    private val jsonMapper = JsonMapper.builder().build()

    fun getVulnerabilities(
        dependencies: List<ParsedDependency>,
        failOnError: Boolean = false
    ): Map<String, List<Vulnerability>> {
        if (dependencies.isEmpty()) {
            return emptyMap()
        }

        val result = mutableMapOf<String, List<Vulnerability>>()

        val componentCoordinates = dependencies.mapNotNull { dep ->
            val version = dep.version ?: return@mapNotNull null
            if (isVariableVersion(version)) return@mapNotNull null
            dependencyToPurl(dep, version)
        }.distinct()

        if (componentCoordinates.isEmpty()) {
            return emptyMap()
        }

        val errors = mutableListOf<Exception>()

        componentCoordinates.chunked(BATCH_SIZE).forEach { batch ->
            try {
                val reports = queryBatch(batch)
                reports.forEach { report ->
                    if (report.vulnerabilities.isNotEmpty()) {
                        val affectedDependency = parseAffectedDependency(report.coordinates)

                        val vulnerabilities = report.vulnerabilities.map { ossVuln ->
                            ossVuln.toVulnerability(
                                affectedDependency = affectedDependency,
                                retrievedAt = java.time.Instant.now()
                            )
                        }

                        val coordinateKey = coordinateToKey(report.coordinates) ?: report.coordinates

                        result[coordinateKey] = vulnerabilities
                    }
                }
            } catch (e: Exception) {
                errors.add(e)
                System.err.println("⚠️  OSS Index no disponible. Análisis de vulnerabilidades omitido.")
                System.err.println("   Detalle: ${e.message}")
            }
        }

        if (failOnError && errors.isNotEmpty()) {
            throw IllegalStateException("OSS Index request failed: ${errors.first().message}", errors.first())
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

        val authToken = token?.trim().takeUnless { it.isNullOrBlank() }
        if (authToken != null) {
            requestBuilder.header("Authorization", "Bearer $authToken")
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
                        parseComponentReports(body)
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
        return version.startsWith(dollarSign) ||
                version.startsWith(dollarSign + "{") ||
                version.any { it in "^~><=*!,| " }
    }

    private fun dependencyToPurl(dep: ParsedDependency, version: String): String? {
        return when (dep.ecosystem) {
            Ecosystem.MAVEN -> "pkg:maven/${dep.groupId}/${dep.artifactId}@$version"
            Ecosystem.NPM -> {
                if (dep.groupId == "npm") {
                    "pkg:npm/${encode(dep.artifactId)}@$version"
                } else {
                    "pkg:npm/${encode(dep.groupId)}/${encode(dep.artifactId)}@$version"
                }
            }

            Ecosystem.PYPI -> "pkg:pypi/${encode(dep.artifactId)}@$version"
        }
    }

    private fun coordinateToKey(coordinate: String): String? {
        val parsed = parseCoordinate(coordinate) ?: return null
        return "${parsed.groupId}:${parsed.artifactId}:${parsed.version}"
    }

    private fun parseComponentReports(body: String): List<ComponentReportResponse> {
        val root = jsonMapper.readTree(body)
        if (!root.isArray) {
            return emptyList()
        }

        return root.mapNotNull { node ->
            val coordinates = node.path("coordinates").textOrNull() ?: return@mapNotNull null
            val vulnerabilities = node.path("vulnerabilities")
                .takeIf { it.isArray }
                ?.mapNotNull(::parseVulnerability)
                .orEmpty()

            ComponentReportResponse(
                coordinates = coordinates,
                vulnerabilities = vulnerabilities,
                reference = node.path("reference").textOrNull(),
                timestamp = node.path("timestamp").longOrNull()
            )
        }
    }

    private fun parseVulnerability(node: JsonNode): OssIndexVulnerability? {
        val id = node.path("id").textOrNull() ?: return null
        val title = node.path("title").textOrNull() ?: id

        return OssIndexVulnerability(
            id = id,
            title = title,
            description = node.path("description").textOrNull(),
            cvssScore = node.path("cvssScore").doubleOrNull(),
            reference = node.path("reference").textOrNull()
        )
    }

    private fun JsonNode.textOrNull(): String? {
        if (isNull || isMissingNode) return null
        return scalarText().takeIf { it.isNotBlank() }
    }

    private fun JsonNode.longOrNull(): Long? {
        if (isNull || isMissingNode) return null
        return when {
            isIntegralNumber -> longValue()
            else -> scalarText().toLongOrNull()
        }
    }

    private fun JsonNode.doubleOrNull(): Double? {
        if (isNull || isMissingNode) return null
        return when {
            isNumber -> doubleValue()
            else -> scalarText().toDoubleOrNull()
        }
    }

    private fun JsonNode.scalarText(): String = when {
        isNull || isMissingNode -> ""
        else -> toString().removeSurrounding("\"")
    }

    private fun parseAffectedDependency(coordinates: String): AffectedDependency {
        val parsed = parseCoordinate(coordinates)
            ?: throw IllegalArgumentException("Unable to parse coordinates: $coordinates")

        return AffectedDependency(
            groupId = parsed.groupId,
            artifactId = parsed.artifactId,
            version = parsed.version,
            ecosystem = parsed.ecosystem
        )
    }

    private data class ParsedCoordinate(
        val ecosystem: Ecosystem,
        val groupId: String,
        val artifactId: String,
        val version: String
    )

    private fun parseCoordinate(coordinates: String): ParsedCoordinate? {
        val cleanCoords = coordinates.substringBefore("?")

        if (cleanCoords.startsWith("pkg:")) {
            val type = cleanCoords.substringAfter("pkg:").substringBefore('/')
            val path = cleanCoords.substringAfter("$type/")
            val packagePart = path.substringBefore('@')
            val version = path.substringAfter('@', "").takeIf { it.isNotBlank() } ?: return null

            return when (type) {
                "maven" -> {
                    val parts = packagePart.split('/')
                    if (parts.size < 2) return null
                    ParsedCoordinate(
                        ecosystem = Ecosystem.MAVEN,
                        groupId = decode(parts[0]),
                        artifactId = decode(parts[1]),
                        version = version
                    )
                }

                "npm" -> {
                    val decoded = decode(packagePart)
                    val (groupId, artifactId) = if (decoded.startsWith("@") && decoded.contains('/')) {
                        decoded.substringBefore('/') to decoded.substringAfter('/')
                    } else {
                        "npm" to decoded
                    }
                    ParsedCoordinate(
                        ecosystem = Ecosystem.NPM,
                        groupId = groupId,
                        artifactId = artifactId,
                        version = version
                    )
                }

                "pypi" -> ParsedCoordinate(
                    ecosystem = Ecosystem.PYPI,
                    groupId = "pypi",
                    artifactId = decode(packagePart),
                    version = version
                )

                else -> null
            }
        }

        val parts = cleanCoords.split(":")
        if (parts.size == 3) {
            return ParsedCoordinate(
                ecosystem = Ecosystem.MAVEN,
                groupId = parts[0],
                artifactId = parts[1],
                version = parts[2]
            )
        }

        return null
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8)
}
