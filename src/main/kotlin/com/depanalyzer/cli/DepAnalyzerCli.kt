package com.depanalyzer.cli

import com.depanalyzer.core.ProjectAnalyzer
import com.depanalyzer.parser.*
import com.depanalyzer.report.*
import com.depanalyzer.repository.NvdClient
import com.depanalyzer.repository.OssIndexClient
import com.depanalyzer.tui.AnalyzeTuiApp
import com.depanalyzer.tui.TerminalCapabilities
import com.depanalyzer.tui.TerminalCapabilitiesDetector
import com.depanalyzer.update.*
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
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
    val showCommandOutput: Boolean = false,
    val ossIndexToken: String?,
    val nvdApiKey: String?,
    val onPartialReport: ((DependencyReport) -> Unit)? = null
)

data class TuiLaunchConfig(
    val initialStatus: String,
    val progressHint: String? = null,
    val scanProvider: ((DependencyReport) -> Unit) -> DependencyReport,
    val initialReport: DependencyReport? = null,
    val applyUpdates: ((List<UpdateSuggestion>) -> List<UpdateResult>)? = null
)

private fun defaultAnalyzeExecutor(request: AnalyzeExecutionRequest): DependencyReport {
    val analyzer = ProjectAnalyzer(
        ossIndexClient = OssIndexClient(token = request.ossIndexToken),
        nvdClient = NvdClient(apiKey = request.nvdApiKey)
    )

    return analyzer.analyze(
        request.projectPath,
        includeChains = request.includeChains,
        disableMaven = request.disableMaven,
        disableGradle = request.disableGradle,
        verbose = request.verbose,
        treeMaxDepth = request.treeMaxDepth,
        treeExpandMode = request.treeExpandMode,
        timeoutSeconds = request.timeoutSeconds,
        useNvd = request.useNvd,
        showCommandOutput = request.showCommandOutput,
        onPartialReport = request.onPartialReport
    )
}

private fun defaultJsonOutputPathProvider(@Suppress("UNUSED_PARAMETER") targetPath: Path): Path {
    return Path.of(System.getProperty("user.dir"), "dependency-report.json")
}

private fun defaultTuiRunner(config: TuiLaunchConfig, capabilities: TerminalCapabilities): DependencyReport? {
    return AnalyzeTuiApp(terminal = Terminal(ansiLevel = capabilities.ansiLevel))
        .runAsync(
            initialStatus = config.initialStatus,
            progressHint = config.progressHint,
            scanProvider = config.scanProvider,
            initialReport = config.initialReport,
            applyUpdates = config.applyUpdates
        )
}

