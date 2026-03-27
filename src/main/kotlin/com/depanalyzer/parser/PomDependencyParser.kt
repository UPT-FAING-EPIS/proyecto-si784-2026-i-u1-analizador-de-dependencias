package com.depanalyzer.parser

import com.depanalyzer.repository.ProjectRepository
import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.Repository
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import java.io.File
import java.io.FileReader

class PomDependencyParser(
    private val envProvider: (String) -> String? = { System.getenv(it) }
) {
    fun parse(pomFile: File): List<ParsedDependency> {
        require(pomFile.exists() && pomFile.isFile) { "Invalid pom file path: ${pomFile.absolutePath}" }
        require(pomFile.name == "pom.xml") { "Expected pom.xml, got ${pomFile.name}" }

        val hierarchy = buildHierarchy(pomFile)
        val allProperties = mergeProperties(hierarchy)
        val managedVersions = mergeManagedVersions(hierarchy, allProperties)

        val result = mutableListOf<ParsedDependency>()

        hierarchy.first().dependencies
            .mapNotNull { toParsedDependency(it, DependencySection.DEPENDENCIES, allProperties, managedVersions) }
            .also(result::addAll)

        hierarchy.first().dependencyManagement?.dependencies.orEmpty()
            .mapNotNull {
                toParsedDependency(
                    it,
                    DependencySection.DEPENDENCY_MANAGEMENT,
                    allProperties,
                    managedVersions
                )
            }
            .also(result::addAll)

        return result
    }

    fun repositories(pomFile: File): List<ProjectRepository> {
        require(pomFile.exists() && pomFile.isFile) { "Invalid pom file path: ${pomFile.absolutePath}" }
        val hierarchy = buildHierarchy(pomFile)
        val properties = mergeProperties(hierarchy)

        val repositories = mutableMapOf<String, ProjectRepository>()

        hierarchy.forEach { model ->
            model.repositories.orEmpty().forEach { repo ->
                val projectRepo = toProjectRepository(repo, properties)
                if (projectRepo != null) {
                    repositories.putIfAbsent(projectRepo.id, projectRepo)
                }
            }
            model.pluginRepositories.orEmpty().forEach { repo ->
                val projectRepo = toProjectRepository(repo, properties)
                if (projectRepo != null) {
                    repositories.putIfAbsent(projectRepo.id, projectRepo)
                }
            }
        }

        return if (repositories.isEmpty()) {
            listOf(ProjectRepository.MAVEN_CENTRAL)
        } else {
            repositories.values.toList()
        }
    }

    private fun toProjectRepository(repo: Repository, properties: Map<String, String>): ProjectRepository? {
        val id = repo.id?.trim() ?: return null
        val url = repo.url?.trim()?.let { resolvePlaceholders(it, properties) } ?: return null

        val releases = repo.releases?.isEnabled?.toString()?.equals("false", true)?.not() ?: true
        val snapshots = repo.snapshots?.isEnabled?.toString()?.equals("true", true) ?: false

        val username = envProvider("MAVEN_REPO_${id.uppercase().replace("-", "_")}_USERNAME")
        val password = envProvider("MAVEN_REPO_${id.uppercase().replace("-", "_")}_PASSWORD")

        return ProjectRepository(
            id = id,
            url = url,
            releases = releases,
            snapshots = snapshots,
            username = username,
            password = password
        )
    }

    private fun buildHierarchy(rootPom: File): List<Model> {
        val models = mutableListOf<Model>()
        var currentPom: File? = rootPom
        var depth = 0

        while (currentPom != null && depth < 10) {
            val model = readModel(currentPom)
            models.add(model)

            val parent = model.parent ?: break
            val relativePath = parent.relativePath?.trim().takeUnless { it.isNullOrBlank() } ?: "../pom.xml"
            val parentPom = File(currentPom.parentFile, relativePath).canonicalFile
            if (!parentPom.exists() || parentPom == currentPom.canonicalFile) {
                break
            }

            currentPom = parentPom
            depth++
        }

        return models
    }

    private fun readModel(file: File): Model = FileReader(file).use { reader ->
        MavenXpp3Reader().read(reader)
    }

    private fun mergeProperties(hierarchy: List<Model>): Map<String, String> {
        val merged = mutableMapOf<String, String>()
        hierarchy.asReversed().forEach { model ->
            model.properties?.forEach { (key, value) ->
                merged[key.toString()] = value.toString()
            }
            model.groupId?.let { merged["project.groupId"] = it }
            model.version?.let { merged["project.version"] = it }
            model.artifactId?.let { merged["project.artifactId"] = it }
            model.parent?.groupId?.let { merged["project.parent.groupId"] = it }
            model.parent?.version?.let { merged["project.parent.version"] = it }
            model.parent?.artifactId?.let { merged["project.parent.artifactId"] = it }
        }
        return merged.mapValues { (_, value) -> resolvePlaceholders(value, merged) }
    }

    private fun mergeManagedVersions(
        hierarchy: List<Model>,
        properties: Map<String, String>
    ): Map<String, String> {
        val managed = mutableMapOf<String, String>()
        hierarchy.asReversed().forEach { model ->
            model.dependencyManagement?.dependencies.orEmpty().forEach { dependency ->
                val key = "${dependency.groupId}:${dependency.artifactId}"
                val rawVersion = dependency.version?.trim()
                if (!rawVersion.isNullOrBlank()) {
                    managed[key] = resolvePlaceholders(rawVersion, properties)
                }
            }
        }
        return managed
    }

    private fun toParsedDependency(
        dependency: Dependency,
        section: DependencySection,
        properties: Map<String, String>,
        managedVersions: Map<String, String>
    ): ParsedDependency? {
        val groupId = dependency.groupId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val artifactId = dependency.artifactId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val key = "$groupId:$artifactId"

        val rawVersion = dependency.version?.trim()
        val version = when {
            !rawVersion.isNullOrEmpty() -> resolvePlaceholders(rawVersion, properties)
            section == DependencySection.DEPENDENCIES -> managedVersions[key]
            else -> null
        }

        val scope = dependency.scope?.trim().takeUnless { it.isNullOrBlank() } ?: "compile"

        return ParsedDependency(
            groupId = resolvePlaceholders(groupId, properties),
            artifactId = resolvePlaceholders(artifactId, properties),
            version = version,
            scope = scope,
            section = section
        )
    }

    private fun resolvePlaceholders(rawValue: String, properties: Map<String, String>): String {
        var resolved = rawValue
        var guard = 0
        val regex = Regex("\\$\\{([^}]+)}")

        while (guard < 20) {
            val match = regex.find(resolved) ?: break
            val key = match.groupValues[1]
            val replacement = properties[key] ?: break
            resolved = resolved.replace("\${$key}", replacement)
            guard++
        }
        return resolved
    }
}
