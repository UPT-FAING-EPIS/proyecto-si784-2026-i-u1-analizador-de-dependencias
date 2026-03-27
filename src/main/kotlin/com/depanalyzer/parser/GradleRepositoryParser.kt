package com.depanalyzer.parser

import com.depanalyzer.repository.ProjectRepository
import java.io.File

class GradleRepositoryParser(
    private val envProvider: (String) -> String? = { System.getenv(it) }
) {
    fun parse(buildFile: File): List<ProjectRepository> {
        val content = buildFile.readText()
        val repositoriesBody = extractBlockBody(content, "repositories") ?: return listOf(ProjectRepository.MAVEN_CENTRAL)
        
        val repos = mutableListOf<ProjectRepository>()
        
        // Standard repositories
        if (repositoriesBody.contains("mavenCentral()")) {
            repos.add(ProjectRepository.MAVEN_CENTRAL)
        }
        if (repositoriesBody.contains("google()")) {
            repos.add(ProjectRepository(id = "google", url = "https://maven.google.com"))
        }
        if (repositoriesBody.contains("jcenter()")) {
            repos.add(ProjectRepository(id = "jcenter", url = "https://jcenter.bintray.com"))
        }
        if (repositoriesBody.contains("mavenLocal()")) {
            // mavenLocal is usually ~/.m2/repository, we can use a placeholder or local file URL
            repos.add(ProjectRepository(id = "mavenLocal", url = "file://${System.getProperty("user.home")}/.m2/repository"))
        }

        // Custom maven repositories
        val mavenBlocks = extractMavenBlocks(repositoriesBody)
        mavenBlocks.forEach { block ->
            val url = extractUrl(block)
            if (url != null) {
                val id = url.substringAfterLast("/").takeIf { it.isNotBlank() } ?: "maven"
                val (username, password) = extractCredentials(block)
                repos.add(ProjectRepository(
                    id = id,
                    url = url,
                    username = username,
                    password = password
                ))
            }
        }

        return if (repos.isEmpty()) listOf(ProjectRepository.MAVEN_CENTRAL) else repos
    }

    private fun extractBlockBody(content: String, blockName: String): String? {
        val startRegex = Regex("""\b$blockName\s*\{""")
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

    private fun extractMavenBlocks(content: String): List<String> {
        val blocks = mutableListOf<String>()
        val startRegex = Regex("""\bmaven\s*\{""")
        var currentMatch = startRegex.find(content)
        
        while (currentMatch != null) {
            val start = currentMatch.range.first
            val openBrace = content.indexOf('{', start)
            if (openBrace != -1) {
                var depth = 0
                var index = openBrace
                while (index < content.length) {
                    when (content[index]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                blocks.add(content.substring(openBrace + 1, index))
                                break
                            }
                        }
                    }
                    index++
                }
            }
            currentMatch = startRegex.find(content, currentMatch.range.last)
        }
        return blocks
    }

    private fun extractUrl(block: String): String? {
        // Groovy: url "..." or url '...'
        // Kotlin: url = uri("...") or url = "..."
        val urlRegex = Regex("""url\s*(?:=|\s*)\s*(?:uri\s*\(\s*)?['"]([^'"]+)['"]""")
        return urlRegex.find(block)?.groupValues?.get(1)
    }

    private fun extractCredentials(block: String): Pair<String?, String?> {
        val credsBody = extractBlockBody(block, "credentials") ?: return null to null
        
        val userRegex = Regex("""username\s*(?:=|\s*)\s*(?:System\.getenv\s*\(\s*['"]([^'"]+)['"]\s*\)|['"]([^'"]+)['"])""")
        val passRegex = Regex("""password\s*(?:=|\s*)\s*(?:System\.getenv\s*\(\s*['"]([^'"]+)['"]\s*\)|['"]([^'"]+)['"])""")
        
        val userMatch = userRegex.find(credsBody)
        val username = when {
            userMatch?.groupValues?.get(1)?.isNotBlank() == true -> envProvider(userMatch.groupValues[1])
            userMatch?.groupValues?.get(2)?.isNotBlank() == true -> userMatch.groupValues[2]
            else -> null
        }
        
        val passMatch = passRegex.find(credsBody)
        val password = when {
            passMatch?.groupValues?.get(1)?.isNotBlank() == true -> envProvider(passMatch.groupValues[1])
            passMatch?.groupValues?.get(2)?.isNotBlank() == true -> passMatch.groupValues[2]
            else -> null
        }
        
        return username to password
    }
}
