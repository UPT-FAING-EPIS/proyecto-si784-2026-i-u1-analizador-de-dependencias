package com.depanalyzer.cli

import tools.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliCapabilitiesTest {
    @Test
    fun `capability document exposes integration contract`() {
        val json = CapabilityJsonWriter.write(
            CapabilityDocument(cliVersion = "2.2.0")
        )
        val root = JsonMapper.builder().build().readTree(json)

        assertEquals("2.2.0", root.path("cliVersion").asText())
        assertTrue(root.path("reportSchemas").any { it.asText() == "1.1" })
        assertTrue(root.path("features").path("analyze.progressJson").booleanValue())
        assertTrue(root.path("features").path("report.dependencyTree").booleanValue())
        assertTrue(root.path("features").path("update.applyById").booleanValue())
    }

    @Test
    fun `progress event is valid ndjson`() {
        val json = ProgressEventJsonWriter.write(
            ProgressEvent(
                type = "phase",
                message = "CVEs",
                phase = "CVEs",
                current = 6,
                total = 7
            )
        )
        val root = JsonMapper.builder().build().readTree(json)

        assertEquals("depanalyzer-progress", root.path("stream").asText())
        assertEquals("CVEs", root.path("phase").asText())
        assertEquals(6, root.path("current").asInt())
        assertEquals(7, root.path("total").asInt())
    }
}
