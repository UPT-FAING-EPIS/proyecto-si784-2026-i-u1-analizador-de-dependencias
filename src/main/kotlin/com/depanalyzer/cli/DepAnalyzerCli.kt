package com.depanalyzer.cli

import com.depanalyzer.core.ProjectAnalyzer
import com.depanalyzer.report.*
import com.depanalyzer.repository.NvdClient
import com.depanalyzer.repository.OssIndexClient
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import kotlin.io.path.writeText

class Depanalyzer : CliktCommand() {
    override fun help(context: Context): String = "Analizador de Dependencias Java/Kotlin"
    override fun run() = Unit
}

data class AnalyzeExecutionRequest(
    val projectPath: Path,
    val includeChains: Boolean,
    val disableMaven: Boolean,
    val disableGradle: Boolean,
    val verbose: Boolean,
    val treeMaxDepth: Int?,
    val treeExpandMode: TreeExpandMode,
    val timeoutSeconds: Long,
    val useNvd: Boolean,
    val ossIndexToken: String?,
    val nvdApiKey: String?
)

class Analyze(
    private val analyzeExecutor: (AnalyzeExecutionRequest) -> DependencyReport = { request ->
        val analyzer = ProjectAnalyzer(
            ossIndexClient = OssIndexClient(token = request.ossIndexToken),
            nvdClient = NvdClient(apiKey = request.nvdApiKey)
        )

        analyzer.analyze(
            request.projectPath,
            includeChains = request.includeChains,
            disableMaven = request.disableMaven,
            disableGradle = request.disableGradle,
            verbose = request.verbose,
            treeMaxDepth = request.treeMaxDepth,
            treeExpandMode = request.treeExpandMode,
            timeoutSeconds = request.timeoutSeconds,
            useNvd = request.useNvd
        )
    },
    private val jsonOutputPathProvider: (Path) -> Path = {
        Path.of(System.getProperty("user.dir"), "dependency-report.json")
    }
) : CliktCommand() {
    override fun help(context: Context): String = "Analiza las dependencias de un proyecto"

    private val path: Path? by argument(help = "Ruta al directorio del proyecto (default: directorio actual)")
        .path(mustExist = true, canBeFile = false)
        .optional()
    private val output: String? by option("-o", "--output", help = "Formato de salida (json a archivo)")
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
    private val dynamic: Boolean by option(
        "--dynamic",
        help = "Fuerza análisis dinámico (más preciso, más lento). Por defecto: análisis estático"
    ).flag(default = false)
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
    private val failOnCritical: Boolean by option(
        "--fail-on-critical",
        help = "Retorna exit code 1 si se detectan CVEs críticos"
    ).flag()

    override fun run() {
        val targetPath = path ?: Path.of(".")
        val startTime = System.currentTimeMillis()
        ProgressTracker.logStart("Iniciando análisis en $targetPath...")
        ProgressTracker.startProgress(
            listOf(
                "Detección",
                "Parseo",
                "Resolución de repos",
                "Consulta de versiones",
                "Árbol transitivo",
                "CVEs",
                "Reporte"
            )
        )

        val token = getTokenFromCliOrEnv()
        val nvdApiKey = getNvdApiKeyFromEnv()

        if (useNvd && nvdApiKey == null) {
            echo("Advertencia: --use-nvd requiere la variable de entorno NVD_API_KEY", err = true)
            echo("Las solicitudes sin token están limitadas a ~50 req/hora", err = true)
        }

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
            analyzeExecutor(
                AnalyzeExecutionRequest(
                    projectPath = targetPath,
                    includeChains = showChains,
                    disableMaven = !dynamic || offline || disableMaven,
                    disableGradle = !dynamic || disableGradle,
                    verbose = verbose,
                    treeMaxDepth = treeDepth,
                    treeExpandMode = expandMode,
                    timeoutSeconds = timeoutSeconds,
                    useNvd = useNvd,
                    ossIndexToken = token,
                    nvdApiKey = nvdApiKey
                )
            )
        } catch (e: Exception) {
            echo("Error durante el análisis: ${e.message}", err = true)
            return
        }

        ProgressTracker.advanceProgress("Reporte")
        val totalDuration = System.currentTimeMillis() - startTime
        ProgressTracker.logSeparator()
        ProgressTracker.completeProgress()
        ProgressTracker.logSuccess("Análisis completado", totalDuration)

        if (output?.lowercase() == "json") {
            val generator = ReportGenerator()
            val outputPath = jsonOutputPathProvider(targetPath)
            val json = if (verbose) generator.toJsonVerbose(report) else generator.toJson(report)
            outputPath.writeText(json)
            echo("Reporte JSON exportado a: $outputPath")
        } else if (verbose) {
            val renderer = ConsoleRenderer(noColor = noColor, useAscii = ascii, treeMaxDepth = treeDepth)
            renderer.renderVerbose(report, showChains = showChains, detailedChains = chainDetail)
        } else {
            val renderer = ConsoleRenderer(noColor = noColor, useAscii = ascii, treeMaxDepth = treeDepth)
            renderer.render(report, showChains = showChains, detailedChains = chainDetail)
        }

        if (failOnCritical && hasCriticalVulnerability(report)) {
            throw ProgramResult(1)
        }
    }

    private fun hasCriticalVulnerability(report: DependencyReport): Boolean {
        return (report.directVulnerable + report.transitiveVulnerable)
            .flatMap { it.vulnerabilities }
            .any { it.severity == VulnerabilitySeverity.CRITICAL }
    }

    private fun getTokenFromCliOrEnv(): String? {
        return ossIndexToken ?: System.getenv("OSS_INDEX_TOKEN")
    }

    private fun getNvdApiKeyFromEnv(): String? {
        return System.getenv("NVD_API_KEY")
    }
}

fun main(args: Array<String>) = Depanalyzer()
    .subcommands(Analyze(), Update())
    .main(args)



