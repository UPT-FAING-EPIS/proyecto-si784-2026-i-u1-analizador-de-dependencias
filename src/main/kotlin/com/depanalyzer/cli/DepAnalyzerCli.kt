package com.depanalyzer.cli

import com.depanalyzer.BuildInfo
import com.depanalyzer.core.ProjectAnalyzer
import com.depanalyzer.parser.*
import com.depanalyzer.parser.npm.NpmPackageParser
import com.depanalyzer.parser.python.PyprojectPoetryParser
import com.depanalyzer.parser.python.RequirementsParser
import com.depanalyzer.report.*
import com.depanalyzer.repository.NvdClient
import com.depanalyzer.repository.OssIndexClient
import com.depanalyzer.telemetry.TelemetryClient
import com.depanalyzer.telemetry.TelemetryConfig
import com.depanalyzer.telemetry.TelemetryEvent
import com.depanalyzer.tui.AnalyzeTuiApp
import com.depanalyzer.tui.TerminalCapabilities
import com.depanalyzer.tui.TerminalCapabilitiesDetector
import com.depanalyzer.update.*
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeText

class Depanalyzer : CliktCommand() {
    init {
        versionOption(BuildInfo.VERSION)
    }

    private val noTelemetry: Boolean by option(
        "--no-telemetry",
        help = "Disable anonymous usage telemetry"
    ).flag(default = false)

    override fun help(context: Context): String = "Analizador de Dependencias multi-ecosistema"
    override fun run() {
        if (noTelemetry) {
            TelemetryConfig.disable()
        }

        TelemetryClient.send(TelemetryEvent(eventType = "app_start"))
    }
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
    val vulnerabilitySourceMode: VulnerabilitySourceMode,
    val showCommandOutput: Boolean = false,
    val ossIndexToken: String?,
    val nvdApiKey: String?,
    val onPartialReport: ((DependencyReport) -> Unit)? = null
)

enum class VulnerabilitySourceMode {
    AUTO,
    OSS_ONLY,
    NVD_ONLY
}

data class TuiLaunchConfig(
    val initialStatus: String,
    val progressHint: String? = null,
    val scanProvider: ((DependencyReport) -> Unit) -> DependencyReport,
    val initialReport: DependencyReport? = null,
    val applyUpdates: ((List<UpdateSuggestion>) -> List<UpdateResult>)? = null
)

