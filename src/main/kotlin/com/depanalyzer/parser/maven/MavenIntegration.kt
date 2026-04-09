package com.depanalyzer.parser.maven

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.parser.PomDependencyParser
import java.io.File

object MavenIntegration {
    private val staticParser = PomDependencyParser()

    fun analyzeMavenProject(
        projectDir: File,
        enableMaven: Boolean = true,
        verbose: Boolean = false
    ): List<DependencyNode> {
        val pomFile = File(projectDir, "pom.xml")

        if (!enableMaven) {
            System.err.println("⚠️ Dynamic analysis disabled. Using static analysis (less precise).")
            if (verbose) {
                System.err.println("[MavenIntegration] Offline mode enabled. Using static analysis (less precise).")
            }
            return fallbackToStaticParsing(pomFile, verbose)
        }

        if (!MavenDetector.isAvailable()) {
            System.err.println("⚠️  Maven not detected. Using static analysis (less precise).")
            return fallbackToStaticParsing(pomFile, verbose)
        }

        val treeOutput = MavenCommandExecutor.execute(projectDir, verbose = verbose)
        if (treeOutput == null) {
            System.err.println("⚠️ Dynamic analysis failed. Using static analysis (less precise).")
            if (verbose) {
                System.err.println("[MavenIntegration] Maven execution failed, timed out, or produced no output")
            }
            return fallbackToStaticParsing(pomFile, verbose)
        }

        if (verbose) {
            System.err.println("[MavenIntegration] Using dynamic Maven analysis")
        }
        System.err.println("✓ Using dynamic analysis (precise)")

        return MavenDependencyTreeParser.parse(treeOutput, verbose = verbose)
    }

    private fun fallbackToStaticParsing(pomFile: File, verbose: Boolean = false): List<DependencyNode> {
        return try {
            val parsedDeps = staticParser.parse(pomFile)

            if (verbose) {
                System.err.println("[MavenIntegration] Static parsing found ${parsedDeps.size} dependencies")
            }

            parsedDeps.map { dep ->
                DependencyNode(
                    id = "${dep.groupId}:${dep.artifactId}",
                    groupId = dep.groupId,
                    artifactId = dep.artifactId,
                    version = dep.version ?: "unknown",
                    parent = null,
                    children = mutableListOf(),
                    scope = dep.scope,
                    isDependencyManagement = dep.section == com.depanalyzer.parser.DependencySection.DEPENDENCY_MANAGEMENT
                )
            }.distinctBy { "${it.groupId}:${it.artifactId}" }
        } catch (e: IllegalArgumentException) {
            if (verbose) {
                System.err.println("[MavenIntegration] Static parsing failed: ${e.message}")
            }
            emptyList()
        }
    }
}
