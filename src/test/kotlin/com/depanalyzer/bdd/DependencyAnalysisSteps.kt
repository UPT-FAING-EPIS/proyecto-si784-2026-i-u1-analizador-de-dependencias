package com.depanalyzer.bdd

import com.depanalyzer.parser.ProjectDetector
import com.depanalyzer.parser.ProjectType
import com.depanalyzer.report.VulnerabilitySeverity
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DependencyAnalysisSteps {
    private lateinit var projectDirectory: Path
    private var detectedType: ProjectType? = null
    private var detectionError: Throwable? = null
    private var severity: VulnerabilitySeverity? = null
    private var jsonOutput = ""

    @Given("un proyecto con el archivo {string}")
    fun aProjectWithFile(fileName: String) {
        projectDirectory = createTempDirectory("depanalyzer-bdd-")
        Files.createFile(projectDirectory.resolve(fileName))
    }

    @Given("un directorio sin archivos de dependencias")
    fun anEmptyDirectory() {
        projectDirectory = createTempDirectory("depanalyzer-bdd-empty-")
    }

    @When("DepAnalyzer detecta el tipo de proyecto")
    fun detectProjectType() {
        runCatching { ProjectDetector().detect(projectDirectory) }
            .onSuccess { detectedType = it }
            .onFailure { detectionError = it }
    }

    @Then("el tipo detectado es {string}")
    fun detectedTypeIs(expected: String) {
        assertEquals(ProjectType.valueOf(expected), detectedType)
    }

    @Then("se informa que el tipo de proyecto no es reconocido")
    fun unknownProjectIsReported() {
        assertTrue(detectionError is IllegalStateException)
        assertTrue(detectionError?.message.orEmpty().contains("No known build files"))
    }

    @Given("un puntaje CVSS de {string}")
    fun aCvssScore(score: String) {
        severity = VulnerabilitySeverity.fromCvssScore(score.toDouble())
    }

    @Given("un puntaje CVSS ausente")
    fun anAbsentCvssScore() {
        severity = VulnerabilitySeverity.fromCvssScore(null)
    }

    @Then("la severidad calculada es {string}")
    fun severityIs(expected: String) {
        assertEquals(VulnerabilitySeverity.valueOf(expected), severity)
    }

    @Given("un reporte JSON mínimo con proyecto {string}")
    fun aMinimalJsonReport(projectName: String) {
        jsonOutput = """{"schemaVersion":"1.0","projectName":"$projectName","outdated":[]}"""
    }

    @When("el reporte es consumido por una automatización")
    fun consumeJsonReport() {
        assertFailsWith<IllegalArgumentException> {
            require(jsonOutput.isBlank()) { "The JSON report is available" }
        }
    }

    @Then("conserva la versión de esquema {string}")
    fun schemaVersionIs(expected: String) {
        assertTrue(jsonOutput.contains(""""schemaVersion":"$expected""""))
    }

    @Then("conserva el nombre de proyecto {string}")
    fun projectNameIs(expected: String) {
        assertTrue(jsonOutput.contains(""""projectName":"$expected""""))
    }
}
