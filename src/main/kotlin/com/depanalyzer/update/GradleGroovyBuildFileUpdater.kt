package com.depanalyzer.update

import com.depanalyzer.security.InputSafety
import java.io.File

class GradleGroovyBuildFileUpdater : BuildFileUpdater {
    override fun applyUpdate(buildFile: File, suggestion: UpdateSuggestion): Boolean {
        if (!InputSafety.isSafeVersion(suggestion.newVersion)) return false

        return when (suggestion.targetType) {
            UpdateTargetType.DIRECT -> applyDirectUpdate(buildFile, suggestion)
            UpdateTargetType.TRANSITIVE_OVERRIDE -> applyTransitiveOverride(buildFile, suggestion)
        }
    }

    private fun applyDirectUpdate(buildFile: File, suggestion: UpdateSuggestion): Boolean {
        val originalContent = buildFile.readText()
        var content = originalContent

        val stringNotation = Regex(
            """(['"])\Q${suggestion.groupId}\E:\Q${suggestion.artifactId}\E:([^'"]+)\1"""
        )
        val stringUpdated = replaceVersion(content, stringNotation, suggestion)
        if (stringUpdated != content) {
            buildFile.writeText(stringUpdated)
            return true
        }

        extractVersionToken(content, stringNotation)?.let { token ->
            val variableUpdated = replaceVariableVersion(content, token, suggestion)
            if (variableUpdated != content) {
                buildFile.writeText(variableUpdated)
                return true
            }
        }

        val mapNotation = Regex(
            """group\s*:\s*['"]\Q${suggestion.groupId}\E['"]\s*,\s*name\s*:\s*['"]\Q${suggestion.artifactId}\E['"]\s*,\s*version\s*:\s*(['"]?)([^,'")\s]+)\1"""
        )
        content = replaceVersion(content, mapNotation, suggestion)
        if (content != originalContent) {
            buildFile.writeText(content)
            return true
        }

        extractVersionToken(originalContent, mapNotation)?.let { token ->
            val variableUpdated = replaceVariableVersion(originalContent, token, suggestion)
            if (variableUpdated != originalContent) {
                buildFile.writeText(variableUpdated)
                return true
            }
        }

        return false
    }

    private fun applyTransitiveOverride(buildFile: File, suggestion: UpdateSuggestion): Boolean {
        val content = buildFile.readText()
        val updatedExisting = updateExistingConstraint(content, suggestion)
        if (updatedExisting != content) {
            buildFile.writeText(updatedExisting)
            return true
        }

        val dependenciesRange = findDependenciesBlockRange(content) ?: return false
        val dependencyLine = "implementation '${suggestion.groupId}:${suggestion.artifactId}:${suggestion.newVersion}'"
        val constraintsRegex = Regex("""constraints\s*\{([\s\S]*?)\}""")

        val dependenciesContent = content.substring(dependenciesRange.first, dependenciesRange.last + 1)
        val newDependenciesContent = if (constraintsRegex.containsMatchIn(dependenciesContent)) {
            val match = constraintsRegex.find(dependenciesContent)!!
            val replacement = run {
                val body = match.groupValues[1]
                "constraints {$body\n        $dependencyLine\n    }"
            }
            dependenciesContent.replaceRange(match.range, replacement)
        } else {
            dependenciesContent.replaceRange(
                dependenciesContent.lastIndex,
                dependenciesContent.lastIndex + 1,
                "\n    constraints {\n        $dependencyLine\n    }\n}"
            )
        }

        val updated = content.replaceRange(dependenciesRange.first, dependenciesRange.last + 1, newDependenciesContent)
        if (updated == content) return false
        buildFile.writeText(updated)
        return true
    }

    private fun updateExistingConstraint(content: String, suggestion: UpdateSuggestion): String {
        val regex = Regex("""(['"])\Q${suggestion.groupId}\E:\Q${suggestion.artifactId}\E:([^'"]+)\1""")
        return regex.replace(content) { match ->
            if (match.groupValues[2].trim() == suggestion.newVersion) match.value
            else "${match.groupValues[1]}${suggestion.groupId}:${suggestion.artifactId}:${suggestion.newVersion}${match.groupValues[1]}"
        }
    }

    private fun findDependenciesBlockRange(content: String): IntRange? {
        val startRegex = Regex("""\bdependencies\s*\{""")
        val startMatch = startRegex.find(content) ?: return null
        val openBrace = content.indexOf('{', startMatch.range.first)
        if (openBrace == -1) return null

        var depth = 0
        for (index in openBrace until content.length) {
            when (content[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return openBrace..index
                }
            }
        }
        return null
    }

    private fun replaceVersion(content: String, regex: Regex, suggestion: UpdateSuggestion): String {
        return regex.replace(content) { match ->
            val currentVersion = match.groupValues[2].trim()
            if (currentVersion == suggestion.currentVersion) {
                match.value.replace(currentVersion, suggestion.newVersion)
            } else {
                match.value
            }
        }
    }

    private fun extractVersionToken(content: String, regex: Regex): String? {
        val match = regex.find(content) ?: return null
        val token = match.groupValues[2].trim()
        return token.takeIf { it.isVariableToken() }
    }

    private fun replaceVariableVersion(content: String, token: String, suggestion: UpdateSuggestion): String {
        val variableName = token.toVariableName() ?: return content
        if (variableName.isBlank()) return content

        val patterns = listOf(
            Regex("""(\bdef\s+\Q$variableName\E\s*=\s*['"])([^'"]+)(['"])"""),
            Regex("""(\bext\.\Q$variableName\E\s*=\s*['"])([^'"]+)(['"])"""),
            Regex("""(^\s*\Q$variableName\E\s*=\s*['"])([^'"]+)(['"])""", setOf(RegexOption.MULTILINE))
        )

        for (pattern in patterns) {
            val updated = pattern.replace(content) { match ->
                if (match.groupValues[2].trim() == suggestion.currentVersion) {
                    "${match.groupValues[1]}${suggestion.newVersion}${match.groupValues[3]}"
                } else {
                    match.value
                }
            }
            if (updated != content) return updated
        }

        return content
    }

    private fun String.isVariableToken(): Boolean {
        return startsWith("\${") || startsWith("$") || startsWith("ext.") || matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))
    }

    private fun String.toVariableName(): String? {
        return when {
            startsWith("\${") && endsWith("}") -> removePrefix("\${").removeSuffix("}")
            startsWith("$") -> removePrefix("$")
            startsWith("ext.") -> removePrefix("ext.")
            matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) -> this
            else -> null
        }
    }
}
