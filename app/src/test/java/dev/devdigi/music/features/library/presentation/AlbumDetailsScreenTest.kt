package dev.devdigi.music.features.library.presentation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDetailsScreenTest {
    @Test
    fun compactWidthUsesStackedAlbumLayout() {
        val layout =
            albumDetailsLayoutSpec(
                availableWidth = 360.dp,
            )

        assertTrue(layout.stackedContent)
        assertEquals(16.dp, layout.horizontalPadding)
        assertEquals(208.dp, layout.artworkSize)
    }

    @Test
    fun mediumWidthUsesSideBySideAlbumLayout() {
        val layout =
            albumDetailsLayoutSpec(
                availableWidth = 700.dp,
            )

        assertFalse(layout.stackedContent)
        assertEquals(24.dp, layout.horizontalPadding)
        assertEquals(240.dp, layout.artworkSize)
    }

    @Test
    fun wideWidthIncreasesSpacingWithoutDeviceChecks() {
        val layout =
            albumDetailsLayoutSpec(
                availableWidth = 1_100.dp,
            )

        assertFalse(layout.stackedContent)
        assertEquals(32.dp, layout.horizontalPadding)
        assertEquals(280.dp, layout.artworkSize)
        assertEquals(1_200.dp, layout.maxContentWidth)
    }

    @Test
    fun albumTitleUsesSafeFallback() {
        assertEquals(
            "Album",
            albumTitleLabel("Album"),
        )

        assertEquals(
            "Untitled album",
            albumTitleLabel("   "),
        )
    }

    @Test
    fun artworkFallbackUsesTrimmedAlbumInitial() {
        assertEquals(
            "M",
            albumArtworkFallback(" midnight "),
        )

        assertEquals(
            "♪",
            albumArtworkFallback(""),
        )

        assertEquals(
            "♪",
            albumArtworkFallback("   "),
        )
    }

    @Test
    fun artistLabelUsesSafeFallback() {
        assertEquals(
            "Artist",
            albumArtistLabel("Artist"),
        )

        assertEquals(
            "Unknown artist",
            albumArtistLabel(null),
        )

        assertEquals(
            "Unknown artist",
            albumArtistLabel("   "),
        )
    }

    @Test
    fun trackCountLabelHandlesZeroSingularAndPlural() {
        assertEquals(
            "No tracks",
            albumTrackCountLabel(0),
        )

        assertEquals(
            "1 track",
            albumTrackCountLabel(1),
        )

        assertEquals(
            "12 tracks",
            albumTrackCountLabel(12),
        )
    }

    @Test
    fun trackTitleUsesSafeFallback() {
        assertEquals(
            "Song",
            trackTitleLabel("Song"),
        )

        assertEquals(
            "Untitled track",
            trackTitleLabel("   "),
        )
    }

    @Test
    fun trackArtistFallsBackToAlbumArtist() {
        assertEquals(
            "Track artist",
            trackArtistLabel(
                trackArtist = "Track artist",
                albumArtist = "Album artist",
            ),
        )

        assertEquals(
            "Album artist",
            trackArtistLabel(
                trackArtist = "   ",
                albumArtist = "Album artist",
            ),
        )

        assertEquals(
            "Unknown artist",
            trackArtistLabel(
                trackArtist = null,
                albumArtist = "   ",
            ),
        )
    }

    @Test
    fun trackPositionShowsAvailableServerMetadata() {
        assertEquals(
            "Disc 2 · Track 7",
            trackPositionLabel(
                trackNumber = 7,
                discNumber = 2,
                position = 0,
            ),
        )

        assertEquals(
            "Track 3",
            trackPositionLabel(
                trackNumber = 3,
                discNumber = null,
                position = 8,
            ),
        )

        assertEquals(
            "Disc 2",
            trackPositionLabel(
                trackNumber = null,
                discNumber = 2,
                position = 8,
            ),
        )

        assertEquals(
            "Track 9",
            trackPositionLabel(
                trackNumber = null,
                discNumber = null,
                position = 8,
            ),
        )
    }

    @Test
    fun invalidTrackNumbersFallBackToServerOrderPosition() {
        assertEquals(
            "Track 4",
            trackPositionLabel(
                trackNumber = 0,
                discNumber = -1,
                position = 3,
            ),
        )
    }

    @Test
    fun durationFormatsMinutesHoursAndFallbacks() {
        assertEquals(
            "0:00",
            trackDurationLabel(0),
        )

        assertEquals(
            "1:05",
            trackDurationLabel(65),
        )

        assertEquals(
            "1:01:01",
            trackDurationLabel(3_661),
        )

        assertEquals(
            "Unknown duration",
            trackDurationLabel(null),
        )

        assertEquals(
            "Unknown duration",
            trackDurationLabel(-1),
        )
    }
}
