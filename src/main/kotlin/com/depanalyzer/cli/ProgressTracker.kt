package com.depanalyzer.cli

object ProgressTracker {

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
