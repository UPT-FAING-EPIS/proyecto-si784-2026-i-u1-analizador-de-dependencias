package com.depanalyzer.parser.maven

object MavenDetector {

    fun isAvailable(): Boolean = try {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val command = if (isWindows) "mvn.cmd" else "mvn"

        val process = ProcessBuilder(command, "--version")
            .redirectErrorStream(true)
            .start()

        val completed = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        
        if (!completed) {
            process.destroyForcibly()
            false
        } else {
            process.exitValue() == 0
        }
    } catch (_: Exception) {

        false
    }

    fun getVersion(): String? = try {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val command = if (isWindows) "mvn.cmd" else "mvn"

        val process = ProcessBuilder(command, "--version")
            .redirectErrorStream(true)
            .start()

        val completed = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)

        if (!completed) {
            process.destroyForcibly()
            return null
        }

        if (process.exitValue() != 0) {
            return null
        }

        process.inputStream.bufferedReader().use { reader ->
            reader.readLines().firstOrNull()?.trim()
        }
    } catch (_: Exception) {
        null
    }
}
