package dev.devdigi.music.features.library.data.remote

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.DefaultSubsonicAuthSigner
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.library.domain.RecentAlbum
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.TimeUnit

class OkHttpRecentAlbumsRemoteDataSourceTest {
    private val successJson =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "version": "1.16.1",
            "albumList2": {
              "album": [{
                "id": "album-1",
                "name": "Synthetic Album",
                "artist": "Example Artist",
                "coverArt": "cover-1"
              }]
            }
          }
        }
        """.trimIndent()

    private fun client() =
        OkHttpRecentAlbumsRemoteDataSource(
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
    fun loadsAlbumsUsingAuthenticatedRecentRequest() {
        val cases =
            listOf(
                "/" to "/rest/getAlbumList2.view",
                "/navidrome/" to "/navidrome/rest/getAlbumList2.view",
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
                        client().loadRecentAlbums(
                            account = account(server, basePath),
                            credentials =
                                AuthCredentials.create(
                                    "alice",
                                    "sesame",
                                ),
                        )
                    }

                assertEquals(
                    RecentAlbumsRemoteResult.Success(
                        listOf(
                            RecentAlbum(
                                id = "album-1",
                                title = "Synthetic Album",
                                artist = "Example Artist",
                                coverArtId = "cover-1",
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

                assertEquals("GET", request.method)
                assertEquals(expectedPath, request.url.encodedPath)

                assertEquals("alice", request.url.queryParameter("u"))
                assertEquals("26719a1196d2a940705a59634eb18eab", request.url.queryParameter("t"))
                assertEquals("c19b2d", request.url.queryParameter("s"))
                assertEquals("1.13.0", request.url.queryParameter("v"))
                assertEquals("devdigi-music", request.url.queryParameter("c"))
                assertEquals("json", request.url.queryParameter("f"))
                assertEquals("recent", request.url.queryParameter("type"))

                assertFalse(request.url.toString().contains("sesame"))
                assertFalse(request.url.queryParameterNames.contains("p"))
            }
        }
    }

    @Test
    fun identityMismatchFailsBeforeNetworking() {
        MockWebServer().use { server ->
            server.start()

            val result =
                runBlocking {
                    client().loadRecentAlbums(
                        account = account(server, username = "alice"),
                        credentials =
                            AuthCredentials.create(
                                "bob",
                                "sesame",
                            ),
                    )
                }

            assertEquals(
                RecentAlbumsRemoteResult.AuthenticationRequired,
                result,
            )

            assertEquals(0, server.requestCount)
        }
    }
}
