package com.depanalyzer.telemetry

import java.util.*

object TelemetryConfig {
    private const val DEFAULT_URL = "https://pulsoback.anvian.net/"
    //private const val DEFAULT_URL = "http://localhost:8080/"

    private var baseUrlOverride: String? = null

    val baseUrl: String
        get() = (baseUrlOverride ?: System.getenv("PULSO_URL") ?: DEFAULT_URL).trimEnd('/')

    val ingestUrl: String
        get() = "$baseUrl/ingest"

    var enabled: Boolean = System.getenv("PULSO_ENABLED")?.lowercase() != "false"
        private set

    val sessionId: String = UUID.randomUUID().toString()

    fun disable() {
        enabled = false
    }

    internal fun resetForTests() {
        enabled = System.getenv("PULSO_ENABLED")?.lowercase() != "false"
        baseUrlOverride = null
    }

    internal fun setBaseUrlForTests(url: String?) {
        baseUrlOverride = url
    }
}
