package com.depanalyzer.repository

import com.depanalyzer.report.AffectedDependency
import com.depanalyzer.report.Vulnerability
import com.depanalyzer.report.VulnerabilitySeverity
import com.depanalyzer.report.VulnerabilitySource
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class ComponentReportResponse(
    val coordinates: String,
    val vulnerabilities: List<OssIndexVulnerability> = emptyList(),
    val reference: String? = null,
    val timestamp: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OssIndexVulnerability(
    val id: String,
    val title: String,
    val description: String? = null,
    @JsonProperty("cvssScore")
    val cvssScore: Double? = null,
    val reference: String? = null
)

fun OssIndexVulnerability.toVulnerability(
    affectedDependency: AffectedDependency,
    retrievedAt: Instant = Instant.now()
): Vulnerability {
    val severity = VulnerabilitySeverity.fromCvssScore(cvssScore)
    return Vulnerability(
        cveId = id,
        severity = severity,
        cvssScore = cvssScore,
        description = description,
        affectedDependency = affectedDependency,
        source = VulnerabilitySource.OSS_INDEX,
        retrievedAt = retrievedAt,
        referenceUrl = reference
    )
}

