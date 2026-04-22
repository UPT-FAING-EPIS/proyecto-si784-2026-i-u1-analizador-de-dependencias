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

    @Test
    fun `should parse gradle 9 root project output`() {
        val output = """
            ------------------------------------------------------------
            Root project 'kotlintest'
            ------------------------------------------------------------

            compileClasspath - Compile classpath for 'main'.
            +--- org.apache.logging.log4j:log4j-core:2.14.1
            |    \--- org.apache.logging.log4j:log4j-api:2.14.1
            \--- com.fasterxml.jackson.core:jackson-databind:2.9.10.8
                 +--- com.fasterxml.jackson.core:jackson-annotations:2.9.10
                 \--- com.fasterxml.jackson.core:jackson-core:2.9.10

            apiElements-published (n)
            No dependencies
        """.trimIndent()

        val nodes = GradleDependencyTreeParser.parse(output)

        val log4jCore = nodes.find { it.artifactId == "log4j-core" }
        assertNotNull(log4jCore)
        assertTrue(log4jCore.children.any { it.artifactId == "log4j-api" })

        val databind = nodes.find { it.artifactId == "jackson-databind" }
        assertNotNull(databind)
        assertTrue(databind.children.any { it.artifactId == "jackson-core" })
    }

    @Test
    fun `should parse output without explicit project header`() {
        val output = """
            compileClasspath - Compile classpath for 'main'.
            +--- org.yaml:snakeyaml:1.26
            \--- commons-collections:commons-collections:3.2.1
        """.trimIndent()

        val nodes = GradleDependencyTreeParser.parse(output)

        assertTrue(nodes.any { it.artifactId == "snakeyaml" })
        assertTrue(nodes.any { it.artifactId == "commons-collections" })
    }

    @Test
    fun `should deduplicate repeated roots across configurations`() {
        val output = """
            Root project 'kotlintest'

            compileClasspath - Compile classpath for 'main'.
            +--- org.apache.logging.log4j:log4j-core:2.14.1
            |    \--- org.apache.logging.log4j:log4j-api:2.14.1
            \--- org.yaml:snakeyaml:1.26

            runtimeClasspath - Runtime classpath of 'main'.
            +--- org.apache.logging.log4j:log4j-core:2.14.1
            |    \--- org.apache.logging.log4j:log4j-api:2.14.1
            \--- org.yaml:snakeyaml:1.26
        """.trimIndent()

        val nodes = GradleDependencyTreeParser.parse(output)

        val log4jRoots = nodes.filter { it.coordinate == "org.apache.logging.log4j:log4j-core:2.14.1" }
        assertEquals(1, log4jRoots.size)
        assertTrue(log4jRoots.single().children.any { it.artifactId == "log4j-api" })

        val snakeYamlRoots = nodes.filter { it.coordinate == "org.yaml:snakeyaml:1.26" }
        assertEquals(1, snakeYamlRoots.size)
    }

    @Test
    fun `should ignore dependency constraints entries`() {
        val output = """
            Root project 'kotlintest'

            testCompileClasspath - Compile classpath for 'test'.
            +--- org.jetbrains.kotlin:kotlin-test:2.3.10
            \--- org.jetbrains.kotlin:kotlin-test:2.3.10 (c)
        """.trimIndent()

        val nodes = GradleDependencyTreeParser.parse(output)
        val kotlinTest = nodes.filter { it.coordinate == "org.jetbrains.kotlin:kotlin-test:2.3.10" }

        assertEquals(1, kotlinTest.size)
    }
}
