package dev.devdigi.music.features.library.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryDestinationTest {
    @Test
    fun noSelectionShowsRecentAlbums() {
        assertEquals(
            LibraryDestination.RecentAlbums,
            libraryDestination(
                selectedAlbumId = null,
            ),
        )
    }

    @Test
    fun selectionShowsExactOpaqueAlbumId() {
        val albumId =
            "album/id + 42"

        assertEquals(
            LibraryDestination.AlbumDetails(
                albumId = albumId,
            ),
            libraryDestination(
                selectedAlbumId = albumId,
            ),
        )
    }
}
