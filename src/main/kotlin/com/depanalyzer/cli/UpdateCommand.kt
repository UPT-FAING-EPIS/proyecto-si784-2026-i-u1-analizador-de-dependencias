package com.depanalyzer.cli

import com.depanalyzer.core.ProjectAnalyzer
import com.depanalyzer.parser.ProjectType
import com.depanalyzer.repository.OssIndexClient
import com.depanalyzer.update.*
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import java.nio.file.Path

class Update(
    private val plannerFactory: (String?) -> UpdatePlanner = { token ->
        AnalyzerUpdatePlanner(
            analyzer = ProjectAnalyzer(ossIndexClient = OssIndexClient(token = token))
        )
    },
    private val updaterFactory: (ProjectType) -> BuildFileUpdater = { type ->
        when (type) {
            ProjectType.MAVEN -> PomBuildFileUpdater()
            ProjectType.GRADLE_GROOVY -> GradleGroovyBuildFileUpdater()
            ProjectType.GRADLE_KOTLIN -> GradleKotlinBuildFileUpdater()
        }
    },
    private val decisionProvider: (Terminal, UpdateSuggestion, Int, Int) -> UpdateDecision = ::defaultDecisionProvider
) : CliktCommand(name = "update") {
    override fun help(context: Context): String = "Actualiza dependencias con confirmación interactiva"

    private val path: Path? by argument(help = "Ruta al directorio del proyecto (default: directorio actual)")
        .path(mustExist = true, canBeFile = false)
        .optional()
    private val ossIndexToken: String? by option(
        "-t",
        "--oss-index-token",
        help = "Token de autenticación para OSS Index API"
    )
    private val dynamic: Boolean by option(
        "--dynamic",
        help = "Fuerza análisis dinámico (más preciso, más lento). Por defecto: análisis estático"
    ).flag(default = false)
    private val dryRun: Boolean by option(
        "--dry-run",
        help = "Muestra qué cambiaría sin modificar archivos"
    ).flag(default = false)
    private val onlySecurity: Boolean by option(
        "--only-security",
        help = "Solo sugiere actualizaciones que resuelven CVEs"
    ).flag(default = false)

    override fun run() {
        val terminal = if (System.getenv("NO_COLOR") != null) {
            Terminal(ansiLevel = AnsiLevel.NONE)
        } else {
            Terminal(ansiLevel = AnsiLevel.TRUECOLOR)
        }
        val targetPath = path ?: Path.of(".")
        val results = executeUpdate(
            targetPath = targetPath,
            terminal = terminal,
            dryRun = getDryRunFromCli(),
            onlySecurity = getOnlySecurityFromCli()
        )
        val appliedCount = results.count { it.applied }
        val omittedCount = results.size - appliedCount
        if (getDryRunFromCli()) {
            echo("Resumen final (dry-run): simuladas=$appliedCount, omitidas=$omittedCount")
        } else {
            echo("Resumen final: aplicadas=$appliedCount, omitidas=$omittedCount")
        }
    }

    internal fun executeUpdate(
        targetPath: Path,
        terminal: Terminal = Terminal(),
        dryRun: Boolean = getDryRunFromCli(),
        onlySecurity: Boolean = getOnlySecurityFromCli()
    ): List<UpdateResult> {
        val planner = plannerFactory(getTokenFromCliOrEnv())
        val plan = planner.plan(
            targetPath,
            UpdateAnalysisOptions(dynamic = getDynamicFromCli())
        )
        val updater = updaterFactory(plan.projectType)
        val orderedSuggestions = plan.suggestions.sortedWith(
            compareBy<UpdateSuggestion> { if (it.reason == UpdateReason.CVE) 0 else 1 }
                .thenBy { it.coordinate }
        )
        val scopedSuggestions = if (onlySecurity) {
            orderedSuggestions.filter { it.reason == UpdateReason.CVE }
        } else {
            orderedSuggestions
        }

        if (scopedSuggestions.isEmpty()) {
            echo("No se encontraron dependencias desactualizadas para actualizar.")
            return emptyList()
        }

        if (onlySecurity) {
            terminal.println("Filtro activo --only-security: se mostrarán solo sugerencias por CVE")
        }
        if (dryRun) {
            terminal.println("Modo --dry-run activo: no se realizarán cambios en archivos")
        }

        terminal.println(bold("Actualizaciones sugeridas para ${plan.buildFile.name}"))
        terminal.println(bold("Formato: dependencia | actual -> nueva | razón"))

        val results = mutableListOf<UpdateResult>()
        var applyAll = false
        var cancelled = false
        var backupCreated = false

        for ((index, suggestion) in scopedSuggestions.withIndex()) {
            if (cancelled) {
                results.add(UpdateResult(suggestion, applied = false, note = "cancelada"))
                continue
            }

            val decision = if (applyAll) {
                UpdateDecision.APPLY
            } else {
                decisionProvider(terminal, suggestion, index, scopedSuggestions.size)
            }

            when (decision) {
                UpdateDecision.SKIP -> {
                    results.add(UpdateResult(suggestion, applied = false, note = "omitida"))
                }

                UpdateDecision.CANCEL -> {
                    cancelled = true
                    results.add(UpdateResult(suggestion, applied = false, note = "cancelada"))
                }

                UpdateDecision.APPLY_ALL,
                UpdateDecision.APPLY -> {
                    if (decision == UpdateDecision.APPLY_ALL) {
                        applyAll = true
                    }

                    if (dryRun) {
                        results.add(UpdateResult(suggestion, applied = true, note = "dry-run: se aplicaría"))
                        continue
                    }

                    if (!backupCreated) {
                        val backup = BuildFileBackup.ensureBackup(plan.buildFile)
                        terminal.println("Backup creado: ${backup.name}")
                        backupCreated = true
                    }

                    val applied = updater.applyUpdate(plan.buildFile, suggestion)
                    val note = if (applied) "aplicada" else "sin coincidencia editable"
                    results.add(UpdateResult(suggestion, applied, note))
                }
            }
        }

        renderSummary(terminal, results, dryRun)
        return results
    }

    private fun getTokenFromCliOrEnv(): String? {
        val cliToken = runCatching { ossIndexToken }.getOrNull()
        return cliToken ?: System.getenv("OSS_INDEX_TOKEN")
    }

    private fun getDynamicFromCli(): Boolean {
        return runCatching { dynamic }.getOrDefault(false)
    }

    private fun getDryRunFromCli(): Boolean {
        return runCatching { dryRun }.getOrDefault(false)
    }

    private fun getOnlySecurityFromCli(): Boolean {
        return runCatching { onlySecurity }.getOrDefault(false)
    }

    private fun renderSummary(terminal: Terminal, results: List<UpdateResult>, dryRun: Boolean) {
        val applied = results.filter { it.applied }
        val omitted = results.filterNot { it.applied }

        terminal.println()
        terminal.println(bold("Resumen de actualizaciones"))
        val summaryTable = table {
            header { row("Estado", "Cantidad") }
            body {
                row(if (dryRun) "Simuladas" else "Aplicadas", applied.size.toString())
                row("Omitidas", omitted.size.toString())
            }
        }
        terminal.println(summaryTable)

        if (applied.isNotEmpty()) {
            terminal.println(bold(if (dryRun) "Cambios simulados" else "Cambios aplicados"))
            val appliedTable = table {
                header { row("Dependencia", "Cambio", "Razón", "Tipo", "Vía") }
                body {
                    applied.forEach { result ->
                        row(
                            result.suggestion.coordinate,
                            "${result.suggestion.currentVersion} -> ${result.suggestion.newVersion}",
                            result.suggestion.reason.label(),
                            result.suggestion.targetType.label(),
                            result.suggestion.viaDirectCoordinate ?: "-"
                        )
                    }
                }
            }
            terminal.println(appliedTable)
        }

        if (omitted.isNotEmpty()) {
            terminal.println(bold("Cambios omitidos"))
            val omittedTable = table {
                header { row("Dependencia", "Cambio", "Razón", "Tipo", "Vía", "Nota") }
                body {
                    omitted.forEach { result ->
                        row(
                            result.suggestion.coordinate,
                            "${result.suggestion.currentVersion} -> ${result.suggestion.newVersion}",
                            result.suggestion.reason.label(),
                            result.suggestion.targetType.label(),
                            result.suggestion.viaDirectCoordinate ?: "-",
                            result.note
                        )
                    }
                }
            }
            terminal.println(omittedTable)
        }
    }

    companion object {
        private fun defaultDecisionProvider(
            terminal: Terminal,
            suggestion: UpdateSuggestion,
            index: Int,
            total: Int
        ): UpdateDecision {
            val label = formatSelectionLabel(suggestion)
            terminal.println()
            terminal.println(
                bold(
                    "[${
                        index + 1
                    }/$total] $label"
                )
            )
            terminal.println("¿Aplicar actualización? [s]í / [n]o / [a]plicar todo / [c]ancelar")

            while (true) {
                val input = readlnOrNull()?.trim()?.lowercase().orEmpty()

                when (input) {
                    "s", "si", "sí", "y", "yes" -> return UpdateDecision.APPLY
                    "n", "no" -> return UpdateDecision.SKIP
                    "a", "all" -> return UpdateDecision.APPLY_ALL
                    "c", "cancel", "cancelar", "q", "quit" -> return UpdateDecision.CANCEL
                }

                terminal.println(red("Opción inválida. Usa s, n, a o c."))
            }
        }

        private fun formatSelectionLabel(suggestion: UpdateSuggestion): String {
            val base =
                "${suggestion.coordinate} | ${suggestion.currentVersion} -> ${suggestion.newVersion} | ${suggestion.reason.label()} | ${suggestion.targetType.label()}"
            return suggestion.viaDirectCoordinate?.let { "$base | via $it" } ?: base
        }
    }
}

enum class UpdateDecision {
    APPLY,
    SKIP,
    APPLY_ALL,
    CANCEL
}
