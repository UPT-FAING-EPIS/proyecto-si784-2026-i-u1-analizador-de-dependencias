package com.depanalyzer.cli

import com.depanalyzer.parser.ProjectType
import com.depanalyzer.update.*
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCommandIntegrationTest {

    @Test
    fun `maven flow applies one and skips one with explicit confirmations`() {
        val projectDir = Files.createTempDirectory("update-maven")
        val buildFile = projectDir.resolve("pom.xml").toFile()
        val original = """
            <project>
              <dependencies>
                <dependency>
                  <groupId>org.slf4j</groupId>
                  <artifactId>slf4j-api</artifactId>
                  <version>1.7.30</version>
                </dependency>
                <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
                  <version>4.12</version>
                </dependency>
              </dependencies>
            </project>
        """.trimIndent()
        buildFile.writeText(original)

        val suggestions = listOf(
            UpdateSuggestion("org.slf4j", "slf4j-api", "1.7.30", "2.0.13", UpdateReason.CVE),
            UpdateSuggestion("junit", "junit", "4.12", "4.13.2", UpdateReason.OUTDATED)
        )
        val planner = FixedPlanner(UpdatePlan(ProjectType.MAVEN, buildFile, suggestions))

        val command = Update(
            plannerFactory = { planner },
            decisionProvider = scriptedDecisions(listOf(UpdateDecision.APPLY, UpdateDecision.SKIP))
        )
        val terminal = Terminal(ansiLevel = AnsiLevel.NONE)
        val results = command.executeUpdate(projectDir, terminal)

        val updatedContent = buildFile.readText()
        assertTrue(updatedContent.contains("<version>2.0.13</version>"))
        assertTrue(updatedContent.contains("<version>4.12</version>"))
        assertEquals(1, results.count { it.applied })
        assertEquals(1, results.count { !it.applied })

        val backupFile = projectDir.resolve("pom.xml.bak").toFile()
        assertTrue(backupFile.exists())
        assertEquals(original, backupFile.readText())
    }

    @Test
    fun `gradle groovy flow applies all when selecting all option`() {
        val projectDir = Files.createTempDirectory("update-gradle-groovy")
        val buildFile = projectDir.resolve("build.gradle").toFile()
        buildFile.writeText(
            """
            dependencies {
                implementation 'org.slf4j:slf4j-api:1.7.30'
                testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.0'
            }
            """.trimIndent()
        )

        val suggestions = listOf(
            UpdateSuggestion("org.slf4j", "slf4j-api", "1.7.30", "2.0.13", UpdateReason.OUTDATED),
            UpdateSuggestion("org.junit.jupiter", "junit-jupiter-api", "5.10.0", "5.11.3", UpdateReason.CVE)
        )
        val planner = FixedPlanner(UpdatePlan(ProjectType.GRADLE_GROOVY, buildFile, suggestions))
        val command = Update(
            plannerFactory = { planner },
            decisionProvider = scriptedDecisions(listOf(UpdateDecision.APPLY_ALL))
        )
        val terminal = Terminal(ansiLevel = AnsiLevel.NONE)
        val results = command.executeUpdate(projectDir, terminal)

        val updatedContent = buildFile.readText()
        assertTrue(updatedContent.contains("org.slf4j:slf4j-api:2.0.13"))
        assertTrue(updatedContent.contains("org.junit.jupiter:junit-jupiter-api:5.11.3"))
        assertTrue(projectDir.resolve("build.gradle.bak").toFile().exists())
        assertEquals(2, results.count { it.applied })
    }

    @Test
    fun `gradle kotlin flow omits all when no dependencies are selected`() {
        val projectDir = Files.createTempDirectory("update-gradle-kotlin")
        val buildFile = projectDir.resolve("build.gradle.kts").toFile()
        val original = """
            dependencies {
                implementation("org.slf4j:slf4j-api:1.7.30")
                testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
            }
        """.trimIndent()
        buildFile.writeText(original)

        val suggestions = listOf(
            UpdateSuggestion("org.slf4j", "slf4j-api", "1.7.30", "2.0.13", UpdateReason.OUTDATED),
            UpdateSuggestion("org.junit.jupiter", "junit-jupiter-api", "5.10.0", "5.11.3", UpdateReason.CVE)
        )
        val planner = FixedPlanner(UpdatePlan(ProjectType.GRADLE_KOTLIN, buildFile, suggestions))
        val command = Update(
            plannerFactory = { planner },
            decisionProvider = scriptedDecisions(listOf(UpdateDecision.SKIP, UpdateDecision.SKIP))
        )
        val terminal = Terminal(ansiLevel = AnsiLevel.NONE)
        val results = command.executeUpdate(projectDir, terminal)

        assertEquals(original, buildFile.readText())
        assertFalse(projectDir.resolve("build.gradle.kts.bak").toFile().exists())
        assertEquals(0, results.count { it.applied })
        assertEquals(2, results.count { !it.applied })
    }

    @Test
    fun `dry run shows simulated changes without modifying build file`() {
        val projectDir = Files.createTempDirectory("update-dry-run")
        val buildFile = projectDir.resolve("pom.xml").toFile()
        val original = """
            <project>
              <dependencies>
                <dependency>
                  <groupId>org.slf4j</groupId>
                  <artifactId>slf4j-api</artifactId>
                  <version>1.7.30</version>
                </dependency>
              </dependencies>
            </project>
        """.trimIndent()
        buildFile.writeText(original)

        val suggestions = listOf(
            UpdateSuggestion("org.slf4j", "slf4j-api", "1.7.30", "2.0.13", UpdateReason.CVE)
        )
        val planner = FixedPlanner(UpdatePlan(ProjectType.MAVEN, buildFile, suggestions))
        val command = Update(
            plannerFactory = { planner },
            decisionProvider = scriptedDecisions(listOf(UpdateDecision.APPLY))
        )

        val terminal = Terminal(ansiLevel = AnsiLevel.NONE)
        val results = command.executeUpdate(projectDir, terminal, dryRun = true)

        assertEquals(original, buildFile.readText())
        assertFalse(projectDir.resolve("pom.xml.bak").toFile().exists())
        assertEquals(1, results.count { it.applied })
        assertTrue(results.first().note.contains("dry-run"))
    }

    @Test
    fun `only security filters out outdated suggestions`() {
        val projectDir = Files.createTempDirectory("update-only-security")
        val buildFile = projectDir.resolve("build.gradle").toFile()
        buildFile.writeText(
            """
            dependencies {
                implementation 'org.slf4j:slf4j-api:1.7.30'
                implementation 'junit:junit:4.12'
            }
            """.trimIndent()
        )

        val suggestions = listOf(
            UpdateSuggestion("org.slf4j", "slf4j-api", "1.7.30", "2.0.13", UpdateReason.CVE),
            UpdateSuggestion("junit", "junit", "4.12", "4.13.2", UpdateReason.OUTDATED)
        )
        val planner = FixedPlanner(UpdatePlan(ProjectType.GRADLE_GROOVY, buildFile, suggestions))
        val command = Update(
            plannerFactory = { planner },
            decisionProvider = scriptedDecisions(listOf(UpdateDecision.APPLY))
        )

        val terminal = Terminal(ansiLevel = AnsiLevel.NONE)
        val results = command.executeUpdate(projectDir, terminal, onlySecurity = true)

        assertEquals(1, results.size)
        assertEquals("org.slf4j:slf4j-api", results.first().suggestion.coordinate)
        assertTrue(buildFile.readText().contains("org.slf4j:slf4j-api:2.0.13"))
        assertTrue(buildFile.readText().contains("junit:junit:4.12"))
    }

    private class FixedPlanner(private val plan: UpdatePlan) : UpdatePlanner {
        override fun plan(projectDir: Path, options: UpdateAnalysisOptions): UpdatePlan = plan
    }

    private fun scriptedDecisions(decisions: List<UpdateDecision>): (Terminal, UpdateSuggestion, Int, Int) -> UpdateDecision {
        val iterator = decisions.iterator()
        return { _, _, _, _ ->
            if (iterator.hasNext()) iterator.next() else UpdateDecision.SKIP
        }
    }
}
