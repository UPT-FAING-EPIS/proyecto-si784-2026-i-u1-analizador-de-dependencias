package com.depanalyzer.core

import com.depanalyzer.repository.OssIndexClient
import com.depanalyzer.repository.RepositoryClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectAnalyzerTest {

    @Test
    fun `analyze handles OssIndexClient exception gracefully without crashing`() {
        val mockOssIndexClient = mockk<OssIndexClient>()
        every { mockOssIndexClient.getVulnerabilities(any()) } throws RuntimeException("Connection failed")

        val analyzer = ProjectAnalyzer(
            repositoryClient = RepositoryClient(),
            ossIndexClient = mockOssIndexClient
        )

        val projectPath = Paths.get(".").toAbsolutePath().normalize()

        val report = try {
            analyzer.analyze(projectPath, includeChains = false)
        } catch (e: Exception) {
            throw AssertionError("analyze() should not throw exception when OssIndexClient fails", e)
        }

        assertNotNull(report, "Report should not be null")
        assertEquals(projectPath.fileName.toString(), report.projectName, "Project name should be set")

        assertTrue(report.directVulnerable.isEmpty(), "DirectVulnerable should be empty when OssIndexClient fails")
        assertTrue(report.transitiveVulnerable.isEmpty(), "TransitiveVulnerable should be empty when OssIndexClient fails")
        assertTrue(report.vulnerabilityChains.isEmpty(), "VulnerabilityChains should be empty when OssIndexClient fails")

        verify { mockOssIndexClient.getVulnerabilities(any()) }
    }

    @Test
    fun `analyze with includeChains=true creates empty chains when OssIndexClient fails`() {
        val mockOssIndexClient = mockk<OssIndexClient>()
        every { mockOssIndexClient.getVulnerabilities(any()) } throws RuntimeException("Timeout after 30 seconds")

        val analyzer = ProjectAnalyzer(
            repositoryClient = RepositoryClient(),
            ossIndexClient = mockOssIndexClient
        )

        val projectPath = Paths.get(".").toAbsolutePath().normalize()

        val report = analyzer.analyze(projectPath, includeChains = true)

        assertNotNull(report, "Report should not be null")
        assertTrue(report.vulnerabilityChains.isEmpty(), "VulnerabilityChains should be empty on exception")

        assertTrue(report.upToDate.isNotEmpty() || report.outdated.isEmpty(), 
            "Report should have valid structure regardless of OSS Index failure")
    }

    @Test
    fun `analyze succeeds with valid OssIndexClient returning empty vulnerabilities`() {
        val mockOssIndexClient = mockk<OssIndexClient>()
        every { mockOssIndexClient.getVulnerabilities(any()) } returns emptyMap()

        val analyzer = ProjectAnalyzer(
            repositoryClient = RepositoryClient(),
            ossIndexClient = mockOssIndexClient
        )

        val projectPath = Paths.get(".").toAbsolutePath().normalize()

        val report = analyzer.analyze(projectPath, includeChains = false)

        assertNotNull(report, "Report should not be null")
        assertEquals(projectPath.fileName.toString(), report.projectName)
        assertTrue(report.directVulnerable.isEmpty(), "No direct vulnerabilities")
        assertTrue(report.transitiveVulnerable.isEmpty(), "No transitive vulnerabilities")

        verify { mockOssIndexClient.getVulnerabilities(any()) }
    }
}
