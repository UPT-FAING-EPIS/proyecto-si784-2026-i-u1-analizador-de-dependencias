package com.depanalyzer.cli

import com.depanalyzer.BuildInfo
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option

data class CapabilityDocument(
    val cliVersion: String = BuildInfo.VERSION,
    val reportSchemas: List<String> = listOf("1.0", "1.1"),
    val features: Map<String, Boolean> = linkedMapOf(
        "analyze.outputFile" to true,
        "analyze.stdout" to true,
        "analyze.progressJson" to true,
        "report.dependencyTree" to true,
        "report.vulnerabilityChains" to true,
        "update.plan" to true,
        "update.applyById" to true
    )
)

class Capabilities : CliktCommand(name = "capabilities") {
    private val output: String by option(
        "--output",
        help = "Formato de salida (json)"
    ).default("json")

    override fun help(context: Context): String = "Muestra capacidades de integracion del CLI"

    override fun run() {
        require(output.equals("json", ignoreCase = true)) { "Solo se admite --output json" }
        echo(CapabilityJsonWriter.write(CapabilityDocument()))
    }
}

internal object CapabilityJsonWriter {
    fun write(document: CapabilityDocument): String {
        val schemas = document.reportSchemas.joinToString(",") { "\"${escape(it)}\"" }
        val features = document.features.entries.joinToString(",") { (name, enabled) ->
            "\"${escape(name)}\":$enabled"
        }
        return """{"cliVersion":"${escape(document.cliVersion)}","reportSchemas":[$schemas],"features":{$features}}"""
    }

    private fun escape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
