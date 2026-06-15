package com.depanalyzer.cli

import com.depanalyzer.report.*
import com.depanalyzer.tui.TerminalCapabilitiesDetector
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
    fun `source mode is auto when no source flag is passed`() {
        val mode = resolveVulnerabilitySourceModeFromFlags(
            forceOss = false,
            forceNvd = false
        )

        assertEquals(VulnerabilitySourceMode.AUTO, mode)
    }

    @Test
    fun `source mode is oss only when oss flag is passed`() {
        val mode = resolveVulnerabilitySourceModeFromFlags(
            forceOss = true,
            forceNvd = false
        )

        assertEquals(VulnerabilitySourceMode.OSS_ONLY, mode)
    }

    @Test
    fun `source mode is nvd only when nvd flag is passed`() {
        val mode = resolveVulnerabilitySourceModeFromFlags(
            forceOss = false,
            forceNvd = true
        )

        assertEquals(VulnerabilitySourceMode.NVD_ONLY, mode)
    }

    @Test
    fun `source mode is invalid when oss and nvd flags are both passed`() {
        val mode = resolveVulnerabilitySourceModeFromFlags(
            forceOss = true,
            forceNvd = true
        )

        assertEquals(null, mode)
    }

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
        assertTrue(jsonContent.contains("\"schemaVersion\" : \"1.0\""))
    }

    @Test
    fun `exports report to requested json path`() {
        val projectDir = Files.createTempDirectory("analyze-json-path")
        val outputFile = projectDir.resolve("reports").resolve("report.json")
        Files.createDirectories(outputFile.parent)

        val command = Analyze(
            analyzeExecutor = { DependencyReport(projectName = "requested-path") }
        )

        command.parse(
            listOf(
                projectDir.toString(),
                "--output",
                "json",
                "--output-file",
                outputFile.toString(),
                "--quiet"
            )
        )

        assertTrue(Files.readString(outputFile).contains("\"projectName\" : \"requested-path\""))
    }

    @Test
    fun `returns exit code 2 when analysis fails`() {
        val projectDir = Files.createTempDirectory("analyze-failure")
        val command = Analyze(
            analyzeExecutor = { error("provider unavailable") }
        )

        val result = assertFailsWith<ProgramResult> {
            command.parse(listOf(projectDir.toString(), "--quiet"))
        }

        assertEquals(2, result.statusCode)
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

    @Test
    fun `does not launch tui with analyze flag in non tty environments`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { name -> if (name == "CI") "true" else null },
            hasConsole = { false }
        )

        var tuiLaunched = false
        val command = Analyze(
            analyzeExecutor = { DependencyReport(projectName = "no-tty") },
            terminalCapabilitiesDetector = detector,
            tuiRunner = { _, _ ->
                tuiLaunched = true
                null
            }
        )

        command.parse(listOf("--tui"))

        assertEquals(false, tuiLaunched)
    }

    @Test
    fun `launches tui when analyze flag is enabled in tty environments`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { null },
            hasConsole = { true }
        )

        var tuiLaunched = false
        val command = Analyze(
            analyzeExecutor = { DependencyReport(projectName = "tty") },
            terminalCapabilitiesDetector = detector,
            tuiRunner = { _, _ ->
                tuiLaunched = true
                null
            }
        )

        command.parse(listOf("--tui"))

        assertEquals(true, tuiLaunched)
    }

    @Test
    fun `passes command output flag to analysis request`() {
        val projectDir = Files.createTempDirectory("analyze-command-output")
        var capturedRequest: AnalyzeExecutionRequest? = null

        val command = Analyze(
            analyzeExecutor = { request ->
                capturedRequest = request
                DependencyReport(projectName = "command-output")
            }
        )

        command.parse(listOf(projectDir.toString(), "--command-output"))

        assertEquals(capturedRequest?.showCommandOutput, true)
    }
}
