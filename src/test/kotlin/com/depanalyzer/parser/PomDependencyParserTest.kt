package com.depanalyzer.parser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PomDependencyParserTest {
    private val parser = PomDependencyParser()

    @Test
    fun `parses dependencies from dependencies section`() {
        val deps = parser.parse(resourcePom("poms/simple/pom.xml"))

        assertEquals(2, deps.size)
        val slf4j = deps.firstOrNull { it.groupId == "org.slf4j" && it.artifactId == "slf4j-api" }
        assertNotNull(slf4j)
        assertEquals("2.0.13", slf4j.version)
        assertEquals("compile", slf4j.scope)
        assertEquals(DependencySection.DEPENDENCIES, slf4j.section)
    }

    @Test
    fun `resolves property placeholders in versions`() {
        val deps = parser.parse(resourcePom("poms/with-properties/pom.xml"))

        val kotlinStdlib = deps.single()
        assertEquals("org.jetbrains.kotlin", kotlinStdlib.groupId)
        assertEquals("kotlin-stdlib", kotlinStdlib.artifactId)
        assertEquals("2.0.21", kotlinStdlib.version)
    }

    @Test
    fun `parses dependencyManagement and resolves managed versions in dependencies`() {
        val deps = parser.parse(resourcePom("poms/with-dependency-management/pom.xml"))

        val direct = deps.firstOrNull {
            it.section == DependencySection.DEPENDENCIES &&
                it.groupId == "com.fasterxml.jackson.core" &&
                it.artifactId == "jackson-databind"
        }
        val managed = deps.firstOrNull {
            it.section == DependencySection.DEPENDENCY_MANAGEMENT &&
                it.groupId == "com.fasterxml.jackson.core" &&
                it.artifactId == "jackson-databind"
        }

        assertNotNull(direct)
        assertNotNull(managed)
        assertEquals("2.18.1", direct.version)
        assertEquals("2.18.1", managed.version)
    }

    @Test
    fun `inherits properties from parent pom and resolves dependency version`() {
        val deps = parser.parse(resourcePom("poms/parent-properties/child/pom.xml"))

        val junit = deps.single()
        assertEquals("junit", junit.groupId)
        assertEquals("junit", junit.artifactId)
        assertEquals("4.13.2", junit.version)
        assertEquals("test", junit.scope)
    }

    @Test
    fun `inherits managed versions from parent dependencyManagement`() {
        val deps = parser.parse(resourcePom("poms/parent-managed/child/pom.xml"))

        val commons = deps.singleOrNull {
            it.groupId == "org.apache.commons" && it.artifactId == "commons-lang3"
        }
        assertNotNull(commons)
        assertEquals("3.16.0", commons.version)
    }

    @Test
    fun `returns compile scope when scope is omitted`() {
        val deps = parser.parse(resourcePom("poms/with-properties/pom.xml"))
        assertTrue(deps.all { it.scope == "compile" })
    }

    private fun resourcePom(path: String): File {
        val url = this::class.java.classLoader.getResource(path)
        requireNotNull(url) { "Missing test resource: $path" }
        return File(url.toURI())
    }
}
