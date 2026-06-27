package com.depanalyzer.report

import com.depanalyzer.core.graph.DependencyNode
import com.depanalyzer.core.graph.VulnerabilityChain

internal class JsonReportWriter {
    private val output = StringBuilder()
    private var level = 0

    fun write(report: DependencyReport): String {
        obj {
            field("schemaVersion", report.schemaVersion)
            field("projectName", report.projectName)
            field("upToDate", report.upToDate, ::writeDependencyInfo)
            field("outdated", report.outdated, ::writeOutdated)
            field("directVulnerable", report.directVulnerable, ::writeVulnerable)
            field("transitiveVulnerable", report.transitiveVulnerable, ::writeVulnerable)
            field("vulnerabilityChains", report.vulnerabilityChains, ::writeChain)
            report.dependencyTree?.let { field("dependencyTree", it, ::writeTreeNode) }
            report.analysis?.let { field("analysis") { writeAnalysis(it) } }
        }
        output.append('\n')
        return output.toString()
    }

    private fun writeDependencyInfo(dependency: DependencyInfo) = obj {
        field("groupId", dependency.groupId)
        field("artifactId", dependency.artifactId)
        field("version", dependency.version)
        field("ecosystem", dependency.ecosystem.name)
        dependency.sourceLocation?.let { field("sourceLocation") { writeLocation(it) } }
    }

    private fun writeOutdated(dependency: OutdatedDependency) = obj {
        field("groupId", dependency.groupId)
        field("artifactId", dependency.artifactId)
        field("currentVersion", dependency.currentVersion)
        field("latestVersion", dependency.latestVersion)
        field("ecosystem", dependency.ecosystem.name)
        dependency.sourceLocation?.let { field("sourceLocation") { writeLocation(it) } }
    }

    private fun writeVulnerable(dependency: VulnerableDependency) = obj {
        field("groupId", dependency.groupId)
        field("artifactId", dependency.artifactId)
        field("version", dependency.version)
        field("vulnerabilities", dependency.vulnerabilities, ::writeVulnerability)
        dependency.dependencyChain?.let { field("dependencyChain", it, ::string) }
        field("ecosystem", dependency.ecosystem.name)
        dependency.sourceLocation?.let { field("sourceLocation") { writeLocation(it) } }
    }

    private fun writeVulnerability(vulnerability: Vulnerability) = obj {
        field("cveId", vulnerability.cveId)
        field("severity", vulnerability.severity.name)
        vulnerability.cvssScore?.let { field("cvssScore", it) }
        vulnerability.description?.let { field("description", it) }
        field("affectedDependency") { writeAffectedDependency(vulnerability.affectedDependency) }
        field("source", vulnerability.source.name)
        vulnerability.retrievedAt?.let { field("retrievedAt", it.toString()) }
        vulnerability.referenceUrl?.let { field("referenceUrl", it) }
    }

    private fun writeAffectedDependency(dependency: AffectedDependency) = obj {
        field("groupId", dependency.groupId)
        field("artifactId", dependency.artifactId)
        field("version", dependency.version)
        field("ecosystem", dependency.ecosystem.name)
    }

    private fun writeLocation(location: DependencySourceLocation) = obj {
        field("file", location.file)
        field("line", location.line)
        field("startColumn", location.startColumn)
        field("endColumn", location.endColumn)
    }

    private fun writeTreeNode(node: DependencyTreeNode) = obj {
        field("groupId", node.groupId)
        field("artifactId", node.artifactId)
        field("currentVersion", node.currentVersion)
        node.latestVersion?.let { field("latestVersion", it) }
        field("isDirectDependency", node.isDirectDependency)
        field("isDependencyManagement", node.isDependencyManagement)
        node.scope?.let { field("scope", it) }
        field("vulnerabilities", node.vulnerabilities, ::writeVulnerability)
        field("children", node.children, ::writeTreeNode)
        node.dependencyChain?.let { field("dependencyChain", it, ::string) }
        field("ecosystem", node.ecosystem.name)
    }

    private fun writeChain(chain: VulnerabilityChain) = obj {
        field("chain", chain.chain, ::writeDependencyNode)
        field("vulnerabilities", chain.vulnerabilities, ::writeVulnerability)
        field("isShortestPath", chain.isShortestPath)
        field("classification", chain.classification.name)
        field("depth", chain.depth)
        field("cveIds", chain.cveIds, ::string)
    }

    private fun writeDependencyNode(node: DependencyNode) = obj {
        field("id", node.id)
        field("groupId", node.groupId)
        field("artifactId", node.artifactId)
        field("version", node.version)
        node.scope?.let { field("scope", it) }
        field("isDependencyManagement", node.isDependencyManagement)
        field("ecosystem", node.ecosystem.name)
        field("coordinate", node.coordinate)
    }

    private fun writeAnalysis(analysis: AnalysisMetadata) = obj {
        field("requestedMode", analysis.requestedMode.name)
        field("actualMode", analysis.actualMode.name)
        field("projectType", analysis.projectType)
        field("ecosystems", analysis.ecosystems, ::string)
        field("durationMs", analysis.durationMs)
        field("warnings", analysis.warnings, ::string)
        field("providers") {
            obj {
                field("requested", analysis.providers.requested)
                field("used", analysis.providers.used, ::string)
                field("warnings", analysis.providers.warnings, ::string)
                field("statuses") {
                    obj {
                        analysis.providers.statuses.forEach { (provider, status) ->
                            field(provider, status)
                        }
                    }
                }
            }
        }
    }

    private fun obj(body: ObjectScope.() -> Unit) {
        output.append('{')
        level++
        ObjectScope().body()
        level--
        newline()
        output.append(indent()).append('}')
    }

    private fun <T> array(values: List<T>, writer: (T) -> Unit) {
        output.append('[')
        if (values.isNotEmpty()) {
            level++
            values.forEachIndexed { index, value ->
                newline()
                output.append(indent())
                writer(value)
                if (index != values.lastIndex) output.append(',')
            }
            level--
            newline()
            output.append(indent())
        }
        output.append(']')
    }

    private fun string(value: String) {
        output.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> output.append("\\\"")
                '\\' -> output.append("\\\\")
                '\b' -> output.append("\\b")
                '\u000C' -> output.append("\\f")
                '\n' -> output.append("\\n")
                '\r' -> output.append("\\r")
                '\t' -> output.append("\\t")
                else -> if (char.code < 0x20) {
                    output.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    output.append(char)
                }
            }
        }
        output.append('"')
    }

    private fun newline() {
        output.append('\n')
    }

    private fun indent(): String = "  ".repeat(level)

    private inner class ObjectScope {
        private var first = true

        fun field(name: String, value: String) = field(name) { string(value) }
        fun field(name: String, value: Int) = field(name) { output.append(value) }
        fun field(name: String, value: Long) = field(name) { output.append(value) }
        fun field(name: String, value: Double) = field(name) {
            require(value.isFinite()) { "JSON does not support non-finite values" }
            output.append(value)
        }
        fun field(name: String, value: Boolean) = field(name) { output.append(value) }
        fun <T> field(name: String, values: List<T>, writer: (T) -> Unit) = field(name) {
            array(values, writer)
        }

        fun field(name: String, writer: () -> Unit) {
            if (!first) output.append(',')
            newline()
            output.append(indent())
            string(name)
            output.append(" : ")
            writer()
            first = false
        }
    }
}
