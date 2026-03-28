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
                        sb.appendLine("    * [${v.severity}] ${v.id}: ${v.title}")
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
                        sb.appendLine("    * [${v.severity}] ${v.id}: ${v.title}")
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
}
