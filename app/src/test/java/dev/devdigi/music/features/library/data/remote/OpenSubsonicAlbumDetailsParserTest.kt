package dev.devdigi.music.features.library.data.remote

import dev.devdigi.music.features.library.domain.AlbumDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenSubsonicAlbumDetailsParserTest {
    @Test
    fun parsesAlbumAndPreservesServerTrackOrder() {
        val result =
            OpenSubsonicAlbumDetailsParser.parse(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "album": {
                      "id": "album-opaque",
                      "name": "Synthetic Album",
                      "artist": "Example Artist",
                      "coverArt": "album-cover",
                      "song": [
                        {
                          "id": "track-b",
                          "title": "Synthetic Track B",
                          "artist": "Example Artist",
                          "track": 2,
                          "discNumber": 1,
                          "duration": 185,
                          "coverArt": "track-cover-b"
                        },
                        {
                          "id": "track-a",
                          "title": "Synthetic Track A",
                          "artist": "Example Artist",
                          "track": 1,
                          "discNumber": 1,
                          "duration": 181,
                          "coverArt": "track-cover-a"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            )

        val success =
            result as AlbumDetailsParseResult.Success

        assertEquals(
            "album-opaque",
            success.album.id,
        )
        assertEquals(
            "Synthetic Album",
            success.album.title,
        )
        assertEquals(
            "Example Artist",
            success.album.artist,
        )
        assertEquals(
            "album-cover",
            success.album.coverArtId,
        )

        assertEquals(
            listOf(
                "track-b",
                "track-a",
            ),
            success.album.tracks.map { it.id },
        )

        val first =
            success.album.tracks.first()

        assertEquals(
            "Synthetic Track B",
            first.title,
        )
        assertEquals(
            "Example Artist",
            first.artist,
        )
        assertEquals(2, first.trackNumber)
        assertEquals(1, first.discNumber)
        assertEquals(185, first.durationSeconds)
        assertEquals(
            "track-cover-b",
            first.coverArtId,
        )
    }

    @Test
    fun acceptsMissingOptionalAlbumMetadataAndEmptyTracks() {
        val result =
            OpenSubsonicAlbumDetailsParser.parse(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "album": {
                      "id": "album-empty",
                      "name": "Empty Synthetic Album"
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(
            AlbumDetails(
                id = "album-empty",
                title = "Empty Synthetic Album",
                artist = null,
                coverArtId = null,
                tracks = emptyList(),
            ),
            (
                result as
                    AlbumDetailsParseResult.Success
            ).album,
        )
    }

    @Test
    fun acceptsMissingOptionalTrackMetadata() {
        val result =
            OpenSubsonicAlbumDetailsParser.parse(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "album": {
                      "id": "album-1",
                      "name": "Synthetic Album",
                      "song": [
                        {
                          "id": "track-1",
                          "title": "Synthetic Track"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            )

        val track =
            (
                result as
                    AlbumDetailsParseResult.Success
            ).album.tracks.single()

        assertNull(track.artist)
        assertNull(track.trackNumber)
        assertNull(track.discNumber)
        assertNull(track.durationSeconds)
        assertNull(track.coverArtId)
    }
}
