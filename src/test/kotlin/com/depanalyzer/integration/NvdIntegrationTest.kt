package com.depanalyzer.integration

import com.depanalyzer.report.AffectedDependency
import com.depanalyzer.report.Vulnerability
import com.depanalyzer.report.VulnerabilitySeverity
import com.depanalyzer.report.VulnerabilitySource
import com.depanalyzer.repository.VulnerabilityMerger
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NvdIntegrationTest {

    @Test
    fun `mergeVulnerabilities - prefers NVD CVSS score when available`() {
        val ossIndexVuln = Vulnerability(
            cveId = "CVE-2021-12345",
            severity = VulnerabilitySeverity.MEDIUM,
            cvssScore = 5.5,
            description = "OSS Index description",
            affectedDependency = AffectedDependency(
                groupId = "org.apache",
                artifactId = "commons-lang3",
                version = "3.9"
            ),
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = Instant.now(),
            referenceUrl = "https://ossindex.example.com/CVE-2021-12345"
        )

        val nvdVuln = Vulnerability(
            cveId = "CVE-2021-12345",
            severity = VulnerabilitySeverity.HIGH,
            cvssScore = 7.5,
            description = "NVD description",
            affectedDependency = AffectedDependency(
                groupId = "org.apache",
                artifactId = "commons-lang3",
                version = "3.9"
            ),
            source = VulnerabilitySource.NVD,
            retrievedAt = Instant.now(),
            referenceUrl = "https://nvd.nist.gov/vuln/detail/CVE-2021-12345"
        )

        val ossIndexMap = mapOf("org.apache:commons-lang3:3.9" to listOf(ossIndexVuln))
        val nvdMap = mapOf("org.apache:commons-lang3:3.9" to listOf(nvdVuln))

        val merged = VulnerabilityMerger.mergeVulnerabilities(ossIndexMap, nvdMap)

        assertEquals(1, merged.size)
        val result = merged["org.apache:commons-lang3:3.9"]
        assertNotNull(result)
        assertEquals(1, result.size)

        val mergedVuln = result[0]
        assertEquals("CVE-2021-12345", mergedVuln.cveId)
        assertEquals(7.5, mergedVuln.cvssScore)
        assertEquals(VulnerabilitySeverity.HIGH, mergedVuln.severity)
        assertEquals(VulnerabilitySource.BOTH, mergedVuln.source)
        assertEquals("https://nvd.nist.gov/vuln/detail/CVE-2021-12345", mergedVuln.referenceUrl)
    }

    @Test
    fun `mergeVulnerabilities - includes CVEs only from NVD`() {
        val nvdOnlyVuln = Vulnerability(
            cveId = "CVE-2022-99999",
            severity = VulnerabilitySeverity.CRITICAL,
            cvssScore = 9.8,
            description = "Critical vulnerability found by NVD",
            affectedDependency = AffectedDependency(
                groupId = "org.springframework",
                artifactId = "spring-core",
                version = "5.3.0"
            ),
            source = VulnerabilitySource.NVD,
            retrievedAt = Instant.now(),
            referenceUrl = "https://nvd.nist.gov/vuln/detail/CVE-2022-99999"
        )

        val ossIndexMap = emptyMap<String, List<Vulnerability>>()
        val nvdMap = mapOf("org.springframework:spring-core:5.3.0" to listOf(nvdOnlyVuln))

        val merged = VulnerabilityMerger.mergeVulnerabilities(ossIndexMap, nvdMap)

        assertEquals(1, merged.size)
        val result = merged["org.springframework:spring-core:5.3.0"]
        assertNotNull(result)
        assertEquals(1, result.size)

        val mergedVuln = result[0]
        assertEquals("CVE-2022-99999", mergedVuln.cveId)
        assertEquals(9.8, mergedVuln.cvssScore)
        assertEquals(VulnerabilitySeverity.CRITICAL, mergedVuln.severity)
        assertEquals(VulnerabilitySource.NVD, mergedVuln.source)
    }

    @Test
    fun `mergeVulnerabilities - handles multiple CVEs for same dependency`() {
        val ossIndexVulns = listOf(
            Vulnerability(
                cveId = "CVE-2021-11111",
                severity = VulnerabilitySeverity.MEDIUM,
                cvssScore = 5.0,
                description = "OSS Index CVE 1",
                affectedDependency = AffectedDependency("junit", "junit", "4.12"),
                source = VulnerabilitySource.OSS_INDEX,
                retrievedAt = Instant.now(),
                referenceUrl = "https://ossindex.example.com"
            ),
            Vulnerability(
                cveId = "CVE-2021-22222",
                severity = VulnerabilitySeverity.LOW,
                cvssScore = 3.0,
                description = "OSS Index CVE 2",
                affectedDependency = AffectedDependency("junit", "junit", "4.12"),
                source = VulnerabilitySource.OSS_INDEX,
                retrievedAt = Instant.now(),
                referenceUrl = "https://ossindex.example.com"
            )
        )

        val nvdVulns = listOf(
            Vulnerability(
                cveId = "CVE-2021-22222",
                severity = VulnerabilitySeverity.MEDIUM,
                cvssScore = 6.0,
                description = "NVD CVE 2 (updated)",
                affectedDependency = AffectedDependency("junit", "junit", "4.12"),
                source = VulnerabilitySource.NVD,
                retrievedAt = Instant.now(),
                referenceUrl = "https://nvd.nist.gov"
            ),
            Vulnerability(
                cveId = "CVE-2021-33333",
                severity = VulnerabilitySeverity.HIGH,
                cvssScore = 7.5,
                description = "NVD CVE 3 (new)",
                affectedDependency = AffectedDependency("junit", "junit", "4.12"),
                source = VulnerabilitySource.NVD,
                retrievedAt = Instant.now(),
                referenceUrl = "https://nvd.nist.gov"
            )
        )

        val ossIndexMap = mapOf("junit:junit:4.12" to ossIndexVulns)
        val nvdMap = mapOf("junit:junit:4.12" to nvdVulns)

        val merged = VulnerabilityMerger.mergeVulnerabilities(ossIndexMap, nvdMap)

        assertEquals(1, merged.size)
        val result = merged["junit:junit:4.12"]
        assertNotNull(result)
        assertEquals(3, result.size)

        val cve1 = result.find { it.cveId == "CVE-2021-11111" }
        assertNotNull(cve1)
        assertEquals(VulnerabilitySource.OSS_INDEX, cve1.source)

        val cve2 = result.find { it.cveId == "CVE-2021-22222" }
        assertNotNull(cve2)
        assertEquals(6.0, cve2.cvssScore)
        assertEquals(VulnerabilitySeverity.MEDIUM, cve2.severity)
        assertEquals(VulnerabilitySource.BOTH, cve2.source)

        val cve3 = result.find { it.cveId == "CVE-2021-33333" }
        assertNotNull(cve3)
        assertEquals(VulnerabilitySource.NVD, cve3.source)
    }

    @Test
    fun `mavenToCpe - transforms common libraries correctly`() {
        val mapper = com.depanalyzer.repository.MavenToCpeMapper

        assertEquals(
            "cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*",
            mapper.mapToCpe("org.apache", "commons-lang3", "3.14.0")
        )

        assertEquals(
            "cpe:2.3:a:springframework:spring:5.3.20:*:*:*:*:*:*:*",
            mapper.mapToCpe("org.springframework", "spring-core", "5.3.20")
        )

        assertEquals(
            "cpe:2.3:a:fasterxml:jackson_databind:2.14.0:*:*:*:*:*:*:*",
            mapper.mapToCpe("com.fasterxml.jackson.core", "jackson-databind", "2.14.0")
        )

        assertEquals(
            "cpe:2.3:a:google:guava:31.1-jre:*:*:*:*:*:*:*",
            mapper.mapToCpe("com.google.guava", "guava", "31.1-jre")
        )
    }

    @Test
    fun `mergeVulnerabilities - preserves empty coordinate keys`() {
        val ossIndexMap = mapOf<String, List<Vulnerability>>(
            "org.example:lib1:1.0.0" to emptyList(),
            "org.example:lib2:1.0.0" to emptyList()
        )
        val nvdMap = emptyMap<String, List<Vulnerability>>()

        val merged = VulnerabilityMerger.mergeVulnerabilities(ossIndexMap, nvdMap)

        assertEquals(2, merged.size)
        assertEquals(0, merged["org.example:lib1:1.0.0"]?.size)
        assertEquals(0, merged["org.example:lib2:1.0.0"]?.size)
    }
}
