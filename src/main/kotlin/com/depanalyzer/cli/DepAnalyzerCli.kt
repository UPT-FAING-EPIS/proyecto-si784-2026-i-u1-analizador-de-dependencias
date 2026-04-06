package com.depanalyzer.cli

import com.depanalyzer.core.ProjectAnalyzer
import com.depanalyzer.report.ConsoleRenderer
import com.depanalyzer.report.ReportGenerator
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

    private val path: Path by argument(help = "Ruta al directorio del proyecto").path(
        mustExist = true,
        canBeFile = false
    )
    private val output: String? by option("-o", "--output", help = "Formato de salida (json)")
    private val noColor: Boolean by option("--no-color", help = "Desactiva el color en la consola").flag()
    private val ossIndexToken: String? by option(
        "-t",
        "--oss-index-token",
        help = "Token de autenticación para OSS Index API"
    )
    private val verbose: Boolean by option(
        "-v",
        "--verbose",
        help = "Modo detallado - muestra estructura completa del modelo"
    ).flag()
    private val showChains: Boolean by option(
        "--show-chains",
        help = "Muestra cadenas de vulnerabilidades (paths desde directas a vulnerables)"
    ).flag()
    private val chainDetail: Boolean by option(
        "--chain-detail",
        help = "Muestra detalles completos de cadenas (requiere --show-chains)"
    ).flag()
    private val offline: Boolean by option(
        "--offline",
        help = "Deshabilita Maven dependency:tree. Usa análisis estático (más rápido, menos preciso)"
    ).flag()
    private val disableMaven: Boolean by option(
        "--disable-maven",
        help = "Fuerza el análisis estático desactivando Maven"
    ).flag()

    override fun run() {
        echo("Iniciando análisis en $path...")

        val token = getTokenFromCliOrEnv()
        val analyzer = ProjectAnalyzer(
            ossIndexClient = OssIndexClient(token = token)
        )

        val report = try {
            analyzer.analyze(path, includeChains = showChains, disableMaven = offline || disableMaven, verbose = verbose)
        } catch (e: Exception) {
            echo("Error durante el análisis: ${e.message}", err = true)
            return
        }

        when {
            output?.lowercase() == "json" && verbose -> {
                val generator = ReportGenerator()
                echo(generator.toJsonVerbose(report))
            }

            output?.lowercase() == "json" -> {
                val generator = ReportGenerator()
                echo(generator.toJson(report))
            }

            verbose -> {
                val renderer = ConsoleRenderer(noColor = noColor)
                renderer.renderVerbose(report, showChains = showChains, detailedChains = chainDetail)
            }

            else -> {
                val renderer = ConsoleRenderer(noColor = noColor)
                renderer.render(report, showChains = showChains, detailedChains = chainDetail)
            }
        }
    }

    private fun getTokenFromCliOrEnv(): String? {
        return ossIndexToken ?: System.getenv("OSS_INDEX_TOKEN")
    }
}

fun main(args: Array<String>) = Depanalyzer()
    .subcommands(Analyze())
    .main(args)

