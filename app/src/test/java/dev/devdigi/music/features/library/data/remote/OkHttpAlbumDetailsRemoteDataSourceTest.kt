package dev.devdigi.music.features.library.data.remote

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.DefaultSubsonicAuthSigner
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.library.domain.AlbumDetails
import dev.devdigi.music.features.library.domain.AlbumTrack
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.TimeUnit

class OkHttpAlbumDetailsRemoteDataSourceTest {
    private val successJson =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "version": "1.16.1",
            "album": {
              "id": "album/id + 42",
              "name": "Synthetic Album",
              "artist": "Example Artist",
              "coverArt": "cover-1",
              "song": [
                {
                  "id": "track-2",
                  "title": "Synthetic Track Two",
                  "artist": "Example Artist",
                  "track": 2,
                  "discNumber": 1,
                  "duration": 182,
                  "coverArt": "track-cover-2"
                },
                {
                  "id": "track-1",
                  "title": "Synthetic Track One",
                  "track": 1
                }
              ]
            }
          }
        }
        """.trimIndent()

    private fun client() =
        OkHttpAlbumDetailsRemoteDataSource(
            signer =
                DefaultSubsonicAuthSigner(
                    saltSource = { "c19b2d" },
                ),
        )

    private fun account(
        server: MockWebServer,
        basePath: String = "/",
        username: String = "alice",
    ): ServerAccountIdentity {
        val endpoint =
            (
                ServerEndpoint.parse(
                    server.url(basePath).toString(),
                ) as EndpointParseResult.Valid
            ).endpoint

        return ServerAccountIdentity(
            endpoint = endpoint,
            username = username,
        )
    }

    @Test
    fun loadsAlbumUsingAuthenticatedGetAlbumRequest() {
        val cases =
            listOf(
                "/" to
                    "/rest/getAlbum.view",
                "/navidrome/" to
                    "/navidrome/rest/getAlbum.view",
            )

        cases.forEach { (basePath, expectedPath) ->
            MockWebServer().use { server ->
                server.start()

                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(successJson)
                        .build(),
                )

                val result =
                    runBlocking {
                        client().loadAlbum(
                            account =
                                account(
                                    server,
                                    basePath,
                                ),
                            credentials =
                                AuthCredentials.create(
                                    "alice",
                                    "sesame",
                                ),
                            albumId =
                                "album/id + 42",
                        )
                    }

                assertEquals(
                    AlbumDetailsRemoteResult.Success(
                        AlbumDetails(
                            id = "album/id + 42",
                            title = "Synthetic Album",
                            artist = "Example Artist",
                            coverArtId = "cover-1",
                            tracks =
                                listOf(
                                    AlbumTrack(
                                        id = "track-2",
                                        title =
                                            "Synthetic Track Two",
                                        artist =
                                            "Example Artist",
                                        trackNumber = 2,
                                        discNumber = 1,
                                        durationSeconds = 182,
                                        coverArtId =
                                            "track-cover-2",
                                    ),
                                    AlbumTrack(
                                        id = "track-1",
                                        title =
                                            "Synthetic Track One",
                                        artist = null,
                                        trackNumber = 1,
                                        discNumber = null,
                                        durationSeconds = null,
                                        coverArtId = null,
                                    ),
                                ),
                        ),
                    ),
                    result,
                )

                val request =
                    requireNotNull(
                        server.takeRequest(
                            1,
                            TimeUnit.SECONDS,
                        ),
                    )

                assertEquals(
                    "GET",
                    request.method,
                )

                assertEquals(
                    expectedPath,
                    request.url.encodedPath,
                )

                assertEquals(
                    "alice",
                    request.url
                        .queryParameter("u"),
                )

                assertEquals(
                    "26719a1196d2a940705a59634eb18eab",
                    request.url
                        .queryParameter("t"),
                )

                assertEquals(
                    "c19b2d",
                    request.url
                        .queryParameter("s"),
                )

                assertEquals(
                    "1.13.0",
                    request.url
                        .queryParameter("v"),
                )

                assertEquals(
                    "devdigi-music",
                    request.url
                        .queryParameter("c"),
                )

                assertEquals(
                    "json",
                    request.url
                        .queryParameter("f"),
                )

                assertEquals(
                    "album/id + 42",
                    request.url
                        .queryParameter("id"),
                )

                assertFalse(
                    request.url
                        .toString()
                        .contains("sesame"),
                )

                assertFalse(
                    request.url
                        .queryParameterNames
                        .contains("p"),
                )
            }
        }
    }

    @Test
    fun identityMismatchFailsBeforeNetworking() {
        MockWebServer().use { server ->
            server.start()

            val result =
                runBlocking {
                    client().loadAlbum(
                        account =
                            account(
                                server,
                                username = "alice",
                            ),
                        credentials =
                            AuthCredentials.create(
                                "bob",
                                "sesame",
                            ),
                        albumId = "album-1",
                    )
                }

            assertEquals(
                AlbumDetailsRemoteResult
                    .AuthenticationRequired,
                result,
            )

            assertEquals(
                0,
                server.requestCount,
            )
        }
    }
}
