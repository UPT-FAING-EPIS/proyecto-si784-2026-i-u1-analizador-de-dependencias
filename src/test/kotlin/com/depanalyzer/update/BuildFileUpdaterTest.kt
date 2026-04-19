package com.depanalyzer.update

import org.junit.jupiter.api.Test
import java.nio.file.Files
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
        assertTrue(text.contains("<version>${'$'}{spring.version}</version>"))
    }

    @Test
    fun `gradle groovy updater updates ext variable version`() {
        val dir = Files.createTempDirectory("gradle-groovy-updater")
        val build = dir.resolve("build.gradle").toFile()
        build.writeText(
            """
            ext {
                springVersion = '6.1.14'
            }

            dependencies {
                implementation "org.springframework:spring-context:${'$'}{springVersion}"
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
}
