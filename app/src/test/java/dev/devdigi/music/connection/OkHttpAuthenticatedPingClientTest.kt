package dev.devdigi.music.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class OkHttpAuthenticatedPingClientTest {
    private val successJson =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "version": "1.16.1",
            "openSubsonic": true,
            "type": "navidrome",
            "serverVersion": "0.57.0"
          }
        }
        """.trimIndent()

    private val authenticated =
        AuthResult.Authenticated(
            ServerMetadata("navidrome", "0.57.0", true),
        )

    private fun client(readTimeoutMillis: Long = 10_000L) =
        OkHttpAuthenticatedPingClient(
            readTimeoutMillis = readTimeoutMillis,
            signer =
                DefaultSubsonicAuthSigner(
                    saltSource = { "c19b2d" },
                ),
        )

    private fun parsedEndpoint(
        server: MockWebServer,
        basePath: String = "/",
    ) = (
        ServerEndpoint.parse(
            server.url(basePath).toString(),
        ) as EndpointParseResult.Valid
    ).endpoint

    private fun ping(
        server: MockWebServer,
        basePath: String = "/",
        username: String = "alice",
    ): AuthResult {
        val endpoint = parsedEndpoint(server, basePath)

        return runBlocking {
            client().ping(
                AuthCredentials.create(username, "sesame"),
                ServerProfile(endpoint),
            )
        }
    }

    private fun successResponse() =
        MockResponse
            .Builder()
            .body(successJson)
            .build()

    private fun assertSignedRequest(
        server: MockWebServer,
        expectedPath: String,
        username: String,
    ) {
        val request =
            requireNotNull(
                server.takeRequest(1, TimeUnit.SECONDS),
            ) { "Expected an authenticated ping request" }

        val url = request.url

        assertEquals("GET", request.method)
        assertEquals(expectedPath, url.encodedPath)

        val expectedParameters =
            setOf("u", "t", "s", "v", "c", "f")

        assertEquals(expectedParameters, url.queryParameterNames)

        val values =
            mapOf(
                "u" to username,
                "t" to "26719a1196d2a940705a59634eb18eab",
                "s" to "c19b2d",
                "v" to "1.13.0",
                "c" to "devdigi-music",
                "f" to "json",
            )

        for ((parameter, value) in values) {
            assertEquals(
                listOf(value),
                url.queryParameterValues(parameter),
            )
        }

        assertFalse(url.toString().contains("sesame"))
    }

    @Test
    fun rootEndpointUsesAuthenticatedSubsonicPing() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(successResponse())

            assertEquals(authenticated, ping(server))

            assertSignedRequest(
                server,
                expectedPath = "/rest/ping.view",
                username = "alice",
            )

            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun rejectsHttpErrorEvenWithValidSuccessEnvelope() {
        for (status in listOf(400, 401, 404, 500, 502, 503)) {
            MockWebServer().use { server ->
                server.start()

                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(status)
                        .body(successJson)
                        .build(),
                )

                assertEquals(
                    AuthResult.AuthProtocolError,
                    ping(server),
                )

                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun rejectsCrossOriginRedirectWithoutForwardingCredentials() {
        for (status in listOf(302, 307, 308)) {
            MockWebServer().use { original ->
                MockWebServer().use { target ->
                    original.start()
                    target.start()

                    original.enqueue(
                        MockResponse
                            .Builder()
                            .code(status)
                            .addHeader(
                                "Location",
                                target.url("/capture").toString(),
                            ).build(),
                    )

                    assertEquals(
                        AuthResult.AuthProtocolError,
                        ping(original),
                    )

                    assertSignedRequest(
                        original,
                        expectedPath = "/rest/ping.view",
                        username = "alice",
                    )

                    assertEquals(1, original.requestCount)

                    assertNull(
                        target.takeRequest(
                            200,
                            TimeUnit.MILLISECONDS,
                        ),
                    )

                    assertEquals(0, target.requestCount)
                }
            }
        }
    }

    @Test
    fun preservesEndpointBasePaths() {
        val cases =
            listOf(
                "/navidrome/" to "/navidrome/rest/ping.view",
                "/library%20archive/" to
                    "/library%20archive/rest/ping.view",
            )

        val usernames =
            listOf(
                "Álice ス",
                " A\u0301lice ス ",
            )

        // The second username has surrounding whitespace
        // and a decomposed Unicode accent.
        assertFalse(usernames[0] == usernames[1].trim())

        for ((basePath, expectedPath) in cases) {
            for (username in usernames) {
                MockWebServer().use { server ->
                    server.start()
                    server.enqueue(successResponse())

                    assertEquals(
                        authenticated,
                        ping(
                            server,
                            basePath = basePath,
                            username = username,
                        ),
                    )

                    assertSignedRequest(
                        server,
                        expectedPath = expectedPath,
                        username = username,
                    )

                    assertEquals(1, server.requestCount)
                }
            }
        }
    }

    @Test
    fun cancellationFinishesPromptlyWhileWaitingForHeaders() =
        runBlocking {
            MockWebServer().use { server ->
                server.start()

                server.enqueue(
                    MockResponse
                        .Builder()
                        .headersDelay(4, TimeUnit.SECONDS)
                        .body(successJson)
                        .build(),
                )

                val endpoint = parsedEndpoint(server)

                val pending =
                    async(Dispatchers.IO) {
                        client().ping(
                            AuthCredentials.create("alice", "sesame"),
                            ServerProfile(endpoint),
                        )
                    }

                requireNotNull(
                    server.takeRequest(2, TimeUnit.SECONDS),
                ) {
                    "The server never received the request"
                }

                pending.cancel()

                withTimeout(900) {
                    pending.join()
                }

                assertTrue(pending.isCancelled)
                assertEquals(1, server.requestCount)
            }
        }

    @Test
    fun mapsFailureCodeMatrixThroughHttp() {
        val cases =
            listOf(
                10 to AuthResult.AuthProtocolError,
                20 to AuthResult.IncompatibleServer,
                30 to AuthResult.IncompatibleServer,
                40 to AuthResult.InvalidCredentials,
                41 to AuthResult.UnsupportedAuthentication,
                42 to AuthResult.UnsupportedAuthentication,
                43 to AuthResult.AuthProtocolError,
                44 to AuthResult.AuthProtocolError,
            )

        for ((code, expected) in cases) {
            MockWebServer().use { server ->
                server.start()

                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(
                            """
                            {
                              "subsonic-response": {
                                "status": "failed",
                                "version": "1.16.1",
                                "error": {
                                  "code": $code,
                                  "message": "Test error"
                                }
                              }
                            }
                            """.trimIndent(),
                        ).build(),
                )

                assertEquals(
                    "Unexpected result for Subsonic code $code",
                    expected,
                    ping(server),
                )

                assertSignedRequest(
                    server,
                    expectedPath = "/rest/ping.view",
                    username = "alice",
                )

                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun connectionRefusedMapsToNetworkError() {
        val server = MockWebServer()
        server.start()

        val endpoint = parsedEndpoint(server)

        server.close()

        val result =
            runBlocking {
                withTimeout(5_000) {
                    client().ping(
                        AuthCredentials.create("alice", "sesame"),
                        ServerProfile(endpoint),
                    )
                }
            }

        assertEquals(AuthResult.NetworkError, result)
    }

    @Test
    fun delayedHeadersTimeoutMapsToNetworkError() {
        MockWebServer().use { server ->
            server.start()

            server.enqueue(
                MockResponse
                    .Builder()
                    .headersDelay(2, TimeUnit.SECONDS)
                    .body(successJson)
                    .build(),
            )

            val endpoint = parsedEndpoint(server)

            val result =
                runBlocking {
                    withTimeout(5_000) {
                        client(readTimeoutMillis = 400L).ping(
                            AuthCredentials.create("alice", "sesame"),
                            ServerProfile(endpoint),
                        )
                    }
                }

            assertEquals(AuthResult.NetworkError, result)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun rejectsOversizedUtf8Response() {
        val cases: List<Pair<Int, AuthResult>> =
            listOf(
                5 to authenticated,
                33_000 to AuthResult.AuthProtocolError,
            )

        for ((characters, expected) in cases) {
            val padding = "é".repeat(characters)

            val json =
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "openSubsonic": true,
                    "type": "navidrome",
                    "serverVersion": "0.57.0",
                    "padding": "$padding"
                  }
                }
                """.trimIndent()

            val byteCount =
                json.toByteArray(Charsets.UTF_8).size

            // Both responses remain below the parser's
            // character limit. Only one exceeds the
            // transport's byte limit.
            assertTrue(json.length < 65_536)

            if (characters == 5) {
                assertTrue(byteCount < 65_536)
            } else {
                assertTrue(byteCount > 65_536)
            }

            MockWebServer().use { server ->
                server.start()

                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(json)
                        .build(),
                )

                assertEquals(
                    "Unexpected result for $byteCount bytes",
                    expected,
                    ping(server),
                )

                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun cancellationWhileResponseBodyIsDelayedFinishesPromptly() =
        runBlocking {
            MockWebServer().use { server ->
                server.start()

                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(successJson)
                        .bodyDelay(3, TimeUnit.SECONDS)
                        .build(),
                )

                val endpoint = parsedEndpoint(server)

                val pending =
                    async(Dispatchers.IO) {
                        client(
                            readTimeoutMillis = 10_000L,
                        ).ping(
                            AuthCredentials.create(
                                "alice",
                                "sesame",
                            ),
                            ServerProfile(endpoint),
                        )
                    }

                requireNotNull(
                    server.takeRequest(
                        2,
                        TimeUnit.SECONDS,
                    ),
                ) {
                    "The server never received the request"
                }

                // Allow the immediate response headers
                // to arrive while the body remains delayed.
                delay(300)

                assertTrue(
                    "The request should still be pending",
                    pending.isActive,
                )

                pending.cancel()

                withTimeout(900) {
                    pending.join()
                }

                assertTrue(pending.isCancelled)
                assertEquals(1, server.requestCount)
            }
        }

    @Test
    fun successiveRequestsUseDifferentSaltsAndTokens() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(successResponse())
            server.enqueue(successResponse())

            val salts =
                ArrayDeque(listOf("a1b2c3", "d4e5f6"))

            val signingClient =
                OkHttpAuthenticatedPingClient(
                    signer =
                        DefaultSubsonicAuthSigner(
                            saltSource = {
                                salts.removeFirst()
                            },
                        ),
                )

            val endpoint = parsedEndpoint(server)

            val credentials =
                AuthCredentials.create("alice", "sesame")

            val profile = ServerProfile(endpoint)

            runBlocking {
                repeat(2) {
                    assertEquals(
                        authenticated,
                        signingClient.ping(
                            credentials,
                            profile,
                        ),
                    )
                }
            }

            val requests =
                List(2) {
                    requireNotNull(
                        server.takeRequest(
                            1,
                            TimeUnit.SECONDS,
                        ),
                    )
                }

            val observedSalts =
                requests.map {
                    it.url.queryParameter("s")
                }

            val tokens =
                requests.map {
                    requireNotNull(
                        it.url.queryParameter("t"),
                    )
                }

            assertEquals(
                listOf("a1b2c3", "d4e5f6"),
                observedSalts,
            )

            assertTrue(tokens[0] != tokens[1])

            for (token in tokens) {
                assertTrue(
                    token.matches(
                        Regex("[0-9a-f]{32}"),
                    ),
                )
            }

            for (request in requests) {
                assertEquals(
                    "/rest/ping.view",
                    request.url.encodedPath,
                )

                assertFalse(
                    request.url
                        .toString()
                        .contains("sesame"),
                )

                assertFalse(
                    request.url.queryParameterNames
                        .contains("p"),
                )
            }

            assertTrue(salts.isEmpty())
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun totalCallTimeoutBoundsDelayedResponseBody() {
        MockWebServer().use { server ->
            server.start()

            server.enqueue(
                MockResponse
                    .Builder()
                    .body(successJson)
                    .bodyDelay(4, TimeUnit.SECONDS)
                    .build(),
            )

            val endpoint = parsedEndpoint(server)

            val boundedClient =
                OkHttpAuthenticatedPingClient(
                    signer =
                        DefaultSubsonicAuthSigner(
                            saltSource = { "c19b2d" },
                        ),
                    readTimeoutMillis = 5_000L,
                    callTimeoutMillis = 1_500L,
                )

            val result =
                runBlocking {
                    withTimeout(3_000) {
                        boundedClient.ping(
                            AuthCredentials.create(
                                "alice",
                                "sesame",
                            ),
                            ServerProfile(endpoint),
                        )
                    }
                }

            assertEquals(
                AuthResult.NetworkError,
                result,
            )

            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun malformedOrInvalidEnvelopeFailsClosed() {
        val cases =
            listOf(
                "malformed JSON" to "{broken",
                "empty response" to "",
                "incorrect envelope" to
                    """{"subsonic-response":[]}""",
            )

        for ((description, payload) in cases) {
            MockWebServer().use { server ->
                server.start()

                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(payload)
                        .build(),
                )

                assertEquals(
                    description,
                    AuthResult.AuthProtocolError,
                    ping(server),
                )

                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun rejectsInvalidUtf8BeforeParsing() {
        val marker = "navidrome"

        assertEquals(
            1,
            Regex(marker).findAll(successJson).count(),
        )

        val validReplacement =
            successJson.replace(
                marker,
                "navi\uFFFDdrome",
            )

        // A decoder that silently replaces malformed
        // bytes could authenticate this valid JSON.
        assertTrue(
            SubsonicResponseParser.parse(validReplacement)
                is AuthResult.Authenticated,
        )

        val malformed =
            Buffer()
                .writeUtf8(successJson.substringBefore(marker))
                .writeUtf8("navi")
                .writeByte(0xC3)
                .writeUtf8("drome")
                .writeUtf8(successJson.substringAfter(marker))

        MockWebServer().use { server ->
            server.start()

            server.enqueue(
                MockResponse
                    .Builder()
                    .body(malformed)
                    .build(),
            )

            assertEquals(
                AuthResult.AuthProtocolError,
                ping(server),
            )

            assertEquals(1, server.requestCount)
        }
    }
}
