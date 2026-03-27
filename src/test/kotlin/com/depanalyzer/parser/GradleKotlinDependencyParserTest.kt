package com.depanalyzer.parser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GradleKotlinDependencyParserTest {

    @Test
    fun `parses simple kotlin dsl dependencies`() {
        val parser = GradleKotlinDependencyParser()
        val deps = parser.parse(resourceFile("gradles/kotlin-simple/build.gradle.kts"))

        assertEquals(2, deps.size)
        
        val slf4j = deps.find { it.artifactId == "slf4j-api" }
        assertNotNull(slf4j)
        assertEquals("org.slf4j", slf4j.groupId)
        assertEquals("2.0.13", slf4j.version)
        assertEquals("implementation", slf4j.configuration)

        val junit = deps.find { it.artifactId == "junit-jupiter-api" }
        assertNotNull(junit)
        assertEquals("org.junit.jupiter", junit.groupId)
        assertEquals("5.11.3", junit.version)
        assertEquals("testImplementation", junit.configuration)
    }

    @Test
    fun `parses kotlin dsl with version catalog`() {
        val tomlFile = resourceFile("gradle/libs.versions.toml")
        val catalog = VersionCatalogParser().parse(tomlFile)
        val parser = GradleKotlinDependencyParser(catalog)
        
        val deps = parser.parse(resourceFile("gradles/kotlin-catalog/build.gradle.kts"))

        assertEquals(4, deps.size)

        val kotlinStdlib = deps.find { it.artifactId == "kotlin-stdlib" }
        assertNotNull(kotlinStdlib)
        assertEquals("org.jetbrains.kotlin", kotlinStdlib.groupId)
        assertEquals("2.0.21", kotlinStdlib.version)

        val jackson = deps.find { it.artifactId == "jackson-databind" }
        assertNotNull(jackson)
        assertEquals("com.fasterxml.jackson.core", jackson.groupId)
        assertEquals("2.18.1", jackson.version)

        val junit = deps.find { it.artifactId == "junit-jupiter" }
        assertNotNull(junit)
        assertEquals("org.junit.jupiter", junit.groupId)
        assertEquals("5.11.3", junit.version)

        val guava = deps.find { it.artifactId == "guava" }
        assertNotNull(guava)
        assertEquals("33.3.1-jre", guava.version)
    }

    private fun resourceFile(path: String): File {
        val url = this::class.java.classLoader.getResource(path)
        requireNotNull(url) { "Missing test resource: $path" }
        return File(url.toURI())
    }
}
