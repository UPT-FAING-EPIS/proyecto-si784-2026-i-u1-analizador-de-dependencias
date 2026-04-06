package com.depanalyzer.parser.maven

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MavenDependencyTreeParserTest {

    private val parser = MavenDependencyTreeParser

    private fun resourceMavenTree(path: String): String {
        val url = this::class.java.classLoader.getResource(path)
        requireNotNull(url) { "Missing test resource: $path" }
        return File(url.toURI()).readText()
    }

    @Test
    fun shouldParseSimpleTreeWith3DirectDependencies() {
        val output = resourceMavenTree("maven-trees/simple-output.txt")
        val result = parser.parse(output, verbose = false)

        assertEquals(3, result.size, "Should have 3 root dependencies")
        assertEquals("junit", result[0].artifactId)
        assertEquals("4.13.2", result[0].version)
        assertEquals("slf4j-api", result[1].artifactId)
        assertEquals("commons-lang3", result[2].artifactId)
    }

    @Test
    fun shouldMarkDirectDependenciesCorrectly() {
        val output = resourceMavenTree("maven-trees/simple-output.txt")
        val result = parser.parse(output, verbose = false)

        result.forEach { node ->
            assertEquals(node.parent, null, "All root nodes should have no parent")
            assertTrue(node.isDirectDependency(), "Root nodes should be direct dependencies")
        }
    }

    @Test
    fun shouldParseMultiLevelTreeWithTransitiveDependencies() {
        val output = resourceMavenTree("maven-trees/transitive-output.txt")
        val result = parser.parse(output, verbose = false)

        assertEquals(3, result.size, "Should have 3 root dependencies")

        val springCore = result.find { it.artifactId == "spring-core" }
        assertNotNull(springCore)
        assertTrue(springCore.children.isNotEmpty(), "spring-core should have children")
        assertEquals(2, springCore.children.size, "spring-core should have 2 direct children")

        val jcl = springCore.children.find { it.artifactId == "spring-jcl" }
        assertNotNull(jcl, "Should have spring-jcl")
        val micrometer = springCore.children.find { it.artifactId == "micrometer-commons" }
        assertNotNull(micrometer, "Should have micrometer-commons")
    }

    @Test
    fun shouldIdentifyDirectVsTransitiveDependencies() {
        val output = resourceMavenTree("maven-trees/transitive-output.txt")
        val result = parser.parse(output, verbose = false)

        result.forEach { node ->
            assertTrue(node.isDirectDependency(), "${node.artifactId} should be direct")
        }

        val springCore = result.find { it.artifactId == "spring-core" }
        assertNotNull(springCore)
        springCore.children.forEach { child ->
            assertFalse(child.isDirectDependency(), "${child.artifactId} should be transitive")
            assertEquals(springCore.id, child.parent?.id, "Parent should be spring-core")
        }
    }

    @Test
    fun shouldParseDifferentScopesCorrectly() {
        val output = resourceMavenTree("maven-trees/with-scopes-output.txt")
        val result = parser.parse(output, verbose = false)

        val testScope = result.find { it.artifactId == "junit" }
        assertNotNull(testScope)

        val providedScope = result.find { it.artifactId == "javax.servlet-api" }
        assertNotNull(providedScope)

        val runtimeScope = result.find { it.artifactId == "commons-lang3" }
        assertNotNull(runtimeScope)

        val systemScope = result.find { it.artifactId == "spring-core" }
        assertNotNull(systemScope)
    }

    @Test
    fun shouldHandleExcludedDependencies() {
        val output = resourceMavenTree("maven-trees/with-excluded-output.txt")
        val result = parser.parse(output, verbose = false)

        assertEquals(3, result.size, "Should still parse excluded dependencies")

        val springCore = result.find { it.artifactId == "spring-core" }
        assertNotNull(springCore)
        assertTrue(springCore.children.isNotEmpty(), "spring-core should have at least one child")
    }

    @Test
    fun shouldBuildCorrectParentChildHierarchy() {
        val output = resourceMavenTree("maven-trees/transitive-output.txt")
        val result = parser.parse(output, verbose = false)

        val springCore = result.find { it.artifactId == "spring-core" }
        assertNotNull(springCore)

        val jcl = springCore.children.find { it.artifactId == "spring-jcl" }
        assertNotNull(jcl)
        assertEquals(springCore.id, jcl.parent?.id, "Parent should be spring-core")
        assertEquals("5.3.22", jcl.version, "Version should be preserved")
    }

    @Test
    fun shouldHandleComplexRealWorldTree() {
        val output = resourceMavenTree("maven-trees/complex-real-world.txt")
        val result = parser.parse(output, verbose = false)

        assertEquals(4, result.size, "Should have 4 root dependencies")


        val springCloudStarter = result.find { it.artifactId == "spring-cloud-starter-config" }
        assertNotNull(springCloudStarter)
        assertTrue(springCloudStarter.children.isNotEmpty(), "Should have children")

        val webflux = springCloudStarter.children.find { it.artifactId == "spring-boot-starter-webflux" }
        assertNotNull(webflux)
        assertTrue(webflux.children.isNotEmpty(), "WebFlux should have children")

        val springBoot = webflux.children.find { it.artifactId == "spring-boot" }
        assertNotNull(springBoot)
        assertTrue(springBoot.children.isNotEmpty(), "Spring Boot should have children")
    }

    @Test
    fun shouldHandleEmptyOrBlankInput() {
        val emptyResult = parser.parse("", verbose = false)
        assertEquals(0, emptyResult.size, "Empty input should return empty list")

        val blankResult = parser.parse("   \n\n   ", verbose = false)
        assertEquals(0, blankResult.size, "Blank input should return empty list")
    }

    @Test
    fun shouldExtractAllCoordinateComponentsCorrectly() {
        val output = resourceMavenTree("maven-trees/simple-output.txt")
        val result = parser.parse(output, verbose = false)

        result.forEach { node ->
            assertTrue(node.groupId.isNotEmpty(), "GroupId should not be empty")
            assertTrue(node.artifactId.isNotEmpty(), "ArtifactId should not be empty")
            assertTrue(node.version.isNotEmpty(), "Version should not be empty")
        }
    }

    @Test
    fun shouldHandleOmittedForDuplicateAnnotation() {
        val output = resourceMavenTree("maven-trees/with-excluded-output.txt")
        val result = parser.parse(output, verbose = false)

        assertTrue(result.isNotEmpty(), "Should parse despite 'omitted' annotations")
    }

    @Test
    fun shouldHandleMavenLogPrefixes() {
        val withInfoPrefixes = """
            [INFO] org.example:myapp:1.0
            [INFO] +- junit:junit:4.13.2 (test)
            [INFO] +- org.slf4j:slf4j-api:1.7.36 (compile)
            [INFO] |  +- org.slf4j:slf4j-log4j12:1.7.36 (compile)
            [INFO] \- commons-lang3:commons-lang3:3.12.0 (runtime)
        """.trimIndent()

        val result = parser.parse(withInfoPrefixes, verbose = false)

        assertEquals(3, result.size, "Should parse 3 root dependencies despite [INFO] prefixes")
        assertEquals("junit", result[0].artifactId, "First dependency should be junit")
        assertEquals("4.13.2", result[0].version, "junit version should be 4.13.2")

        val slf4j = result.find { it.artifactId == "slf4j-api" }
        assertNotNull(slf4j, "Should find slf4j-api")
        assertTrue(slf4j.children.isNotEmpty(), "slf4j-api should have children")
    }

    @Test
    fun shouldHandleMixedLogPrefixes() {
        val mixedPrefixes = """
            [INFO] org.example:myapp:1.0
            [WARNING] Some warning text that doesn't matter
            [INFO] +- junit:junit:4.13.2 (test)
            [INFO] +- org.slf4j:slf4j-api:1.7.36 (compile)
            [ERROR] Some error text that doesn't matter
            [INFO] \- commons-lang3:commons-lang3:3.12.0 (runtime)
        """.trimIndent()

        val result = parser.parse(mixedPrefixes)

        assertEquals(3, result.size, "Should parse 3 root dependencies (all are at depth 0)")
    }

    @Test
    fun shouldExtractVersionCorrectlyFromMavenFormat5Parts() {
        val mavenFormat5Parts = """
            [INFO] org.example:myapp:1.0
            [INFO] +- org.slf4j:slf4j-api:jar:2.0.13:compile
            [INFO] +- junit:junit:jar:4.13.2:test
            [INFO] \- org.apache.commons:commons-lang3:jar:3.12.0:runtime
        """.trimIndent()

        val result = parser.parse(mavenFormat5Parts, verbose = false)

        assertEquals(3, result.size, "Should parse 3 root dependencies")

        val slf4j = result.find { it.artifactId == "slf4j-api" }
        assertNotNull(slf4j)
        assertEquals("2.0.13", slf4j.version, "Version should be 2.0.13, not jar")
        
        val junit = result.find { it.artifactId == "junit" }
        assertNotNull(junit)
        assertEquals("4.13.2", junit.version, "Version should be 4.13.2, not jar")
        
        val commons = result.find { it.artifactId == "commons-lang3" }
        assertNotNull(commons)
        assertEquals("3.12.0", commons.version, "Version should be 3.12.0, not jar")
    }

    @Test
    fun shouldCorrectlyNestTransitiveDependenciesUnderRoots() {
        val simpleTree = """
            [INFO] org.example:myapp:1.0
            [INFO] +- org.slf4j:slf4j-api:jar:2.0.13:compile
            [INFO] |  \- org.slf4j:slf4j-log4j12:jar:1.7.36:runtime
            [INFO] \- junit:junit:jar:4.13.2:test
        """.trimIndent()

        val result = parser.parse(simpleTree, verbose = false)

        assertEquals(2, result.size, "Should have exactly 2 root dependencies (slf4j-api and junit)")

        val slf4j = result[0]
        assertEquals("slf4j-api", slf4j.artifactId, "First root should be slf4j-api")
        assertEquals(null, slf4j.parent, "Root should have no parent")

        assertEquals(1, slf4j.children.size, "slf4j-api should have 1 child")
        val log4j12 = slf4j.children[0]
        assertEquals("slf4j-log4j12", log4j12.artifactId, "Child should be slf4j-log4j12")
        assertEquals(slf4j.id, log4j12.parent?.id, "Parent should be slf4j-api")

        val junit = result[1]
        assertEquals("junit", junit.artifactId, "Second root should be junit")
        assertEquals(null, junit.parent, "Root should have no parent")
        assertEquals(0, junit.children.size, "junit should have no children")
    }
}
