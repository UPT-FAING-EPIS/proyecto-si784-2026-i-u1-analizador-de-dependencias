package com.depanalyzer.parser

import java.io.File

class GradleGroovyDependencyParser {
    fun parse(buildFile: File): List<ParsedGradleDependency> {
        require(buildFile.exists() && buildFile.isFile) { "Invalid build.gradle path: ${buildFile.absolutePath}" }
        require(buildFile.name == "build.gradle") { "Expected build.gradle, got ${buildFile.name}" }

        val content = buildFile.readText()
        val vars = parseVariables(content)
        val dependenciesBody = extractDependenciesBody(content) ?: return emptyList()

        val cleanBody = stripBlockComments(dependenciesBody)
        val result = mutableListOf<ParsedGradleDependency>()
        cleanBody.lines().forEach { line ->
            parseDependencyLine(line, vars)?.let(result::add)
        }

        return result
    }

    private fun stripBlockComments(content: String): String {
        val blockCommentRegex = Regex("""/\*[\s\S]*?\*/""")
        return content.replace(blockCommentRegex, "")
    }

    private fun parseVariables(content: String): Map<String, String> {
        val vars = mutableMapOf<String, String>()

        // 1. Capture 'def varName = "value"'
        val defRegex = Regex("""def\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*['"]([^'"]+)['"]""")
        defRegex.findAll(content).forEach { match ->
            vars[match.groupValues[1]] = match.groupValues[2]
        }

        // 2. Capture 'ext { ... }' block
        val extBlockRegex = Regex("""ext\s*\{([\s\S]*?)}""")
        extBlockRegex.findAll(content).forEach { blockMatch ->
            val body = blockMatch.groupValues[1]
            val assignmentRegex = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*['"]([^'"]+)['"]""")
            assignmentRegex.findAll(body).forEach { match ->
                vars[match.groupValues[1]] = match.groupValues[2]
            }
        }

        // 3. Capture 'ext.varName = "value"'
        val extDotRegex = Regex("""ext\.([A-Za-z_][A-Za-z0-9_]*)\s*=\s*['"]([^'"]+)['"]""")
        extDotRegex.findAll(content).forEach { match ->
            vars[match.groupValues[1]] = match.groupValues[2]
        }

        return vars
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

    private fun parseDependencyLine(line: String, vars: Map<String, String>): ParsedGradleDependency? {
        val noComment = line.substringBefore("//").trim()
        if (noComment.isBlank()) return null
        if (noComment.contains("project(")) return null

        // Flexible regex for configuration and notation
        // String notation: implementation 'group:artifact:version' or implementation("group:artifact:version")
        val stringNotation = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*\(?\s*['"]([^:'"]+):([^:'"]+):([^'"]+)['"]\s*\)?$""")
        stringNotation.matchEntire(noComment)?.let { match ->
            val configuration = match.groupValues[1]
            val groupId = match.groupValues[2]
            val artifactId = match.groupValues[3]
            val version = resolveVersion(match.groupValues[4], vars)
            return ParsedGradleDependency(
                groupId = groupId,
                artifactId = artifactId,
                version = version,
                configuration = configuration
            )
        }

        // Map notation: implementation group: '...', name: '...', version: '...'
        // It can also be implementation(group: '...', name: '...', version: '...')
        val mapNotation = Regex(
            """^([A-Za-z_][A-Za-z0-9_]*)\s*\(?\s*group\s*:\s*['"]([^'"]+)['"]\s*,\s*name\s*:\s*['"]([^'"]+)['"]\s*,\s*version\s*:\s*([^)\s]+)\s*\)?$"""
        )
        mapNotation.matchEntire(noComment)?.let { match ->
            val configuration = match.groupValues[1]
            val groupId = match.groupValues[2]
            val artifactId = match.groupValues[3]
            val rawVersion = match.groupValues[4].trim().removePrefix("'").removeSuffix("'").removePrefix("\"").removeSuffix("\"")
            val version = resolveVersion(rawVersion, vars)
            return ParsedGradleDependency(
                groupId = groupId,
                artifactId = artifactId,
                version = version,
                configuration = configuration
            )
        }

        return null
    }

    private fun resolveVersion(raw: String, vars: Map<String, String>): String {
        // Handle ${varName}, $varName, ext.varName, or just varName
        val cleanRaw = raw.removePrefix("\${").removeSuffix("}").removePrefix("$")
        
        return when {
            cleanRaw.startsWith("ext.") -> vars[cleanRaw.removePrefix("ext.")] ?: raw
            vars.containsKey(cleanRaw) -> vars[cleanRaw] ?: raw
            else -> raw
        }
    }
}
