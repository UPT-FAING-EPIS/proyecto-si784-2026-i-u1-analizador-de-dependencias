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
        // Verbose mode includes all fields, Jackson already serializes everything
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

    fun toTextVerbose(report: DependencyReport): String {
        val sb = StringBuilder()
        sb.appendLine("====================================================")
        sb.appendLine("Análisis de Dependencias: ${report.projectName}")
        sb.appendLine("====================================================")
        sb.appendLine()

        if (report.directVulnerable.isNotEmpty() || report.transitiveVulnerable.isNotEmpty()) {
            sb.appendLine("VULNERABILIDADES DETECTADAS (DETALLADO)")
            sb.appendLine("---------------------------------------")

            val allVulnerabilities = mutableListOf<Pair<String, Vulnerability>>()

            report.directVulnerable.forEach { dep ->
                dep.vulnerabilities.forEach { v ->
                    allVulnerabilities.add("${dep.groupId}:${dep.artifactId}:${dep.version}" to v)
                }
            }

            report.transitiveVulnerable.forEach { dep ->
                dep.vulnerabilities.forEach { v ->
                    allVulnerabilities.add("${dep.groupId}:${dep.artifactId}:${dep.version}" to v)
                }
            }

            // Format as table
            sb.appendLine("┌──────────────────┬──────────┬───────────┬────────────┬──────────────────────┬──────────────────────────────┐")
            sb.appendLine("│ CVE ID           │ Severity │ CVSS Score│ Source     │ Retrieved At         │ Affected Dependency          │")
            sb.appendLine("├──────────────────┼──────────┼───────────┼────────────┼──────────────────────┼──────────────────────────────┤")

            allVulnerabilities.forEach { (coord, v) ->
                val cveId = v.cveId.padEnd(16).take(16)
                val severity = v.severity.toString().padEnd(8).take(8)
                val cvssScore = (v.cvssScore?.toString() ?: "N/A").padEnd(9).take(9)
                val source = v.source.toString().padEnd(10).take(10)
                val retrievedAt = (v.retrievedAt?.toString() ?: "N/A").padEnd(20).take(20)
                val affectedDep = coord.padEnd(28).take(28)

                sb.appendLine("│ $cveId │ $severity │ $cvssScore │ $source │ $retrievedAt │ $affectedDep │")
            }

            sb.appendLine("└──────────────────┴──────────┴───────────┴────────────┴──────────────────────┴──────────────────────────────┘")
            sb.appendLine()
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
