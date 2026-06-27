package com.depanalyzer.core

import com.depanalyzer.cli.ProgressTracker
import com.depanalyzer.cli.VulnerabilitySourceMode
import com.depanalyzer.core.graph.ChainResolver
import com.depanalyzer.core.graph.DependencyGraphBuilder
import com.depanalyzer.parser.*
import com.depanalyzer.parser.gradle.GradleIntegration
import com.depanalyzer.parser.maven.MavenIntegration
import com.depanalyzer.parser.npm.NpmIntegration
import com.depanalyzer.parser.python.PythonIntegration
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
        vulnerabilitySourceMode: VulnerabilitySourceMode = VulnerabilitySourceMode.AUTO,
        showCommandOutput: Boolean = false,
        onPartialReport: ((DependencyReport) -> Unit)? = null
    ): DependencyReport {
        val analysisStartedAt = System.currentTimeMillis()
        ProgressTracker.advanceProgress("Detección")
        val type = projectDetector.detect(projectDir)
        val dirFile = projectDir.toFile()
        val requestedMode = when (type) {
            ProjectType.MAVEN -> if (disableMaven) AnalysisMode.STATIC else AnalysisMode.DYNAMIC
            ProjectType.GRADLE_GROOVY,
            ProjectType.GRADLE_KOTLIN -> if (disableGradle) AnalysisMode.STATIC else AnalysisMode.DYNAMIC
            else -> AnalysisMode.STATIC
        }
        var actualMode = requestedMode
        val analysisWarnings = mutableListOf<String>()
        val resolutionModeListener: (AnalysisMode, String?) -> Unit = { mode, warning ->
            actualMode = mode
            warning?.let { analysisWarnings += it }
        }

        ProgressTracker.logDetected("Proyecto detectado: $type")

        ProgressTracker.advanceProgress("Parseo")
        val (dependencies, rootNodes) = when (type) {
            ProjectType.MAVEN -> {
                ProgressTracker.logProcessing("Analizando proyecto Maven...")
                val mavenNodes = MavenIntegration.analyzeMavenProject(
                    projectDir = dirFile,
                    enableMaven = !disableMaven,
                    verbose = verbose,
                    timeoutSeconds = timeoutSeconds,
                    showCommandOutput = showCommandOutput,
                    onResolutionMode = resolutionModeListener
                )

                val parsedDeps = mavenNodes.flatMap { node ->
                    flattenNodeTree(node)
                }
                Pair(parsedDeps, mavenNodes)
            }

            ProjectType.GRADLE_GROOVY -> {
                ProgressTracker.logProcessing("Analizando proyecto Gradle (build.gradle)...")
                val gradleNodes = GradleIntegration.analyzeGradleProject(
                    projectDir = dirFile,
                    enableGradle = !disableGradle,
                    verbose = verbose,
                    timeoutSeconds = timeoutSeconds,
                    showCommandOutput = showCommandOutput,
                    onResolutionMode = resolutionModeListener
                )

                val parsedDeps = gradleNodes.flatMap { node ->
                    flattenNodeTree(node)
                }
                Pair(parsedDeps, gradleNodes)
            }

            ProjectType.GRADLE_KOTLIN -> {
                ProgressTracker.logProcessing("Analizando proyecto Gradle (build.gradle.kts)...")
                val gradleNodes = GradleIntegration.analyzeGradleProject(
                    projectDir = dirFile,
                    enableGradle = !disableGradle,
                    verbose = verbose,
                    timeoutSeconds = timeoutSeconds,
                    showCommandOutput = showCommandOutput,
                    onResolutionMode = resolutionModeListener
                )

                val parsedDeps = gradleNodes.flatMap { node ->
                    flattenNodeTree(node)
                }
                Pair(parsedDeps, gradleNodes)
            }

            ProjectType.NPM -> {
                ProgressTracker.logProcessing("Analizando proyecto Node.js (package.json)...")
                NpmIntegration.analyzeNpmProject(dirFile)
            }

            ProjectType.PYTHON_POETRY -> {
                ProgressTracker.logProcessing("Analizando proyecto Python (pyproject.toml/poetry.lock)...")
                PythonIntegration.analyzePoetryProject(dirFile)
            }

            ProjectType.PYTHON_REQUIREMENTS -> {
                ProgressTracker.logProcessing("Analizando proyecto Python (requirements.txt)...")
                PythonIntegration.analyzeRequirementsProject(dirFile)
            }
        }

        ProgressTracker.advanceProgress("Resolución de repos")
        val repositories = when (type) {
            ProjectType.MAVEN -> {
                val parser = PomDependencyParser()
                val pomFile = File(dirFile, "pom.xml")
                parser.repositories(pomFile)
            }

            ProjectType.GRADLE_GROOVY -> {
                val buildFile = File(dirFile, "build.gradle")
                GradleRepositoryParser().parse(buildFile)
            }

            ProjectType.GRADLE_KOTLIN -> {
                val buildFile = File(dirFile, "build.gradle.kts")
                GradleRepositoryParser().parse(buildFile)
            }

            ProjectType.NPM,
            ProjectType.PYTHON_POETRY,
            ProjectType.PYTHON_REQUIREMENTS -> {
                emptyList()
            }
        }

        val upToDate = mutableListOf<DependencyInfo>()
        val outdated = mutableListOf<OutdatedDependency>()
        val directDependencies = rootNodes.map { root ->
            ParsedDependency(
                groupId = root.groupId,
                artifactId = root.artifactId,
                version = root.version,
                scope = root.scope ?: "compile",
                section = if (root.isDependencyManagement) DependencySection.DEPENDENCY_MANAGEMENT else DependencySection.DEPENDENCIES,
                ecosystem = root.ecosystem
            )
        }.distinctBy { "${it.ecosystem}:${it.groupId}:${it.artifactId}:${it.version}" }

        val initialTree = buildDependencyTree(
            vulnerabilityMap = emptyMap(),
            outdatedMap = emptyList(),
            maxDepth = treeMaxDepth,
            expandMode = treeExpandMode,
            rootNodes = rootNodes
        )
        emitPartial(
            onPartialReport,
            DependencyReport(
                projectName = projectDir.name,
                dependencyTree = initialTree
            )
        )

        ProgressTracker.advanceProgress("Consulta de versiones")
        val distinctDependencies = dependencies.distinctBy { "${it.ecosystem}:${it.groupId}:${it.artifactId}" }
        distinctDependencies.forEachIndexed { index, dep ->
            val currentVersion = dep.version
            if (currentVersion != null && !isVariable(currentVersion)) {
                val latest = findLatestVersion(repositories, dep)
                if (latest != null && latest != currentVersion) {
                    outdated.add(
                        OutdatedDependency(
                            groupId = dep.groupId,
                            artifactId = dep.artifactId,
                            currentVersion = currentVersion,
                            latestVersion = latest,
                            ecosystem = dep.ecosystem
                        )
                    )
                } else {
                    upToDate.add(
                        DependencyInfo(
                            groupId = dep.groupId,
                            artifactId = dep.artifactId,
                            version = currentVersion,
                            ecosystem = dep.ecosystem
                        )
                    )
                }
            } else {
                upToDate.add(
                    DependencyInfo(
                        groupId = dep.groupId,
                        artifactId = dep.artifactId,
                        version = currentVersion ?: "unknown",
                        ecosystem = dep.ecosystem
                    )
                )
            }

            val shouldEmitProgressiveSnapshot = onPartialReport != null &&
                    ((index + 1) % 4 == 0 || index == distinctDependencies.lastIndex)
            if (shouldEmitProgressiveSnapshot) {
                val progressiveTree = buildDependencyTree(
                    vulnerabilityMap = emptyMap(),
                    outdatedMap = outdated,
                    maxDepth = treeMaxDepth,
                    expandMode = treeExpandMode,
                    rootNodes = rootNodes
                )
                emitPartial(
                    onPartialReport,
                    DependencyReport(
                        projectName = projectDir.name,
                        upToDate = upToDate.toList(),
                        outdated = outdated.toList(),
                        dependencyTree = progressiveTree
                    )
                )
            }
        }

        val versionedTree = buildDependencyTree(
            vulnerabilityMap = emptyMap(),
            outdatedMap = outdated,
            maxDepth = treeMaxDepth,
            expandMode = treeExpandMode,
            rootNodes = rootNodes
        )
        emitPartial(
            onPartialReport,
            DependencyReport(
                projectName = projectDir.name,
                upToDate = upToDate,
                outdated = outdated,
                dependencyTree = versionedTree
            )
        )

        ProgressTracker.advanceProgress("Árbol transitivo")
        // El árbol parcial ya se generó y emitió en `versionedTree` para la UI.

        ProgressTracker.logSecurity("Consultando vulnerabilidades...")
        ProgressTracker.advanceProgress("CVEs")
        val mavenDependencies = dependencies.filter { it.ecosystem == Ecosystem.MAVEN }
        val hasNonMaven = dependencies.any { it.ecosystem != Ecosystem.MAVEN }
        val providersUsed = mutableListOf<String>()
        val providerWarnings = mutableListOf<String>()
        val vulnerabilityMap = when (vulnerabilitySourceMode) {
            VulnerabilitySourceMode.OSS_ONLY -> {
                try {
                    ossIndexClient.getVulnerabilities(dependencies, failOnError = true).also {
                        providersUsed += "OSS_INDEX"
                    }
                } catch (e: Exception) {
                    throw IllegalStateException("[OSS] no se pudo obtener vulnerabilidades: ${e.message}", e)
                }
            }

            VulnerabilitySourceMode.NVD_ONLY -> {
                if (mavenDependencies.isEmpty()) {
                    val warning = "NVD solo aplica a dependencias Maven. CVE analysis skipped."
                    ProgressTracker.logWarning(warning)
                    providerWarnings += warning
                    emptyMap()
                } else {
                    try {
                        nvdClient.getVulnerabilities(mavenDependencies).also {
                            providersUsed += "NVD"
                        }
                    } catch (e: Exception) {
                        throw IllegalStateException("[NVD] no se pudo obtener vulnerabilidades: ${e.message}", e)
                    }
                }
            }

            VulnerabilitySourceMode.AUTO -> {
                val ossVulns = runCatching {
                    ossIndexClient.getVulnerabilities(dependencies, failOnError = true)
                }.onSuccess {
                    providersUsed += "OSS_INDEX"
                }.onFailure {
                    providerWarnings += "OSS Index no disponible: ${it.message ?: it.javaClass.simpleName}"
                }.getOrNull()
                if (ossVulns != null) {
                    val nvdVulns = if (mavenDependencies.isNotEmpty()) {
                        runCatching {
                            ProgressTracker.logSecurity("Enriqueciendo con datos de NVD...")
                            nvdClient.getVulnerabilities(mavenDependencies)
                        }.onSuccess {
                            providersUsed += "NVD"
                        }.getOrElse {
                            providerWarnings += "NVD no disponible: ${it.message ?: it.javaClass.simpleName}"
                            if (verbose) {
                                System.err.println("  NVD enrichment failed: ${it.message}")
                            }
                            emptyMap()
                        }
                    } else {
                        if (hasNonMaven && verbose) {
                            System.err.println("  NVD enrichment skipped for non-Maven ecosystems")
                        }
                        emptyMap()
                    }
                    VulnerabilityMerger.mergeVulnerabilities(ossVulns, nvdVulns)
                } else {
                    runCatching {
                        if (mavenDependencies.isEmpty()) emptyMap() else nvdClient.getVulnerabilities(mavenDependencies)
                    }
                        .onSuccess {
                            if (mavenDependencies.isNotEmpty()) providersUsed += "NVD"
                        }
                        .getOrElse {
                            val warning = "No se pudo consultar OSS ni NVD. Vulnerability analysis skipped."
                            providerWarnings += "NVD no disponible: ${it.message ?: it.javaClass.simpleName}"
                            ProgressTracker.logWarning(warning)
                            if (verbose) {
                                System.err.println("  Details OSS/NVD: ${it.message}")
                            }
                            emptyMap()
                        }
                }
            }
        }

        val (directVulnerable, transitiveVulnerable) = classifyVulnerabilities(
            dependencies = dependencies,
            directDependencies = directDependencies,
            vulnerabilityMap = vulnerabilityMap
        )

        val cveTree = buildDependencyTree(
            vulnerabilityMap = vulnerabilityMap,
            outdatedMap = outdated,
            maxDepth = treeMaxDepth,
            expandMode = treeExpandMode,
            rootNodes = rootNodes
        )
        emitPartial(
            onPartialReport,
            DependencyReport(
                projectName = projectDir.name,
                upToDate = upToDate,
                outdated = outdated,
                directVulnerable = directVulnerable,
                transitiveVulnerable = transitiveVulnerable,
                dependencyTree = cveTree
            )
        )

        val chains = if (includeChains) {
            buildVulnerabilityChains(dependencies, directDependencies, vulnerabilityMap)
        } else {
            emptyList()
        }

        ProgressTracker.logBuilding("Construyendo reporte final...")
        val dependencyTree = buildDependencyTree(
            vulnerabilityMap = vulnerabilityMap,
            outdatedMap = outdated,
            maxDepth = treeMaxDepth,
            expandMode = treeExpandMode,
            rootNodes = rootNodes
        )

        val report = DependencyReport(
            projectName = projectDir.name,
            upToDate = upToDate,
            outdated = outdated,
            directVulnerable = directVulnerable,
            transitiveVulnerable = transitiveVulnerable,
            vulnerabilityChains = chains,
            dependencyTree = dependencyTree,
            analysis = AnalysisMetadata(
                requestedMode = requestedMode,
                actualMode = actualMode,
                projectType = type.name,
                ecosystems = dependencies.map { it.ecosystem.name }.distinct().sorted(),
                durationMs = System.currentTimeMillis() - analysisStartedAt,
                warnings = analysisWarnings.distinct(),
                providers = ProviderAnalysisMetadata(
                    requested = vulnerabilitySourceMode.name,
                    used = providersUsed.distinct(),
                    warnings = providerWarnings.distinct(),
                    statuses = providerStatuses(providersUsed, providerWarnings)
                )
            )
        )
        val finalReport = attachDirectSourceLocations(
            report = report,
            projectType = type,
            projectDir = dirFile,
            directDependencies = directDependencies
        )
        emitPartial(onPartialReport, finalReport)
        return finalReport
    }

    private fun providerStatuses(
        providersUsed: List<String>,
        warnings: List<String>
    ): Map<String, String> = linkedMapOf(
        "OSS_INDEX" to providerStatus("OSS_INDEX", providersUsed, warnings),
        "NVD" to providerStatus("NVD", providersUsed, warnings)
    )

    private fun providerStatus(
        provider: String,
        providersUsed: List<String>,
        warnings: List<String>
    ): String {
        val displayName = provider.replace('_', ' ')
        if (provider in providersUsed) {
            return if (warnings.any { it.contains(displayName, ignoreCase = true) }) "DEGRADED" else "AVAILABLE"
        }
        return if (warnings.any { it.contains(displayName, ignoreCase = true) }) "UNAVAILABLE" else "NOT_USED"
    }

    private fun emitPartial(
        onPartialReport: ((DependencyReport) -> Unit)?,
        report: DependencyReport
    ) {
        if (onPartialReport == null) return
        runCatching { onPartialReport(report) }
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

        val outdatedByCoordinate = outdatedMap.associateBy { "${it.groupId}:${it.artifactId}:${it.currentVersion}" }

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
                dependencyChain = buildDependencyChain(coordinates, dependencies, directDependencies),
                ecosystem = dep.ecosystem
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

    private fun findLatestVersion(repos: List<ProjectRepository>, dependency: ParsedDependency): String? {
        return repositoryClient.getLatestVersion(dependency, repos)
    }

    private fun isVariable(version: String): Boolean {
        return version.startsWith("$") ||
                version.startsWith($$"${") ||
                version.any { it in "^~><=*!,| " }
    }

    private fun flattenNodeTree(node: com.depanalyzer.core.graph.DependencyNode): List<ParsedDependency> {
        val result = mutableListOf<ParsedDependency>()

        result.add(
            ParsedDependency(
                groupId = node.groupId,
                artifactId = node.artifactId,
                version = node.version,
                scope = node.scope ?: "compile",
                section = if (node.isDependencyManagement) DependencySection.DEPENDENCY_MANAGEMENT else DependencySection.DEPENDENCIES,
                ecosystem = node.ecosystem
            )
        )

        node.children.forEach { child ->
            result.addAll(flattenNodeTree(child))
        }

        return result
    }

    private fun attachDirectSourceLocations(
        report: DependencyReport,
        projectType: ProjectType,
        projectDir: File,
        directDependencies: List<ParsedDependency>
    ): DependencyReport {
        val buildFile = when (projectType) {
            ProjectType.MAVEN -> File(projectDir, "pom.xml")
            ProjectType.GRADLE_GROOVY -> File(projectDir, "build.gradle")
            ProjectType.GRADLE_KOTLIN -> File(projectDir, "build.gradle.kts")
            ProjectType.NPM -> File(projectDir, "package.json")
            ProjectType.PYTHON_POETRY -> File(projectDir, "pyproject.toml")
            ProjectType.PYTHON_REQUIREMENTS -> File(projectDir, "requirements.txt")
        }
        if (!buildFile.isFile) return report

        val lines = runCatching { buildFile.readLines() }.getOrElse { return report }
        val locations = directDependencies.associateNotNull { dependency ->
            val match = lines.withIndex().firstOrNull { (_, line) ->
                line.contains(dependency.artifactId, ignoreCase = dependency.ecosystem != Ecosystem.MAVEN)
            } ?: return@associateNotNull null
            val start = match.value.indexOf(
                string = dependency.artifactId,
                ignoreCase = dependency.ecosystem != Ecosystem.MAVEN
            )
            if (start < 0) return@associateNotNull null

            dependency.locationKey() to DependencySourceLocation(
                file = buildFile.name,
                line = match.index + 1,
                startColumn = start + 1,
                endColumn = start + dependency.artifactId.length + 1
            )
        }

        return report.copy(
            upToDate = report.upToDate.map { dependency ->
                dependency.copy(sourceLocation = locations[dependency.locationKey()])
            },
            outdated = report.outdated.map { dependency ->
                dependency.copy(sourceLocation = locations[dependency.locationKey()])
            },
            directVulnerable = report.directVulnerable.map { dependency ->
                dependency.copy(sourceLocation = locations[dependency.locationKey()])
            }
        )
    }

    private fun ParsedDependency.locationKey(): String = "$ecosystem:$groupId:$artifactId"
    private fun DependencyInfo.locationKey(): String = "$ecosystem:$groupId:$artifactId"
    private fun OutdatedDependency.locationKey(): String = "$ecosystem:$groupId:$artifactId"
    private fun VulnerableDependency.locationKey(): String = "$ecosystem:$groupId:$artifactId"

    private inline fun <T, K, V> Iterable<T>.associateNotNull(
        transform: (T) -> Pair<K, V>?
    ): Map<K, V> {
        val destination = LinkedHashMap<K, V>()
        for (element in this) {
            val pair = transform(element) ?: continue
            destination[pair.first] = pair.second
        }
        return destination
    }
}
