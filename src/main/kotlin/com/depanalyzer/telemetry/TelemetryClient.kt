package com.depanalyzer.telemetry

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

object TelemetryClient {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val mapper = JsonMapper.builder().build()

    private val pendingThreads = ConcurrentLinkedQueue<Thread>()

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    fun send(event: TelemetryEvent) {
        if (!TelemetryConfig.enabled) return

        val worker = Thread {
            try {
                val payload = linkedMapOf<String, Any?>(
                    "appId" to event.appId,
                    "appVersion" to event.appVersion,
                    "os" to event.os,
                    "eventType" to event.eventType,
                    "sessionId" to event.sessionId,
                    "arch" to event.arch,
                    "feature" to event.feature,
                    "durationMs" to event.durationMs,
                    "errorType" to event.errorType,
                    "errorMessage" to event.errorMessage
                ).filterValues { it != null }

                val body = mapper.writeValueAsString(payload).toRequestBody(json)
                val request = Request.Builder()
                    .url(TelemetryConfig.ingestUrl)
                    .post(body)
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        System.err.println("[telemetry] POST /ingest returned ${response.code} - skipping")
                    }
                }
            } catch (e: Exception) {
                System.err.println("[telemetry] send failed silently: ${e.message}")
            } finally {
                pendingThreads.remove(Thread.currentThread())
            }
        }.also {
            it.isDaemon = true
            pendingThreads.add(it)
        }
        worker.start()
    }

    fun flush(timeoutMs: Long = 1500L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val active = pendingThreads.toList().filter { it.isAlive }
            if (active.isEmpty()) {
                return
            }

            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) {
                return
            }

            active.forEach { thread ->
                if (!thread.isAlive) return@forEach
                val perThreadWait = minOf(remaining, 200L)
                runCatching { thread.join(perThreadWait) }
            }
        }
    }
}
