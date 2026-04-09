package com.depanalyzer.parser.gradle

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.parser.GradleGroovyDependencyParser
import com.depanalyzer.parser.GradleKotlinDependencyParser
import java.io.File

object GradleIntegration {

    fun analyzeGradleProject(
        projectDir: File,
        enableGradle: Boolean = true,
        verbose: Boolean = false
    ): List<DependencyNode> {
        require(projectDir.exists() && projectDir.isDirectory) { "Project directory must exist: ${projectDir.absolutePath}" }

        if (!enableGradle) {
            if (verbose) {
                System.err.println("[GradleIntegration] Dynamic Gradle analysis disabled, using static parsing")
            }
            return fallbackToStaticParsing(projectDir, verbose)
        }

        if (!GradleDetector.isAvailable()) {
            if (verbose) {
                System.err.println("[GradleIntegration] Gradle not found in PATH, falling back to static parsing")
            }
            return fallbackToStaticParsing(projectDir, verbose)
        }

        return try {
            if (verbose) {
                System.err.println("[GradleIntegration] Starting dynamic Gradle analysis")
            }

            val output = GradleCommandExecutor.execute(projectDir, verbose = verbose)
                ?: run {
                    if (verbose) {
                        System.err.println("[GradleIntegration] Gradle command returned null, falling back to static parsing")
                    }
                    return fallbackToStaticParsing(projectDir, verbose)
                }

            val nodes = GradleDependencyTreeParser.parse(output, verbose)
            if (nodes.isEmpty()) {
                if (verbose) {
                    System.err.println("[GradleIntegration] Gradle output parsing produced no nodes, falling back to static parsing")
                }
                fallbackToStaticParsing(projectDir, verbose)
            } else {
                if (verbose) {
                    System.err.println("[GradleIntegration] Successfully parsed ${nodes.size} root dependencies from gradle")
                }
                nodes
            }
        } catch (e: Exception) {
            if (verbose) {
                System.err.println("[GradleIntegration] Exception during Gradle analysis, falling back to static parsing")
                e.printStackTrace(System.err)
            }
            fallbackToStaticParsing(projectDir, verbose)
        }
    }

    private fun fallbackToStaticParsing(projectDir: File, verbose: Boolean = false): List<DependencyNode> {
        if (verbose) {
            System.err.println("[GradleIntegration] Using static parsing fallback")
        }

        val buildFileKts = File(projectDir, "build.gradle.kts")
        val buildFileGroovy = File(projectDir, "build.gradle")

        val buildFile = when {
            buildFileKts.exists() -> buildFileKts
            buildFileGroovy.exists() -> buildFileGroovy
            else -> {
                if (verbose) {
                    System.err.println("[GradleIntegration] No build.gradle or build.gradle.kts found")
                }
                return emptyList()
            }
        }

        // Parse using appropriate parser
        val parsedDeps = try {
            when {
                buildFile.name == "build.gradle.kts" -> {
                    if (verbose) {
                        System.err.println("[GradleIntegration] Using Kotlin DSL parser")
                    }
                    // Note: Version catalog loading would happen here in a real implementation
                    GradleKotlinDependencyParser().parse(buildFile)
                }

                else -> {
                    if (verbose) {
                        System.err.println("[GradleIntegration] Using Groovy DSL parser")
                    }
                    GradleGroovyDependencyParser().parse(buildFile)
                }
            }
        } catch (e: Exception) {
            if (verbose) {
                System.err.println("[GradleIntegration] Error during static parsing:")
                e.printStackTrace(System.err)
            }
            emptyList()
        }

        return parsedDeps.filter { it.version != null }.map { dep ->
            DependencyNode(
                id = "${dep.groupId}:${dep.artifactId}:${dep.version}",
                groupId = dep.groupId,
                artifactId = dep.artifactId,
                version = dep.version!!,
                parent = null,
                children = mutableListOf(),
                scope = mapConfigurationToScope(dep.configuration),
                isDependencyManagement = false
            )
        }
    }

    private fun mapConfigurationToScope(configName: String): String {
        return when {
            configName.contains("compile", ignoreCase = true) && !configName.contains("test", ignoreCase = true) ->
                "compile"

            configName.contains("runtime", ignoreCase = true) && !configName.contains("test", ignoreCase = true) ->
                "runtime"

            configName.contains("test", ignoreCase = true) -> "test"
            configName.contains("provided", ignoreCase = true) -> "provided"
            else -> "compile"
        }
    }
}
