package com.depanalyzer.parser.gradle

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.parser.GradleGroovyDependencyParser
import com.depanalyzer.parser.GradleKotlinDependencyParser
import com.depanalyzer.parser.GradleRepositoryParser
import com.depanalyzer.parser.ParsedDependency
import com.depanalyzer.parser.ParsedGradleDependency
import com.depanalyzer.parser.DependencySection
import java.io.File

/**
 * Orchestrates Gradle dependency analysis using a two-tier strategy:
 * 1. Primary: Execute "gradle dependencies" to get full dependency tree
 * 2. Fallback: Use static parsing (regex-based) if gradle is unavailable
 *
 * Similar to MavenIntegration but for Gradle projects.
 */
object GradleIntegration {

    /**
     * Analyzes a Gradle project to extract dependencies.
     * Uses dynamic analysis (gradle dependencies) if available, falls back to static parsing.
     *
     * @param projectDir the gradle project directory
     * @param enableGradle whether to enable gradle command execution (default: true)
     * @param verbose whether to print verbose output (default: false)
     * @return list of DependencyNodes representing the dependency tree
     */
    fun analyzeGradleProject(
        projectDir: File,
        enableGradle: Boolean = true,
        verbose: Boolean = false
    ): List<DependencyNode> {
        require(projectDir.exists() && projectDir.isDirectory) { "Project directory must exist: ${projectDir.absolutePath}" }

        // Check if dynamic analysis is disabled
        if (!enableGradle) {
            if (verbose) {
                System.err.println("[GradleIntegration] Dynamic Gradle analysis disabled, using static parsing")
            }
            return fallbackToStaticParsing(projectDir, verbose)
        }

        // Check if Gradle is available
        if (!GradleDetector.isAvailable()) {
            if (verbose) {
                System.err.println("[GradleIntegration] Gradle not found in PATH, falling back to static parsing")
            }
            return fallbackToStaticParsing(projectDir, verbose)
        }

        // Attempt to execute gradle dependencies
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

    /**
     * Falls back to static parsing (regex-based) when dynamic analysis is unavailable.
     * Detects whether the project uses Groovy DSL (build.gradle) or Kotlin DSL (build.gradle.kts)
     * and uses the appropriate parser.
     *
     * @param projectDir the gradle project directory
     * @param verbose whether to print verbose output
     * @return list of DependencyNodes from static parsing
     */
    private fun fallbackToStaticParsing(projectDir: File, verbose: Boolean = false): List<DependencyNode> {
        if (verbose) {
            System.err.println("[GradleIntegration] Using static parsing fallback")
        }

        // Detect build file type
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

        // Convert ParsedGradleDependency to DependencyNode
        return parsedDeps.filter { it.version != null }.map { dep ->
            DependencyNode(
                id = "${dep.groupId}:${dep.artifactId}:${dep.version}",
                groupId = dep.groupId,
                artifactId = dep.artifactId,
                version = dep.version!!,  // Safely unwrap since we filtered for non-null
                parent = null,
                children = mutableListOf()
            )
        }
    }
}
