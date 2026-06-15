package com.depanalyzer.cli

import com.depanalyzer.parser.ProjectType
import com.depanalyzer.update.UpdateAnalysisOptions
import com.depanalyzer.update.UpdatePlan
import com.depanalyzer.update.UpdatePlanner
import com.depanalyzer.update.UpdateReason
import com.depanalyzer.update.UpdateSuggestion
import com.depanalyzer.update.BuildFileUpdater
import com.github.ajalt.clikt.core.parse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateCommandTest {

    @Test
    fun `uses current directory when path argument is omitted`() {
        val buildFile = Files.createTempFile("update-default-path", ".pom.xml").toFile().apply {
            writeText("<project></project>")
        }

        var capturedPath: Path? = null
        val planner = object : UpdatePlanner {
            override fun plan(projectDir: Path, options: UpdateAnalysisOptions): UpdatePlan {
                capturedPath = projectDir.toAbsolutePath().normalize()
                return UpdatePlan(ProjectType.MAVEN, buildFile, emptyList())
            }
        }

        val command = Update(plannerFactory = { planner })
        command.parse(emptyList())

        assertEquals(Path.of(".").toAbsolutePath().normalize(), capturedPath)
    }

    @Test
    fun `passes dynamic option to planner when flag is enabled`() {
        val projectDir = Files.createTempDirectory("update-dynamic-option")
        val buildFile = projectDir.resolve("pom.xml").toFile().apply {
            writeText("<project></project>")
        }

        var capturedOptions: UpdateAnalysisOptions? = null
        val planner = object : UpdatePlanner {
            override fun plan(projectDir: Path, options: UpdateAnalysisOptions): UpdatePlan {
                capturedOptions = options
                return UpdatePlan(ProjectType.MAVEN, buildFile, emptyList())
            }
        }

        val command = Update(plannerFactory = { planner })
        command.parse(listOf(projectDir.toString(), "--dynamic"))

        assertEquals(true, capturedOptions?.dynamic)
    }

    @Test
    fun `writes machine readable update plan`() {
        val projectDir = Files.createTempDirectory("update-plan")
        val buildFile = projectDir.resolve("pom.xml").toFile().apply {
            writeText("<project></project>")
        }
        val outputFile = projectDir.resolve("plan.json")
        val suggestion = UpdateSuggestion(
            groupId = "org.example",
            artifactId = "demo",
            currentVersion = "1.0.0",
            newVersion = "1.1.0",
            reason = UpdateReason.CVE
        )
        val planner = object : UpdatePlanner {
            override fun plan(projectDir: Path, options: UpdateAnalysisOptions): UpdatePlan {
                return UpdatePlan(ProjectType.MAVEN, buildFile, listOf(suggestion))
            }
        }

        Update(plannerFactory = { planner }).parse(
            listOf(projectDir.toString(), "--plan", "--output-file", outputFile.toString())
        )

        val json = Files.readString(outputFile)
        assertTrue(json.contains("\"schemaVersion\" : \"1.0\""))
        assertTrue(json.contains("\"id\" : \"${suggestion.suggestionId}\""))
    }

    @Test
    fun `applies only explicitly requested suggestion id`() {
        val projectDir = Files.createTempDirectory("update-apply-id")
        val buildFile = projectDir.resolve("pom.xml").toFile().apply {
            writeText("<project></project>")
        }
        val selected = UpdateSuggestion(
            groupId = "org.example",
            artifactId = "selected",
            currentVersion = "1.0.0",
            newVersion = "2.0.0",
            reason = UpdateReason.CVE
        )
        val omitted = UpdateSuggestion(
            groupId = "org.example",
            artifactId = "omitted",
            currentVersion = "1.0.0",
            newVersion = "2.0.0",
            reason = UpdateReason.OUTDATED
        )
        val planner = object : UpdatePlanner {
            override fun plan(projectDir: Path, options: UpdateAnalysisOptions): UpdatePlan {
                return UpdatePlan(ProjectType.MAVEN, buildFile, listOf(selected, omitted))
            }
        }
        val applied = mutableListOf<String>()
        val updater = object : BuildFileUpdater {
            override fun applyUpdate(
                buildFile: java.io.File,
                suggestion: UpdateSuggestion
            ): Boolean {
                applied += suggestion.artifactId
                return true
            }
        }

        Update(
            plannerFactory = { planner },
            updaterFactory = { updater },
            selectionProvider = { _, _ -> error("interactive selection must not run") }
        ).parse(listOf(projectDir.toString(), "--apply-id", selected.suggestionId))

        assertEquals(listOf("selected"), applied)
        assertTrue(projectDir.resolve("pom.xml.bak").toFile().exists())
    }
}
