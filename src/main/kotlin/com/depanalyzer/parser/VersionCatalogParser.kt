package com.depanalyzer.parser

import java.io.File

class VersionCatalogParser {
    fun parse(tomlFile: File): VersionCatalog {
        if (!tomlFile.exists()) return VersionCatalog()

        val content = tomlFile.readText()
        val versions = parseSection(content, "versions")
        val libraries = parseLibraries(content)

        return VersionCatalog(versions, libraries)
    }

    private fun parseSection(content: String, sectionName: String): Map<String, String> {
        val sectionRegex = Regex("""\[$sectionName]\s*([\s\S]*?)(?=\n\[|$)""")
        val sectionMatch = sectionRegex.find(content) ?: return emptyMap()
        val body = sectionMatch.groupValues[1]

        val result = mutableMapOf<String, String>()
        val lineRegex = Regex("""^([A-Za-z0-9_-]+)\s*=\s*['"]([^'"]+)['"]""", RegexOption.MULTILINE)
        lineRegex.findAll(body).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private fun parseLibraries(content: String): Map<String, LibraryInfo> {
        val sectionRegex = Regex("""\[libraries]\s*([\s\S]*?)(?=\n\[|$)""")
        val sectionMatch = sectionRegex.find(content) ?: return emptyMap()
        val body = sectionMatch.groupValues[1]

        val result = mutableMapOf<String, LibraryInfo>()
        
        // Match both inline: alias = "group:artifact:version"
        // and map: alias = { group = "...", name = "...", version.ref = "..." }
        val lines = body.lines().filter { it.contains("=") }
        lines.forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size < 2) return@forEach
            val alias = parts[0].trim()
            val value = parts[1].trim()

            if (value.startsWith("{")) {
                val group = extractFromMap(value, "group") ?: return@forEach
                val name = extractFromMap(value, "name") ?: return@forEach
                val versionRef = extractFromMap(value, "version.ref")
                val version = extractFromMap(value, "version")
                result[alias] = LibraryInfo(group, name, versionRef, version)
            } else {
                val stringVal = value.trim('"').trim('\'')
                val gav = stringVal.split(":")
                if (gav.size == 3) {
                    result[alias] = LibraryInfo(gav[0], gav[1], version = gav[2])
                }
            }
        }
        return result
    }

    private fun extractFromMap(mapStr: String, key: String): String? {
        val regex = Regex("""$key\s*=\s*['"]([^'"]+)['"]""")
        return regex.find(mapStr)?.groupValues?.get(1)
    }
}
