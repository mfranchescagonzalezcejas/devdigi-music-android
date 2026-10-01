package dev.devdigi.music.features.library.domain

import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class AlbumDetailsTest {
    @Test
    fun successfulAlbumDetailsKeepExactAccountAndTrackOrder() {
        val account =
            ServerAccountIdentity(
                endpoint = endpoint(),
                username = "alice",
            )

        val first =
            AlbumTrack(
                id = "track-opaque-a",
                title = "Synthetic Track A",
                artist = "Example Artist",
                trackNumber = 1,
                discNumber = 1,
                durationSeconds = 183,
                coverArtId = "cover-track-a",
            )

        val second =
            AlbumTrack(
                id = "track-opaque-b",
                title = "Synthetic Track B",
                artist = null,
                trackNumber = 2,
                discNumber = 1,
                durationSeconds = null,
                coverArtId = null,
            )

        val album =
            AlbumDetails(
                id = "album-opaque",
                title = "Synthetic Album",
                artist = "Example Artist",
                coverArtId = "cover-album",
                tracks =
                    listOf(
                        first,
                        second,
                    ),
            )

        val result =
            AlbumDetailsLoadResult.Success(
                details =
                    AccountScopedAlbumDetails(
                        account = account,
                        album = album,
                    ),
            )

        assertSame(
            account,
            result.details.account,
        )

        assertEquals(
            "album-opaque",
            result.details.album.id,
        )

        assertEquals(
            listOf(
                "track-opaque-a",
                "track-opaque-b",
            ),
            result.details.album.tracks
                .map(AlbumTrack::id),
        )
    }

    @Test
    fun trackDomainDoesNotCarryResolvedTransportSecretsOrUrls() {
        val fieldNames =
            AlbumTrack::class.java
                .declaredFields
                .map { it.name.lowercase() }
                .toSet()

        listOf(
            "url",
            "uri",
            "password",
            "token",
            "salt",
            "username",
            "endpoint",
        ).forEach { forbidden ->
            assertFalse(
                "AlbumTrack must not carry $forbidden",
                fieldNames.any {
                    forbidden in it
                },
            )
        }
    }

    @Test
    fun repositoryResultKeepsFailureKindsDistinct() {
        val outcomes =
            listOf(
                AlbumDetailsLoadResult.AuthenticationRequired,
                AlbumDetailsLoadResult.NetworkError,
                AlbumDetailsLoadResult.MalformedResponse,
                AlbumDetailsLoadResult.ServerError,
            )

        assertEquals(
            4,
            outcomes.toSet().size,
        )
    }

    private fun endpoint(): ServerEndpoint =
        (
            ServerEndpoint.parse(
                "https://music.example.com",
            ) as EndpointParseResult.Valid
        ).endpoint
}