internal fun resolveVulnerabilitySourceModeFromFlags(
    forceOss: Boolean,
    forceNvd: Boolean
): VulnerabilitySourceMode? {
    if (forceOss && forceNvd) {
        return null
    }

    return when {
        forceOss -> VulnerabilitySourceMode.OSS_ONLY
        forceNvd -> VulnerabilitySourceMode.NVD_ONLY
        else -> VulnerabilitySourceMode.AUTO
    }
}

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
        vulnerabilitySourceMode = request.vulnerabilitySourceMode,
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
    private val telemetryCommandName: String = commandName
    private val path: Path? by argument(help = "Ruta al directorio del proyecto (default: directorio actual)")
        .path(mustExist = true, canBeFile = false)
        .optional()
    private val output: String? by option("-o", "--output", help = "Formato de salida (json a archivo)")
    private val outputFile: String? by option(
        "--output-file",
        help = "Ruta del reporte JSON; use '-' para stdout"
    )
    private val quiet: Boolean by option(
        "--quiet",
        help = "Suprime progreso y mensajes informativos"
    ).flag(default = false)
    private val noColor: Boolean by option("--no-color", help = "Desactiva el color en la consola").flag()
    private val tui: Boolean by option("--tui", help = "Activa la interfaz TUI interactiva").flag()
    private val ossToken: String? by option("--oss-token", help = "Token de autenticación para OSS Index API")
    private val nvdToken: String? by option("--nvd-token", help = "API key para NVD API")
    private val oss: Boolean by option("--oss", help = "Fuerza uso de OSS Index (sin fallback)").flag()
    private val nvd: Boolean by option("--nvd", help = "Fuerza uso de NVD (sin fallback)").flag()
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
    private val commandOutput: Boolean by option(
        "--command-output",
        help = "Muestra salida detallada de comandos Gradle/Maven durante el analisis dinamico"
    ).flag()
    private val progressJson: Boolean by option(
        "--progress-json",
        help = "Emite eventos NDJSON de progreso por stderr"
    ).flag(default = false)
    private val failOnCritical: Boolean by option(
        "--fail-on-critical",
        help = "Retorna exit code 1 si se detectan CVEs críticos"
    ).flag()

    override fun run() {
        val targetPath = path ?: Path.of(".")
        val tuiRequested = forceTui || tui
        val startTime = System.currentTimeMillis()
        val token = getOssTokenFromCliOrEnv()
        val nvdApiKey = getNvdTokenFromCliOrEnv()
        val sourceMode = resolveVulnerabilitySourceMode() ?: return

        trackCommandAndFlagFeatures()

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

        ProgressTracker.setMuted(interactiveTui || quiet || outputFile == "-")
        ProgressTracker.setEventListener(
            if (progressJson) {
                { event -> System.err.println(ProgressEventJsonWriter.write(event)) }
            } else {
                null
            }
        )

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

            if (!interactiveTui && sourceMode == VulnerabilitySourceMode.NVD_ONLY && nvdApiKey == null) {
                echo("Advertencia: --nvd sin token/API key puede estar muy limitado (~50 req/hora)", err = true)
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
                    vulnerabilitySourceMode = sourceMode,
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
                vulnerabilitySourceMode = sourceMode,
                showCommandOutput = commandOutput,
                ossIndexToken = token,
                nvdApiKey = nvdApiKey
            )

            val report = try {
                analyzeExecutor(standardRequest)
            } catch (e: Exception) {
                sendErrorEvent(e)
                echo("Error durante el análisis: ${e.message}", err = true)
                throw ProgramResult(2)
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
        } catch (e: ProgramResult) {
            throw e
        } catch (e: Exception) {
            sendErrorEvent(e)
            throw e
        } finally {
            val durationMs = System.currentTimeMillis() - startTime
            TelemetryClient.send(
                TelemetryEvent(
                    eventType = "scan_run",
                    durationMs = durationMs
                )
            )
            TelemetryClient.flush(timeoutMs = 2500L)
            ProgressTracker.setMuted(false)
            ProgressTracker.setListener(null)
            ProgressTracker.setEventListener(null)
        }
    }

    private fun trackCommandAndFlagFeatures() {
        TelemetryClient.send(
            TelemetryEvent(
                eventType = "feature_used",
                feature = "${telemetryCommandName}_command"
            )
        )

        if (tui) trackFeature("flag_tui")
        if (output != null) trackFeature("flag_output_${output!!.lowercase()}")
        if (outputFile != null) trackFeature("flag_output_file")
        if (quiet) trackFeature("flag_quiet")
        if (verbose) trackFeature("flag_verbose")
        if (showChains) trackFeature("flag_show_chains")
        if (chainDetail) trackFeature("flag_chain_detail")
        if (offline) trackFeature("flag_offline")
        if (dynamic) trackFeature("flag_dynamic")
        if (disableMaven) trackFeature("flag_disable_maven")
        if (disableGradle) trackFeature("flag_disable_gradle")
        if (ascii) trackFeature("flag_ascii")
        if (treeDepth != null) trackFeature("flag_tree_depth")
        if (treeExpand != null) trackFeature("flag_tree_expand")
        if (timeout != null) trackFeature("flag_timeout")
        if (oss) trackFeature("flag_oss")
        if (nvd) trackFeature("flag_nvd")
        if (commandOutput) trackFeature("flag_command_output")
        if (progressJson) trackFeature("flag_progress_json")
        if (failOnCritical) trackFeature("flag_fail_on_critical")
    }

    private fun trackFeature(feature: String) {
        TelemetryClient.send(TelemetryEvent(eventType = "feature_used", feature = feature))
    }

    private fun sendErrorEvent(error: Throwable) {
        TelemetryClient.send(
            TelemetryEvent(
                eventType = "error",
                errorType = error.javaClass.simpleName,
                errorMessage = error.message?.take(200)
            )
        )
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

                ProjectType.NPM -> {
                    val packageFile = File(projectDir, "package.json")
                    NpmPackageParser()
                        .parse(packageFile)
                        .distinctBy { "${it.groupId}:${it.artifactId}" }
                        .map { dep ->
                            DependencyTreeNode(
                                groupId = dep.groupId,
                                artifactId = dep.artifactId,
                                currentVersion = dep.version ?: "unknown",
                                isDirectDependency = true,
                                scope = dep.scope,
                                ecosystem = dep.ecosystem
                            )
                        }
                }

                ProjectType.PYTHON_POETRY -> {
                    val pyprojectFile = File(projectDir, "pyproject.toml")
                    if (!pyprojectFile.exists()) {
                        emptyList()
                    } else {
                        PyprojectPoetryParser()
                            .parse(pyprojectFile)
                            .distinctBy { "${it.groupId}:${it.artifactId}" }
                            .map { dep ->
                                DependencyTreeNode(
                                    groupId = dep.groupId,
                                    artifactId = dep.artifactId,
                                    currentVersion = dep.version ?: "unknown",
                                    isDirectDependency = true,
                                    scope = dep.scope,
                                    ecosystem = dep.ecosystem
                                )
                            }
                    }
                }

                ProjectType.PYTHON_REQUIREMENTS -> {
                    val requirementsFile = File(projectDir, "requirements.txt")
                    if (!requirementsFile.exists()) {
                        emptyList()
                    } else {
                        RequirementsParser()
                            .parse(requirementsFile)
                            .distinctBy { "${it.groupId}:${it.artifactId}" }
                            .map { dep ->
                                DependencyTreeNode(
                                    groupId = dep.groupId,
                                    artifactId = dep.artifactId,
                                    currentVersion = dep.version ?: "unknown",
                                    isDirectDependency = true,
                                    scope = dep.scope,
                                    ecosystem = dep.ecosystem
                                )
                            }
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
            ProjectType.NPM -> File(projectDir, "package.json")
            ProjectType.PYTHON_POETRY -> File(projectDir, "pyproject.toml")
            ProjectType.PYTHON_REQUIREMENTS -> File(projectDir, "requirements.txt")
        }
    }

    private fun updaterForProjectType(projectType: ProjectType): BuildFileUpdater {
        return when (projectType) {
            ProjectType.MAVEN -> PomBuildFileUpdater()
            ProjectType.GRADLE_GROOVY -> GradleGroovyBuildFileUpdater()
            ProjectType.GRADLE_KOTLIN -> GradleKotlinBuildFileUpdater()
            ProjectType.NPM -> NpmPackageJsonBuildFileUpdater()
            ProjectType.PYTHON_POETRY -> PyprojectBuildFileUpdater()
            ProjectType.PYTHON_REQUIREMENTS -> RequirementsBuildFileUpdater()
        }
    }

    private fun renderCliOutput(targetPath: Path, report: DependencyReport, capabilities: TerminalCapabilities) {
        if (output?.lowercase() == "json") {
            val generator = ReportGenerator()
            val json = if (verbose) generator.toJsonVerbose(report) else generator.toJson(report)
            if (outputFile == "-") {
                echo(json)
                return
            }

            val outputPath = outputFile
                ?.let(Path::of)
                ?: jsonOutputPathProvider(targetPath)
            outputPath.writeText(json)
            if (!quiet) {
                echo("Reporte JSON exportado a: $outputPath")
            }
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

    private fun getOssTokenFromCliOrEnv(): String? {
        return (ossToken ?: System.getenv("OSS_INDEX_TOKEN"))
            ?.trim()
            ?.takeUnless { it.isBlank() }
    }

    private fun getNvdTokenFromCliOrEnv(): String? {
        return nvdToken ?: System.getenv("NVD_API_KEY")
    }

    private fun resolveVulnerabilitySourceMode(): VulnerabilitySourceMode? {
        val resolved = resolveVulnerabilitySourceModeFromFlags(forceOss = oss, forceNvd = nvd)
        if (resolved == null) {
            echo("Error: --oss y --nvd son mutuamente excluyentes", err = true)
            return null
        }

        return resolved
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
    .subcommands(Analyze(), Tui(), Update(), Capabilities())
    .main(args)
