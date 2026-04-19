package com.depanalyzer.update

import java.io.File

class PomBuildFileUpdater : BuildFileUpdater {
    override fun applyUpdate(buildFile: File, suggestion: UpdateSuggestion): Boolean {
        return when (suggestion.targetType) {
            UpdateTargetType.DIRECT -> applyDirectUpdate(buildFile, suggestion)
            UpdateTargetType.TRANSITIVE_OVERRIDE -> applyTransitiveOverride(buildFile, suggestion)
        }
    }

    private fun applyDirectUpdate(buildFile: File, suggestion: UpdateSuggestion): Boolean {
        val content = buildFile.readText()
        val dependencyRegex = Regex(
            """(<dependency>\s*<groupId>\Q${suggestion.groupId}\E</groupId>\s*<artifactId>\Q${suggestion.artifactId}\E</artifactId>[\s\S]*?<version>)([^<]+)(</version>)"""
        )

        val dependencyMatch = dependencyRegex.find(content) ?: return false
        val currentToken = dependencyMatch.groupValues[2].trim()

        val replaced = when {
            currentToken == suggestion.currentVersion -> {
                dependencyRegex.replace(content) { match ->
                    val current = match.groupValues[2].trim()
                    if (current == suggestion.currentVersion) {
                        "${match.groupValues[1]}${suggestion.newVersion}${match.groupValues[3]}"
                    } else {
                        match.value
                    }
                }
            }

            currentToken.isPropertyReference() -> {
                val propertyName = currentToken.extractPropertyName() ?: return false
                replacePropertyValue(content, propertyName, suggestion)
            }

            else -> content
        }

        if (replaced == content) return false
        buildFile.writeText(replaced)
        return true
    }

    private fun applyTransitiveOverride(buildFile: File, suggestion: UpdateSuggestion): Boolean {
        val content = buildFile.readText()
        val existingDepRegex = Regex(
            """(<dependency>\s*<groupId>\Q${suggestion.groupId}\E</groupId>\s*<artifactId>\Q${suggestion.artifactId}\E</artifactId>[\s\S]*?<version>)([^<]+)(</version>)"""
        )

        val existingUpdated = existingDepRegex.replace(content) { match ->
            val current = match.groupValues[2].trim()
            if (current == suggestion.newVersion) match.value
            else "${match.groupValues[1]}${suggestion.newVersion}${match.groupValues[3]}"
        }
        if (existingUpdated != content) {
            buildFile.writeText(existingUpdated)
            return true
        }

        val dependencySnippet = """
            <dependency>
                <groupId>${suggestion.groupId}</groupId>
                <artifactId>${suggestion.artifactId}</artifactId>
                <version>${suggestion.newVersion}</version>
            </dependency>
        """.trimIndent()

        val managementRegex =
            Regex("""(<dependencyManagement>\s*<dependencies>)([\s\S]*?)(</dependencies>\s*</dependencyManagement>)""")
        val withManagement = managementRegex.find(content)?.let { match ->
            val insertion = "\n$dependencySnippet\n"
            content.replaceRange(
                match.range,
                "${match.groupValues[1]}${match.groupValues[2]}$insertion${match.groupValues[3]}"
            )
        }

        val finalContent = when {
            withManagement != null -> withManagement
            content.contains("</project>") -> {
                val block = """
                    <dependencyManagement>
                        <dependencies>
                            $dependencySnippet
                        </dependencies>
                    </dependencyManagement>
                """.trimIndent()
                content.replace("</project>", "\n$block\n</project>")
            }

            else -> content
        }

        if (finalContent == content) return false
        buildFile.writeText(finalContent)
        return true
    }

    private fun replacePropertyValue(content: String, propertyName: String, suggestion: UpdateSuggestion): String {
        val propertyRegex = Regex("""(<\Q$propertyName\E>)([^<]+)(</\Q$propertyName\E>)""")
        val propertyMatch = propertyRegex.find(content) ?: return content
        val current = propertyMatch.groupValues[2].trim()

        if (current != suggestion.currentVersion) return content

        return propertyRegex.replace(content) { match ->
            if (match.groupValues[2].trim() == suggestion.currentVersion) {
                "${match.groupValues[1]}${suggestion.newVersion}${match.groupValues[3]}"
            } else {
                match.value
            }
        }
    }

    private fun String.isPropertyReference(): Boolean {
        return startsWith("\${") && endsWith("}")
    }

    private fun String.extractPropertyName(): String? {
        if (!isPropertyReference()) return null
        return removePrefix("\${").removeSuffix("}").trim()
    }
}
