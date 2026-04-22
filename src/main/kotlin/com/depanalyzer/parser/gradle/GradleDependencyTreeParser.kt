package com.depanalyzer.parser.gradle

import com.depanalyzer.core.graph.DependencyNode

object GradleDependencyTreeParser {

    fun parse(output: String, verbose: Boolean = false): List<DependencyNode> {
        if (output.isEmpty()) return emptyList()

        val result = mutableListOf<DependencyNode>()
        val projects = extractProjects(output).ifEmpty {
            if (verbose) {
                System.err.println("[GradleDependencyTreeParser] No explicit project headers found, parsing entire output")
            }
            listOf("root" to output)
        }

        if (verbose) {
            System.err.println("[GradleDependencyTreeParser] Found ${projects.size} projects")
        }

        for ((projectName, projectContent) in projects) {
            val configurations = extractConfigurations(projectContent)
            if (verbose) {
                System.err.println("[GradleDependencyTreeParser] Project '$projectName': ${configurations.size} configurations")
            }

            for ((configName, configContent) in configurations) {
                val treeLines = parseTreeLines(configContent, configName)
                if (treeLines.isNotEmpty()) {
                    val nodes = buildHierarchy(treeLines)
                    result.addAll(nodes)
                }
            }
        }

        return deduplicateRoots(result)
    }

    private fun extractProjects(output: String): List<Pair<String, String>> {
        val projectRegex = Regex(
            pattern = """^(?:Root\s+)?project\s+'([^']*)'""",
            options = setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        )
        val matches = projectRegex.findAll(output)
        val projects = mutableListOf<Pair<String, String>>()

        val positions = matches.map { it.range.first to it.groupValues[1] }.toList()

        for (i in positions.indices) {
            val startPos = positions[i].first
            val projectName = positions[i].second
            val endPos = if (i < positions.size - 1) positions[i + 1].first else output.length

            val projectContent = output.substring(startPos, endPos)
            projects.add(projectName to projectContent)
        }

        return projects
    }

    private fun extractConfigurations(projectContent: String): List<Pair<String, String>> {
        val configRegex = Regex("""(?m)^([A-Za-z][A-Za-z0-9-]*)\s*-\s*""")
        val matches = configRegex.findAll(projectContent)
        val configurations = mutableListOf<Pair<String, String>>()

        val positions = matches.map { it.range.first to it.groupValues[1] }.toList()

        for (i in positions.indices) {
            val startPos = positions[i].first
            val configName = positions[i].second
            val endPos = if (i < positions.size - 1) positions[i + 1].first else projectContent.length

            val configContent = projectContent.substring(startPos, endPos)
            configurations.add(configName to configContent)
        }

        return configurations
    }

    private fun parseTreeLines(content: String, configName: String): List<TreeLine> {
        val result = mutableListOf<TreeLine>()
        val scope = mapConfigurationToScope(configName)

        content.split("\n")
            .filter { it.isNotEmpty() && (it.contains("---") || it.contains(":")) }
            .forEach { line ->
                parseTreeLine(line, scope)?.let { result.add(it) }
            }

        return result
    }

    private fun parseTreeLine(line: String, scope: String): TreeLine? {
        if (line.contains("(c)")) {
            return null
        }

        val depth = calculateDepth(line)

        // Extract the dependency coordinate
        val coordinateRegex = Regex("""([a-zA-Z0-9\-_.]+):([a-zA-Z0-9\-_.]+):([a-zA-Z0-9\-_.]+)""")
        val match = coordinateRegex.find(line) ?: return null

        val groupId = match.groupValues[1]
        val artifactId = match.groupValues[2]
        val version = match.groupValues[3]

        // Check for annotations
        val isExcluded = line.contains("(*)") || line.contains("(n)") || line.contains("(c)")
        val isDirect = depth == 0

        return TreeLine(
            depth = depth,
            line = line.trim(),
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            scope = scope,
            isDirect = isDirect,
            isExcluded = isExcluded
        )
    }

    private fun calculateDepth(line: String): Int {
        var depth = 0
        for (char in line) {
            if (char in "| +\\" || char == '-') {
                if (char != '-') depth++
                if (char == '\\' || char == '+') break
            } else if (char != ' ') {
                break
            }
        }
        return maxOf(0, depth / 4)  // Rough estimate: every 4 chars per level
    }

