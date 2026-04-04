package com.depanalyzer.cli

import com.depanalyzer.core.ProjectAnalyzer
import com.depanalyzer.report.*
import com.depanalyzer.repository.OssIndexClient
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

class Depanalyzer : CliktCommand() {
    override fun help(context: Context): String = "Analizador de Dependencias Java/Kotlin"
    override fun run() = Unit
}

class Analyze : CliktCommand() {
    override fun help(context: Context): String = "Analiza las dependencias de un proyecto"
    
    private val path: Path by argument(help = "Ruta al directorio del proyecto").path(mustExist = true, canBeFile = false)
    private val output: String? by option("-o", "--output", help = "Formato de salida (json)")
    private val noColor: Boolean by option("--no-color", help = "Desactiva el color en la consola").flag()
    private val ossIndexToken: String? by option("--oss-index-token", help = "Token de autenticación para OSS Index API")

    override fun run() {
        echo("Iniciando análisis en $path...")
        
        val token = getTokenFromCliOrEnv()
        val analyzer = ProjectAnalyzer(
            ossIndexClient = OssIndexClient(token = token)
        )
        
        val report = try {
            analyzer.analyze(path)
        } catch (e: Exception) {
            echo("Error durante el análisis: ${e.message}", err = true)
            return
        }

        if (output?.lowercase() == "json") {
            val generator = ReportGenerator()
            echo(generator.toJson(report))
        } else {
            val renderer = ConsoleRenderer(noColor = noColor)
            renderer.render(report)
        }
    }

    private fun getTokenFromCliOrEnv(): String? {
        return ossIndexToken ?: System.getenv("OSS_INDEX_TOKEN")
    }
}

fun main(args: Array<String>) = Depanalyzer()
    .subcommands(Analyze())
    .main(args)

