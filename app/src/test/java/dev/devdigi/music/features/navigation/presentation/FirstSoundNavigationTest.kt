package dev.devdigi.music.features.navigation.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirstSoundNavigationTest {
    @Test
    fun initialNavigationDefaultsToHome() {
        assertEquals(
            FirstSoundNavigationState(
                primaryDestination =
                    FirstSoundPrimaryDestination.HOME,
                selectedAlbumId = null,
            ),
            firstSoundNavigationState(
                savedPrimaryDestination = null,
                selectedAlbumId = null,
            ),
        )
    }

    @Test
    fun savedPrimaryDestinationIsRestored() {
        assertEquals(
            FirstSoundPrimaryDestination.LIBRARY,
            firstSoundNavigationState(
                savedPrimaryDestination =
                    FirstSoundPrimaryDestination
                        .LIBRARY
                        .name,
                selectedAlbumId = null,
            ).primaryDestination,
        )
    }

    @Test
    fun invalidSavedDestinationFallsBackToHome() {
        assertEquals(
            FirstSoundPrimaryDestination.HOME,
            firstSoundNavigationState(
                savedPrimaryDestination =
                    "not-a-real-destination",
                selectedAlbumId = null,
            ).primaryDestination,
        )
    }

    @Test
    fun selectingPrimaryDestinationClosesAlbumDetails() {
        val current =
            FirstSoundNavigationState(
                primaryDestination =
                    FirstSoundPrimaryDestination.HOME,
                selectedAlbumId =
                    "album/id + 42",
            )

        assertEquals(
            FirstSoundNavigationState(
                primaryDestination =
                    FirstSoundPrimaryDestination.SEARCH,
                selectedAlbumId = null,
            ),
            selectFirstSoundPrimary(
                state = current,
                destination =
                    FirstSoundPrimaryDestination.SEARCH,
            ),
        )
    }

    @Test
    fun albumOpenedFromHomeKeepsHomeAsOrigin() {
        val opened =
            openFirstSoundAlbum(
                state =
                    FirstSoundNavigationState(
                        primaryDestination =
                            FirstSoundPrimaryDestination.HOME,
                        selectedAlbumId = null,
                    ),
                albumId = "opaque/home",
            )

        assertEquals(
            FirstSoundPrimaryDestination.HOME,
            opened.primaryDestination,
        )

        assertEquals(
            "opaque/home",
            opened.selectedAlbumId,
        )

        assertEquals(
            FirstSoundNavigationState(
                primaryDestination =
                    FirstSoundPrimaryDestination.HOME,
                selectedAlbumId = null,
            ),
            backFromFirstSoundSecondary(
                opened,
            ),
        )
    }

    @Test
    fun albumOpenedFromLibraryKeepsLibraryAsOrigin() {
        val opened =
            openFirstSoundAlbum(
                state =
                    FirstSoundNavigationState(
                        primaryDestination =
                            FirstSoundPrimaryDestination.LIBRARY,
                        selectedAlbumId = null,
                    ),
                albumId = "opaque/library",
            )

        assertEquals(
            FirstSoundPrimaryDestination.LIBRARY,
            opened.primaryDestination,
        )

        assertEquals(
            "opaque/library",
            opened.selectedAlbumId,
        )

        assertEquals(
            FirstSoundPrimaryDestination.LIBRARY,
            backFromFirstSoundSecondary(
                opened,
            ).primaryDestination,
        )
    }

    @Test
    fun searchCannotOwnAlbumDetails() {
        val state =
            openFirstSoundAlbum(
                state =
                    FirstSoundNavigationState(
                        primaryDestination =
                            FirstSoundPrimaryDestination.SEARCH,
                        selectedAlbumId = null,
                    ),
                albumId = "must-not-open",
            )

        assertNull(
            state.selectedAlbumId,
        )
    }

    @Test
    fun discoverCannotRestoreAlbumDetails() {
        val state =
            firstSoundNavigationState(
                savedPrimaryDestination =
                    FirstSoundPrimaryDestination
                        .DISCOVER
                        .name,
                selectedAlbumId =
                    "stale-album",
            )

        assertEquals(
            FirstSoundPrimaryDestination.DISCOVER,
            state.primaryDestination,
        )

        assertNull(
            state.selectedAlbumId,
        )
    }

    @Test
    fun searchPlaceholderIsHonest() {
        assertEquals(
            "Search is not implemented yet.",
            firstSoundPlaceholderMessage(
                FirstSoundPrimaryDestination.SEARCH,
            ),
        )
    }

    @Test
    fun discoverPlaceholderIsHonest() {
        assertEquals(
            "Discover is not implemented yet.",
            firstSoundPlaceholderMessage(
                FirstSoundPrimaryDestination.DISCOVER,
            ),
        )
    }

    @Test
    fun implementedLibraryRootsDoNotHavePlaceholderCopy() {
        assertNull(
            firstSoundPlaceholderMessage(
                FirstSoundPrimaryDestination.HOME,
            ),
        )

        assertNull(
            firstSoundPlaceholderMessage(
                FirstSoundPrimaryDestination.LIBRARY,
            ),
        )
    }
}
