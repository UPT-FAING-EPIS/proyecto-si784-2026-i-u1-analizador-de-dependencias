package com.depanalyzer.update

import com.depanalyzer.parser.LibraryInfo
import com.depanalyzer.parser.VersionCatalogParser
import java.io.File

class GradleKotlinBuildFileUpdater(
    private val catalogParser: VersionCatalogParser = VersionCatalogParser()
) : BuildFileUpdater {
    override fun applyUpdate(buildFile: File, suggestion: UpdateSuggestion): Boolean {
        if (suggestion.targetType == UpdateTargetType.TRANSITIVE_OVERRIDE) {
            return applyTransitiveOverride(buildFile, suggestion)
        }

        val content = buildFile.readText()
        val stringNotation = Regex(
            """(['"])\Q${suggestion.groupId}\E:\Q${suggestion.artifactId}\E:([^'"]+)\1"""
        )

        val replaced = stringNotation.replace(content) { match ->
            val current = match.groupValues[2].trim()
            if (current == suggestion.currentVersion) {
                match.value.replace(current, suggestion.newVersion)
            } else {
                match.value
            }
        }

        if (replaced != content) {
            buildFile.writeText(replaced)
            return true
        }

        return updateVersionCatalog(buildFile, content, suggestion)
    }

    private fun applyTransitiveOverride(buildFile: File, suggestion: UpdateSuggestion): Boolean {
        val content = buildFile.readText()
        val directUpdated = applyUpdate(buildFile, suggestion.copy(targetType = UpdateTargetType.DIRECT))
        if (directUpdated) return true

        val refreshed = buildFile.readText()
        val existingConstraintRegex = Regex(
            """implementation\s*\(\s*['"]\Q${suggestion.groupId}\E:\Q${suggestion.artifactId}\E:([^'"]+)['"]\s*\)"""
        )
        val updatedExisting = existingConstraintRegex.replace(refreshed) { match ->
            if (match.groupValues[1].trim() == suggestion.newVersion) match.value
            else "implementation(\"${suggestion.groupId}:${suggestion.artifactId}:${suggestion.newVersion}\")"
        }
        if (updatedExisting != refreshed) {
            buildFile.writeText(updatedExisting)
            return true
        }

        val dependenciesRange = findDependenciesBlockRange(refreshed) ?: return false
        val dependenciesContent = refreshed.substring(dependenciesRange.first, dependenciesRange.last + 1)
        val line = "implementation(\"${suggestion.groupId}:${suggestion.artifactId}:${suggestion.newVersion}\")"
        val constraintsRegex = Regex("""constraints\s*\{([\s\S]*?)\}""")

        val newDependenciesContent = if (constraintsRegex.containsMatchIn(dependenciesContent)) {
            val match = constraintsRegex.find(dependenciesContent)!!
            val replacement = run {
                val body = match.groupValues[1]
                "constraints {$body\n        $line\n    }"
            }
            dependenciesContent.replaceRange(match.range, replacement)
        } else {
            dependenciesContent.replaceRange(
                dependenciesContent.lastIndex,
                dependenciesContent.lastIndex + 1,
                "\n    constraints {\n        $line\n    }\n}"
            )
        }

        val updated =
            refreshed.replaceRange(dependenciesRange.first, dependenciesRange.last + 1, newDependenciesContent)
        if (updated == refreshed) return false
        buildFile.writeText(updated)
        return true
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

    private fun updateVersionCatalog(buildFile: File, buildContent: String, suggestion: UpdateSuggestion): Boolean {
        val catalogFile = File(buildFile.parentFile, "gradle/libs.versions.toml")
        if (!catalogFile.exists()) return false

        val catalog = catalogParser.parse(catalogFile)
        val matchingAliases = catalog.libraries.filterValues { lib ->
            lib.group == suggestion.groupId && lib.name == suggestion.artifactId
        }
        if (matchingAliases.isEmpty()) return false

        val usedAliases = matchingAliases.filterKeys { alias -> isAliasUsed(buildContent, alias) }
        if (usedAliases.isEmpty()) return false

        var toml = catalogFile.readText()
        var changed = false
        for ((alias, libInfo) in usedAliases) {
            val updated = updateTomlForAlias(toml, alias, libInfo, suggestion)
            if (updated != toml) {
                toml = updated
                changed = true
            }
        }

        if (!changed) return false
        catalogFile.writeText(toml)
        return true
    }

    private fun isAliasUsed(buildContent: String, alias: String): Boolean {
        val dotAlias = alias.replace('-', '.')
        return Regex("""\blibs\.\Q$alias\E\b""").containsMatchIn(buildContent) ||
                Regex("""\blibs\.\Q$dotAlias\E\b""").containsMatchIn(buildContent)
    }

    private fun updateTomlForAlias(
        toml: String,
        alias: String,
        libInfo: LibraryInfo,
        suggestion: UpdateSuggestion
    ): String {
        libInfo.versionRef?.let { ref ->
            val versionsSectionUpdated = updateVersionInVersionsSection(toml, ref, suggestion)
            if (versionsSectionUpdated != toml) return versionsSectionUpdated
        }

        if (!libInfo.version.isNullOrBlank()) {
            val inlineMapUpdated = updateInlineLibraryVersion(toml, alias, suggestion)
            if (inlineMapUpdated != toml) return inlineMapUpdated

            val stringNotationUpdated = updateStringLibraryVersion(toml, alias, suggestion)
            if (stringNotationUpdated != toml) return stringNotationUpdated
        }

        return toml
    }

    private fun updateVersionInVersionsSection(toml: String, key: String, suggestion: UpdateSuggestion): String {
        val sectionRegex = Regex("""(\[versions]\s*[\s\S]*?)(?=\n\[[^]]+]|$)""")
        val sectionMatch = sectionRegex.find(toml) ?: return toml
        val section = sectionMatch.groupValues[1]

        val lineRegex = Regex("""(^\s*\Q$key\E\s*=\s*['"])([^'"]+)(['"].*$)""", setOf(RegexOption.MULTILINE))
        val updatedSection = lineRegex.replace(section) { match ->
            if (match.groupValues[2].trim() == suggestion.currentVersion) {
                "${match.groupValues[1]}${suggestion.newVersion}${match.groupValues[3]}"
            } else {
                match.value
            }
        }

        if (updatedSection == section) return toml
        return toml.replaceRange(sectionMatch.range, updatedSection)
    }

    private fun updateInlineLibraryVersion(toml: String, alias: String, suggestion: UpdateSuggestion): String {
        val lineRegex = Regex(
            """(^\s*\Q$alias\E\s*=\s*\{[^\n]*?version\s*=\s*['"])([^'"]+)(['"][^\n]*}\s*$)""",
            setOf(RegexOption.MULTILINE)
        )

        return lineRegex.replace(toml) { match ->
            if (match.groupValues[2].trim() == suggestion.currentVersion) {
                "${match.groupValues[1]}${suggestion.newVersion}${match.groupValues[3]}"
            } else {
                match.value
            }
        }
    }

    private fun updateStringLibraryVersion(toml: String, alias: String, suggestion: UpdateSuggestion): String {
        val lineRegex = Regex(
            """(^\s*\Q$alias\E\s*=\s*['"][^:'"]+:[^:'"]+:)([^'"]+)(['"].*$)""",
            setOf(RegexOption.MULTILINE)
        )

        return lineRegex.replace(toml) { match ->
            if (match.groupValues[2].trim() == suggestion.currentVersion) {
                "${match.groupValues[1]}${suggestion.newVersion}${match.groupValues[3]}"
            } else {
                match.value
            }
        }
    }
}
