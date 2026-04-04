package com.depanalyzer.repository

import com.depanalyzer.report.Vulnerability
import com.depanalyzer.report.VulnerabilitySeverity
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

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

fun OssIndexVulnerability.toVulnerability(): Vulnerability {
    val severity = calculateSeverity(cvssScore)
    return Vulnerability(
        id = id,
        title = title,
        description = description,
        cvssScore = cvssScore,
        severity = severity
    )
}

fun calculateSeverity(cvssScore: Double?): VulnerabilitySeverity = when {
    cvssScore == null -> VulnerabilitySeverity.UNKNOWN
    cvssScore >= 9.0 -> VulnerabilitySeverity.CRITICAL
    cvssScore >= 7.0 -> VulnerabilitySeverity.HIGH
    cvssScore >= 4.0 -> VulnerabilitySeverity.MEDIUM
    else -> VulnerabilitySeverity.LOW
}

