package com.depanalyzer.repository

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import kotlin.test.*

class RepositoryClientTest {

    private val metadataXml = """
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

    @Test
    fun `fetches latest version from repository metadata`() {
        var requestedPath: String? = null
        val client = repositoryClientReturning(body = metadataXml) { path, _ ->
            requestedPath = path
        }

        val repo = ProjectRepository(id = "test", url = "https://repo.example.com/maven2")
        val version = client.getLatestVersion(repo, "org.slf4j", "slf4j-api")

        assertEquals("2.0.13", version)
        assertEquals("/maven2/org/slf4j/slf4j-api/maven-metadata.xml", requestedPath)
    }

    @Test
    fun `returns null if repository returns 404`() {
        val client = repositoryClientReturning(code = 404, body = "")

        val repo = ProjectRepository(id = "test", url = "https://repo.example.com/maven2")
        val version = client.getLatestVersion(repo, "non.existent", "artifact")

        assertNull(version)
    }

    @Test
    fun `handles basic authentication`() {
        var authorizationHeader: String? = null
        val client = repositoryClientReturning(
            body = "<metadata><versioning><release>1.0.0</release></versioning></metadata>",
            trustedCredentialHosts = setOf("repo.example.com")
        ) { _, header ->
            authorizationHeader = header
        }

        val repo = ProjectRepository(
            id = "secure",
            url = "https://repo.example.com/maven2",
            username = "admin",
            password = "password"
        )

        client.getLatestVersion(repo, "com.secure", "artifact")

        assertNotNull(authorizationHeader)
        assertTrue(authorizationHeader!!.startsWith("Basic "))
    }

    @Test
    fun `does not send credentials to untrusted host`() {
        var authorizationHeader: String? = null
        val client = repositoryClientReturning(
            body = "<metadata><versioning><release>1.0.0</release></versioning></metadata>",
            trustedCredentialHosts = emptySet()
        ) { _, header ->
            authorizationHeader = header
        }

        val repo = ProjectRepository(
            id = "secure",
            url = "https://repo.example.com/maven2",
            username = "admin",
            password = "password"
        )

        client.getLatestVersion(repo, "com.secure", "artifact")

        assertNull(authorizationHeader)
    }

    @Test
    fun `does not send credentials over plain http even for trusted host`() {
        var authorizationHeader: String? = null
        val client = repositoryClientReturning(
            body = "<metadata><versioning><release>1.0.0</release></versioning></metadata>",
            trustedCredentialHosts = setOf("repo.example.com")
        ) { _, header ->
            authorizationHeader = header
        }

        val repo = ProjectRepository(
            id = "secure",
            url = "http://repo.example.com/maven2",
            username = "admin",
            password = "password"
        )

        client.getLatestVersion(repo, "com.secure", "artifact")

        assertNull(authorizationHeader)
    }

    @Test
    fun `rejects malicious version values from metadata`() {
        val maliciousMetadata = """
            <metadata>
                <versioning>
                    <release>1.0'; system('rm -rf /')</release>
                    <latest>1.0'; system('rm -rf /')</latest>
                    <versions>
                        <version>1.0'; system('rm -rf /')</version>
                    </versions>
                </versioning>
            </metadata>
        """.trimIndent()

        val client = repositoryClientReturning(body = maliciousMetadata)

        val repo = ProjectRepository(id = "test", url = "https://repo.example.com/maven2")
        val version = client.getLatestVersion(repo, "org.example", "demo")

        assertNull(version)
    }

    @Test
    fun `blocks requests to non standard ports`() {
        var called = false
        val client = repositoryClientReturning(body = metadataXml) { _, _ ->
            called = true
        }

        val repo = ProjectRepository(id = "test", url = "https://repo.example.com:8443/maven2")
        val version = client.getLatestVersion(repo, "org.slf4j", "slf4j-api")

        assertNull(version)
        assertFalse(called)
    }

    @Test
    fun `integration test against Maven Central`() {
        // This test requires internet access.
        val client = RepositoryClient(connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
        val repo = ProjectRepository.MAVEN_CENTRAL
        val version = client.getLatestVersion(repo, "org.slf4j", "slf4j-api")

        assertNotNull(version)
        // slf4j-api 2.0.x is current, so we just check it starts with a number
        assertTrue(version.first().isDigit())
    }

    private fun repositoryClientReturning(
        code: Int = 200,
        body: String,
        trustedCredentialHosts: Set<String> = emptySet(),
        onRequest: (path: String?, authorization: String?) -> Unit = { _, _ -> }
    ): RepositoryClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            onRequest(request.url.encodedPath, request.header("Authorization"))

            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "ERROR")
                .body(body.toResponseBody("application/xml".toMediaType()))
                .build()
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        return RepositoryClient(client = httpClient, trustedCredentialHosts = trustedCredentialHosts)
    }
}
