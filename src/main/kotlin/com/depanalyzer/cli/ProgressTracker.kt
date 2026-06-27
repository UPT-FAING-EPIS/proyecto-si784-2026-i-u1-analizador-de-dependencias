package com.depanalyzer.cli

object ProgressTracker {

    private var progressSteps: List<String> = emptyList()
    private var currentStep: Int = 0
    private var muted: Boolean = false

    @Volatile
    private var listener: ((String) -> Unit)? = null

    @Volatile
    private var eventListener: ((ProgressEvent) -> Unit)? = null

    fun setMuted(value: Boolean) {
        muted = value
    }

    fun setListener(value: ((String) -> Unit)?) {
        listener = value
    }

    fun setEventListener(value: ((ProgressEvent) -> Unit)?) {
        eventListener = value
    }

    private fun emit(message: String) {
        listener?.invoke(message)
    }

    fun startProgress(steps: List<String>) {
        progressSteps = steps
        currentStep = 0
        emit("Inicializando")
        emitEvent(
            ProgressEvent(
                type = "started",
                message = "Inicializando analisis",
                phase = "Inicializando",
                current = 0,
                total = progressSteps.size
            )
        )
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

        emit(stepName)
        emitEvent(
            ProgressEvent(
                type = "phase",
                message = stepName,
                phase = stepName,
                current = currentStep,
                total = progressSteps.size
            )
        )
        renderProgress(stepName)
    }

    fun completeProgress() {
        if (progressSteps.isEmpty()) return
        currentStep = progressSteps.size
        emit("Completado")
        emitEvent(
            ProgressEvent(
                type = "completed",
                message = "Analisis completado",
                phase = "Completado",
                current = currentStep,
                total = progressSteps.size
            )
        )
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

        emit("$label ($currentStep/$total)")
        if (!muted) {
            System.err.println("[$bar] $currentStep/$total - $label")
        }
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
        emit(message)
        emitEvent(ProgressEvent(type = "command", message = message))
        if (!muted) {
            System.err.println(message)
        }
    }

    fun logSuccess(message: String, elapsedMs: Long? = null) {
        val msg = if (elapsedMs != null) {
            "$message (${formatDuration(elapsedMs)})"
        } else {
            message
        }
        emit(msg)
        emitEvent(ProgressEvent(type = "success", message = msg))
        if (!muted) {
            System.err.println("✓ $msg")
        }
    }

    fun logWarning(message: String) {
        emit(message)
        emitEvent(ProgressEvent(type = "warning", message = message))
        if (!muted) {
            System.err.println("⚠️  $message")
        }
    }

    fun logSearching(message: String) {
        emit(message)
        emitEvent(ProgressEvent(type = "info", message = message))
        if (!muted) {
            System.err.println("🔍 $message")
        }
    }

    fun logProcessing(message: String) {
        emit(message)
        emitEvent(ProgressEvent(type = "info", message = message))
        if (!muted) {
            System.err.println("📦 $message")
        }
    }

    fun logDetected(message: String) {
        emit(message)
        emitEvent(ProgressEvent(type = "info", message = message))
        if (!muted) {
            System.err.println("📁 $message")
        }
    }

    fun logBuilding(message: String) {
        emit(message)
        emitEvent(ProgressEvent(type = "info", message = message))
        if (!muted) {
            System.err.println("🌳 $message")
        }
    }

    fun logSecurity(message: String) {
        emit(message)
        emitEvent(ProgressEvent(type = "info", message = message))
        if (!muted) {
            System.err.println("🛡️  $message")
        }
    }

    fun logStart(message: String) {
        emit(message)
        emitEvent(ProgressEvent(type = "info", message = message))
        if (!muted) {
            System.err.println("🚀 $message")
        }
    }

    fun logSeparator() {
        if (!muted) {
            System.err.println()
        }
    }

    private fun emitEvent(event: ProgressEvent) {
        eventListener?.invoke(event)
    }
}
