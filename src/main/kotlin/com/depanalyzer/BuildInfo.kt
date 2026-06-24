package com.depanalyzer

object BuildInfo {
    val VERSION: String by lazy {
        BuildInfo::class.java.getResourceAsStream("/version.properties")
            ?.bufferedReader()
            ?.useLines { lines ->
                lines.firstOrNull { it.startsWith("version=") }
                    ?.substringAfter("version=")
                    ?.trim()
            }
            ?.takeIf { it.isNotEmpty() }
            ?: "development"
    }
}
