package com.depanalyzer.report

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal

class ConsoleRenderer(
    noColor: Boolean = false,
    private val useAscii: Boolean = false,
    private val treeMaxDepth: Int? = null,
    ansiLevel: AnsiLevel? = null
) {
    private val terminal = Terminal(
        ansiLevel = ansiLevel ?: if (noColor) AnsiLevel.NONE else AnsiLevel.TRUECOLOR
    )

    fun render(report: DependencyReport, showChains: Boolean = false, detailedChains: Boolean = false) {
        terminal.println(bold("===================================================="))
        terminal.println(bold("Análisis de Dependencias: ") + blue(report.projectName))
        terminal.println(bold("===================================================="))
        terminal.println()

        report.dependencyTree?.takeIf { it.isNotEmpty() }?.let {
            renderDependencyTree(it)
        } ?: run {
            renderVulnerabilities(report)
            renderOutdated(report)
        }

        if (showChains && report.vulnerabilityChains.isNotEmpty()) {
            renderVulnerabilityChains(report, detailedChains)
        }
        renderSummary(report)
    }

    fun renderVerbose(report: DependencyReport, showChains: Boolean = false, detailedChains: Boolean = false) {
        terminal.println(bold("===================================================="))
        terminal.println(bold("Análisis de Dependencias: ") + blue(report.projectName))
        terminal.println(bold("===================================================="))
        terminal.println()

        report.dependencyTree?.takeIf { it.isNotEmpty() }?.let {
            renderDependencyTreeVerbose(it)
        } ?: run {
            renderVulnerabilitiesVerbose(report)
            renderOutdated(report)
        }

        if (showChains && report.vulnerabilityChains.isNotEmpty()) {
            renderVulnerabilityChains(report, detailedChains)
        }
        renderSummary(report)
    }

    private fun renderVulnerabilities(report: DependencyReport) {
        if (report.directVulnerable.isEmpty() && report.transitiveVulnerable.isEmpty()) return

        terminal.println(bold(red("VULNERABILIDADES DETECTADAS")))
        terminal.println(red("---------------------------"))

        val allVulnerabilities = mutableListOf<Triple<String, String, Vulnerability>>()

        report.directVulnerable.forEach { dep ->
            dep.vulnerabilities.forEach { v ->
                allVulnerabilities.add(Triple("${dep.groupId}:${dep.artifactId}:${dep.version}", "Directa", v))
            }
        }

        report.transitiveVulnerable.forEach { dep ->
            dep.vulnerabilities.forEach { v ->
                allVulnerabilities.add(Triple("${dep.groupId}:${dep.artifactId}:${dep.version}", "Transitiva", v))
            }
        }

        val vulnerabilityTable = table {
            header {
                row(
                    bold("CVE ID"),
                    bold("Severity"),
                    bold("CVSS"),
                    bold("Source"),
                    bold("Retrieved At"),
                    bold("Affected Dependency")
                )
            }
            body {
                allVulnerabilities.forEach { (coord, _, v) ->
                    val color = severityColor(v.severity)
                    row(
                        v.cveId,
                        color(v.severity.toString()),
                        v.cvssScore?.toString() ?: "N/A",
                        v.source.toString(),
                        v.retrievedAt?.toString()?.substring(0, 19) ?: "N/A",
                        coord
                    )
                }
            }
        }

        terminal.println(vulnerabilityTable)
        terminal.println()
    }

    private fun renderOutdated(report: DependencyReport) {
        if (report.outdated.isEmpty()) return

        terminal.println(bold(yellow("DEPENDENCIAS DESACTUALIZADAS")))
        terminal.println(yellow("----------------------------"))

        val outdatedTable = table {
            header { row(bold("Dependencia"), bold("Actual"), bold("Nueva"), bold("Estado")) }
            body {
                report.outdated.forEach { dep ->
                    row(
                        "${dep.groupId}:${dep.artifactId}",
                        dep.currentVersion,
                        green(dep.latestVersion),
                        yellow("OUTDATED")
                    )
                }
            }
        }
        terminal.println(outdatedTable)
        terminal.println()
    }

    private fun renderSummary(report: DependencyReport) {
        terminal.println(bold("RESUMEN"))
        terminal.println("-------")
        terminal.println("  " + green("Al día: ${report.upToDate.size}"))
        terminal.println("  " + yellow("Desactualizadas: ${report.outdated.size}"))

        val totalVulnerabilities = report.directVulnerable.size + report.transitiveVulnerable.size
        if (totalVulnerabilities > 0) {
            terminal.println("  " + red("Vulnerabilidades: $totalVulnerabilities"))
        } else {
            terminal.println("  " + green("Vulnerabilidades: 0"))
        }

        terminal.println(bold("===================================================="))
    }

    private fun renderVulnerabilityChains(report: DependencyReport, detailed: Boolean = false) {
        terminal.println(bold(cyan("CADENAS DE VULNERABILIDADES")))
        terminal.println(cyan("---------------------------"))

        if (report.vulnerabilityChains.isEmpty()) {
            terminal.println("No vulnerability chains found")
            return
        }

        report.vulnerabilityChains.groupBy { it.directDependency.id }.forEach { (directDepId, chainsForDirect) ->
            terminal.println(bold("De: ") + yellow(directDepId))
            data class ChainSignature(
                val vulnerableNodeId: String,
                val cveSet: Set<String>
            )

            val signatureMap = chainsForDirect.groupBy { chain ->
                ChainSignature(
                    vulnerableNodeId = chain.vulnerableNode.id,
                    cveSet = chain.cveIds.toSet()
                )
            }
            signatureMap.forEach { (_, pathsWithSameSignature) ->
                val shortestPath = pathsWithSameSignature.minByOrNull { it.chain.size }
                    ?: return@forEach
                val marker = cyan("✓")
                val chainPath = shortestPath.chain.joinToString(" → ") { it.coordinate }
                terminal.println("  $marker $chainPath")
                if (pathsWithSameSignature.size > 1) {
                    val alternativeCount = pathsWithSameSignature.size - 1
                    terminal.println(gray("    📌 +$alternativeCount alternative path${if (alternativeCount > 1) "s" else ""} (all longer)"))
                }
                if (detailed) {
                    shortestPath.vulnerabilities.forEach { vuln ->
                        val color = severityColor(vuln.severity)
                        terminal.println("    - " + color("[${vuln.severity}] ${vuln.cveId}"))
                    }
                }
            }
            terminal.println()
        }
    }

    private fun severityColor(severity: VulnerabilitySeverity): TextStyle {
        return when (severity) {
            VulnerabilitySeverity.CRITICAL -> (red + bold)
            VulnerabilitySeverity.HIGH -> red
            VulnerabilitySeverity.MEDIUM -> yellow
            VulnerabilitySeverity.LOW -> gray
            VulnerabilitySeverity.UNKNOWN -> white
        }
    }

    private fun renderDependencyTree(nodes: List<DependencyTreeNode>, level: Int = 0) {
        if (level == 0) {
            terminal.println(bold(red("📦 DEPENDENCIAS CON PROBLEMAS")))
            terminal.println(red("" + if (useAscii) "----------------------------" else "────────────────────────────"))
        }

        nodes.forEach { node ->
            // Verificar profundidad máxima
            if (treeMaxDepth != null && level >= treeMaxDepth) {
                return@forEach
            }

            val prefix = getTreePrefix(level, useAscii)
            val marker = if (node.isDirectDependency) {
                if (useAscii) "[DIRECT]" else "🔴"
            } else {
                if (useAscii) "[TRANSITIVE]" else "🟡"
            }

            val severityColor = node.maxSeverity?.let { severityColor(it) } ?: white
            val nodeLabel = "${node.groupId}:${node.artifactId}:${node.currentVersion}"

            terminal.println("$prefix $marker ${severityColor(nodeLabel)} ${if (node.isDirectDependency) "" else "[TRANSITIVO]"}")

            if (node.latestVersion != null) {
                val updatePrefix = getTreeContinuePrefix(level, useAscii)
                val updateMarker = if (useAscii) "[UPDATE]" else "⬆️"
                terminal.println("$updatePrefix $updateMarker ${cyan("Disponible: ${node.latestVersion}")}")
            }

            node.vulnerabilities.forEach { vuln ->
                val vulnPrefix = getTreeContinuePrefix(level, useAscii)
                val vulnMarker = when (vuln.severity) {
                    VulnerabilitySeverity.CRITICAL -> if (useAscii) "[CRITICAL]" else "🔴"
                    VulnerabilitySeverity.HIGH -> if (useAscii) "[HIGH]" else "🟠"
                    VulnerabilitySeverity.MEDIUM -> if (useAscii) "[MEDIUM]" else "🟡"
                    VulnerabilitySeverity.LOW -> if (useAscii) "[LOW]" else "🟢"
                    VulnerabilitySeverity.UNKNOWN -> if (useAscii) "[UNKNOWN]" else "⚪"
                }
                val vulnColor = severityColor(vuln.severity)
                val cvssStr = vuln.cvssScore?.let { " (${it})" } ?: ""
                terminal.println("$vulnPrefix $vulnMarker ${vulnColor("[${vuln.cveId}] ${vuln.severity}$cvssStr")}")
            }

            if (node.children.isNotEmpty()) {
                renderDependencyTree(node.children, level + 1)
            }
        }

        if (level == 0) {
            terminal.println()
        }
    }

    private fun renderDependencyTreeVerbose(nodes: List<DependencyTreeNode>, level: Int = 0) {
        if (level == 0) {
            terminal.println(bold(red("📦 DEPENDENCIAS CON PROBLEMAS (DETALLADO)")))
            terminal.println(red("" + if (useAscii) "-------------------------------------------" else "───────────────────────────────────────────"))
        }

        nodes.forEach { node ->
            if (treeMaxDepth != null && level >= treeMaxDepth) {
                return@forEach
            }

            val prefix = getTreePrefix(level, useAscii)
            val marker = if (node.isDirectDependency) {
                if (useAscii) "[DIRECT]" else "🔴"
            } else {
                if (useAscii) "[TRANSITIVE]" else "🟡"
            }

            val nodeLabel = "${node.groupId}:${node.artifactId}:${node.currentVersion}"
            val scopeStr = node.scope?.let { " | $it" } ?: ""
            val typeStr = if (node.isDirectDependency) "DIRECTO" else "TRANSITIVO"

            terminal.println("$prefix $marker ${bold(nodeLabel)} [$typeStr$scopeStr]")

            val detailPrefix = getTreeContinuePrefix(level, useAscii)
            terminal.println("$detailPrefix ID: ${node.coordinate}")

            node.dependencyChain?.takeIf { it.isNotEmpty() }?.let { chain ->
                val chainStr = chain.joinToString(" → ")
                terminal.println("$detailPrefix ${gray("Ruta: $chainStr")}")
            }

            if (node.latestVersion != null) {
                val updateMarker = if (useAscii) "[UPDATE]" else "⬆️"
                terminal.println("$detailPrefix $updateMarker ${cyan("Actualización: ${node.latestVersion}")}")
            }

            if (node.vulnerabilities.isNotEmpty()) {
                terminal.println("$detailPrefix ${bold("Vulnerabilidades:")} ${node.vulnerabilities.size}")
                node.vulnerabilities.forEach { vuln ->
                    val vulnColor = severityColor(vuln.severity)
                    terminal.println("$detailPrefix  ├─ ${vulnColor("[${vuln.cveId}] ${vuln.severity}")} ${vuln.cvssScore?.let { "(CVSS $it)" } ?: ""}")

                    vuln.description?.let { desc ->
                        val truncated = if (desc.length > 80) desc.substring(0, 77) + "..." else desc
                        terminal.println("$detailPrefix  │  $truncated")
                    }

                    terminal.println("$detailPrefix  │  Fuente: ${vuln.source}")
                    vuln.retrievedAt?.let {
                        terminal.println(
                            "$detailPrefix  │  Obtenido: ${
                                it.toString().substring(0, 19)
                            }"
                        )
                    }
                    vuln.referenceUrl?.let { terminal.println("$detailPrefix  │  ${blue(it)}") }
                }
            }

            terminal.println()

            if (node.children.isNotEmpty()) {
                renderDependencyTreeVerbose(node.children, level + 1)
            }
        }

        if (level == 0) {
            terminal.println()
        }
    }

    private fun getTreePrefix(level: Int, useAscii: Boolean): String {
        val indent = "  ".repeat(level)
        return if (useAscii) {
            if (level == 0) "" else "$indent|"
        } else {
            if (level == 0) "" else "$indent└"
        }
    }

    private fun getTreeContinuePrefix(level: Int, useAscii: Boolean): String {
        val indent = "  ".repeat(level)
        return if (useAscii) {
            if (level == 0) "|" else "$indent|"
        } else {
            if (level == 0) "│" else "$indent│"
        }
    }

    private fun renderVulnerabilitiesVerbose(report: DependencyReport) {
        if (report.directVulnerable.isEmpty() && report.transitiveVulnerable.isEmpty()) return

        terminal.println(bold(red("VULNERABILIDADES DETECTADAS (DETALLADO)")))
        terminal.println(red("------------------------------------------"))

        if (report.directVulnerable.isNotEmpty()) {
            terminal.println(bold("Directas:"))
            report.directVulnerable.forEach { dep ->
                terminal.println("  - ${dep.groupId}:${dep.artifactId}:" + yellow(dep.version))
                dep.vulnerabilities.forEach { v ->
                    val color = severityColor(v.severity)
                    val desc = v.description ?: "No description available"
                    terminal.println("    * " + color("[${v.severity}] ${v.cveId}: $desc"))
                }
            }
            terminal.println()
        }

        if (report.transitiveVulnerable.isNotEmpty()) {
            terminal.println(bold("Transitivas:"))
            report.transitiveVulnerable.forEach { dep ->
                terminal.println("  - ${dep.groupId}:${dep.artifactId}:" + yellow(dep.version))
                if (dep.dependencyChain != null) {
                    terminal.println(gray("    Ruta: ${dep.dependencyChain.joinToString(" -> ")}"))
                }
                dep.vulnerabilities.forEach { v ->
                    val color = severityColor(v.severity)
                    val desc = v.description ?: "No description available"
                    terminal.println("    * " + color("[${v.severity}] ${v.cveId}: $desc"))
                }
            }
            terminal.println()
        }
    }
}
