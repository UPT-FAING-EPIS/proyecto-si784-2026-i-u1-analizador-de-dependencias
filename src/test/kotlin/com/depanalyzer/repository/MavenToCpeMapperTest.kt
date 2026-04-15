package com.depanalyzer.repository

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MavenToCpeMapperTest {

    @Test
    fun `mapToCpe - transformApacheCommonsLang3 correctamente`() {
        val cpe = MavenToCpeMapper.mapToCpe("org.apache", "commons-lang3", "3.14.0")

        assertEquals("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*", cpe)
    }

    @Test
    fun `mapToCpe - transformGoogleGuava correctamente`() {
        val cpe = MavenToCpeMapper.mapToCpe("com.google.guava", "guava", "31.1-jre")

        assertEquals("cpe:2.3:a:google:guava:31.1-jre:*:*:*:*:*:*:*", cpe)
    }

    @Test
    fun `mapToCpe - transformSpringFramework correctamente`() {
        val cpe = MavenToCpeMapper.mapToCpe("org.springframework", "spring-core", "5.3.20")

        assertEquals("cpe:2.3:a:springframework:spring:5.3.20:*:*:*:*:*:*:*", cpe)
    }

    @Test
    fun `mapToCpe - transformLog4jCore correctamente`() {
        val cpe = MavenToCpeMapper.mapToCpe("org.apache.logging.log4j", "log4j-core", "2.17.1")

        assertEquals("cpe:2.3:a:apache:log4j:2.17.1:*:*:*:*:*:*:*", cpe)
    }

    @Test
    fun `mapToCpe - transformSLF4J_Correctly`() {
        val cpe = MavenToCpeMapper.mapToCpe("org.slf4j", "slf4j-api", "1.7.36")

        assertEquals("cpe:2.3:a:slf4j:slf4j:1.7.36:*:*:*:*:*:*:*", cpe)
    }

    @Test
    fun `mapToCpe - transformJacksonDatabind correctamente`() {
        val cpe = MavenToCpeMapper.mapToCpe("com.fasterxml.jackson.core", "jackson-databind", "2.14.0")

        assertEquals("cpe:2.3:a:fasterxml:jackson_databind:2.14.0:*:*:*:*:*:*:*", cpe)
    }

    @Test
    fun `mapToCpe - preserveVersionComplexity`() {
        val cpe = MavenToCpeMapper.mapToCpe("org.apache", "commons-lang3", "3.14.0-RC1-SNAPSHOT")

        assertEquals("cpe:2.3:a:apache:commons_lang:3.14.0-RC1-SNAPSHOT:*:*:*:*:*:*:*", cpe)
    }

    @Test
    fun `mapToCpe - replaceDashesWithUnderscores`() {
        val cpe = MavenToCpeMapper.mapToCpe("com.squareup.okhttp3", "okhttp", "4.10.0")

        assertEquals("cpe:2.3:a:squareup:okhttp:4.10.0:*:*:*:*:*:*:*", cpe)
    }

    @Test
    fun `mapToCpe - knownMappingOverride_JUnit`() {
        val cpe = MavenToCpeMapper.mapToCpe("org.junit.jupiter", "junit-jupiter-api", "5.9.0")

        assertEquals("cpe:2.3:a:junit:junit:5.9.0:*:*:*:*:*:*:*", cpe)
    }

    @Test
    fun `mapToCpe - knownMappingOverride_Mockito`() {
        val cpe = MavenToCpeMapper.mapToCpe("org.mockito", "mockito-core", "4.8.1")

        assertEquals("cpe:2.3:a:mockito:mockito:4.8.1:*:*:*:*:*:*:*", cpe)
    }

    @Test
    fun `mapToCpe - validateCpeFormat`() {
        val cpe = MavenToCpeMapper.mapToCpe("org.apache", "commons-lang3", "3.14.0")

        val parts = cpe.split(":")
        assertEquals(13, parts.size)
        assertEquals("cpe", parts[0])
        assertEquals("2.3", parts[1])
        assertEquals("a", parts[2])
        assertEquals("apache", parts[3])
        assertEquals("commons_lang", parts[4])
        assertEquals("3.14.0", parts[5])

        for (i in 6 until parts.size) {
            assertEquals("*", parts[i])
        }
    }

    @Test
    fun `mapToCpe - edgeCaseEmptyVersion`() {
        val cpe = MavenToCpeMapper.mapToCpe("org.apache", "commons-lang3", "")

        assertEquals("cpe:2.3:a:apache:commons_lang::*:*:*:*:*:*:*", cpe)
    }
}
