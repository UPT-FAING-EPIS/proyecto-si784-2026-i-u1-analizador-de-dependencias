package com.depanalyzer.update

import org.junit.jupiter.api.Test
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildFileUpdaterTest {

    @Test
    fun `pom updater updates property referenced version`() {
        val dir = Files.createTempDirectory("pom-updater")
        val pom = dir.resolve("pom.xml").toFile()
        pom.writeText(
            $$"""
            <project>
              <properties>
                <spring.version>5.3.34</spring.version>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.springframework</groupId>
                  <artifactId>spring-context</artifactId>
                  <version>${spring.version}</version>
                </dependency>
              </dependencies>
            </project>
            """.trimIndent()
        )

        val updated = PomBuildFileUpdater().applyUpdate(
            pom,
            UpdateSuggestion("org.springframework", "spring-context", "5.3.34", "7.0.7", UpdateReason.CVE)
        )

        assertTrue(updated)
        val text = pom.readText()
        assertTrue(text.contains("<spring.version>7.0.7</spring.version>"))
        assertTrue(text.contains($$"<version>${spring.version}</version>"))
        assertTrue(isWellFormedXml(text))
    }

    @Test
    fun `pom updater updates direct version using real pom fixture`() {
        val pom = copyPomFixture("poms/simple/pom.xml")

        val updated = PomBuildFileUpdater().applyUpdate(
            pom,
            UpdateSuggestion("org.slf4j", "slf4j-api", "2.0.13", "2.0.16", UpdateReason.OUTDATED)
        )

        assertTrue(updated)
        val text = pom.readText()
        assertTrue(text.contains("<version>2.0.16</version>"))
        assertTrue(text.contains("<version>5.11.0</version>"))
        assertTrue(isWellFormedXml(text))
    }

    @Test
    fun `pom updater updates property value using real pom fixture`() {
        val pom = copyPomFixture("poms/with-properties/pom.xml")

        val updated = PomBuildFileUpdater().applyUpdate(
            pom,
            UpdateSuggestion("org.jetbrains.kotlin", "kotlin-stdlib", "2.0.21", "2.1.0", UpdateReason.OUTDATED)
        )

        assertTrue(updated)
        val text = pom.readText()
        assertTrue(text.contains("<kotlin.version>2.1.0</kotlin.version>"))
        assertTrue(text.contains($$"<version>${kotlin.version}</version>"))
        assertTrue(isWellFormedXml(text))
    }

    @Test
    fun `pom updater preserves comments and non target formatting from real pom fixture`() {
        val pom = copyPomFixture("poms/with-comments/pom.xml")

        val updated = PomBuildFileUpdater().applyUpdate(
            pom,
            UpdateSuggestion("org.slf4j", "slf4j-api", "1.7.30", "2.0.13", UpdateReason.CVE)
        )

        assertTrue(updated)
        val expected =
            resourceText("poms/with-comments/pom.xml").replace("<version>1.7.30</version>", "<version>2.0.13</version>")
        assertEquals(expected, pom.readText())
        assertTrue(isWellFormedXml(pom.readText()))
    }

    @Test
    fun `pom updater does not save when resulting xml is invalid`() {
        val dir = Files.createTempDirectory("pom-updater-invalid-xml")
        val pom = dir.resolve("pom.xml").toFile()
        val original = """
            <project>
              <dependencies>
                <dependency>
                  <groupId>org.slf4j</groupId>
                  <artifactId>slf4j-api</artifactId>
                  <version>1.7.30</version>
                </dependency>
              </dependencies>
        """.trimIndent()
        pom.writeText(original)

        val updated = PomBuildFileUpdater().applyUpdate(
            pom,
            UpdateSuggestion("org.slf4j", "slf4j-api", "1.7.30", "2.0.13", UpdateReason.CVE)
        )

        assertFalse(updated)
        assertEquals(original, pom.readText())
    }

    @Test
    fun `gradle groovy updater updates ext variable version`() {
        val dir = Files.createTempDirectory("gradle-groovy-updater")
        val build = dir.resolve("build.gradle").toFile()
        build.writeText(
            $$"""
            ext {
                springVersion = '6.1.14'
            }

            dependencies {
                implementation "org.springframework:spring-context:${springVersion}"
            }
            """.trimIndent()
        )

        val updated = GradleGroovyBuildFileUpdater().applyUpdate(
            build,
            UpdateSuggestion("org.springframework", "spring-context", "6.1.14", "7.0.7", UpdateReason.OUTDATED)
        )

        assertTrue(updated)
        assertTrue(build.readText().contains("springVersion = '7.0.7'"))
    }

    @Test
    fun `gradle groovy updater updates string notation using real fixture preserving format`() {
        val build = copyGradleFixture("gradles/simple/build.gradle", "build.gradle")
        val original = build.readText()

        val updated = GradleGroovyBuildFileUpdater().applyUpdate(
            build,
            UpdateSuggestion("org.slf4j", "slf4j-api", "2.0.13", "2.0.16", UpdateReason.OUTDATED)
        )

        assertTrue(updated)
        val expected = original.replace("org.slf4j:slf4j-api:2.0.13", "org.slf4j:slf4j-api:2.0.16")
        assertEquals(expected, build.readText())
    }

    @Test
    fun `gradle groovy updater updates map notation using real fixture preserving format`() {
        val build = copyGradleFixture("gradles/map-notation/build.gradle", "build.gradle")
        val original = build.readText()

        val updated = GradleGroovyBuildFileUpdater().applyUpdate(
            build,
            UpdateSuggestion("org.springframework", "spring-core", "6.1.14", "6.1.15", UpdateReason.OUTDATED)
        )

        assertTrue(updated)
        val expected = original.replace("version: '6.1.14'", "version: '6.1.15'")
        assertEquals(expected, build.readText())
    }

    @Test
    fun `gradle groovy updater updates ext variable declaration using real fixture preserving format`() {
        val build = copyGradleFixture("gradles/with-ext/build.gradle", "build.gradle")
        val original = build.readText()

        val updated = GradleGroovyBuildFileUpdater().applyUpdate(
            build,
            UpdateSuggestion("org.postgresql", "postgresql", "42.7.4", "42.7.5", UpdateReason.CVE)
        )

        assertTrue(updated)
        val expected = original.replace("ext.postgresVersion = '42.7.4'", "ext.postgresVersion = '42.7.5'")
        val text = build.readText()
        assertEquals(expected, text)
        assertTrue(text.contains("version: ext.postgresVersion"))
    }

    @Test
    fun `gradle kotlin updater updates libs versions toml when alias used`() {
        val dir = Files.createTempDirectory("gradle-kotlin-updater")
        val gradleDir = dir.resolve("gradle").toFile()
        gradleDir.mkdirs()

        val build = dir.resolve("build.gradle.kts").toFile()
        build.writeText(
            """
            dependencies {
                implementation(libs.spring.context)
            }
            """.trimIndent()
        )

        val toml = dir.resolve("gradle/libs.versions.toml").toFile()
        toml.writeText(
            """
            [versions]
            spring = "6.1.14"

            [libraries]
            spring-context = { group = "org.springframework", name = "spring-context", version.ref = "spring" }
            """.trimIndent()
        )

        val updated = GradleKotlinBuildFileUpdater().applyUpdate(
            build,
            UpdateSuggestion("org.springframework", "spring-context", "6.1.14", "7.0.7", UpdateReason.OUTDATED)
        )

        assertTrue(updated)
        val tomlText = toml.readText()
        assertTrue(tomlText.contains("spring = \"7.0.7\""))
    }

    @Test
    fun `gradle kotlin updater returns false when catalog alias is not used`() {
        val dir = Files.createTempDirectory("gradle-kotlin-updater-unused")
        val gradleDir = dir.resolve("gradle").toFile()
        gradleDir.mkdirs()

        val build = dir.resolve("build.gradle.kts").toFile()
        build.writeText("dependencies { implementation(\"org.example:other:1.0.0\") }")

        val toml = dir.resolve("gradle/libs.versions.toml").toFile()
        toml.writeText(
            """
            [versions]
            spring = "6.1.14"

            [libraries]
            spring-context = { group = "org.springframework", name = "spring-context", version.ref = "spring" }
            """.trimIndent()
        )

        val updated = GradleKotlinBuildFileUpdater().applyUpdate(
            build,
            UpdateSuggestion("org.springframework", "spring-context", "6.1.14", "7.0.7", UpdateReason.OUTDATED)
        )

        assertFalse(updated)
        assertEquals(
            """
            [versions]
            spring = "6.1.14"

            [libraries]
            spring-context = { group = "org.springframework", name = "spring-context", version.ref = "spring" }
            """.trimIndent(),
            toml.readText()
        )
    }

    @Test
    fun `gradle kotlin updater updates libs versions toml from real fixtures preserving format`() {
        val dir = Files.createTempDirectory("gradle-kotlin-catalog-fixture")
        val build = dir.resolve("build.gradle.kts").toFile()
        build.writeText(resourceText("gradles/kotlin-catalog/build.gradle.kts"))

        val gradleDir = dir.resolve("gradle")
        Files.createDirectories(gradleDir)
        val toml = gradleDir.resolve("libs.versions.toml").toFile()
        val originalToml = resourceText("gradle/libs.versions.toml")
        toml.writeText(originalToml)

        val updated = GradleKotlinBuildFileUpdater().applyUpdate(
            build,
            UpdateSuggestion("org.jetbrains.kotlin", "kotlin-stdlib", "2.0.21", "2.1.0", UpdateReason.OUTDATED)
        )

        assertTrue(updated)
        val expectedToml = originalToml.replace("kotlin = \"2.0.21\"", "kotlin = \"2.1.0\"")
        assertEquals(resourceText("gradles/kotlin-catalog/build.gradle.kts"), build.readText())
        assertEquals(expectedToml, toml.readText())
    }

    @Test
    fun `pom updater adds dependency management override for transitive suggestion`() {
        val dir = Files.createTempDirectory("pom-updater-override")
        val pom = dir.resolve("pom.xml").toFile()
        pom.writeText(
            """
            <project>
              <dependencies>
                <dependency>
                  <groupId>org.springframework</groupId>
                  <artifactId>spring-context</artifactId>
                  <version>5.3.34</version>
                </dependency>
              </dependencies>
            </project>
            """.trimIndent()
        )

        val updated = PomBuildFileUpdater().applyUpdate(
            pom,
            UpdateSuggestion(
                groupId = "org.apache.logging.log4j",
                artifactId = "log4j-core",
                currentVersion = "2.17.0",
                newVersion = "2.22.1",
                reason = UpdateReason.CVE,
                targetType = UpdateTargetType.TRANSITIVE_OVERRIDE,
                viaDirectCoordinate = "org.springframework:spring-context"
            )
        )

        assertTrue(updated)
        val text = pom.readText()
        assertTrue(text.contains("<dependencyManagement>"))
        assertTrue(text.contains("<groupId>org.apache.logging.log4j</groupId>"))
        assertTrue(text.contains("<artifactId>log4j-core</artifactId>"))
        assertTrue(text.contains("<version>2.22.1</version>"))
    }

    @Test
    fun `gradle groovy updater adds constraints override for transitive suggestion`() {
        val dir = Files.createTempDirectory("gradle-groovy-updater-override")
        val build = dir.resolve("build.gradle").toFile()
        build.writeText(
            """
            dependencies {
                implementation 'org.springframework:spring-context:5.3.34'
            }
            """.trimIndent()
        )

        val updated = GradleGroovyBuildFileUpdater().applyUpdate(
            build,
            UpdateSuggestion(
                groupId = "org.apache.logging.log4j",
                artifactId = "log4j-core",
                currentVersion = "2.17.0",
                newVersion = "2.22.1",
                reason = UpdateReason.CVE,
                targetType = UpdateTargetType.TRANSITIVE_OVERRIDE,
                viaDirectCoordinate = "org.springframework:spring-context"
            )
        )

        assertTrue(updated)
        val text = build.readText()
        assertTrue(text.contains("constraints"))
        assertTrue(text.contains("org.apache.logging.log4j:log4j-core:2.22.1"))
    }

    @Test
    fun `gradle kotlin updater adds constraints override for transitive suggestion`() {
        val dir = Files.createTempDirectory("gradle-kotlin-updater-override")
        val build = dir.resolve("build.gradle.kts").toFile()
        build.writeText(
            """
            dependencies {
                implementation("org.springframework:spring-context:5.3.34")
            }
            """.trimIndent()
        )

        val updated = GradleKotlinBuildFileUpdater().applyUpdate(
            build,
            UpdateSuggestion(
                groupId = "org.apache.logging.log4j",
                artifactId = "log4j-core",
                currentVersion = "2.17.0",
                newVersion = "2.22.1",
                reason = UpdateReason.CVE,
                targetType = UpdateTargetType.TRANSITIVE_OVERRIDE,
                viaDirectCoordinate = "org.springframework:spring-context"
            )
        )

        assertTrue(updated)
        val text = build.readText()
        assertTrue(text.contains("constraints"))
        assertTrue(text.contains("org.apache.logging.log4j:log4j-core:2.22.1"))
    }

    @Test
    fun `pom updater rejects unsafe target version`() {
        val dir = Files.createTempDirectory("pom-updater-unsafe-version")
        val pom = dir.resolve("pom.xml").toFile()
        val original = """
            <project>
              <dependencies>
                <dependency>
                  <groupId>org.slf4j</groupId>
                  <artifactId>slf4j-api</artifactId>
                  <version>1.7.30</version>
                </dependency>
              </dependencies>
            </project>
        """.trimIndent()
        pom.writeText(original)

        val updated = PomBuildFileUpdater().applyUpdate(
            pom,
            UpdateSuggestion("org.slf4j", "slf4j-api", "1.7.30", "1.0'; system('rm -rf /')", UpdateReason.CVE)
        )

        assertFalse(updated)
        assertEquals(original, pom.readText())
    }

    @Test
    fun `gradle groovy updater rejects unsafe target version`() {
        val dir = Files.createTempDirectory("gradle-groovy-updater-unsafe-version")
        val build = dir.resolve("build.gradle").toFile()
        val original = """
            dependencies {
                implementation 'org.slf4j:slf4j-api:1.7.30'
            }
        """.trimIndent()
        build.writeText(original)

        val updated = GradleGroovyBuildFileUpdater().applyUpdate(
            build,
            UpdateSuggestion("org.slf4j", "slf4j-api", "1.7.30", "1.0'; system('rm -rf /')", UpdateReason.CVE)
        )

        assertFalse(updated)
        assertEquals(original, build.readText())
    }

    @Test
    fun `gradle kotlin updater rejects unsafe target version`() {
        val dir = Files.createTempDirectory("gradle-kotlin-updater-unsafe-version")
        val build = dir.resolve("build.gradle.kts").toFile()
        val original = """
            dependencies {
                implementation("org.slf4j:slf4j-api:1.7.30")
            }
        """.trimIndent()
        build.writeText(original)

        val updated = GradleKotlinBuildFileUpdater().applyUpdate(
            build,
            UpdateSuggestion("org.slf4j", "slf4j-api", "1.7.30", "1.0'; system('rm -rf /')", UpdateReason.CVE)
        )

        assertFalse(updated)
        assertEquals(original, build.readText())
    }

    private fun copyPomFixture(resourcePath: String): File {
        val dir = Files.createTempDirectory("pom-updater-fixture")
        val pom = dir.resolve("pom.xml")
        Files.writeString(pom, resourceText(resourcePath))
        return pom.toFile()
    }

    private fun copyGradleFixture(resourcePath: String, targetFileName: String): File {
        val dir = Files.createTempDirectory("gradle-updater-fixture")
        val build = dir.resolve(targetFileName)
        Files.writeString(build, resourceText(resourcePath))
        return build.toFile()
    }

    private fun resourceText(resourcePath: String): String {
        val url = requireNotNull(javaClass.classLoader.getResource(resourcePath)) {
            "Missing resource: $resourcePath"
        }
        return Path.of(url.toURI()).toFile().readText()
    }

    private fun isWellFormedXml(content: String): Boolean {
        return try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(content)))
            true
        } catch (_: Exception) {
            false
        }
    }
}
