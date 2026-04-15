package com.depanalyzer.core

import com.depanalyzer.cli.ProgressTracker
import com.depanalyzer.core.graph.ChainResolver
import com.depanalyzer.core.graph.DependencyGraphBuilder
import com.depanalyzer.parser.*
import com.depanalyzer.parser.gradle.GradleIntegration
import com.depanalyzer.parser.maven.MavenIntegration
import com.depanalyzer.report.*
import com.depanalyzer.repository.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.name

class ProjectAnalyzer(
    private val repositoryClient: RepositoryClient = RepositoryClient(),
    private val ossIndexClient: OssIndexClient = OssIndexClient(),
    private val nvdClient: NvdClient = NvdClient(),
    private val projectDetector: ProjectDetector = ProjectDetector()
) {
    fun analyze(
        projectDir: Path,
        includeChains: Boolean = false,
        disableMaven: Boolean = false,
        disableGradle: Boolean = false,
        verbose: Boolean = false,
        treeMaxDepth: Int? = null,
        treeExpandMode: TreeExpandMode = TreeExpandMode.ALL,
        timeoutSeconds: Long = 1800L,
        useNvd: Boolean = false
    ): DependencyReport {
        val type = projectDetector.detect(projectDir)
        val dirFile = projectDir.toFile()

        ProgressTracker.logDetected("Proyecto detectado: $type")

        val (dependencies, repositories, rootNodes) = when (type) {
            ProjectType.MAVEN -> {
                ProgressTracker.logProcessing("Analizando proyecto Maven...")
                val mavenNodes = MavenIntegration.analyzeMavenProject(
                    projectDir = dirFile,
                    enableMaven = !disableMaven,
                    verbose = verbose,
                    timeoutSeconds = timeoutSeconds
                )

                val parsedDeps = mavenNodes.flatMap { node ->
                    flattenNodeTree(node)
                }

                val parser = PomDependencyParser()
                val pomFile = File(dirFile, "pom.xml")
                Triple(parsedDeps, parser.repositories(pomFile), mavenNodes)
            }

            ProjectType.GRADLE_GROOVY -> {
                ProgressTracker.logProcessing("Analizando proyecto Gradle (build.gradle)...")
                val gradleNodes = GradleIntegration.analyzeGradleProject(
                    projectDir = dirFile,
                    enableGradle = !disableGradle,
                    verbose = verbose,
                    timeoutSeconds = timeoutSeconds
                )

                val parsedDeps = gradleNodes.flatMap { node ->
                    flattenNodeTree(node)
                }

                val buildFile = File(dirFile, "build.gradle")
                Triple(parsedDeps, GradleRepositoryParser().parse(buildFile), gradleNodes)
            }

            ProjectType.GRADLE_KOTLIN -> {
                ProgressTracker.logProcessing("Analizando proyecto Gradle (build.gradle.kts)...")
                val gradleNodes = GradleIntegration.analyzeGradleProject(
                    projectDir = dirFile,
                    enableGradle = !disableGradle,
                    verbose = verbose,
                    timeoutSeconds = timeoutSeconds
                )

                val parsedDeps = gradleNodes.flatMap { node ->
                    flattenNodeTree(node)
                }

                val buildFile = File(dirFile, "build.gradle.kts")
                Triple(parsedDeps, GradleRepositoryParser().parse(buildFile), gradleNodes)
            }
        }

        val upToDate = mutableListOf<DependencyInfo>()
        val outdated = mutableListOf<OutdatedDependency>()
        val directDependencies = mutableListOf<ParsedDependency>()

        dependencies.distinctBy { "${it.groupId}:${it.artifactId}" }.forEach { dep ->
            val currentVersion = dep.version
            if (currentVersion != null && !isVariable(currentVersion)) {
                directDependencies.add(dep)
                val latest = findLatestVersion(repositories, dep.groupId, dep.artifactId)
                if (latest != null && latest != currentVersion) {
                    outdated.add(OutdatedDependency(dep.groupId, dep.artifactId, currentVersion, latest))
                } else {
                    upToDate.add(DependencyInfo(dep.groupId, dep.artifactId, currentVersion))
                }
            } else {
                upToDate.add(DependencyInfo(dep.groupId, dep.artifactId, currentVersion ?: "unknown"))
            }
        }

        ProgressTracker.logSecurity("Consultando vulnerabilidades...")
        val vulnerabilityMap = try {
            val ossIndexVulns = ossIndexClient.getVulnerabilities(dependencies)

            if (useNvd) {
                ProgressTracker.logSecurity("Enriqueciendo con datos de NVD...")
                val nvdVulns = try {
                    nvdClient.getVulnerabilities(dependencies)
                } catch (e: Exception) {
                    if (verbose) {
                        System.err.println("  NVD enrichment failed: ${e.message}")
                    }
                    emptyMap()
                }

                VulnerabilityMerger.mergeVulnerabilities(ossIndexVulns, nvdVulns)
            } else {
                ossIndexVulns
            }
        } catch (e: Exception) {
            ProgressTracker.logWarning("OSS Index authentication failed (401). Vulnerability analysis skipped.")
            if (verbose) {
                System.err.println("  Details: ${e.message}")
            }
            emptyMap()
        }

        val (directVulnerable, transitiveVulnerable) = classifyVulnerabilities(
            dependencies = dependencies,
            directDependencies = directDependencies,
            vulnerabilityMap = vulnerabilityMap
        )

        val chains = if (includeChains) {
            buildVulnerabilityChains(dependencies, directDependencies, vulnerabilityMap)
        } else {
            emptyList()
        }

        ProgressTracker.logBuilding("Construyendo árbol de dependencias...")
        val dependencyTree = buildDependencyTree(
            vulnerabilityMap = vulnerabilityMap,
            outdatedMap = outdated,
            maxDepth = treeMaxDepth,
            expandMode = treeExpandMode,
            rootNodes = rootNodes
        )

        return DependencyReport(
            projectName = projectDir.name,
            upToDate = upToDate,
            outdated = outdated,
            directVulnerable = directVulnerable,
            transitiveVulnerable = transitiveVulnerable,
            vulnerabilityChains = chains,
            dependencyTree = dependencyTree
        )
    }

    private fun buildDependencyTree(
        vulnerabilityMap: Map<String, List<Vulnerability>>,
        outdatedMap: List<OutdatedDependency>,
        maxDepth: Int?,
        expandMode: TreeExpandMode,
        rootNodes: List<com.depanalyzer.core.graph.DependencyNode>
    ): List<DependencyTreeNode>? {
        if (rootNodes.isEmpty()) {
            return null
        }

        val outdatedByCoordinate = outdatedMap.associate {
            "${it.groupId}:${it.artifactId}:${it.currentVersion}" to it
        }

        val builder = DependencyTreeBuilder(
            vulnerabilities = vulnerabilityMap,
            outdatedMap = outdatedByCoordinate
        )

        return builder.buildTree(rootNodes, maxDepth, expandMode).takeIf { it.isNotEmpty() }
    }

    private fun classifyVulnerabilities(
        dependencies: List<ParsedDependency>,
        directDependencies: List<ParsedDependency>,
        vulnerabilityMap: Map<String, List<Vulnerability>>
    ): Pair<List<VulnerableDependency>, List<VulnerableDependency>> {
        val direct = mutableListOf<VulnerableDependency>()
        val transitive = mutableListOf<VulnerableDependency>()

        val directCoordinates = directDependencies.map { "${it.groupId}:${it.artifactId}:${it.version}" }.toSet()

        vulnerabilityMap.forEach { (coordinates, vulnerabilities) ->
            val dep = dependencies.find { "${it.groupId}:${it.artifactId}:${it.version}" == coordinates }
                ?: return@forEach

            val vulnerableDep = VulnerableDependency(
                groupId = dep.groupId,
                artifactId = dep.artifactId,
                version = dep.version!!,
                vulnerabilities = vulnerabilities,
                dependencyChain = buildDependencyChain(coordinates, dependencies, directDependencies)
            )

            if (coordinates in directCoordinates) {
                direct.add(vulnerableDep)
            } else {
                transitive.add(vulnerableDep)
            }
        }

        return Pair(direct, transitive)
    }

    private fun buildDependencyChain(
        targetCoordinates: String,
        allDependencies: List<ParsedDependency>,
        directDependencies: List<ParsedDependency>
    ): List<String>? {
        if (directDependencies.any { "${it.groupId}:${it.artifactId}:${it.version}" == targetCoordinates }) {
            return null
        }

        val target = allDependencies.find { "${it.groupId}:${it.artifactId}:${it.version}" == targetCoordinates }
            ?: return null

        return listOf(target.groupId + ":" + target.artifactId)
    }

    private fun buildVulnerabilityChains(
        dependencies: List<ParsedDependency>,
        directDependencies: List<ParsedDependency>,
        vulnerabilityMap: Map<String, List<Vulnerability>>
    ): List<com.depanalyzer.core.graph.VulnerabilityChain> {
        val builder = DependencyGraphBuilder()
        val graph = builder.buildGraph(
            directDependencies = directDependencies,
            allDependencies = dependencies,
            vulnerabilities = vulnerabilityMap
        )

        return ChainResolver.resolveAllChains(graph, vulnerabilityMap)
    }

    private fun findLatestVersion(repos: List<ProjectRepository>, groupId: String, artifactId: String): String? {
        for (repo in repos) {
            val version = repositoryClient.getLatestVersion(repo, groupId, artifactId)
            if (version != null) return version
        }
        return null
    }

    private fun isVariable(version: String): Boolean = version.startsWith("$") || version.startsWith($$"${")

    private fun flattenNodeTree(node: com.depanalyzer.core.graph.DependencyNode): List<ParsedDependency> {
        val result = mutableListOf<ParsedDependency>()

        result.add(
            ParsedDependency(
                groupId = node.groupId,
                artifactId = node.artifactId,
                version = node.version,
                scope = node.scope ?: "compile",
                section = if (node.isDependencyManagement) DependencySection.DEPENDENCY_MANAGEMENT else DependencySection.DEPENDENCIES
            )
        )

        node.children.forEach { child ->
            result.addAll(flattenNodeTree(child))
        }

        return result
    }
}
