package com.depanalyzer.parser

import java.io.File
import java.nio.file.Path

class ProjectDetector {
    fun detect(directory: Path): ProjectType {
        val dirFile = directory.toFile()
        if (!dirFile.exists() || !dirFile.isDirectory) {
            throw IllegalArgumentException("The path provided is not a valid directory: $directory")
        }

        // Priority check for project files
        val gradleKotlinFiles = listOf("build.gradle.kts", "settings.gradle.kts")
        if (gradleKotlinFiles.any { File(dirFile, it).exists() }) {
            return ProjectType.GRADLE_KOTLIN
        }

        val gradleGroovyFiles = listOf("build.gradle", "settings.gradle")
        if (gradleGroovyFiles.any { File(dirFile, it).exists() }) {
            return ProjectType.GRADLE_GROOVY
        }

        if (File(dirFile, "pom.xml").exists()) {
            return ProjectType.MAVEN
        }

        throw IllegalStateException("No known build files (pom.xml, build.gradle, build.gradle.kts) found in $directory")
    }
}
