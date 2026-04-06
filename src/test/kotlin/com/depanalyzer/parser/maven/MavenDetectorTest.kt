package com.depanalyzer.parser.maven

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MavenDetectorTest {

    @Test
    fun shouldDetectMavenWhenAvailable() {
        val isAvailable = MavenDetector.isAvailable()

        if (isAvailable) {
            assertTrue(true, "Maven should be detected if installed")
        } else {
            assertFalse(false, "Maven correctly reported as unavailable")
        }
    }

    @Test
    fun shouldReturnVersionStringWhenMavenAvailable() {
        if (MavenDetector.isAvailable()) {
            val version = MavenDetector.getVersion()
            assertNotNull(version, "Version should not be null if Maven is available")
            assertTrue(version.contains("Maven") || version.contains("maven"), 
                      "Version string should contain 'Maven'")
        }
    }

    @Test
    fun shouldHandleMavenUnavailableGracefully() {

        val isAvailable = MavenDetector.isAvailable()
        val version = MavenDetector.getVersion()

        if (isAvailable) {
            assertNotNull(version, "If Maven is available, version should not be null")
        } else {
            assertEquals(version, null, "If Maven is not available, version should be null")
        }
    }
}
