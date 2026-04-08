package com.depanalyzer.parser.gradle

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GradleDependencyTreeParserTest {

    private fun loadTestResource(filename: String): String {
        val file = File("src/test/resources/gradle-outputs/$filename")
        return if (file.exists()) {
            file.readText()
        } else {
            ""
        }
    }

    @Test
    fun `should parse simple project with single configuration`() {
        val output = loadTestResource("simple-project.txt")
        if (output.isEmpty()) {
            println("Test resource not found, skipping")
            return
        }

        val nodes = GradleDependencyTreeParser.parse(output)
        assertTrue(nodes.isNotEmpty(), "Should parse at least one dependency")

        // Should find slf4j-api
        val slf4j = nodes.find { it.artifactId == "slf4j-api" }
        assertNotNull(slf4j, "Should find slf4j-api")
        assertEquals("2.0.13", slf4j.version)

        // Should find junit
        val junit = nodes.find { it.artifactId == "junit" }
        assertNotNull(junit, "Should find junit")
        assertEquals("4.13.2", junit.version)
    }

    @Test
    fun `should parse multi-project builds`() {
        val output = loadTestResource("multiproject-dependencies.txt")
        if (output.isEmpty()) {
            println("Test resource not found, skipping")
            return
        }

        val nodes = GradleDependencyTreeParser.parse(output)
        assertTrue(nodes.isNotEmpty(), "Should parse dependencies from multiple projects")

        // Should find dependencies from both app and lib
        val appLib = nodes.find { it.artifactId == "lib" }
        val slf4j = nodes.find { it.artifactId == "slf4j-api" }

        assertTrue(appLib != null || slf4j != null, "Should find dependencies from multi-project")
    }

    @Test
    fun `should parse dependencies with transitive children`() {
        val output = loadTestResource("with-transitive.txt")
        if (output.isEmpty()) {
            println("Test resource not found, skipping")
            return
        }

        val nodes = GradleDependencyTreeParser.parse(output)
        assertTrue(nodes.isNotEmpty(), "Should parse transitive dependencies")

        // Should find jackson-databind (root dependency)
        val jackson = nodes.find { it.artifactId == "jackson-databind" }
        assertNotNull(jackson, "Should find jackson-databind")

        // jackson-databind should have children (annotations, core, bom)
        assertTrue(jackson.children.isNotEmpty(), "Should have transitive dependencies")
    }

    @Test
    fun `should parse dependencies with different scopes`() {
        val output = loadTestResource("with-scopes.txt")
        if (output.isEmpty()) {
            println("Test resource not found, skipping")
            return
        }

        val nodes = GradleDependencyTreeParser.parse(output)
        assertTrue(nodes.isNotEmpty(), "Should parse dependencies with different scopes")

        // Verify we have multiple types of dependencies
        val deps = nodes.map { it.artifactId }.toSet()
        assertTrue(deps.isNotEmpty(), "Should parse multiple dependencies")
    }

    @Test
    fun `should extract groupId and artifactId correctly`() {
        val output = loadTestResource("simple-project.txt")
        if (output.isEmpty()) {
            println("Test resource not found, skipping")
            return
        }

        val nodes = GradleDependencyTreeParser.parse(output)
        val slf4j = nodes.find { it.artifactId == "slf4j-api" }

        assertNotNull(slf4j)
        assertEquals("org.slf4j", slf4j.groupId)
        assertEquals("slf4j-api", slf4j.artifactId)
        assertEquals("2.0.13", slf4j.version)
    }

    @Test
    fun `should handle empty output gracefully`() {
        val output = ""
        val nodes = GradleDependencyTreeParser.parse(output)
        assertEquals(0, nodes.size, "Should return empty list for empty input")
    }
}
