package com.depanalyzer.repository

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: RepositoryClient

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = RepositoryClient(connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetches latest version from mock repository`() {
        val metadataXml = """
            <metadata>
                <groupId>org.slf4j</groupId>
                <artifactId>slf4j-api</artifactId>
                <versioning>
                    <latest>2.0.13</latest>
                    <release>2.0.13</release>
                    <versions>
                        <version>2.0.12</version>
                        <version>2.0.13</version>
                    </versions>
                    <lastUpdated>20240101120000</lastUpdated>
                </versioning>
            </metadata>
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(metadataXml))

        val repo = ProjectRepository(id = "test", url = mockWebServer.url("/").toString())
        val version = client.getLatestVersion(repo, "org.slf4j", "slf4j-api")

        assertEquals("2.0.13", version)
        
        val request = mockWebServer.takeRequest()
        assertEquals("/org/slf4j/slf4j-api/maven-metadata.xml", request.path)
    }

    @Test
    fun `returns null if repository returns 404`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val repo = ProjectRepository(id = "test", url = mockWebServer.url("/").toString())
        val version = client.getLatestVersion(repo, "non.existent", "artifact")

        assertNull(version)
    }

    @Test
    fun `handles basic authentication`() {
        mockWebServer.enqueue(MockResponse().setBody("<metadata><versioning><release>1.0.0</release></versioning></metadata>"))

        val repo = ProjectRepository(
            id = "secure",
            url = mockWebServer.url("/").toString(),
            username = "admin",
            password = "password"
        )
        
        client.getLatestVersion(repo, "com.secure", "artifact")
        
        val request = mockWebServer.takeRequest()
        assertNotNull(request.getHeader("Authorization"))
        assertTrue(request.getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun `integration test against Maven Central`() {
        // This test requires internet access.
        val repo = ProjectRepository.MAVEN_CENTRAL
        val version = client.getLatestVersion(repo, "org.slf4j", "slf4j-api")
        
        assertNotNull(version)
        // slf4j-api 2.0.x is current, so we just check it starts with a number
        assertTrue(version.first().isDigit())
    }
}
