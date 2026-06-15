package com.depanalyzer.core

import com.depanalyzer.parser.ParsedDependency
import com.depanalyzer.repository.OssIndexClient
import com.depanalyzer.repository.ProjectRepository
import com.depanalyzer.repository.RepositoryClient
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProjectAnalyzerSourceLocationTest {

    @Test
    fun `attaches package json location to direct dependency`() {
        val projectDir = Files.createTempDirectory("source-location")
        projectDir.resolve("package.json").writeText(
            """
            {
              "name": "source-location",
              "dependencies": {
                "lodash": "4.17.21"
              }
            }
            """.trimIndent()
        )

        val repositoryClient = mockk<RepositoryClient>()
        every {
            repositoryClient.getLatestVersion(
                any<ParsedDependency>(),
                any<List<ProjectRepository>>()
            )
        } returns "4.17.21"

        val ossIndexClient = mockk<OssIndexClient>()
        every {
            ossIndexClient.getVulnerabilities(any(), any())
        } returns emptyMap()

        val report = ProjectAnalyzer(
            repositoryClient = repositoryClient,
            ossIndexClient = ossIndexClient
        ).analyze(projectDir)

        val dependency = report.upToDate.single()
        val location = assertNotNull(dependency.sourceLocation)
        assertEquals("package.json", location.file)
        assertEquals(4, location.line)
        assertEquals(6, location.startColumn)
        assertEquals(12, location.endColumn)
    }
}
