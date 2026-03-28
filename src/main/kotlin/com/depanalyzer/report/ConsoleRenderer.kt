package com.depanalyzer.report

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal

class ConsoleRenderer(noColor: Boolean = false) {
    private val terminal = Terminal(
        ansiLevel = if (noColor) AnsiLevel.NONE else AnsiLevel.TRUECOLOR
    )

    fun render(report: DependencyReport) {
        terminal.println(bold("===================================================="))
        terminal.println(bold("Análisis de Dependencias: ") + blue(report.projectName))
        terminal.println(bold("===================================================="))
        terminal.println()

        renderVulnerabilities(report)
        renderOutdated(report)
        renderSummary(report)
    }

    private fun renderVulnerabilities(report: DependencyReport) {
        if (report.directVulnerable.isEmpty() && report.transitiveVulnerable.isEmpty()) return

        terminal.println(bold(red("VULNERABILIDADES DETECTADAS")))
        terminal.println(red("---------------------------"))

        if (report.directVulnerable.isNotEmpty()) {
            terminal.println(bold("Directas:"))
            report.directVulnerable.forEach { dep ->
                terminal.println("  - ${dep.groupId}:${dep.artifactId}:" + yellow(dep.version))
                dep.vulnerabilities.forEach { v ->
                    val color = severityColor(v.severity)
                    terminal.println("    * " + color("[${v.severity}] ${v.id}: ${v.title}"))
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
                    terminal.println("    * " + color("[${v.severity}] ${v.id}: ${v.title}"))
                }
            }
            terminal.println()
        }
    }

    private fun renderOutdated(report: DependencyReport) {
        if (report.outdated.isEmpty()) return

        terminal.println(bold(yellow("DEPENDENCIAS DESACTUALIZADAS")))
        terminal.println(yellow("----------------------------"))

        val outdatedTable = table {
            header { row(bold("Dependencia"), bold("Actual"), bold("Nueva"), bold("Estado")) }
            body {
                report.outdated.forEach { dep ->
                    row("${dep.groupId}:${dep.artifactId}", dep.currentVersion, green(dep.latestVersion), yellow("OUTDATED"))
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

    private fun severityColor(severity: VulnerabilitySeverity): TextStyle {
        return when (severity) {
            VulnerabilitySeverity.CRITICAL -> (red + bold)
            VulnerabilitySeverity.HIGH -> red
            VulnerabilitySeverity.MEDIUM -> yellow
            VulnerabilitySeverity.LOW -> gray
            VulnerabilitySeverity.UNKNOWN -> white
        }
    }
}
