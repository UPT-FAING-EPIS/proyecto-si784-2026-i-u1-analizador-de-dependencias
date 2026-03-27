package com.depanalyzer.parser

import com.depanalyzer.repository.ProjectRepository
import java.io.File

class GradleKotlinDependencyParser(
    private val catalog: VersionCatalog = VersionCatalog(),
    private val repoParser: GradleRepositoryParser = GradleRepositoryParser()
) {
    fun parse(buildFile: File): List<ParsedGradleDependency> {
        require(buildFile.exists() && buildFile.isFile) { "Invalid build.gradle.kts path: ${buildFile.absolutePath}" }
        require(buildFile.name == "build.gradle.kts") { "Expected build.gradle.kts, got ${buildFile.name}" }

        val content = buildFile.readText()
        val dependenciesBody = extractDependenciesBody(content) ?: return emptyList()

        val cleanBody = stripBlockComments(dependenciesBody)
        val result = mutableListOf<ParsedGradleDependency>()
        cleanBody.lines().forEach { line ->
            parseDependencyLine(line)?.let(result::add)
        }

        return result
    }

    fun repositories(buildFile: File): List<ProjectRepository> {
        return repoParser.parse(buildFile)
    }

    private fun stripBlockComments(content: String): String {
        val blockCommentRegex = Regex("""/\*[\s\S]*?\*/""")
        return content.replace(blockCommentRegex, "")
    }

    private fun extractDependenciesBody(content: String): String? {
        val startRegex = Regex("""\bdependencies\s*\{""")
        val startMatch = startRegex.find(content) ?: return null
        val start = startMatch.range.first
        val openBrace = content.indexOf('{', start)
        if (openBrace == -1) return null

        var depth = 0
        var index = openBrace
        while (index < content.length) {
            when (content[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return content.substring(openBrace + 1, index)
                    }
                }
            }
            index++
        }
        return null
    }

    private fun parseDependencyLine(line: String): ParsedGradleDependency? {
        val noComment = line.substringBefore("//").trim()
        if (noComment.isBlank()) return null
        if (noComment.contains("project(")) return null

        // 1. String notation: implementation("group:artifact:version")
        val stringNotation = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*['"]([^:'"]+):([^:'"]+):([^'"]+)['"]\s*\)$""")
        stringNotation.matchEntire(noComment)?.let { match ->
            return ParsedGradleDependency(
                groupId = match.groupValues[2],
                artifactId = match.groupValues[3],
                version = match.groupValues[4],
                configuration = match.groupValues[1]
            )
        }

        // 2. Catalog notation: implementation(libs.alias) or implementation(libs.alias.name)
        val catalogNotation = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*libs\.([A-Za-z0-9._-]+)\s*\)$""")
        catalogNotation.matchEntire(noComment)?.let { match ->
            val configuration = match.groupValues[1]
            val aliasRaw = match.groupValues[2]
            // Aliases in libs.xxx.yyy are often libs.xxx-yyy in TOML
            val alias = aliasRaw.replace(".", "-")
            
            val libInfo = catalog.libraries[alias] ?: catalog.libraries[aliasRaw]
            if (libInfo != null) {
                val version = libInfo.version ?: libInfo.versionRef?.let { catalog.versions[it] }
                return ParsedGradleDependency(
                    groupId = libInfo.group,
                    artifactId = libInfo.name,
                    version = version,
                    configuration = configuration
                )
            }
        }

        return null
    }
}
