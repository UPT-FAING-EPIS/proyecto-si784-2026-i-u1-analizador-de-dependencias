package com.depanalyzer.repository

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NvdClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: NvdClient

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        client = NvdClient(
            connectTimeoutSeconds = 5,
            readTimeoutSeconds = 5,
            apiKey = "test-api-key",
            client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build(),
            baseUrl = mockWebServer.url("/").toString().dropLast(1),
            requestDelayMs = 0
        )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }


    @Test
    fun `searchByCpe - consulta exitosa con vulnerabilidades`() {
        val mockResponse = """
            {
              "resultsPerPage": 1,
              "startIndex": 0,
              "totalResults": 1,
              "vulnerabilities": [
                {
                  "cve": {
                    "id": "CVE-2021-23463",
                    "published": "2021-06-10T12:00:00Z",
                    "lastModified": "2021-06-15T14:00:00Z",
                    "vulnStatus": "Published",
                    "descriptions": [
                      {
                        "lang": "en",
                        "value": "Buffer overflow in parser module"
                      }
                    ],
                    "metrics": {
                      "cvssMetricV3": [
                        {
                          "source": "[email protected]",
                          "type": "Primary",
                          "cvssData": {
                            "version": "3.1",
                            "vectorString": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H",
                            "baseScore": 9.8,
                            "baseSeverity": "CRITICAL"
                          },
                          "baseSeverity": "CRITICAL"
                        }
                      ]
                    },
                    "references": [
                      {
                        "url": "https://example.com/advisory",
                        "source": "[email protected]"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        val result = client.searchByCpe("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*")

        assertEquals(1, result.size)
        assertEquals("CVE-2021-23463", result[0].id)
        assertEquals("Buffer overflow in parser module", result[0].descriptions[0].value)
    }

    @Test
    fun `searchByCpe - parseCvssV3Score y vector correctamente`() {
        val mockResponse = """
            {
              "resultsPerPage": 1,
              "startIndex": 0,
              "totalResults": 1,
              "vulnerabilities": [
                {
                  "cve": {
                    "id": "CVE-2021-23463",
                    "metrics": {
                      "cvssMetricV3": [
                        {
                          "cvssData": {
                            "version": "3.1",
                            "vectorString": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H",
                            "baseScore": 9.8,
                            "baseSeverity": "CRITICAL"
                          }
                        }
                      ]
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        val result = client.searchByCpe("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*")

        assertEquals(1, result.size)
        val cve = result[0]
        assertEquals(9.8, cve.metrics?.cvssMetricV3?.get(0)?.cvssData?.baseScore)
        assertEquals(
            "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H",
            cve.metrics?.cvssMetricV3?.get(0)?.cvssData?.vectorString
        )
    }

    @Test
    fun `searchByCpe - parseMultipleDescriptions con diferentes idiomas`() {
        val mockResponse = """
            {
              "resultsPerPage": 1,
              "startIndex": 0,
              "totalResults": 1,
              "vulnerabilities": [
                {
                  "cve": {
                    "id": "CVE-2021-23463",
                    "descriptions": [
                      {
                        "lang": "en",
                        "value": "English description"
                      },
                      {
                        "lang": "es",
                        "value": "Descripción en español"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        val result = client.searchByCpe("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*")

        assertEquals(1, result.size)
        assertEquals(2, result[0].descriptions.size)
        assertEquals("en", result[0].descriptions[0].lang)
        assertEquals("es", result[0].descriptions[1].lang)
    }

    @Test
    fun `searchByCpe - parseCpeConfigurations correctamente`() {
        val mockResponse = """
            {
              "resultsPerPage": 1,
              "startIndex": 0,
              "totalResults": 1,
              "vulnerabilities": [
                {
                  "cve": {
                    "id": "CVE-2021-23463",
                    "configurations": [
                      {
                        "nodes": [
                          {
                            "operator": "OR",
                            "cpeMatch": [
                              {
                                "vulnerable": true,
                                "criteria": "cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*"
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        val result = client.searchByCpe("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*")

        assertEquals(1, result.size)
        assertNotNull(result[0].configurations)
        assertEquals(1, result[0].configurations!!.size)
        assertTrue(result[0].configurations!![0].nodes[0].cpeMatch[0].vulnerable)
    }

    @Test
    fun `searchByCpe - parseReferences correctamente`() {
        val mockResponse = """
            {
              "resultsPerPage": 1,
              "startIndex": 0,
              "totalResults": 1,
              "vulnerabilities": [
                {
                  "cve": {
                    "id": "CVE-2021-23463",
                    "references": [
                      {
                        "url": "https://example.com/advisory",
                        "source": "[email protected]",
                        "tags": ["Vendor Advisory", "Third Party Advisory"]
                      },
                      {
                        "url": "https://nvd.nist.gov/vuln/detail/CVE-2021-23463",
                        "source": "[email protected]"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        val result = client.searchByCpe("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*")

        assertEquals(1, result.size)
        assertEquals(2, result[0].references.size)
        assertEquals("https://example.com/advisory", result[0].references[0].url)
        assertEquals(2, result[0].references[0].tags?.size)
    }

    @Test
    fun `searchByCpe - authenticatesWithBearerToken en header`() {
        val mockResponse = """
            {
              "resultsPerPage": 0,
              "startIndex": 0,
              "totalResults": 0,
              "vulnerabilities": []
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        client.searchByCpe("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*")

        val request = mockWebServer.takeRequest()
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"))
    }

    @Test
    fun `searchByCpe - handlesCvssV2Fallback para CVEs antiguos`() {
        val mockResponse = """
            {
              "resultsPerPage": 1,
              "startIndex": 0,
              "totalResults": 1,
              "vulnerabilities": [
                {
                  "cve": {
                    "id": "CVE-2010-12345",
                    "metrics": {
                      "cvssMetricV2": [
                        {
                          "cvssData": {
                            "version": "2.0",
                            "vectorString": "AV:N/AC:L/Au:N/C:C/I:C/A:C",
                            "baseScore": 10.0
                          },
                          "baseSeverity": "HIGH"
                        }
                      ]
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        val result = client.searchByCpe("cpe:2.3:a:apache:struts:1.2.0:*:*:*:*:*:*:*")

        assertEquals(1, result.size)
        assertNotNull(result[0].metrics?.cvssMetricV2)
        assertEquals(10.0, result[0].metrics?.cvssMetricV2?.get(0)?.cvssData?.baseScore)
    }

    @Test
    fun `searchByCpe - handlesEmptyResults correctamente`() {
        val mockResponse = """
            {
              "resultsPerPage": 0,
              "startIndex": 0,
              "totalResults": 0,
              "vulnerabilities": []
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        val result = client.searchByCpe("cpe:2.3:a:unknownvendor:unknownproduct:1.0.0:*:*:*:*:*:*:*")

        assertEquals(0, result.size)
    }

    @Test
    fun `searchByCpe - handleRate429WithExponentialBackoff`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(429))

        val mockResponse = """
            {
              "resultsPerPage": 1,
              "startIndex": 0,
              "totalResults": 1,
              "vulnerabilities": [
                {
                  "cve": {
                    "id": "CVE-2021-23463"
                  }
                }
              ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        val result = client.searchByCpe("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*")

        assertEquals(1, result.size)
        assertEquals("CVE-2021-23463", result[0].id)
    }

    @Test
    fun `searchByCpe - returnsEmptyAfterMaxRetries`() {
        repeat(3) {
            mockWebServer.enqueue(MockResponse().setResponseCode(429))
        }

        val result = client.searchByCpe("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*")

        assertEquals(0, result.size)
    }

    @Test
    fun `searchByCpe - handles500Error gracefully`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = client.searchByCpe("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*")

        assertEquals(0, result.size)
    }

    @Test
    fun `searchByCpe - handles403Error gracefully`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        val result = client.searchByCpe("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*")

        assertEquals(0, result.size)
    }

    @Test
    fun `searchByCpe - handlesMalformedJson gracefully`() {
        mockWebServer.enqueue(MockResponse().setBody("{invalid json}"))

        val result = client.searchByCpe("cpe:2.3:a:apache:commons_lang:3.14.0:*:*:*:*:*:*:*")

        assertEquals(0, result.size)
    }

    @Test
    fun `searchByKeyword - fallbackWhenCpeFails`() {
        val mockResponse = """
            {
              "resultsPerPage": 1,
              "startIndex": 0,
              "totalResults": 1,
              "vulnerabilities": [
                {
                  "cve": {
                    "id": "CVE-2021-23463"
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        val result = client.searchByKeyword("commons-lang3")

        assertEquals(1, result.size)
        assertEquals("CVE-2021-23463", result[0].id)
    }

    @Test
    fun `getCveById - retrievesSpecificCveSuccessfully`() {
        val mockResponse = """
            {
              "resultsPerPage": 1,
              "startIndex": 0,
              "totalResults": 1,
              "vulnerabilities": [
                {
                  "cve": {
                    "id": "CVE-2021-23463",
                    "descriptions": [
                      {
                        "lang": "en",
                        "value": "Specific CVE"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        val result = client.getCveById("CVE-2021-23463")

        assertNotNull(result)
        assertEquals("CVE-2021-23463", result.id)
        assertEquals("Specific CVE", result.descriptions[0].value)
    }

    @Test
    fun `getCveById - returnsNullWhenNotFound`() {
        val mockResponse = """
            {
              "resultsPerPage": 0,
              "startIndex": 0,
              "totalResults": 0,
              "vulnerabilities": []
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse))

        val result = client.getCveById("CVE-9999-99999")

        kotlin.test.assertNull(result)
    }
}