    private fun buildHierarchy(lines: List<TreeLine>): List<DependencyNode> {
        if (lines.isEmpty()) return emptyList()

        val result = mutableListOf<DependencyNode>()
        val stack = mutableListOf<Pair<DependencyNode, Int>>()  // Node + depth

        for (line in lines) {
            val node = DependencyNode(
                id = "${line.groupId}:${line.artifactId}:${line.version}",
                groupId = line.groupId,
                artifactId = line.artifactId,
                version = line.version,
                parent = null,
                children = mutableListOf(),
                scope = line.scope,
                isDependencyManagement = false
            )

            // Pop stack until we find the parent at a shallower depth
            while (stack.isNotEmpty() && stack.last().second >= line.depth) {
                stack.removeAt(stack.size - 1)
            }

            if (stack.isEmpty()) {
                // This is a root node
                result.add(node)
            } else {
                // Add as child to the node at the top of the stack
                val parent = stack.last().first
                val nodeWithParent = node.copy(parent = parent)
                parent.addChild(nodeWithParent)
                stack.add(nodeWithParent to line.depth)
                continue
            }

            stack.add(node to line.depth)
        }

        return result
    }

    private fun deduplicateRoots(nodes: List<DependencyNode>): List<DependencyNode> {
        if (nodes.isEmpty()) return emptyList()

        val deduped = linkedMapOf<String, DependencyNode>()
        for (node in nodes) {
            val existing = deduped[node.coordinate]
            deduped[node.coordinate] = if (existing == null) {
                copyTree(node, parent = null)
            } else {
                mergeTrees(existing, node, parent = null)
            }
        }
        return deduped.values.toList()
    }

    private fun mergeTrees(base: DependencyNode, incoming: DependencyNode, parent: DependencyNode?): DependencyNode {
        val baseChildrenByCoordinate = base.children.associateBy { it.coordinate }.toMutableMap()
        for (incomingChild in incoming.children) {
            val existing = baseChildrenByCoordinate[incomingChild.coordinate]
            baseChildrenByCoordinate[incomingChild.coordinate] = if (existing == null) {
                copyTree(incomingChild, parent = null)
            } else {
                mergeTrees(existing, incomingChild, parent = null)
            }
        }

        val mergedChildren = baseChildrenByCoordinate.values.sortedBy { it.coordinate }.toMutableList()
        val chosenScope = pickPreferredScope(base.scope, incoming.scope)

        val merged = DependencyNode(
            id = base.id,
            groupId = base.groupId,
            artifactId = base.artifactId,
            version = base.version,
            parent = parent,
            children = mutableListOf(),
            vulnerabilities = base.vulnerabilities,
            scope = chosenScope,
            isDependencyManagement = base.isDependencyManagement || incoming.isDependencyManagement
        )

        val childrenWithParent = mergedChildren.map { child ->
            setParentRecursively(child, merged)
        }
        merged.children.addAll(childrenWithParent)
        return merged
    }

    private fun copyTree(node: DependencyNode, parent: DependencyNode?): DependencyNode {
        val copy = DependencyNode(
            id = node.id,
            groupId = node.groupId,
            artifactId = node.artifactId,
            version = node.version,
            parent = parent,
            children = mutableListOf(),
            vulnerabilities = node.vulnerabilities,
            scope = node.scope,
            isDependencyManagement = node.isDependencyManagement
        )
        copy.children.addAll(node.children.map { child -> copyTree(child, copy) })
        return copy
    }

    private fun setParentRecursively(node: DependencyNode, parent: DependencyNode): DependencyNode {
        return copyTree(node, parent)
    }

    private fun pickPreferredScope(primary: String?, secondary: String?): String? {
        val score = mapOf(
            "compile" to 4,
            "runtime" to 3,
            "provided" to 2,
            "test" to 1
        )

        val left = primary?.lowercase()
        val right = secondary?.lowercase()
        if (left == null) return secondary
        if (right == null) return primary

        return if ((score[left] ?: 0) >= (score[right] ?: 0)) primary else secondary
    }

    private fun mapConfigurationToScope(configName: String): String {
        return when {
            configName.contains("compile", ignoreCase = true) && !configName.contains("test", ignoreCase = true) ->
                "compile"

            configName.contains("runtime", ignoreCase = true) && !configName.contains("test", ignoreCase = true) ->
                "runtime"

            configName.contains("test", ignoreCase = true) -> "test"
            configName.contains("provided", ignoreCase = true) -> "provided"
            else -> "compile"  // default
        }
    }

    /**
     * Represents a parsed line from the dependency tree.
     */
    private data class TreeLine(
        val depth: Int,
        val line: String,
        val groupId: String,
        val artifactId: String,
        val version: String,
        val scope: String,
        val isDirect: Boolean = false,
        val isExcluded: Boolean = false
    )
}
