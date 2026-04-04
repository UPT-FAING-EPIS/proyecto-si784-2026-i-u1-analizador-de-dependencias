package com.depanalyzer.repository

import com.depanalyzer.report.Vulnerability
import com.depanalyzer.report.VulnerabilitySeverity
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Modelo de request para la API v3 de OSS Index.
 * Ejemplo: "org.slf4j:slf4j-api:2.0.13"
 */
data class ComponentRequest(
    val coordinates: String
)

/**
 * Modelo de response de la API v3 de OSS Index.
 * Representa el reporte de vulnerabilidades para un componente específico.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ComponentReportResponse(
    val coordinates: String,
    val vulnerabilities: List<OssIndexVulnerability> = emptyList(),
    val reference: String? = null,
    val timestamp: Long? = null
)

/**
 * Representa una vulnerabilidad reportada por OSS Index.
 * Contiene detalles de CVE, CVSS score y referencias.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OssIndexVulnerability(
    val id: String,                    // CVE-2021-23463, etc.
    val title: String,
    val description: String? = null,
    @JsonProperty("cvssScore")
    val cvssScore: Double? = null,
    val reference: String? = null
)

/**
 * Mapea una vulnerabilidad de OSS Index a nuestro modelo interno.
 */
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

/**
 * Clasifica la severidad de una vulnerabilidad basada en el CVSS score.
 *
 * Criterios:
 * - CRITICAL: CVSS >= 9.0
 * - HIGH: 7.0 <= CVSS < 9.0
 * - MEDIUM: 4.0 <= CVSS < 7.0
 * - LOW: CVSS < 4.0
 * - UNKNOWN: sin CVSS score
 */
fun calculateSeverity(cvssScore: Double?): VulnerabilitySeverity = when {
    cvssScore == null -> VulnerabilitySeverity.UNKNOWN
    cvssScore >= 9.0 -> VulnerabilitySeverity.CRITICAL
    cvssScore >= 7.0 -> VulnerabilitySeverity.HIGH
    cvssScore >= 4.0 -> VulnerabilitySeverity.MEDIUM
    else -> VulnerabilitySeverity.LOW
}

/**
 * Wrapper para la respuesta de un batch de componentes.
 * OSS Index retorna un array de ComponentReportResponse.
 */
typealias ComponentBatchResponse = List<ComponentReportResponse>
