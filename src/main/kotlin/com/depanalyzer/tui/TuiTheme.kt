package com.depanalyzer.tui

import com.depanalyzer.report.VulnerabilitySeverity

private const val RESET = "\u001b[0m"
private const val BOLD = "\u001b[1m"
private const val DIM = "\u001b[2m"

data class TuiTheme(
    val enabled: Boolean = true
) {
    private fun wrap(code: String, text: String): String {
        if (!enabled) return text
        return "$code$text$RESET"
    }

    fun chrome(text: String): String = wrap("\u001b[38;5;141m$BOLD", text)
    fun title(text: String): String = wrap("\u001b[38;5;213m$BOLD", text)
    fun muted(text: String): String = wrap("\u001b[38;5;246m$DIM", text)
    fun section(text: String): String = wrap("\u001b[38;5;111m$BOLD", text)
    fun selectedRow(text: String): String = wrap("\u001b[48;5;236m\u001b[38;5;225m$BOLD", text)
    fun selectedVersion(text: String): String = wrap("\u001b[38;5;218m$BOLD", text)
    fun targetVersion(text: String): String = wrap("\u001b[38;5;120m$BOLD", text)
    fun tabActive(text: String): String = wrap("\u001b[38;5;213m$BOLD", text)
    fun tabInactive(text: String): String = wrap("\u001b[38;5;246m", text)
    fun tabDisabled(text: String): String = wrap("\u001b[38;5;240m$DIM", text)
    fun chipActive(text: String): String = wrap("\u001b[48;5;237m\u001b[38;5;220m$BOLD", text)
    fun chipInactive(text: String): String = wrap("\u001b[38;5;246m", text)
    fun scanOk(text: String): String = wrap("\u001b[38;5;120m$BOLD", text)
    fun scanWarn(text: String): String = wrap("\u001b[38;5;220m$BOLD", text)
    fun scanDanger(text: String): String = wrap("\u001b[38;5;204m$BOLD", text)

    fun severityBadge(severity: VulnerabilitySeverity, text: String): String {
        val code = when (severity) {
            VulnerabilitySeverity.CRITICAL -> "\u001b[48;5;52m\u001b[38;5;224m$BOLD"
            VulnerabilitySeverity.HIGH -> "\u001b[48;5;88m\u001b[38;5;224m$BOLD"
            VulnerabilitySeverity.MEDIUM -> "\u001b[48;5;94m\u001b[38;5;230m$BOLD"
            VulnerabilitySeverity.LOW -> "\u001b[48;5;22m\u001b[38;5;230m$BOLD"
            VulnerabilitySeverity.UNKNOWN -> "\u001b[48;5;238m\u001b[38;5;254m$BOLD"
        }
        return wrap(code, text)
    }

    fun statusBadge(entry: TuiDependencyEntry, width: Int, isPending: Boolean = false): String {
        val text = when {
            isPending -> "Pend."
            entry.vulnerabilityCount > 0 && (entry.latestVersion != null || entry.outdatedCount > 0) -> "CVE tr."
            entry.vulnerabilityCount > 0 -> "CVE"
            entry.latestVersion != null || entry.outdatedCount > 0 -> "Desact."
            else -> "OK"
        }.padEnd(width)

        return when {
            isPending -> wrap("\u001b[38;5;141m$BOLD", text)
            entry.vulnerabilityCount > 0 -> wrap("\u001b[38;5;205m$BOLD", text)
            entry.latestVersion != null || entry.outdatedCount > 0 -> wrap("\u001b[38;5;220m$BOLD", text)
            else -> wrap("\u001b[38;5;120m$BOLD", text)
        }
    }
}
