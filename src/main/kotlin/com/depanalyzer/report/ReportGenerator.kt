package com.depanalyzer.report

import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class ReportGenerator {
    private val jsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build()

    fun toJson(report: DependencyReport): String {
        return jsonMapper.writeValueAsString(report)
    }

    fun toJsonVerbose(report: DependencyReport): String {
        return jsonMapper.writeValueAsString(report)
    }

    fun toText(report: DependencyReport): String {
        val sb = StringBuilder()
        sb.appendLine("====================================================")
        sb.appendLine("Análisis de Dependencias: ${report.projectName}")
        sb.appendLine("====================================================")
        sb.appendLine()

        if (report.directVulnerable.isNotEmpty() || report.transitiveVulnerable.isNotEmpty()) {
            sb.appendLine("VULNERABILIDADES DETECTADAS")
            sb.appendLine("---------------------------")

            if (report.directVulnerable.isNotEmpty()) {
                sb.appendLine("[Directas]")
                report.directVulnerable.forEach { dep ->
                    sb.appendLine("  - ${dep.groupId}:${dep.artifactId}:${dep.version}")
                    dep.vulnerabilities.forEach { v ->
                        val desc = v.description ?: "No description available"
                        sb.appendLine("    * [${v.severity}] ${v.cveId}: $desc")
                    }
                }
                sb.appendLine()
            }

            if (report.transitiveVulnerable.isNotEmpty()) {
                sb.appendLine("[Transitivas]")
                report.transitiveVulnerable.forEach { dep ->
                    sb.appendLine("  - ${dep.groupId}:${dep.artifactId}:${dep.version}")
                    if (dep.dependencyChain != null) {
                        sb.appendLine("    Ruta: ${dep.dependencyChain.joinToString(" -> ")}")
                    }
                    dep.vulnerabilities.forEach { v ->
                        val desc = v.description ?: "No description available"
                        sb.appendLine("    * [${v.severity}] ${v.cveId}: $desc")
                    }
                }
                sb.appendLine()
            }
        }

        if (report.outdated.isNotEmpty()) {
            sb.appendLine("DEPENDENCIAS DESACTUALIZADAS")
            sb.appendLine("----------------------------")
            report.outdated.forEach { dep ->
                sb.appendLine("  - ${dep.groupId}:${dep.artifactId}: ${dep.currentVersion} -> ${dep.latestVersion}")
            }
            sb.appendLine()
        }

        sb.appendLine("RESUMEN")
        sb.appendLine("-------")
        sb.appendLine("  Al día: ${report.upToDate.size}")
        sb.appendLine("  Desactualizadas: ${report.outdated.size}")
        sb.appendLine("  Vulnerabilidades directas: ${report.directVulnerable.size}")
        sb.appendLine("  Vulnerabilidades transitivas: ${report.transitiveVulnerable.size}")
        sb.appendLine("====================================================")

        return sb.toString()
    }

    private fun renderTreeNode(sb: StringBuilder, node: DependencyTreeNode, level: Int, useAscii: Boolean) {
        val indent = "  ".repeat(level)
        val prefix = if (useAscii) {
            if (level == 0) "" else "|"
        } else {
            if (level == 0) "" else "│"
        }

        val marker = if (node.isDirectDependency) {
            if (useAscii) "[DIRECT]" else "🔴"
        } else {
            if (useAscii) "[TRANSITIVE]" else "🟡"
        }

        val nodeLabel = "${node.groupId}:${node.artifactId}:${node.currentVersion}"
        sb.appendLine("$indent$prefix$marker $nodeLabel")

        if (node.latestVersion != null) {
            val updateMarker = if (useAscii) "[UPDATE]" else "⬆️"
            val updateIndent = if (level == 0) "" else "  ".repeat(level) + "|  "
            sb.appendLine("$updateIndent$updateMarker Disponible: ${node.latestVersion}")
        }

        node.vulnerabilities.forEach { vuln ->
            val vulnMarker = when (vuln.severity) {
                VulnerabilitySeverity.CRITICAL -> if (useAscii) "[CRITICAL]" else "🔴"
                VulnerabilitySeverity.HIGH -> if (useAscii) "[HIGH]" else "🟠"
                VulnerabilitySeverity.MEDIUM -> if (useAscii) "[MEDIUM]" else "🟡"
                VulnerabilitySeverity.LOW -> if (useAscii) "[LOW]" else "🟢"
                VulnerabilitySeverity.UNKNOWN -> if (useAscii) "[UNKNOWN]" else "⚪"
            }
            val cvssStr = vuln.cvssScore?.let { " (${it})" } ?: ""
            val vulnIndent = if (level == 0) "" else "  ".repeat(level) + "|  "
            sb.appendLine("$vulnIndent$vulnMarker [${vuln.cveId}] ${vuln.severity}$cvssStr")
        }

        node.children.forEach { child ->
            renderTreeNode(sb, child, level + 1, useAscii)
        }
    }
}
