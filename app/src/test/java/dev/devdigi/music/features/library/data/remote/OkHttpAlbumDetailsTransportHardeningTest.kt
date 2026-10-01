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

class OkHttpAlbumDetailsTransportHardeningTest {
    private val successJson =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "version": "1.16.1",
            "album": {
              "id": "album-1",
              "name": "Synthetic Album",
              "song": []
            }
          }
        }
        """.trimIndent()

    private fun client(
        readTimeoutMillis: Long = 10_000L,
        callTimeoutMillis: Long = 15_000L,
    ) = OkHttpAlbumDetailsRemoteDataSource(
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
        source: OkHttpAlbumDetailsRemoteDataSource =
            client(),
    ): AlbumDetailsRemoteResult =
        runBlocking {
            source.loadAlbum(
                account = account(server),
                credentials =
                    AuthCredentials.create(
                        "alice",
                        "sesame",
                    ),
                albumId = "album-1",
            )
        }

    @Test
    fun mapsProtocolFailuresDistinctly() {
        val cases =
            listOf(
                failedResponse(40) to
                    AlbumDetailsRemoteResult
                        .AuthenticationRequired,
                failedResponse(70) to
                    AlbumDetailsRemoteResult.ServerError,
                "{broken" to
                    AlbumDetailsRemoteResult
                        .MalformedResponse,
            )

        cases.forEach { (payload, expected) ->
            MockWebServer().use { server ->
                server.start()

                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(payload)
                        .build(),
                )

                assertEquals(
                    expected,
                    load(server),
                )
            }
        }
    }

    @Test
    fun mapsHttpFailuresWithoutParsingBody() {
        val cases =
            listOf(
                401 to
                    AlbumDetailsRemoteResult
                        .AuthenticationRequired,
                403 to
                    AlbumDetailsRemoteResult
                        .AuthenticationRequired,
                500 to
                    AlbumDetailsRemoteResult.ServerError,
                503 to
                    AlbumDetailsRemoteResult.ServerError,
            )

        cases.forEach { (status, expected) ->
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
                    expected,
                    load(server),
                )
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
                                target
                                    .url("/capture")
                                    .toString(),
                            ).build(),
                    )

                    assertEquals(
                        AlbumDetailsRemoteResult
                            .ServerError,
                        load(original),
                    )

                    assertEquals(
                        1,
                        original.requestCount,
                    )

                    assertNull(
                        target.takeRequest(
                            200,
                            TimeUnit.MILLISECONDS,
                        ),
                    )

                    assertEquals(
                        0,
                        target.requestCount,
                    )
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
                        ).loadAlbum(
                            account = account(server),
                            credentials =
                                AuthCredentials.create(
                                    "alice",
                                    "sesame",
                                ),
                            albumId = "album-1",
                        )
                    }
                }

            assertEquals(
                AlbumDetailsRemoteResult.NetworkError,
                result,
            )
        }
    }

    @Test
    fun oversizedUtf8ResponseIsRejectedBeforeParsing() {
        val padding =
            "é".repeat(140_000)

        val oversized =
            successJson.replace(
                "\"album\": {",
                "\"padding\":\"$padding\",\"album\": {",
            )

        assertTrue(
            oversized.length <
                OkHttpAlbumDetailsRemoteDataSource
                    .MAX_RESPONSE_BYTES,
        )

        assertTrue(
            oversized
                .toByteArray(Charsets.UTF_8)
                .size >
                OkHttpAlbumDetailsRemoteDataSource
                    .MAX_RESPONSE_BYTES,
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
                AlbumDetailsRemoteResult
                    .MalformedResponse,
                load(server),
            )
        }
    }

    @Test
    fun malformedUtf8IsRejectedBeforeParsing() {
        val marker =
            "Synthetic Album"

        val malformed =
            Buffer()
                .writeUtf8(
                    successJson.substringBefore(
                        marker,
                    ),
                ).writeByte(0xC3)
                .writeUtf8(
                    successJson.substringAfter(
                        marker,
                    ),
                )

        MockWebServer().use { server ->
            server.start()

            server.enqueue(
                MockResponse
                    .Builder()
                    .body(malformed)
                    .build(),
            )

            assertEquals(
                AlbumDetailsRemoteResult
                    .MalformedResponse,
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
