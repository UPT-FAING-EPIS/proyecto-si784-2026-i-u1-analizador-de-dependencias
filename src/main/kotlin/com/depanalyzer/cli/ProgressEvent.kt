package com.depanalyzer.cli

import java.time.Instant

data class ProgressEvent(
    val type: String,
    val message: String,
    val phase: String? = null,
    val current: Int? = null,
    val total: Int? = null,
    val timestamp: Instant = Instant.now()
)

internal object ProgressEventJsonWriter {
    fun write(event: ProgressEvent): String {
        val fields = mutableListOf(
            "\"stream\":\"depanalyzer-progress\"",
            "\"type\":\"${escape(event.type)}\"",
            "\"message\":\"${escape(event.message)}\"",
            "\"timestamp\":\"${event.timestamp}\""
        )
        event.phase?.let { fields += "\"phase\":\"${escape(it)}\"" }
        event.current?.let { fields += "\"current\":$it" }
        event.total?.let { fields += "\"total\":$it" }
        return "{${fields.joinToString(",")}}"
    }

    private fun escape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u${char.code.toString(16).padStart(4, '0')}") else append(char)
            }
        }
    }
}
