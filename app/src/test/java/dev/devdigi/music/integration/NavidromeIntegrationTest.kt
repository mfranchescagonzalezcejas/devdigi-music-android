package dev.devdigi.music.integration

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.AuthResult
import dev.devdigi.music.connection.DefaultSubsonicAuthSigner
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.OkHttpAuthenticatedPingClient
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.connection.ServerProfile
import dev.devdigi.music.features.library.data.remote.AlbumDetailsRemoteResult
import dev.devdigi.music.features.library.data.remote.OkHttpAlbumDetailsRemoteDataSource
import dev.devdigi.music.features.library.data.remote.OkHttpRecentAlbumsRemoteDataSource
import dev.devdigi.music.features.library.data.remote.RecentAlbumsRemoteResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NavidromeIntegrationTest {
    @Test
    fun syntheticServerAuthenticatesThroughRealApplicationBoundary() {
        assumeTrue(
            System.getProperty("navidrome.integration") == "true",
        )

        val endpointValue =
            requiredEnv("NAVIDROME_INTEGRATION_URL")
        val username =
            requiredEnv("NAVIDROME_INTEGRATION_USERNAME")
        val password =
            requiredEnv("NAVIDROME_INTEGRATION_PASSWORD")

        val parsed =
            ServerEndpoint.parse(endpointValue)

        check(parsed is EndpointParseResult.Valid) {
            "Synthetic integration endpoint was rejected."
        }

        val profile =
            ServerProfile(parsed.endpoint)

        val signer =
            DefaultSubsonicAuthSigner()

        val authenticated =
            runBlocking {
                OkHttpAuthenticatedPingClient(
                    signer = signer,
                ).ping(
                    credentials =
                        AuthCredentials.create(
                            username = username,
                            password = password,
                        ),
                    profile = profile,
                )
            }

        assertTrue(
            authenticated is AuthResult.Authenticated,
        )

        val rejected =
            runBlocking {
                OkHttpAuthenticatedPingClient(
                    signer = signer,
                ).ping(
                    credentials =
                        AuthCredentials.create(
                            username = username,
                            password = "$password-invalid",
                        ),
                    profile = profile,
                )
            }

        assertEquals(
            AuthResult.InvalidCredentials,
            rejected,
        )

        val account =
            ServerAccountIdentity(
                endpoint = parsed.endpoint,
                username = username,
            )

        val recent =
            runBlocking {
                OkHttpRecentAlbumsRemoteDataSource(
                    signer = signer,
                ).loadRecentAlbums(
                    account = account,
                    credentials =
                        AuthCredentials.create(
                            username = username,
                            password = password,
                        ),
                )
            }

        check(
            recent is RecentAlbumsRemoteResult.Success,
        ) {
            "Recent Albums application boundary failed."
        }

        val album =
            recent.albums.single {
                it.title == SYNTHETIC_ALBUM
            }

        assertTrue(
            "Navidrome album ID must be opaque and non-blank.",
            album.id.isNotBlank(),
        )

        val detailsResult =
            runBlocking {
                OkHttpAlbumDetailsRemoteDataSource(
                    signer = signer,
                ).loadAlbum(
                    account = account,
                    credentials =
                        AuthCredentials.create(
                            username = username,
                            password = password,
                        ),
                    albumId = album.id,
                )
            }

        check(
            detailsResult is AlbumDetailsRemoteResult.Success,
        ) {
            "Album Details application boundary failed."
        }

        val details = detailsResult.album

        assertEquals(
            album.id,
            details.id,
        )

        assertEquals(
            SYNTHETIC_ALBUM,
            details.title,
        )

        assertEquals(
            listOf(
                "Synthetic Track A",
                "Synthetic Track B",
            ),
            details.tracks.map { it.title },
        )

        assertEquals(
            listOf(1, 2),
            details.tracks.map { it.trackNumber },
        )

        assertEquals(
            2,
            details.tracks
                .map { it.id }
                .distinct()
                .size,
        )

        assertTrue(
            "Navidrome track IDs must be opaque and non-blank.",
            details.tracks.all {
                it.id.isNotBlank()
            },
        )
    }

    private fun requiredEnv(name: String): String =
        requireNotNull(
            System.getenv(name),
        ) {
            "$name is required by the integration runner."
        }.also {
            require(it.isNotBlank()) {
                "$name must not be blank."
            }
        }

    private companion object {
        const val SYNTHETIC_ALBUM =
            "DevDigi Synthetic Album"
    }
}
