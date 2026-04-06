package com.depanalyzer.core

import com.depanalyzer.core.graph.ChainResolver
import com.depanalyzer.core.graph.DependencyGraphBuilder
import com.depanalyzer.parser.*
import com.depanalyzer.parser.maven.MavenIntegration
import com.depanalyzer.report.*
import com.depanalyzer.repository.OssIndexClient
import com.depanalyzer.repository.ProjectRepository
import com.depanalyzer.repository.RepositoryClient
import java.io.File
import java.nio.file.Path
import kotlin.io.path.name

class ProjectAnalyzer(
    private val repositoryClient: RepositoryClient = RepositoryClient(),
    private val ossIndexClient: OssIndexClient = OssIndexClient()
) {
    fun analyze(projectDir: Path, includeChains: Boolean = false, disableMaven: Boolean = false, verbose: Boolean = false): DependencyReport {
        val detector = ProjectDetector()
        val type = detector.detect(projectDir)
        val dirFile = projectDir.toFile()

        val (dependencies, repositories) = when (type) {
            ProjectType.MAVEN -> {
                val mavenNodes = MavenIntegration.analyzeMavenProject(
                    projectDir = dirFile,
                    enableMaven = !disableMaven,
                    verbose = verbose
                )

                val parsedDeps = mavenNodes.flatMap { node ->
                    flattenNodeTree(node)
                }
                
                val parser = PomDependencyParser()
                val pomFile = File(dirFile, "pom.xml")
                parsedDeps to parser.repositories(pomFile)
            }
            ProjectType.GRADLE_GROOVY -> {
                val parser = GradleGroovyDependencyParser()
                val buildFile = File(dirFile, "build.gradle")
                parser.parse(buildFile).map { it.toCommon() } to parser.repositories(buildFile)
            }
            ProjectType.GRADLE_KOTLIN -> {
                val catalog = loadVersionCatalog(dirFile)
                val parser = GradleKotlinDependencyParser(catalog)
                val buildFile = File(dirFile, "build.gradle.kts")
                parser.parse(buildFile).map { it.toCommon() } to parser.repositories(buildFile)
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

        val vulnerabilityMap = try {
            ossIndexClient.getVulnerabilities(dependencies)
        } catch (e: Exception) {
            System.err.println("⚠️ Warning: OSS Index authentication failed (401). Vulnerability analysis skipped. Please set OSS_INDEX_USER and OSS_INDEX_TOKEN to enable.")
            if (e.message != null) {
                System.err.println("  Details: ${e.message}")
            }
            emptyMap()
        }

        val (directVulnerable, transitiveVulnerable) = classifyVulnerabilities(
            dependencies = dependencies,
            directDependencies = directDependencies,
            vulnerabilityMap = vulnerabilityMap
        )

        // Build vulnerability chains if requested
        val chains = if (includeChains) {
            buildVulnerabilityChains(dependencies, directDependencies, vulnerabilityMap)
        } else {
            emptyList()
        }

        return DependencyReport(
            projectName = projectDir.name,
            upToDate = upToDate,
            outdated = outdated,
            directVulnerable = directVulnerable,
            transitiveVulnerable = transitiveVulnerable,
            vulnerabilityChains = chains
        )
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

    private fun loadVersionCatalog(projectDir: File): VersionCatalog {
        val catalogFile = File(projectDir, "gradle/libs.versions.toml")
        return if (catalogFile.exists()) {
            VersionCatalogParser().parse(catalogFile)
        } else {
            VersionCatalog()
        }
    }

    private fun isVariable(version: String): Boolean = version.startsWith("$") || version.startsWith($$"${")

    private fun ParsedGradleDependency.toCommon() = ParsedDependency(
        groupId = groupId,
        artifactId = artifactId,
        version = version,
        scope = configuration,
        section = DependencySection.DEPENDENCIES
    )

    /**
     * Flatten a DependencyNode tree into a list of ParsedDependency objects.
     * Recursively traverses all children and converts them to ParsedDependency.
     */
    private fun flattenNodeTree(node: com.depanalyzer.core.graph.DependencyNode): List<ParsedDependency> {
        val result = mutableListOf<ParsedDependency>()
        
        // Add this node
        result.add(
            ParsedDependency(
                groupId = node.groupId,
                artifactId = node.artifactId,
                version = node.version,
                scope = "compile",  // Default scope when using Maven Tree
                section = if (node.isDirectDependency()) DependencySection.DEPENDENCIES else DependencySection.DEPENDENCIES
            )
        )
        
        // Recursively add children
        node.children.forEach { child ->
            result.addAll(flattenNodeTree(child))
        }
        
        return result
    }
}
