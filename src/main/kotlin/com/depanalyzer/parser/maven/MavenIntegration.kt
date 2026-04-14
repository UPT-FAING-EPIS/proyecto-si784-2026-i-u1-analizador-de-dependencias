package com.depanalyzer.parser.maven

import com.depanalyzer.cli.ProgressTracker
import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.parser.PomDependencyParser
import java.io.File
import kotlin.time.Duration.Companion.seconds

object MavenIntegration {
    private val staticParser = PomDependencyParser()

    fun analyzeMavenProject(
        projectDir: File,
        enableMaven: Boolean = true,
        verbose: Boolean = false,
        timeoutSeconds: Long = 1800L
    ): List<DependencyNode> {
        val pomFile = File(projectDir, "pom.xml")

        if (!enableMaven) {
            ProgressTracker.logWarning("Análisis dinámico deshabilitado. Usando análisis estático (menos preciso).")
            if (verbose) {
                System.err.println("[MavenIntegration] Offline mode enabled. Using static analysis (less precise).")
            }
            return fallbackToStaticParsing(pomFile, verbose)
        }

        ProgressTracker.logSearching("Buscando Maven...")
        if (MavenDetector.findMavenCommand(projectDir, verbose) == null) {
            ProgressTracker.logWarning("Maven no encontrado. Usando análisis estático (menos preciso).")
            if (verbose) {
                System.err.println("[MavenIntegration] No maven command found (checked: project wrapper and global mvn), falling back to static parsing")
            }
            return fallbackToStaticParsing(pomFile, verbose)
        }

        ProgressTracker.logProcessing("Analizando dependencias Maven...")
        val treeOutput = MavenCommandExecutor.execute(
            projectDir,
            timeout = timeoutSeconds.seconds,
            verbose = verbose,
            isDefaultTimeout = (timeoutSeconds == 1800L)
        )
        if (treeOutput == null) {
            ProgressTracker.logWarning("Análisis dinámico falló. Usando análisis estático (menos preciso).")
            if (verbose) {
                System.err.println("[MavenIntegration] Maven execution failed, timed out, or produced no output")
            }
            return fallbackToStaticParsing(pomFile, verbose)
        }

        if (verbose) {
            System.err.println("[MavenIntegration] Using dynamic Maven analysis")
        }

        val parsedNodes = MavenDependencyTreeParser.parse(treeOutput, verbose = verbose)
        ProgressTracker.logSuccess("${parsedNodes.size} dependencias encontradas")
        return parsedNodes
    }

    private fun fallbackToStaticParsing(pomFile: File, verbose: Boolean = false): List<DependencyNode> {
        if (verbose) {
            System.err.println("[MavenIntegration] Falling back to static parsing")
        }
        return try {
            val parsedDeps = staticParser.parse(pomFile)

            if (verbose) {
                System.err.println("[MavenIntegration] Static parsing found ${parsedDeps.size} dependencies")
            }

            val nodes = parsedDeps.map { dep ->
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

            ProgressTracker.logSuccess("${nodes.size} dependencias encontradas (análisis estático)")
            nodes
        } catch (e: IllegalArgumentException) {
            if (verbose) {
                System.err.println("[MavenIntegration] Static parsing failed: ${e.message}")
            }
            emptyList()
        }
    }
}
