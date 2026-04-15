package com.depanalyzer.repository

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class NvdCveResponse(
    @JsonProperty("resultsPerPage")
    val resultsPerPage: Int,
    @JsonProperty("startIndex")
    val startIndex: Int,
    @JsonProperty("totalResults")
    val totalResults: Int,
    @JsonProperty("vulnerabilities")
    val vulnerabilities: List<NvdVulnerability> = emptyList(),
    @JsonProperty("format")
    val format: String? = null,
    @JsonProperty("version")
    val version: String? = null,
    @JsonProperty("timestamp")
    val timestamp: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NvdVulnerability(
    @JsonProperty("cve")
    val cve: NvdCve
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NvdCve(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("sourceIdentifier")
    val sourceIdentifier: String? = null,
    @JsonProperty("published")
    val published: Instant? = null,
    @JsonProperty("lastModified")
    val lastModified: Instant? = null,
    @JsonProperty("vulnStatus")
    val vulnStatus: String? = null,
    @JsonProperty("descriptions")
    val descriptions: List<CveDescription> = emptyList(),
    @JsonProperty("metrics")
    val metrics: NvdMetrics? = null,
    @JsonProperty("weaknesses")
    val weaknesses: List<CveWeakness>? = null,
    @JsonProperty("configurations")
    val configurations: List<CveConfiguration>? = null,
    @JsonProperty("references")
    val references: List<CveReference> = emptyList(),
    @JsonProperty("vendorComments")
    val vendorComments: List<VendorComment>? = null,
    @JsonProperty("cveTags")
    val cveTags: List<CveTag>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CveDescription(
    @JsonProperty("lang")
    val lang: String,
    @JsonProperty("value")
    val value: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NvdMetrics(
    @JsonProperty("cvssMetricV3")
    val cvssMetricV3: List<CvssMetricV3>? = null,
    @JsonProperty("cvssMetricV2")
    val cvssMetricV2: List<CvssMetricV2>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CvssMetricV3(
    @JsonProperty("source")
    val source: String? = null,
    @JsonProperty("type")
    val type: String? = null,
    @JsonProperty("cvssData")
    val cvssData: CvssDataV3,
    @JsonProperty("baseSeverity")
    val baseSeverity: String? = null,
    @JsonProperty("exploitabilityScore")
    val exploitabilityScore: Double? = null,
    @JsonProperty("impactScore")
    val impactScore: Double? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CvssDataV3(
    @JsonProperty("version")
    val version: String,
    @JsonProperty("vectorString")
    val vectorString: String,
    @JsonProperty("baseScore")
    val baseScore: Double,
    @JsonProperty("baseSeverity")
    val baseSeverity: String? = null,
    @JsonProperty("attackVector")
    val attackVector: String? = null,
    @JsonProperty("attackComplexity")
    val attackComplexity: String? = null,
    @JsonProperty("privilegesRequired")
    val privilegesRequired: String? = null,
    @JsonProperty("userInteraction")
    val userInteraction: String? = null,
    @JsonProperty("scope")
    val scope: String? = null,
    @JsonProperty("confidentialityImpact")
    val confidentialityImpact: String? = null,
    @JsonProperty("integrityImpact")
    val integrityImpact: String? = null,
    @JsonProperty("availabilityImpact")
    val availabilityImpact: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CvssMetricV2(
    @JsonProperty("source")
    val source: String? = null,
    @JsonProperty("type")
    val type: String? = null,
    @JsonProperty("cvssData")
    val cvssData: CvssDataV2,
    @JsonProperty("baseSeverity")
    val baseSeverity: String? = null,
    @JsonProperty("exploitabilityScore")
    val exploitabilityScore: Double? = null,
    @JsonProperty("impactScore")
    val impactScore: Double? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CvssDataV2(
    @JsonProperty("version")
    val version: String,
    @JsonProperty("vectorString")
    val vectorString: String,
    @JsonProperty("baseScore")
    val baseScore: Double,
    @JsonProperty("baseSeverity")
    val baseSeverity: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CveConfiguration(
    @JsonProperty("nodes")
    val nodes: List<CpeNode> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CpeNode(
    @JsonProperty("operator")
    val operator: String? = null,
    @JsonProperty("negate")
    val negate: Boolean? = null,
    @JsonProperty("cpeMatch")
    val cpeMatch: List<CpeMatch> = emptyList(),
    @JsonProperty("children")
    val children: List<CpeNode>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CpeMatch(
    @JsonProperty("vulnerable")
    val vulnerable: Boolean,
    @JsonProperty("criteria")
    val criteria: String,
    @JsonProperty("matchCriteriaId")
    val matchCriteriaId: String? = null,
    @JsonProperty("versionStartIncluding")
    val versionStartIncluding: String? = null,
    @JsonProperty("versionEndIncluding")
    val versionEndIncluding: String? = null,
    @JsonProperty("versionStartExcluding")
    val versionStartExcluding: String? = null,
    @JsonProperty("versionEndExcluding")
    val versionEndExcluding: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CveReference(
    @JsonProperty("url")
    val url: String,
    @JsonProperty("source")
    val source: String? = null,
    @JsonProperty("tags")
    val tags: List<String>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CveWeakness(
    @JsonProperty("source")
    val source: String? = null,
    @JsonProperty("type")
    val type: String? = null,
    @JsonProperty("description")
    val description: List<CweDescription> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CweDescription(
    @JsonProperty("lang")
    val lang: String,
    @JsonProperty("value")
    val value: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VendorComment(
    @JsonProperty("organization")
    val organization: String? = null,
    @JsonProperty("comment")
    val comment: String? = null,
    @JsonProperty("lastModified")
    val lastModified: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CveTag(
    @JsonProperty("sourceIdentifier")
    val sourceIdentifier: String? = null,
    @JsonProperty("tags")
    val tags: List<String> = emptyList()
)
