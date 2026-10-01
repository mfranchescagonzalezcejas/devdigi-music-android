package dev.devdigi.music.features.library.data.remote

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.DefaultSubsonicAuthSigner
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class OkHttpRecentAlbumsTransportHardeningTest {
    private val successJson =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "version": "1.16.1",
            "albumList2": {
              "album": [{
                "id": "album-1",
                "name": "Synthetic Album"
              }]
            }
          }
        }
        """.trimIndent()

    private fun client(
        readTimeoutMillis: Long = 10_000L,
        callTimeoutMillis: Long = 15_000L,
    ) = OkHttpRecentAlbumsRemoteDataSource(
        signer =
            DefaultSubsonicAuthSigner(
                saltSource = { "c19b2d" },
            ),
        readTimeoutMillis = readTimeoutMillis,
        callTimeoutMillis = callTimeoutMillis,
    )

    private fun account(server: MockWebServer): ServerAccountIdentity {
        val endpoint =
            (
                ServerEndpoint.parse(
                    server.url("/").toString(),
                ) as EndpointParseResult.Valid
            ).endpoint

        return ServerAccountIdentity(
            endpoint = endpoint,
            username = "alice",
        )
    }

    private fun load(
        server: MockWebServer,
        source: OkHttpRecentAlbumsRemoteDataSource = client(),
    ): RecentAlbumsRemoteResult =
        runBlocking {
            source.loadRecentAlbums(
                account = account(server),
                credentials =
                    AuthCredentials.create(
                        "alice",
                        "sesame",
                    ),
            )
        }

    @Test
    fun mapsProtocolAndHttpFailures() {
        val protocolCases =
            listOf(
                failedResponse(40) to
                    RecentAlbumsRemoteResult.AuthenticationRequired,
                failedResponse(99) to
                    RecentAlbumsRemoteResult.ServerError,
                "{broken" to
                    RecentAlbumsRemoteResult.MalformedResponse,
            )

        protocolCases.forEach { (payload, expected) ->
            MockWebServer().use { server ->
                server.start()

                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(payload)
                        .build(),
                )

                assertEquals(expected, load(server))
            }
        }

        val httpCases =
            listOf(
                401 to RecentAlbumsRemoteResult.AuthenticationRequired,
                403 to RecentAlbumsRemoteResult.AuthenticationRequired,
                500 to RecentAlbumsRemoteResult.ServerError,
                503 to RecentAlbumsRemoteResult.ServerError,
            )

        httpCases.forEach { (status, expected) ->
            MockWebServer().use { server ->
                server.start()

                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(status)
                        .body(successJson)
                        .build(),
                )

                assertEquals(expected, load(server))
            }
        }
    }

    @Test
    fun redirectsNeverForwardAuthenticationParameters() {
        for (status in listOf(301, 302, 307, 308)) {
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
                        RecentAlbumsRemoteResult.ServerError,
                        load(original),
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
    fun timeoutMapsToNetworkError() {
        MockWebServer().use { server ->
            server.start()

            server.enqueue(
                MockResponse
                    .Builder()
                    .headersDelay(
                        2,
                        TimeUnit.SECONDS,
                    ).body(successJson)
                    .build(),
            )

            val result =
                runBlocking {
                    withTimeout(5_000) {
                        client(
                            readTimeoutMillis = 300L,
                            callTimeoutMillis = 1_000L,
                        ).loadRecentAlbums(
                            account = account(server),
                            credentials =
                                AuthCredentials.create(
                                    "alice",
                                    "sesame",
                                ),
                        )
                    }
                }

            assertEquals(
                RecentAlbumsRemoteResult.NetworkError,
                result,
            )
        }
    }

    @Test
    fun oversizedUtf8ResponseIsRejectedBeforeParsing() {
        val padding = "é".repeat(140_000)

        val oversized =
            successJson.replace(
                "\"albumList2\": {",
                "\"padding\":\"$padding\",\"albumList2\": {",
            )

        assertTrue(
            oversized.length <
                OkHttpRecentAlbumsRemoteDataSource.MAX_RESPONSE_BYTES,
        )

        assertTrue(
            oversized.toByteArray(Charsets.UTF_8).size >
                OkHttpRecentAlbumsRemoteDataSource.MAX_RESPONSE_BYTES,
        )

        MockWebServer().use { server ->
            server.start()

            server.enqueue(
                MockResponse
                    .Builder()
                    .body(oversized)
                    .build(),
            )

            assertEquals(
                RecentAlbumsRemoteResult.MalformedResponse,
                load(server),
            )
        }
    }

    @Test
    fun malformedUtf8IsRejectedBeforeParsing() {
        val marker = "Synthetic Album"

        val malformed =
            Buffer()
                .writeUtf8(successJson.substringBefore(marker))
                .writeByte(0xC3)
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
                RecentAlbumsRemoteResult.MalformedResponse,
                load(server),
            )
        }
    }

    private fun failedResponse(code: Int): String =
        """
        {
          "subsonic-response": {
            "status": "failed",
            "version": "1.16.1",
            "error": {
              "code": $code,
              "message": "Synthetic failure"
            }
          }
        }
        """.trimIndent()
}