abstract class BaseAnalyzeCommand(
    commandName: String,
    private val forceTui: Boolean,
    private val analyzeExecutor: (AnalyzeExecutionRequest) -> DependencyReport,
    private val jsonOutputPathProvider: (Path) -> Path,
    private val terminalCapabilitiesDetector: TerminalCapabilitiesDetector,
    private val tuiRunner: (TuiLaunchConfig, TerminalCapabilities) -> DependencyReport?
) : CliktCommand(name = commandName) {
    private val path: Path? by argument(help = "Ruta al directorio del proyecto (default: directorio actual)")
        .path(mustExist = true, canBeFile = false)
        .optional()
    private val output: String? by option("-o", "--output", help = "Formato de salida (json a archivo)")
    private val noColor: Boolean by option("--no-color", help = "Desactiva el color en la consola").flag()
    private val tui: Boolean by option("--tui", help = "Activa la interfaz TUI interactiva").flag()
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
    private val commandOutput: Boolean by option(
        "--command-output",
        help = "Muestra salida detallada de comandos Gradle/Maven durante el analisis dinamico"
    ).flag()
    private val failOnCritical: Boolean by option(
        "--fail-on-critical",
        help = "Retorna exit code 1 si se detectan CVEs críticos"
    ).flag()

    override fun run() {
        val targetPath = path ?: Path.of(".")
        val tuiRequested = forceTui || tui
        val startTime = System.currentTimeMillis()
        val token = getTokenFromCliOrEnv()
        val nvdApiKey = getNvdApiKeyFromEnv()

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
        val capabilities = terminalCapabilitiesDetector.detect(noColor = noColor)
        val interactiveTui = tuiRequested && capabilities.supportsInteractiveTui

        ProgressTracker.setMuted(interactiveTui)

        try {
            if (!interactiveTui) {
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
            }

            if (!interactiveTui && useNvd && nvdApiKey == null) {
                echo("Advertencia: --use-nvd requiere la variable de entorno NVD_API_KEY", err = true)
                echo("Las solicitudes sin token están limitadas a ~50 req/hora", err = true)
            }

            if (interactiveTui) {
                val dynamicForcedMessage = buildDynamicForcedMessage()
                val initialDirectReport = buildInitialDirectReport(targetPath)
                val updateApplier = buildTuiUpdateApplier(targetPath)
                val tuiRequest = AnalyzeExecutionRequest(
                    projectPath = targetPath,
                    includeChains = true,
                    disableMaven = false,
                    disableGradle = false,
                    verbose = verbose,
                    treeMaxDepth = treeDepth,
                    treeExpandMode = expandMode,
                    timeoutSeconds = timeoutSeconds,
                    useNvd = useNvd,
                    showCommandOutput = commandOutput,
                    ossIndexToken = token,
                    nvdApiKey = nvdApiKey
                )

                val tuiReport = tuiRunner(
                    TuiLaunchConfig(
                        initialStatus = "Iniciando escaneo dinámico...",
                        progressHint = dynamicForcedMessage,
                        initialReport = initialDirectReport,
                        applyUpdates = updateApplier,
                        scanProvider = { onPartialReport ->
                            analyzeExecutor(tuiRequest.copy(onPartialReport = onPartialReport))
                        }
                    ),
                    capabilities
                )

                if (failOnCritical && tuiReport != null && hasCriticalVulnerability(tuiReport)) {
                    throw ProgramResult(1)
                }
                return
            }

            if (tuiRequested && !capabilities.supportsInteractiveTui) {
                echo("Advertencia: TUI no disponible en este entorno (sin TTY o CI). Se usa salida CLI.", err = true)
            }

            val standardRequest = AnalyzeExecutionRequest(
                projectPath = targetPath,
                includeChains = showChains,
                disableMaven = !dynamic || offline || disableMaven,
                disableGradle = !dynamic || disableGradle,
                verbose = verbose,
                treeMaxDepth = treeDepth,
                treeExpandMode = expandMode,
                timeoutSeconds = timeoutSeconds,
                useNvd = useNvd,
                showCommandOutput = commandOutput,
                ossIndexToken = token,
                nvdApiKey = nvdApiKey
            )

            val report = try {
                analyzeExecutor(standardRequest)
            } catch (e: Exception) {
                echo("Error durante el análisis: ${e.message}", err = true)
                return
            }

            if (!tuiRequested) {
                ProgressTracker.advanceProgress("Reporte")
            }
            val totalDuration = System.currentTimeMillis() - startTime
            if (!tuiRequested) {
                ProgressTracker.logSeparator()
                ProgressTracker.completeProgress()
                ProgressTracker.logSuccess("Análisis completado", totalDuration)
            }

            renderCliOutput(targetPath, report, capabilities)

            if (failOnCritical && hasCriticalVulnerability(report)) {
                throw ProgramResult(1)
            }
        } finally {
            ProgressTracker.setMuted(false)
            ProgressTracker.setListener(null)
        }
    }

    private fun buildDynamicForcedMessage(): String {
        val ignoredFlags = mutableListOf<String>()
        if (offline) ignoredFlags += "--offline"
        if (disableMaven) ignoredFlags += "--disable-maven"
        if (disableGradle) ignoredFlags += "--disable-gradle"

        return if (ignoredFlags.isEmpty()) {
            "Modo TUI: análisis dinámico habilitado para dependencias transitivas"
        } else {
            "Modo TUI: análisis dinámico forzado, ignorando ${ignoredFlags.joinToString(", ")}"
        }
    }

    private fun buildInitialDirectReport(projectPath: Path): DependencyReport? {
        return runCatching {
            val projectType = ProjectDetector().detect(projectPath)
            val projectDir = projectPath.toFile()
            val directNodes = when (projectType) {
                ProjectType.MAVEN -> {
                    val pom = File(projectDir, "pom.xml")
                    PomDependencyParser()
                        .parse(pom)
                        .filter { it.section == DependencySection.DEPENDENCIES }
                        .distinctBy { "${it.groupId}:${it.artifactId}" }
                        .map { dep ->
                            DependencyTreeNode(
                                groupId = dep.groupId,
                                artifactId = dep.artifactId,
                                currentVersion = dep.version ?: "unknown",
                                isDirectDependency = true,
                                scope = dep.scope
                            )
                        }
                }

                ProjectType.GRADLE_GROOVY -> {
                    val buildFile = File(projectDir, "build.gradle")
                    GradleGroovyDependencyParser()
                        .parse(buildFile)
                        .distinctBy { "${it.groupId}:${it.artifactId}" }
                        .map { dep ->
                            DependencyTreeNode(
                                groupId = dep.groupId,
                                artifactId = dep.artifactId,
                                currentVersion = dep.version ?: "unknown",
                                isDirectDependency = true,
                                scope = dep.configuration
                            )
                        }
                }

                ProjectType.GRADLE_KOTLIN -> {
                    val buildFile = File(projectDir, "build.gradle.kts")
                    GradleKotlinDependencyParser()
                        .parse(buildFile)
                        .distinctBy { "${it.groupId}:${it.artifactId}" }
                        .map { dep ->
                            DependencyTreeNode(
                                groupId = dep.groupId,
                                artifactId = dep.artifactId,
                                currentVersion = dep.version ?: "unknown",
                                isDirectDependency = true,
                                scope = dep.configuration
                            )
                        }
                }
            }

            DependencyReport(
                projectName = projectPath.fileName?.toString() ?: projectPath.toString(),
                dependencyTree = directNodes
            )
        }.getOrNull()
    }

    private fun buildTuiUpdateApplier(projectPath: Path): ((List<UpdateSuggestion>) -> List<UpdateResult>)? {
        val projectType = runCatching { ProjectDetector().detect(projectPath) }.getOrNull() ?: return null
        val buildFile = resolveBuildFile(projectPath, projectType)
        if (!buildFile.exists()) return null

        val updater = updaterForProjectType(projectType)
        return { suggestions ->
            if (suggestions.isEmpty()) {
                emptyList()
            } else {
                runCatching { BuildFileBackup.ensureBackup(buildFile) }
                suggestions.map { suggestion ->
                    val directSuggestion = suggestion.copy(targetType = UpdateTargetType.DIRECT)
                    val result = runCatching { updater.applyUpdate(buildFile, directSuggestion) }
                    if (result.isSuccess) {
                        val applied = result.getOrDefault(false)
                        UpdateResult(
                            suggestion = directSuggestion,
                            applied = applied,
                            note = if (applied) "aplicada" else "sin coincidencia editable"
                        )
                    } else {
                        UpdateResult(
                            suggestion = directSuggestion,
                            applied = false,
                            note = "error: ${result.exceptionOrNull()?.message ?: "desconocido"}"
                        )
                    }
                }
            }
        }
    }

    private fun resolveBuildFile(projectPath: Path, projectType: ProjectType): File {
        val projectDir = projectPath.toFile()
        return when (projectType) {
            ProjectType.MAVEN -> File(projectDir, "pom.xml")
            ProjectType.GRADLE_GROOVY -> File(projectDir, "build.gradle")
            ProjectType.GRADLE_KOTLIN -> File(projectDir, "build.gradle.kts")
        }
    }

    private fun updaterForProjectType(projectType: ProjectType): BuildFileUpdater {
        return when (projectType) {
            ProjectType.MAVEN -> PomBuildFileUpdater()
            ProjectType.GRADLE_GROOVY -> GradleGroovyBuildFileUpdater()
            ProjectType.GRADLE_KOTLIN -> GradleKotlinBuildFileUpdater()
        }
    }

    private fun renderCliOutput(targetPath: Path, report: DependencyReport, capabilities: TerminalCapabilities) {
        if (output?.lowercase() == "json") {
            val generator = ReportGenerator()
            val outputPath = jsonOutputPathProvider(targetPath)
            val json = if (verbose) generator.toJsonVerbose(report) else generator.toJson(report)
            outputPath.writeText(json)
            echo("Reporte JSON exportado a: $outputPath")
            return
        }

        val renderer = ConsoleRenderer(
            noColor = noColor || capabilities.ansiLevel == com.github.ajalt.mordant.rendering.AnsiLevel.NONE,
            useAscii = ascii,
            treeMaxDepth = treeDepth,
            ansiLevel = capabilities.ansiLevel
        )

        if (verbose) {
            renderer.renderVerbose(report, showChains = showChains, detailedChains = chainDetail)
        } else {
            renderer.render(report, showChains = showChains, detailedChains = chainDetail)
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

class Analyze(
    analyzeExecutor: (AnalyzeExecutionRequest) -> DependencyReport = ::defaultAnalyzeExecutor,
    jsonOutputPathProvider: (Path) -> Path = ::defaultJsonOutputPathProvider,
    terminalCapabilitiesDetector: TerminalCapabilitiesDetector = TerminalCapabilitiesDetector(),
    tuiRunner: (TuiLaunchConfig, TerminalCapabilities) -> DependencyReport? = ::defaultTuiRunner
) : BaseAnalyzeCommand(
    commandName = "analyze",
    forceTui = false,
    analyzeExecutor = analyzeExecutor,
    jsonOutputPathProvider = jsonOutputPathProvider,
    terminalCapabilitiesDetector = terminalCapabilitiesDetector,
    tuiRunner = tuiRunner
) {
    override fun help(context: Context): String = "Analiza las dependencias de un proyecto"
}

class Tui(
    analyzeExecutor: (AnalyzeExecutionRequest) -> DependencyReport = ::defaultAnalyzeExecutor,
    jsonOutputPathProvider: (Path) -> Path = ::defaultJsonOutputPathProvider,
    terminalCapabilitiesDetector: TerminalCapabilitiesDetector = TerminalCapabilitiesDetector(),
    tuiRunner: (TuiLaunchConfig, TerminalCapabilities) -> DependencyReport? = ::defaultTuiRunner
) : BaseAnalyzeCommand(
    commandName = "tui",
    forceTui = true,
    analyzeExecutor = analyzeExecutor,
    jsonOutputPathProvider = jsonOutputPathProvider,
    terminalCapabilitiesDetector = terminalCapabilitiesDetector,
    tuiRunner = tuiRunner
) {
    override fun help(context: Context): String = "Abre la interfaz TUI interactiva"
}

fun main(args: Array<String>) = Depanalyzer()
    .subcommands(Analyze(), Tui(), Update())
    .main(args)
