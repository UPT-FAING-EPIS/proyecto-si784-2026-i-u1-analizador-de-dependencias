package com.depanalyzer.repository

import com.depanalyzer.parser.ParsedDependency
import com.depanalyzer.report.AffectedDependency
import com.depanalyzer.report.Vulnerability
import com.depanalyzer.report.VulnerabilitySeverity
import com.depanalyzer.report.VulnerabilitySource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
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
    private val jsonMapper = JsonMapper.builder().build()

    private var lastRequestTime = 0L
    @Volatile
    private var rateLimitBlockedUntilMs: Long = 0L
    @Volatile
    private var rateLimitWarningEmitted: Boolean = false

    fun getVulnerabilities(
        dependencies: List<ParsedDependency>,
        verbose: Boolean = false
    ): Map<String, List<Vulnerability>> {
        val result = mutableMapOf<String, List<Vulnerability>>()
        var stopDueToRateLimit = false

        for (dep in dependencies) {
            if (stopDueToRateLimit) {
                break
            }

            if (dep.version.isNullOrBlank() || dep.version.contains("$")) {
                continue
            }

            val cpeString = MavenToCpeMapper.mapToCpe(dep.groupId, dep.artifactId, dep.version)

            applyRequestDelay()
            val cves = searchByCpe(cpeString)
            if (isRateLimitBlocked()) {
                stopDueToRateLimit = true
            }

            if (cves.isEmpty() && verbose) {
                System.err.println("⚠️  NVD: No CVEs found for CPE: $cpeString, trying keyword search...")
            }

            if (cves.isEmpty()) {
                if (stopDueToRateLimit) {
                    continue
                }
                applyRequestDelay()
                val keywordCves = searchByKeyword("${dep.groupId}:${dep.artifactId}")
                if (isRateLimitBlocked()) {
                    stopDueToRateLimit = true
                }
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

        if (stopDueToRateLimit && !rateLimitWarningEmitted) {
            System.err.println("⚠️  NVD: Rate limit alcanzado repetidamente. Se omiten consultas restantes en esta ejecución.")
            rateLimitWarningEmitted = true
        }

        return result
    }

    fun searchByCpe(
        cpeString: String,
        retries: Int = 0
    ): List<NvdCve> {
        if (isRateLimitBlocked()) {
            return emptyList()
        }

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
        if (isRateLimitBlocked()) {
            return emptyList()
        }

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
        if (isRateLimitBlocked()) {
            return null
        }

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
                        clearRateLimitBlock()
                        val body = response.body.string()
                        parseNvdResponse(body)
                    }

                    response.code == 429 && retries < MAX_RETRIES -> {
                        val backoffMs = (INITIAL_BACKOFF_MS * (2.0.pow(retries.toDouble()))).toLong()
                        System.err.println("⏳ NVD: Rate limited (HTTP 429). Waiting ${backoffMs}ms before retry ${retries + 1}/$MAX_RETRIES...")
                        Thread.sleep(backoffMs)
                        performRequest(request, retries + 1)
                    }

                    else -> {
                        if (response.code == 429) {
                            blockForRateLimitWindow()
                        }
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

    private fun isRateLimitBlocked(): Boolean {
        return System.currentTimeMillis() < rateLimitBlockedUntilMs
    }

    private fun blockForRateLimitWindow() {
        rateLimitBlockedUntilMs = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
    }

    private fun clearRateLimitBlock() {
        rateLimitBlockedUntilMs = 0L
        rateLimitWarningEmitted = false
    }

    private fun parseNvdResponse(body: String): NvdCveResponse {
        val root = jsonMapper.readTree(body)
        return NvdCveResponse(
            resultsPerPage = root.path("resultsPerPage").asInt(0),
            startIndex = root.path("startIndex").asInt(0),
            totalResults = root.path("totalResults").asInt(0),
            vulnerabilities = root.path("vulnerabilities")
                .takeIf { it.isArray }
                ?.mapNotNull { vulnNode ->
                    val cveNode =
                        vulnNode.path("cve").takeIf { !it.isMissingNode && !it.isNull } ?: return@mapNotNull null
                    parseNvdCve(cveNode)?.let { NvdVulnerability(it) }
                }
                .orEmpty(),
            format = root.path("format").textOrNull(),
            version = root.path("version").textOrNull(),
            timestamp = root.path("timestamp").textOrNull()
        )
    }

    private fun parseNvdCve(node: JsonNode): NvdCve? {
        val id = node.path("id").textOrNull() ?: return null

        return NvdCve(
            id = id,
            sourceIdentifier = node.path("sourceIdentifier").textOrNull(),
            published = node.path("published").instantOrNull(),
            lastModified = node.path("lastModified").instantOrNull(),
            vulnStatus = node.path("vulnStatus").textOrNull(),
            descriptions = node.path("descriptions")
                .takeIf { it.isArray }
                ?.mapNotNull { descNode ->
                    val lang = descNode.path("lang").textOrNull() ?: return@mapNotNull null
                    val value = descNode.path("value").textOrNull() ?: return@mapNotNull null
                    CveDescription(lang = lang, value = value)
                }
                .orEmpty(),
            metrics = parseMetrics(node.path("metrics")),
            weaknesses = parseWeaknesses(node.path("weaknesses")),
            configurations = parseConfigurations(node.path("configurations")),
            references = parseReferences(node.path("references")),
            vendorComments = parseVendorComments(node.path("vendorComments")),
            cveTags = parseCveTags(node.path("cveTags"))
        )
    }

    private fun parseMetrics(node: JsonNode): NvdMetrics? {
        if (node.isMissingNode || node.isNull) return null

        val cvssV3 = node.path("cvssMetricV3")
            .takeIf { it.isArray }
            ?.mapNotNull { metricNode ->
                val cvssDataNode = metricNode.path("cvssData")
                val version = cvssDataNode.path("version").textOrNull() ?: return@mapNotNull null
                val vector = cvssDataNode.path("vectorString").textOrNull() ?: return@mapNotNull null
                val baseScore = cvssDataNode.path("baseScore").doubleOrNull() ?: return@mapNotNull null
                CvssMetricV3(
                    source = metricNode.path("source").textOrNull(),
                    type = metricNode.path("type").textOrNull(),
                    cvssData = CvssDataV3(
                        version = version,
                        vectorString = vector,
                        baseScore = baseScore,
                        baseSeverity = cvssDataNode.path("baseSeverity").textOrNull(),
                        attackVector = cvssDataNode.path("attackVector").textOrNull(),
                        attackComplexity = cvssDataNode.path("attackComplexity").textOrNull(),
                        privilegesRequired = cvssDataNode.path("privilegesRequired").textOrNull(),
                        userInteraction = cvssDataNode.path("userInteraction").textOrNull(),
                        scope = cvssDataNode.path("scope").textOrNull(),
                        confidentialityImpact = cvssDataNode.path("confidentialityImpact").textOrNull(),
                        integrityImpact = cvssDataNode.path("integrityImpact").textOrNull(),
                        availabilityImpact = cvssDataNode.path("availabilityImpact").textOrNull()
                    ),
                    baseSeverity = metricNode.path("baseSeverity").textOrNull(),
                    exploitabilityScore = metricNode.path("exploitabilityScore").doubleOrNull(),
                    impactScore = metricNode.path("impactScore").doubleOrNull()
                )
            }

        val cvssV2 = node.path("cvssMetricV2")
            .takeIf { it.isArray }
            ?.mapNotNull { metricNode ->
                val cvssDataNode = metricNode.path("cvssData")
                val version = cvssDataNode.path("version").textOrNull() ?: return@mapNotNull null
                val vector = cvssDataNode.path("vectorString").textOrNull() ?: return@mapNotNull null
                val baseScore = cvssDataNode.path("baseScore").doubleOrNull() ?: return@mapNotNull null
                CvssMetricV2(
                    source = metricNode.path("source").textOrNull(),
                    type = metricNode.path("type").textOrNull(),
                    cvssData = CvssDataV2(
                        version = version,
                        vectorString = vector,
                        baseScore = baseScore,
                        baseSeverity = cvssDataNode.path("baseSeverity").textOrNull()
                    ),
                    baseSeverity = metricNode.path("baseSeverity").textOrNull(),
                    exploitabilityScore = metricNode.path("exploitabilityScore").doubleOrNull(),
                    impactScore = metricNode.path("impactScore").doubleOrNull()
                )
            }

        if (cvssV3.isNullOrEmpty() && cvssV2.isNullOrEmpty()) return null
        return NvdMetrics(cvssMetricV3 = cvssV3, cvssMetricV2 = cvssV2)
    }

    private fun parseWeaknesses(node: JsonNode): List<CveWeakness>? {
        if (!node.isArray) return null
        return node.toList().map { weaknessNode ->
            CveWeakness(
                source = weaknessNode.path("source").textOrNull(),
                type = weaknessNode.path("type").textOrNull(),
                description = weaknessNode.path("description")
                    .takeIf { it.isArray }
                    ?.mapNotNull { descNode ->
                        val lang = descNode.path("lang").textOrNull() ?: return@mapNotNull null
                        val value = descNode.path("value").textOrNull() ?: return@mapNotNull null
                        CweDescription(lang = lang, value = value)
                    }
                    .orEmpty()
            )
        }
    }

    private fun parseConfigurations(node: JsonNode): List<CveConfiguration>? {
        if (!node.isArray) return null
        return node.toList().map { cfgNode ->
            CveConfiguration(
                nodes = cfgNode.path("nodes")
                    .takeIf { it.isArray }
                    ?.toList()
                    ?.map { parseCpeNode(it) }
                    .orEmpty()
            )
        }
    }

    private fun parseCpeNode(node: JsonNode): CpeNode {
        return CpeNode(
            operator = node.path("operator").textOrNull(),
            negate = node.path("negate").booleanOrNull(),
            cpeMatch = node.path("cpeMatch")
                .takeIf { it.isArray }
                ?.mapNotNull { matchNode ->
                    val vulnerable = matchNode.path("vulnerable").booleanOrNull() ?: return@mapNotNull null
                    val criteria = matchNode.path("criteria").textOrNull() ?: return@mapNotNull null
                    CpeMatch(
                        vulnerable = vulnerable,
                        criteria = criteria,
                        matchCriteriaId = matchNode.path("matchCriteriaId").textOrNull(),
                        versionStartIncluding = matchNode.path("versionStartIncluding").textOrNull(),
                        versionEndIncluding = matchNode.path("versionEndIncluding").textOrNull(),
                        versionStartExcluding = matchNode.path("versionStartExcluding").textOrNull(),
                        versionEndExcluding = matchNode.path("versionEndExcluding").textOrNull()
                    )
                }
                .orEmpty(),
            children = node.path("children")
                .takeIf { it.isArray }
                ?.toList()
                ?.map { parseCpeNode(it) }
        )
    }

    private fun parseReferences(node: JsonNode): List<CveReference> {
        if (!node.isArray) return emptyList()
        return node.mapNotNull { refNode ->
            val url = refNode.path("url").textOrNull() ?: return@mapNotNull null
            CveReference(
                url = url,
                source = refNode.path("source").textOrNull(),
                tags = refNode.path("tags")
                    .takeIf { it.isArray }
                    ?.mapNotNull { it.textOrNull() }
            )
        }
    }

    private fun parseVendorComments(node: JsonNode): List<VendorComment>? {
        if (!node.isArray) return null
        return node.toList().map {
            VendorComment(
                organization = it.path("organization").textOrNull(),
                comment = it.path("comment").textOrNull(),
                lastModified = it.path("lastModified").textOrNull()
            )
        }
    }

    private fun parseCveTags(node: JsonNode): List<CveTag>? {
        if (!node.isArray) return null
        return node.toList().map {
            CveTag(
                sourceIdentifier = it.path("sourceIdentifier").textOrNull(),
                tags = it.path("tags")
                    .takeIf { tagsNode -> tagsNode.isArray }
                    ?.mapNotNull { tagNode -> tagNode.textOrNull() }
                    .orEmpty()
            )
        }
    }

    private fun JsonNode.textOrNull(): String? {
        if (isMissingNode || isNull) return null
        return asString().takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JsonNode.doubleOrNull(): Double? {
        if (isMissingNode || isNull) return null
        return if (isNumber) asDouble() else asString().toDoubleOrNull()
    }

    private fun JsonNode.booleanOrNull(): Boolean? {
        if (isMissingNode || isNull) return null
        return if (isBoolean) asBoolean() else asString().toBooleanStrictOrNull()
    }

    private fun JsonNode.instantOrNull(): Instant? {
        val value = textOrNull() ?: return null
        return runCatching { Instant.parse(value) }.getOrNull()
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val RATE_LIMIT_COOLDOWN_MS = 60_000L
    }
}
