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
    fun artworkFallbackUsesAlbumInitial() {
        assertEquals(
            "M",
            albumArtworkFallback("midnight"),
        )

        assertEquals(
            "♪",
            albumArtworkFallback(""),
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
}
