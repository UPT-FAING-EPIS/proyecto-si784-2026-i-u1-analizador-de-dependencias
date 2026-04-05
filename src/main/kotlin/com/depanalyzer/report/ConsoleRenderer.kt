package com.depanalyzer.report

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal

class ConsoleRenderer(noColor: Boolean = false) {
    private val terminal = Terminal(
        ansiLevel = if (noColor) AnsiLevel.NONE else AnsiLevel.TRUECOLOR
    )

    fun render(report: DependencyReport, showChains: Boolean = false, detailedChains: Boolean = false) {
        terminal.println(bold("===================================================="))
        terminal.println(bold("Análisis de Dependencias: ") + blue(report.projectName))
        terminal.println(bold("===================================================="))
        terminal.println()

        renderVulnerabilities(report)
        renderOutdated(report)
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

        renderVulnerabilitiesVerbose(report)
        renderOutdated(report)
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

        // Render table with mordant
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

        // Group by direct dependency first
        report.vulnerabilityChains.groupBy { it.directDependency.id }.forEach { (directDepId, chainsForDirect) ->
            terminal.println(bold("De: ") + yellow(directDepId))
            
            // Group by signature (direct + vulnerable + cveSet) to identify alternative paths
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
            
            // For each unique signature, show only the shortest path with alternative count
            signatureMap.forEach { (_, pathsWithSameSignature) ->
                // Find shortest path (by chain size)
                val shortestPath = pathsWithSameSignature.minByOrNull { it.chain.size }
                    ?: return@forEach
                
                val marker = cyan("✓")
                val chainPath = shortestPath.chain.joinToString(" → ") { it.coordinate }
                terminal.println("  $marker $chainPath")
                
                // Show alternative paths note if there are multiple paths with same signature
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
