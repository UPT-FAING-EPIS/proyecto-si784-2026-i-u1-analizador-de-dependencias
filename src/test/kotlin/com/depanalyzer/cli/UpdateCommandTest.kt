package com.depanalyzer.cli

import com.depanalyzer.parser.ProjectType
import com.depanalyzer.update.UpdateAnalysisOptions
import com.depanalyzer.update.UpdatePlan
import com.depanalyzer.update.UpdatePlanner
import com.github.ajalt.clikt.core.parse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

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
}
