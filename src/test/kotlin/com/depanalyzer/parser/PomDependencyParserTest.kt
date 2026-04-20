package com.depanalyzer.parser

import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlin.test.*

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

    @Test
    fun `fallbacks to maven central if no repositories are declared`() {
        val repos = parser.repositories(resourcePom("poms/simple/pom.xml"))

        assertEquals(1, repos.size)
        assertEquals("central", repos[0].id)
        assertEquals("https://repo1.maven.org/maven2", repos[0].url)
    }

    @Test
    fun `extracts multiple repositories including nexus and jitpack`() {
        val repos = parser.repositories(resourcePom("poms/with-repositories/pom.xml"))

        assertEquals(3, repos.size)

        val jitpack = repos.find { it.id == "jitpack" }
        assertNotNull(jitpack)
        assertEquals("https://jitpack.io", jitpack.url)

        val google = repos.find { it.id == "google" }
        assertNotNull(google)
        assertEquals("https://maven.google.com", google.url)
        assertTrue(google.releases)
        assertTrue(!google.snapshots)

        val nexus = repos.find { it.id == "nexus-private" }
        assertNotNull(nexus)
        assertEquals("https://nexus.example.com/repository/maven-public/", nexus.url)
    }

    @Test
    fun `resolves authentication from environment variables`() {
        val mockEnv = mockk<(String) -> String?>()
        every { mockEnv("MAVEN_REPO_NEXUS_AUTH_USERNAME") } returns "admin"
        every { mockEnv("MAVEN_REPO_NEXUS_AUTH_PASSWORD") } returns "secret"

        val authParser = PomDependencyParser(
            envProvider = mockEnv,
            trustedCredentialHosts = setOf("nexus.example.com")
        )
        val repos = authParser.repositories(resourcePom("poms/with-auth/pom.xml"))

        val nexusAuth = repos.find { it.id == "nexus-auth" }
        assertNotNull(nexusAuth)
        assertEquals("admin", nexusAuth.username)
        assertEquals("secret", nexusAuth.password)
    }

    @Test
    fun `does not read env credentials for untrusted repository host`() {
        var envLookups = 0
        val authParser = PomDependencyParser(
            envProvider = {
                envLookups++
                "secret"
            },
            trustedCredentialHosts = emptySet()
        )

        val repos = authParser.repositories(resourcePom("poms/with-auth/pom.xml"))
        val nexusAuth = repos.find { it.id == "nexus-auth" }

        assertNotNull(nexusAuth)
        assertNull(nexusAuth.username)
        assertNull(nexusAuth.password)
        assertEquals(0, envLookups)
    }

    @Test
    fun `filters repository urls that target localhost and falls back to maven central`() {
        val dir = Files.createTempDirectory("pom-repo-ssrf")
        val pom = dir.resolve("pom.xml").toFile()
        pom.writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.test</groupId>
                <artifactId>demo</artifactId>
                <version>1.0.0</version>
                <repositories>
                    <repository>
                        <id>unsafe</id>
                        <url>http://localhost:8081/repository/maven-public/</url>
                    </repository>
                </repositories>
            </project>
            """.trimIndent()
        )

        val repos = parser.repositories(pom)

        assertEquals(1, repos.size)
        assertEquals("central", repos[0].id)
    }

    @Test
    fun `blocks parent pom traversal outside project parent tree`() {
        val root = Files.createTempDirectory("pom-parent-traversal")
        val projectDir = Files.createDirectories(root.resolve("workspace/project"))
        val childDir = Files.createDirectories(projectDir.resolve("child"))
        val outsideDir = Files.createDirectories(root.resolve("workspace/outside"))

        outsideDir.resolve("pom.xml").toFile().writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>outside-parent</artifactId>
                <version>1.0.0</version>
                <properties>
                    <shared.version>9.9.9</shared.version>
                </properties>
            </project>
            """.trimIndent()
        )

        val childPom = childDir.resolve("pom.xml").toFile()
        childPom.writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>com.example</groupId>
                    <artifactId>outside-parent</artifactId>
                    <version>1.0.0</version>
                    <relativePath>../../outside/pom.xml</relativePath>
                </parent>
                <groupId>com.example</groupId>
                <artifactId>child</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>${'$'}{shared.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """.trimIndent()
        )

        val deps = parser.parse(childPom)
        val junit = deps.single()

        assertEquals("${'$'}{shared.version}", junit.version)
    }

    private fun resourcePom(path: String): File {
        val url = this::class.java.classLoader.getResource(path)
        requireNotNull(url) { "Missing test resource: $path" }
        return File(url.toURI())
    }
}
