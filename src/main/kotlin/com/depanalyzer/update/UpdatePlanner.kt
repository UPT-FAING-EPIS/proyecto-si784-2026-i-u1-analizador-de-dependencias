package com.depanalyzer.update

import com.depanalyzer.core.ProjectAnalyzer
import com.depanalyzer.parser.*
import java.io.File
import java.nio.file.Path

data class UpdatePlan(
    val projectType: ProjectType,
    val buildFile: File,
    val suggestions: List<UpdateSuggestion>
)

data class UpdateAnalysisOptions(
    val dynamic: Boolean = false,
    val timeoutSeconds: Long = 1800L
)

interface UpdatePlanner {
    fun plan(projectDir: Path, options: UpdateAnalysisOptions = UpdateAnalysisOptions()): UpdatePlan
}

class AnalyzerUpdatePlanner(
    private val analyzer: ProjectAnalyzer = ProjectAnalyzer(),
    private val detector: ProjectDetector = ProjectDetector(),
    private val pomParser: PomDependencyParser = PomDependencyParser(),
    private val gradleGroovyParser: GradleGroovyDependencyParser = GradleGroovyDependencyParser(),
    private val gradleKotlinParser: GradleKotlinDependencyParser = GradleKotlinDependencyParser()
) : UpdatePlanner {
    override fun plan(projectDir: Path, options: UpdateAnalysisOptions): UpdatePlan {
        val projectType = detector.detect(projectDir)
        val report = analyzer.analyze(
            projectDir = projectDir,
            disableMaven = !options.dynamic,
            disableGradle = !options.dynamic,
            timeoutSeconds = options.timeoutSeconds
        )
        val buildFile = resolveBuildFile(projectDir, projectType)
        val declaredCoordinates = declaredCoordinates(buildFile, projectType)
        val vulnerableCoordinates = (report.directVulnerable + report.transitiveVulnerable)
            .map { "${it.groupId}:${it.artifactId}" }
            .toSet()
        val outdatedByGaAndVersion = report.outdated.associateBy(
            keySelector = { "${it.groupId}:${it.artifactId}:${it.currentVersion}" },
            valueTransform = { it.latestVersion }
        )

        val directSuggestions = report.outdated
            .filter { "${it.groupId}:${it.artifactId}" in declaredCoordinates }
            .map { outdated ->
                val coordinate = "${outdated.groupId}:${outdated.artifactId}"
                val hasCve = coordinate in vulnerableCoordinates
                val reason = when {
                    hasCve -> UpdateReason.CVE
                    else -> UpdateReason.OUTDATED
                }

                UpdateSuggestion(
                    groupId = outdated.groupId,
                    artifactId = outdated.artifactId,
                    currentVersion = outdated.currentVersion,
                    newVersion = outdated.latestVersion,
                    reason = reason,
                    targetType = UpdateTargetType.DIRECT
                )
            }

        val transitiveOverrideSuggestions = if (options.dynamic) {
            buildTransitiveOverrideSuggestions(
                report = report,
                outdatedByGaAndVersion = outdatedByGaAndVersion,
                existingDirectCoordinates = directSuggestions.map { it.coordinate }.toSet()
            )
        } else {
            emptyList()
        }

        val suggestions = (directSuggestions + transitiveOverrideSuggestions)
            .distinctBy { "${it.coordinate}:${it.currentVersion}:${it.newVersion}:${it.targetType}" }
            .sortedBy { it.coordinate }

        return UpdatePlan(
            projectType = projectType,
            buildFile = buildFile,
            suggestions = suggestions
        )
    }

    private fun buildTransitiveOverrideSuggestions(
        report: com.depanalyzer.report.DependencyReport,
        outdatedByGaAndVersion: Map<String, String>,
        existingDirectCoordinates: Set<String>
    ): List<UpdateSuggestion> {
        val roots = report.dependencyTree.orEmpty()
        if (roots.isEmpty()) return emptyList()

        val rootByCoordinate = roots.associateBy { it.coordinate }
        val result = mutableListOf<UpdateSuggestion>()

        fun visit(node: com.depanalyzer.report.DependencyTreeNode) {
            if (!node.isDirectDependency) {
                val rootCoordinate = node.dependencyChain?.firstOrNull()
                val rootNode = rootCoordinate?.let { rootByCoordinate[it] }
                val rootIsFullyUpdated =
                    rootNode != null && rootNode.latestVersion == null && rootNode.vulnerabilities.isEmpty()

                val hasProblem = node.latestVersion != null || node.vulnerabilities.isNotEmpty()
                val latest = node.latestVersion
                    ?: outdatedByGaAndVersion["${node.groupId}:${node.artifactId}:${node.currentVersion}"]

                if (rootIsFullyUpdated && hasProblem && latest != null && latest != node.currentVersion) {
                    val coordinate = "${node.groupId}:${node.artifactId}"
                    if (coordinate !in existingDirectCoordinates) {
                        val reason = if (node.vulnerabilities.isNotEmpty()) UpdateReason.CVE else UpdateReason.OUTDATED
                        result.add(
                            UpdateSuggestion(
                                groupId = node.groupId,
                                artifactId = node.artifactId,
                                currentVersion = node.currentVersion,
                                newVersion = latest,
                                reason = reason,
                                targetType = UpdateTargetType.TRANSITIVE_OVERRIDE,
                                viaDirectCoordinate = rootNode.coordinate.substringBeforeLast(":")
                            )
                        )
                    }
                }
            }

            node.children.forEach(::visit)
        }

        roots.forEach(::visit)
        return result
    }

    private fun resolveBuildFile(projectDir: Path, type: ProjectType): File {
        val dir = projectDir.toFile()
        return when (type) {
            ProjectType.MAVEN -> File(dir, "pom.xml")
            ProjectType.GRADLE_GROOVY -> File(dir, "build.gradle")
            ProjectType.GRADLE_KOTLIN -> File(dir, "build.gradle.kts")
        }
    }

    private fun declaredCoordinates(buildFile: File, type: ProjectType): Set<String> {
        return when (type) {
            ProjectType.MAVEN -> {
                pomParser.parse(buildFile)
                    .filter { it.section == DependencySection.DEPENDENCIES || it.section == DependencySection.DEPENDENCY_MANAGEMENT }
                    .map { "${it.groupId}:${it.artifactId}" }
                    .toSet()
            }

            ProjectType.GRADLE_GROOVY -> {
                gradleGroovyParser.parse(buildFile)
                    .map { "${it.groupId}:${it.artifactId}" }
                    .toSet()
            }

            ProjectType.GRADLE_KOTLIN -> {
                gradleKotlinParser.parse(buildFile)
                    .map { "${it.groupId}:${it.artifactId}" }
                    .toSet()
            }
        }
    }
}
