package com.depanalyzer.repository

import com.depanalyzer.parser.ParsedDependency
import com.depanalyzer.report.AffectedDependency
import com.depanalyzer.report.Vulnerability
import com.depanalyzer.report.VulnerabilitySeverity
import com.depanalyzer.report.VulnerabilitySource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class NvdClient(
    connectTimeoutSeconds: Long = 10,
    readTimeoutSeconds: Long = 20,
    private val apiKey: String? = System.getenv("NVD_API_KEY"),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://services.nvd.nist.gov/rest/json/cves/2.0",
    private val requestDelayMs: Long = if (System.getenv("NVD_API_KEY") != null) 100 else 600
) {
    private val jsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    private var lastRequestTime = 0L

    fun getVulnerabilities(
        dependencies: List<ParsedDependency>,
        verbose: Boolean = false
    ): Map<String, List<Vulnerability>> {
        val result = mutableMapOf<String, List<Vulnerability>>()

        for (dep in dependencies) {
            if (dep.version.isNullOrBlank() || dep.version.contains("\$")) {
                continue
            }

            val cpeString = MavenToCpeMapper.mapToCpe(dep.groupId, dep.artifactId, dep.version)

            applyRequestDelay()
            val cves = searchByCpe(cpeString)

            if (cves.isEmpty() && verbose) {
                System.err.println("⚠️  NVD: No CVEs found for CPE: $cpeString, trying keyword search...")
            }

            if (cves.isEmpty()) {
                applyRequestDelay()
                val keywordCves = searchByKeyword("${dep.groupId}:${dep.artifactId}")
                if (keywordCves.isNotEmpty() && verbose) {
                    System.err.println("✓ NVD: Found ${keywordCves.size} CVEs via keyword search for ${dep.artifactId}")
                }

                val vulnerabilities = keywordCves.map { cve ->
                    nvdCveToVulnerability(cve, dep)
                }

                if (vulnerabilities.isNotEmpty()) {
                    val key = "${dep.groupId}:${dep.artifactId}:${dep.version}"
                    result[key] = vulnerabilities
                }
            } else {
                if (verbose) {
                    System.err.println("✓ NVD: Found ${cves.size} CVEs for ${dep.artifactId}")
                }

                val vulnerabilities = cves.map { cve ->
                    nvdCveToVulnerability(cve, dep)
                }

                if (vulnerabilities.isNotEmpty()) {
                    val key = "${dep.groupId}:${dep.artifactId}:${dep.version}"
                    result[key] = vulnerabilities
                }
            }
        }

        return result
    }

    fun searchByCpe(
        cpeString: String,
        retries: Int = 0
    ): List<NvdCve> {
        return try {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("cpeName", cpeString)
                .addQueryParameter("resultsPerPage", "2000")
                .build()

            val request = buildRequest(url.toString())
            val response = performRequest(request, retries)
            response?.vulnerabilities?.map { it.cve } ?: emptyList()
        } catch (e: Exception) {
            System.err.println("⚠️  NVD: Error searching by CPE '$cpeString': ${e.message}")
            emptyList()
        }
    }

    fun searchByKeyword(
        keyword: String,
        limit: Int = 20,
        retries: Int = 0
    ): List<NvdCve> {
        return try {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("keywordSearch", keyword)
                .addQueryParameter("resultsPerPage", limit.toString())
                .build()

            val request = buildRequest(url.toString())
            val response = performRequest(request, retries)
            response?.vulnerabilities?.map { it.cve } ?: emptyList()
        } catch (e: Exception) {
            System.err.println("⚠️  NVD: Error searching by keyword '$keyword': ${e.message}")
            emptyList()
        }
    }

    fun getCveById(cveId: String): NvdCve? {
        return try {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("cveId", cveId)
                .build()

            val request = buildRequest(url.toString())
            val response = performRequest(request)
            response?.vulnerabilities?.firstOrNull()?.cve
        } catch (e: Exception) {
            System.err.println("⚠️  NVD: Error fetching CVE '$cveId': ${e.message}")
            null
        }
    }

    private fun buildRequest(url: String): Request {
        val requestBuilder = Request.Builder().url(url)

        if (apiKey != null) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        return requestBuilder.build()
    }

    private fun performRequest(
        request: Request,
        retries: Int = 0
    ): NvdCveResponse? {
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body.string()
                        jsonMapper.readValue(body, NvdCveResponse::class.java)
                    }

                    response.code == 429 && retries < MAX_RETRIES -> {
                        val backoffMs = (INITIAL_BACKOFF_MS * (2.0.pow(retries.toDouble()))).toLong()
                        System.err.println("⏳ NVD: Rate limited (HTTP 429). Waiting ${backoffMs}ms before retry ${retries + 1}/$MAX_RETRIES...")
                        Thread.sleep(backoffMs)
                        performRequest(request, retries + 1)
                    }

                    else -> {
                        System.err.println("⚠️  NVD: HTTP ${response.code} error: ${response.message}")
                        null
                    }
                }
            }
        } catch (e: IOException) {
            System.err.println("⚠️  NVD: Network error: ${e.message}")
            null
        } catch (e: Exception) {
            System.err.println("⚠️  NVD: Error parsing response: ${e.message}")
            null
        }
    }

    private fun nvdCveToVulnerability(
        cve: NvdCve,
        dep: ParsedDependency
    ): Vulnerability {
        val cveId = cve.id
        val cvssScore = extractCvssV3Score(cve)
        val severity =
            if (cvssScore != null) VulnerabilitySeverity.fromCvssScore(cvssScore) else VulnerabilitySeverity.UNKNOWN
        val description =
            cve.descriptions.firstOrNull { it.lang == "en" }?.value ?: cve.descriptions.firstOrNull()?.value
        val referenceUrl = cve.references.firstOrNull()?.url

        return Vulnerability(
            cveId = cveId,
            severity = severity,
            cvssScore = cvssScore,
            description = description,
            affectedDependency = AffectedDependency(
                groupId = dep.groupId,
                artifactId = dep.artifactId,
                version = dep.version ?: ""
            ),
            source = VulnerabilitySource.NVD,
            retrievedAt = Instant.now(),
            referenceUrl = referenceUrl
        )
    }

    private fun extractCvssV3Score(cve: NvdCve): Double? {
        val cvssV3Metrics = cve.metrics?.cvssMetricV3 ?: return null

        return cvssV3Metrics.maxByOrNull { it.cvssData.version }
            ?.cvssData
            ?.baseScore
    }

    private fun applyRequestDelay() {
        val timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime
        if (timeSinceLastRequest < requestDelayMs) {
            Thread.sleep(requestDelayMs - timeSinceLastRequest)
        }
        lastRequestTime = System.currentTimeMillis()
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val INITIAL_BACKOFF_MS = 1000L
    }
}
