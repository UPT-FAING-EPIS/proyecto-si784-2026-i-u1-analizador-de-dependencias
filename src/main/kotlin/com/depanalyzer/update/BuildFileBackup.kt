package com.depanalyzer.update

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object BuildFileBackup {
    fun ensureBackup(buildFile: File): File {
        val backupFile = File(buildFile.parentFile, "${buildFile.name}.bak")
        if (!backupFile.exists()) {
            Files.copy(buildFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return backupFile
    }
}
