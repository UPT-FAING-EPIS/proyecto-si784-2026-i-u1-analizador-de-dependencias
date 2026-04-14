package com.depanalyzer.parser.gradle

import com.depanalyzer.cli.ProgressTracker
import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.parser.GradleGroovyDependencyParser
import com.depanalyzer.parser.GradleKotlinDependencyParser
import java.io.File
import kotlin.time.Duration.Companion.seconds

object GradleIntegration {

    fun analyzeGradleProject(
        projectDir: File,
        enableGradle: Boolean = true,
        verbose: Boolean = false,
        timeoutSeconds: Long = 1800L
    ): List<DependencyNode> {
        require(projectDir.exists() && projectDir.isDirectory) { "Project directory must exist: ${projectDir.absolutePath}" }

        if (!enableGradle) {
            ProgressTracker.logWarning("Análisis dinámico deshabilitado. Usando análisis estático (menos preciso).")
            if (verbose) {
                System.err.println("[GradleIntegration] Dynamic Gradle analysis disabled, using static parsing")
            }
            return fallbackToStaticParsing(projectDir, verbose)
        }

        ProgressTracker.logSearching("Buscando Gradle...")
        if (GradleDetector.findGradleCommand(projectDir, verbose) == null) {
            ProgressTracker.logWarning("Gradle no encontrado. Usando análisis estático (menos preciso).")
            if (verbose) {
                System.err.println("[GradleIntegration] No gradle command found (checked: project wrapper and global gradle), falling back to static parsing")
            }
            return fallbackToStaticParsing(projectDir, verbose)
        }

        ProgressTracker.logProcessing("Analizando dependencias Gradle...")
        return try {
            if (verbose) {
                System.err.println("[GradleIntegration] Starting dynamic Gradle analysis")
            }

            val output = GradleCommandExecutor.execute(
                projectDir,
                timeout = timeoutSeconds.seconds,
                verbose = verbose,
                isDefaultTimeout = (timeoutSeconds == 1800L)
            )
                ?: run {
                    val errorInfo = GradleCommandExecutor.getLastErrorInfo()
                    val errorReason = errorInfo?.let { " (${it.message})" } ?: ""
                    ProgressTracker.logWarning("Análisis dinámico falló$errorReason. Usando análisis estático (menos preciso).")
                    if (verbose) {
                        System.err.println("[GradleIntegration] Gradle command returned null, falling back to static parsing")
                        if (errorInfo != null) {
                            System.err.println("[GradleIntegration] Error type: ${errorInfo.type}")
                            System.err.println("[GradleIntegration] Suggested flags: ${errorInfo.suggestedFlags}")
                        }
                    }
                    return fallbackToStaticParsing(projectDir, verbose)
                }

            val nodes = GradleDependencyTreeParser.parse(output, verbose)
            if (nodes.isEmpty()) {
                ProgressTracker.logWarning("Análisis dinámico falló. Usando análisis estático (menos preciso).")
                if (verbose) {
                    System.err.println("[GradleIntegration] Gradle output parsing produced no nodes, falling back to static parsing")
                }
                fallbackToStaticParsing(projectDir, verbose)
            } else {
                if (verbose) {
                    System.err.println("[GradleIntegration] Successfully parsed ${nodes.size} root dependencies from gradle")
                }
                ProgressTracker.logSuccess("${nodes.size} dependencias encontradas")
                nodes
            }
        } catch (e: Exception) {
            ProgressTracker.logWarning("Análisis dinámico falló. Usando análisis estático (menos preciso).")
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

        val parsedDeps = try {
            when {
                buildFile.name == "build.gradle.kts" -> {
                    if (verbose) {
                        System.err.println("[GradleIntegration] Using Kotlin DSL parser")
                    }
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

        val nodes = parsedDeps.filter { it.version != null }.map { dep ->
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

        ProgressTracker.logSuccess("${nodes.size} dependencias encontradas (análisis estático)")
        return nodes
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
