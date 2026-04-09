package com.depanalyzer.parser.maven

import com.depanalyzer.core.graph.DependencyNode

object MavenDependencyTreeParser {

    fun parse(treeOutput: String, verbose: Boolean = false): List<DependencyNode> {
        if (treeOutput.isBlank()) {
            if (verbose) System.err.println("ℹ️  Parser: Empty tree output")
            return emptyList()
        }

        val lines = treeOutput.lines()
        if (verbose) System.err.println("ℹ️  Parser: Processing ${lines.size} lines")

        val treeLines = parseLines(lines)
        if (verbose) System.err.println("ℹ️  Parser: Extracted ${treeLines.size} parsed lines")

        if (treeLines.isEmpty()) {
            if (verbose) System.err.println("⚠️  Parser: No valid tree lines found after parsing")
            return emptyList()
        }

        val dependencyLines = treeLines.drop(1)

        if (dependencyLines.isEmpty()) {
            if (verbose) System.err.println("⚠️  Parser: No dependencies found (only root project line)")
            return emptyList()
        }

        val minDepth = dependencyLines.minOfOrNull { it.depth } ?: 0
        val adjustedLines = dependencyLines.map { treeLine ->
            treeLine.copy(depth = treeLine.depth - minDepth)
        }

        val result = buildHierarchy(adjustedLines)
        if (verbose) System.err.println("ℹ️  Parser: Built hierarchy with ${result.size} root dependencies")
        return result
    }

    private fun parseLines(lines: List<String>): List<TreeLine> {
        return lines
            .mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null

                val cleanLine = line.replace(Regex("^\\[(INFO|WARNING|ERROR)] ?"), "")
                parseLine(cleanLine)
            }
            .filter { it.groupId.isNotEmpty() && it.artifactId.isNotEmpty() }
    }

    private fun parseLine(line: String): TreeLine? {
        val trimmed = line.trimEnd()
        if (trimmed.isBlank()) return null

        if (!trimmed.contains("|") && !trimmed.contains("+") && !trimmed.contains("\\")) {

            val coords = trimmed.substringBefore(" ").trim()
            if (coords.contains(":")) {
                val parts = coords.split(":")
                if (parts.size >= 3) {
                    return TreeLine(
                        depth = 0,
                        line = trimmed,
                        groupId = parts[0],
                        artifactId = parts[1],
                        version = parts.getOrNull(2) ?: "",
                        scope = null,
                        isDirect = true,
                        isExcluded = false
                    )
                }
            }
            return null
        }

        val depth = calculateDepth(trimmed)

        val contentStart = findContentStart(trimmed)
        if (contentStart < 0 || contentStart >= trimmed.length) {
            return null
        }

        val content = trimmed.substring(contentStart).trim()
        if (content.isEmpty()) return null

        val isExcluded = content.contains("[EXCLUDED]") ||
                content.contains("[omitted for duplicate]") ||
                content.contains("(omitted for duplicate)")

        val (coords, scope) = extractCoordinates(content)
        if (coords.isEmpty()) return null

        val parts = coords.split(":")
        if (parts.size < 3) return null

        val (groupId, artifactId, version) = when (parts.size) {
            3 -> {
                Triple(parts[0], parts[1], parts[2])
            }

            4, 5 -> {

                Triple(parts[0], parts[1], parts[parts.size - 2])
            }

            else -> {
                return null
            }
        }

        return TreeLine(
            depth = depth,
            line = trimmed,
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            scope = scope,
            isDirect = depth == 0,
            isExcluded = isExcluded
        )
    }

    private fun calculateDepth(line: String): Int {
        var depth = 0
        var i = 0

        while (i < line.length) {
            when {
                line[i] == '|' -> {
                    depth++
                    i += 1
                    while (i < line.length && line[i] == ' ') {
                        i++
                    }
                }

                line[i] == '+' || line[i] == '\\' -> {
                    break
                }

                line[i] == ' ' && i + 2 < line.length && line[i + 1] == ' ' && line[i + 2] == ' ' -> {
                    depth++
                    i += 3
                }

                line[i] == ' ' -> {
                    i++
                }

                else -> {
                    break
                }
            }
        }

        return depth
    }

    private fun findContentStart(line: String): Int {
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch !in setOf('|', '+', '\\', '-', ' ')) {
                return i
            }
            i++
        }
        return -1
    }

    private fun extractCoordinates(content: String): Pair<String, String?> {
        val scopeMatch = Regex("\\((\\w+)\\)").find(content)
        val scope = scopeMatch?.groupValues?.getOrNull(1)

        val coordinates = content
            .substringBefore("(")
            .substringBefore(" ")
            .trim()

        return Pair(coordinates, scope)
    }

    private fun buildHierarchy(treeLines: List<TreeLine>): List<DependencyNode> {
        if (treeLines.isEmpty()) return emptyList()

        val stack = mutableListOf<Pair<Int, DependencyNode>>()
        val rootNodes = mutableListOf<DependencyNode>()

        for (treeLine in treeLines) {

            while (stack.isNotEmpty() && stack.last().first >= treeLine.depth) {
                stack.removeAt(stack.size - 1)
            }
            val parentNode = if (stack.isEmpty()) {
                null
            } else {
                stack.last().second
            }

            val node = DependencyNode(
                id = treeLine.run { "$groupId:$artifactId" },
                groupId = treeLine.groupId,
                artifactId = treeLine.artifactId,
                version = treeLine.version,
                parent = parentNode,
                children = mutableListOf(),
                scope = treeLine.scope,
                isDependencyManagement = false
            )

            parentNode?.addChild(node)

            if (parentNode == null) {
                rootNodes.add(node)
            }

            stack.add(Pair(treeLine.depth, node))
        }

        return rootNodes
    }
}
