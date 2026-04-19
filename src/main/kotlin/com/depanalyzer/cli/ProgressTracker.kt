package com.depanalyzer.cli

object ProgressTracker {

    private var progressSteps: List<String> = emptyList()
    private var currentStep: Int = 0

    fun startProgress(steps: List<String>) {
        progressSteps = steps
        currentStep = 0
        renderProgress()
    }

    fun advanceProgress(stepName: String) {
        if (progressSteps.isEmpty()) return

        val index = progressSteps.indexOf(stepName)
        currentStep = when {
            index >= 0 -> index + 1
            currentStep < progressSteps.size -> currentStep + 1
            else -> progressSteps.size
        }

        renderProgress(stepName)
    }

    fun completeProgress() {
        if (progressSteps.isEmpty()) return
        currentStep = progressSteps.size
        renderProgress("Completado")
        progressSteps = emptyList()
        currentStep = 0
    }

    private fun renderProgress(currentLabel: String? = null) {
        val total = progressSteps.size
        if (total == 0) return

        val width = 20
        val filled = (currentStep * width) / total
        val bar = "#".repeat(filled) + "-".repeat(width - filled)
        val label = currentLabel ?: "Inicializando"

        System.err.println("[$bar] $currentStep/$total - $label")
    }

    fun formatDuration(milliseconds: Long): String {
        return when {
            milliseconds < 1000 -> "${milliseconds}ms"
            else -> {
                val seconds = milliseconds / 1000.0
                "%.1fs".format(seconds)
            }
        }
    }

    fun logStep(message: String) {
        System.err.println(message)
    }

    fun logSuccess(message: String, elapsedMs: Long? = null) {
        val msg = if (elapsedMs != null) {
            "$message (${formatDuration(elapsedMs)})"
        } else {
            message
        }
        System.err.println("✓ $msg")
    }

    fun logWarning(message: String) {
        System.err.println("⚠️  $message")
    }

    fun logSearching(message: String) {
        System.err.println("🔍 $message")
    }

    fun logProcessing(message: String) {
        System.err.println("📦 $message")
    }

    fun logDetected(message: String) {
        System.err.println("📁 $message")
    }

    fun logBuilding(message: String) {
        System.err.println("🌳 $message")
    }

    fun logSecurity(message: String) {
        System.err.println("🛡️  $message")
    }

    fun logStart(message: String) {
        System.err.println("🚀 $message")
    }

    fun logSeparator() {
        System.err.println()
    }
}
