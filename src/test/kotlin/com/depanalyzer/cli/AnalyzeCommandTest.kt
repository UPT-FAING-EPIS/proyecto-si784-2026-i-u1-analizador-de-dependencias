package com.depanalyzer.cli

import com.depanalyzer.report.*
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.parse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AnalyzeCommandTest {

    @Test
    fun `uses current directory when path argument is omitted`() {
        var capturedPath: Path? = null
        val outputFile = Files.createTempFile("analyze-default", ".json")

        val command = Analyze(
            analyzeExecutor = { request ->
                capturedPath = request.projectPath.toAbsolutePath().normalize()
                DependencyReport(projectName = "default-path")
            },
            jsonOutputPathProvider = { outputFile }
        )

        command.parse(listOf("--output", "json"))

        assertEquals(Path.of(".").toAbsolutePath().normalize(), capturedPath)
        assertTrue(Files.exists(outputFile))
    }

    @Test
    fun `exports report to json file when output is json`() {
        val projectDir = Files.createTempDirectory("analyze-json")
        val outputFile = Files.createTempFile("dependency-report", ".json")

        val command = Analyze(
            analyzeExecutor = {
                DependencyReport(projectName = "json-project")
            },
            jsonOutputPathProvider = { outputFile }
        )

        command.parse(listOf(projectDir.toString(), "--output", "json"))

        val jsonContent = Files.readString(outputFile)
        assertTrue(jsonContent.contains("\"projectName\" : \"json-project\""))
    }

    @Test
    fun `returns exit code 1 when fail on critical is enabled and report has critical vulnerabilities`() {
        val projectDir = Files.createTempDirectory("analyze-critical")
        val outputFile = Files.createTempFile("dependency-report-critical", ".json")

        val criticalReport = DependencyReport(
            projectName = "critical-project",
            directVulnerable = listOf(
                VulnerableDependency(
                    groupId = "org.example",
                    artifactId = "vulnerable-lib",
                    version = "1.0.0",
                    vulnerabilities = listOf(
                        Vulnerability(
                            cveId = "CVE-2026-0001",
                            severity = VulnerabilitySeverity.CRITICAL,
                            cvssScore = 9.8,
                            description = "Critical vulnerability",
                            affectedDependency = AffectedDependency("org.example", "vulnerable-lib", "1.0.0"),
                            source = VulnerabilitySource.OSS_INDEX,
                            retrievedAt = null,
                            referenceUrl = null
                        )
                    )
                )
            )
        )

        val command = Analyze(
            analyzeExecutor = { criticalReport },
            jsonOutputPathProvider = { outputFile }
        )

        val result = assertFailsWith<ProgramResult> {
            command.parse(listOf(projectDir.toString(), "--output", "json", "--fail-on-critical"))
        }

        assertEquals(1, result.statusCode)
    }
}
