package com.depanalyzer.parser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GradleGroovyDependencyParserTest {
    private val parser = GradleGroovyDependencyParser()

    @Test
    fun `parses simple dependencies string notation`() {
        val deps = parser.parse(resourceGradle("gradles/simple/build.gradle"))

        assertEquals(3, deps.size)
        
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
    fun `parses map notation dependencies`() {
        val deps = parser.parse(resourceGradle("gradles/map-notation/build.gradle"))

        assertEquals(3, deps.size)

        val spring = deps.find { it.artifactId == "spring-core" }
        assertNotNull(spring)
        assertEquals("org.springframework", spring.groupId)
        assertEquals("6.1.14", spring.version)
        assertEquals("implementation", spring.configuration)

        val lombok = deps.find { it.artifactId == "lombok" }
        assertNotNull(lombok)
        assertEquals("org.projectlombok", lombok.groupId)
        assertEquals("1.18.34", lombok.version)
        assertEquals("compileOnly", lombok.configuration)
    }

    @Test
    fun `resolves ext variables`() {
        val deps = parser.parse(resourceGradle("gradles/with-ext/build.gradle"))

        assertEquals(3, deps.size)

        val slf4j = deps.find { it.artifactId == "slf4j-api" }
        assertEquals("2.0.13", slf4j?.version)

        val junit = deps.find { it.artifactId == "junit-jupiter-api" }
        assertEquals("5.11.3", junit?.version)

        val postgres = deps.find { it.artifactId == "postgresql" }
        assertEquals("42.7.4", postgres?.version)
    }

    @Test
    fun `handles mixed content and ignores project dependencies`() {
        val deps = parser.parse(resourceGradle("gradles/mixed/build.gradle"))

        // Should ignore project(':core') and comments
        assertEquals(3, deps.size)

        assertTrue(deps.any { it.artifactId == "guava" })
        assertTrue(deps.any { it.artifactId == "mockito-core" })
        assertTrue(deps.any { it.artifactId == "commons-lang3" })
        
        // Ensure project dependency is not there
        assertTrue(deps.none { it.artifactId == "core" })
    }

    @Test
    fun `handles interpolated versions and def variables`() {
        val deps = parser.parse(resourceGradle("gradles/interpolated/build.gradle"))

        val springCore = deps.find { it.artifactId == "spring-core" }
        assertEquals("6.1.14", springCore?.version)

        val springContext = deps.find { it.artifactId == "spring-context" }
        assertEquals("6.1.14", springContext?.version)

        val jackson = deps.find { it.artifactId == "jackson-databind" }
        assertEquals("2.18.1", jackson?.version)
    }

    @Test
    fun `extracts repositories from build gradle`() {
        val repos = parser.repositories(resourceGradle("gradles/with-repositories/build.gradle"))

        assertEquals(4, repos.size)
        assertTrue(repos.any { it.url == "https://repo1.maven.org/maven2" })
        assertTrue(repos.any { it.url == "https://maven.google.com" })
        assertTrue(repos.any { it.url == "https://jitpack.io" })
        assertTrue(repos.any { it.url.contains("nexus.example.com") })
    }

    private fun resourceGradle(path: String): File {
        val url = this::class.java.classLoader.getResource(path)
        requireNotNull(url) { "Missing test resource: $path" }
        return File(url.toURI())
    }
}
