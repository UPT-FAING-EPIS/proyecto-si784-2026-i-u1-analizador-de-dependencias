package com.depanalyzer.cli

import com.depanalyzer.core.ProjectAnalyzer
import com.depanalyzer.report.ConsoleRenderer
import com.depanalyzer.report.ReportGenerator
import com.depanalyzer.report.TreeExpandMode
import com.depanalyzer.repository.NvdClient
import com.depanalyzer.repository.OssIndexClient
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
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
    private val disableGradle: Boolean by option(
        "--disable-gradle",
        help = "Desactiva Gradle dependency tree execution, usa análisis estático de build.gradle"
    ).flag()
    private val ascii: Boolean by option(
        "--ascii",
        help = "Usa caracteres ASCII en lugar de Unicode para el árbol de dependencias"
    ).flag()
    private val treeDepth: Int? by option(
        "--tree-depth",
        help = "Limita la profundidad del árbol de dependencias a N niveles"
    ).int()
    private val treeExpand: String? by option(
        "--tree-expand",
        help = "Modo de expansión del árbol: collapsed, critical, high, medium, all (default: all)"
    )
    private val timeout: Int? by option(
        "--timeout",
        help = "Timeout en segundos para descarga de dependencias (default: 1800s = 30 min)"
    ).int()
    private val useNvd: Boolean by option(
        "--use-nvd",
        help = "Enriquece vulnerabilidades con datos de NVD (requiere NVD_API_KEY)"
    ).flag()

    override fun run() {
        val startTime = System.currentTimeMillis()
        ProgressTracker.logStart("Iniciando análisis en $path...")

        val token = getTokenFromCliOrEnv()
        val nvdApiKey = getNvdApiKeyFromEnv()
        
        if (useNvd && nvdApiKey == null) {
            echo("Advertencia: --use-nvd requiere la variable de entorno NVD_API_KEY", err = true)
            echo("Las solicitudes sin token están limitadas a ~50 req/hora", err = true)
        }
        
        val analyzer = ProjectAnalyzer(
            ossIndexClient = OssIndexClient(token = token),
            nvdClient = NvdClient(apiKey = nvdApiKey)
        )

        val expandMode = when (treeExpand?.lowercase()) {
            "collapsed" -> TreeExpandMode.COLLAPSED
            "critical" -> TreeExpandMode.CRITICAL
            "high" -> TreeExpandMode.HIGH
            "medium" -> TreeExpandMode.MEDIUM
            "all", null -> TreeExpandMode.ALL
            else -> {
                echo(
                    "Error: modo de expansión desconocido '$treeExpand'. Use: collapsed, critical, high, medium, all",
                    err = true
                )
                return
            }
        }

        val timeoutSeconds = timeout?.toLong() ?: 1800L

        val report = try {
            analyzer.analyze(
                path,
                includeChains = showChains,
                disableMaven = offline || disableMaven,
                disableGradle = disableGradle,
                verbose = verbose,
                treeMaxDepth = treeDepth,
                treeExpandMode = expandMode,
                timeoutSeconds = timeoutSeconds,
                useNvd = useNvd
            )
        } catch (e: Exception) {
            echo("Error durante el análisis: ${e.message}", err = true)
            return
        }

        val totalDuration = System.currentTimeMillis() - startTime
        ProgressTracker.logSeparator()
        ProgressTracker.logSuccess("Análisis completado", totalDuration)

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
                val renderer = ConsoleRenderer(noColor = noColor, useAscii = ascii, treeMaxDepth = treeDepth)
                renderer.renderVerbose(report, showChains = showChains, detailedChains = chainDetail)
            }

            else -> {
                val renderer = ConsoleRenderer(noColor = noColor, useAscii = ascii, treeMaxDepth = treeDepth)
                renderer.render(report, showChains = showChains, detailedChains = chainDetail)
            }
        }
    }

    private fun getTokenFromCliOrEnv(): String? {
        return ossIndexToken ?: System.getenv("OSS_INDEX_TOKEN")
    }
    
    private fun getNvdApiKeyFromEnv(): String? {
        return System.getenv("NVD_API_KEY")
    }
}

fun main(args: Array<String>) = Depanalyzer()
    .subcommands(Analyze())
    .main(args)



