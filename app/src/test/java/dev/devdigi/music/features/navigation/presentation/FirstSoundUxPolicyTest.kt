package dev.devdigi.music.features.navigation.presentation

import androidx.compose.ui.unit.dp
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstSoundUxPolicyTest {
    @Test
    fun sameExactAuthenticatedIdentityPreservesNavigation() {
        val identity =
            identity(
                endpoint =
                    "https://music.example",
                username = "listener-a",
            )

        val state =
            FirstSoundNavigationState(
                primaryDestination =
                    FirstSoundPrimaryDestination.LIBRARY,
                selectedAlbumId =
                    "opaque-album",
                nowPlayingVisible = true,
            )

        assertEquals(
            state,
            firstSoundNavigationAfterAccountChange(
                state = state,
                previousIdentity = identity,
                currentIdentity = identity,
                authenticated = true,
            ),
        )
    }

    @Test
    fun usernameChangeResetsAccountScopedNavigation() {
        val previous =
            identity(
                endpoint =
                    "https://music.example",
                username = "listener-a",
            )

        val current =
            identity(
                endpoint =
                    "https://music.example",
                username = "listener-b",
            )

        assertEquals(
            defaultNavigation(),
            firstSoundNavigationAfterAccountChange(
                state = dirtyNavigation(),
                previousIdentity = previous,
                currentIdentity = current,
                authenticated = true,
            ),
        )
    }

    @Test
    fun endpointChangeResetsAccountScopedNavigation() {
        val previous =
            identity(
                endpoint =
                    "https://one.example",
                username = "listener",
            )

        val current =
            identity(
                endpoint =
                    "https://two.example",
                username = "listener",
            )

        assertEquals(
            defaultNavigation(),
            firstSoundNavigationAfterAccountChange(
                state = dirtyNavigation(),
                previousIdentity = previous,
                currentIdentity = current,
                authenticated = true,
            ),
        )
    }

    @Test
    fun unauthenticatedSessionResetsNavigation() {
        val identity =
            identity(
                endpoint =
                    "https://music.example",
                username = "listener",
            )

        assertEquals(
            defaultNavigation(),
            firstSoundNavigationAfterAccountChange(
                state = dirtyNavigation(),
                previousIdentity = identity,
                currentIdentity = null,
                authenticated = false,
            ),
        )
    }

    @Test
    fun backFromNowPlayingReturnsToUnderlyingAlbum() {
        val current =
            FirstSoundNavigationState(
                primaryDestination =
                    FirstSoundPrimaryDestination.LIBRARY,
                selectedAlbumId =
                    "opaque-album",
                nowPlayingVisible = true,
            )

        assertEquals(
            current.copy(
                nowPlayingVisible = false,
            ),
            backFromFirstSoundNavigation(
                current,
            ),
        )
    }

    @Test
    fun backFromAlbumReturnsToOriginatingPrimaryRoot() {
        val current =
            FirstSoundNavigationState(
                primaryDestination =
                    FirstSoundPrimaryDestination.HOME,
                selectedAlbumId =
                    "opaque-album",
            )

        assertEquals(
            FirstSoundNavigationState(
                primaryDestination =
                    FirstSoundPrimaryDestination.HOME,
                selectedAlbumId = null,
            ),
            backFromFirstSoundNavigation(
                current,
            ),
        )
    }

    @Test
    fun backAtPrimaryRootDefersToAndroid() {
        assertNull(
            backFromFirstSoundNavigation(
                defaultNavigation(),
            ),
        )
    }

    @Test
    fun compactWidthUsesBottomNavigation() {
        assertEquals(
            FirstSoundPrimaryNavigationLayout.BOTTOM_BAR,
            firstSoundPrimaryNavigationLayout(
                719.dp,
            ),
        )
    }

    @Test
    fun wideWidthUsesNavigationRail() {
        assertEquals(
            FirstSoundPrimaryNavigationLayout.RAIL,
            firstSoundPrimaryNavigationLayout(
                720.dp,
            ),
        )
    }

    @Test
    fun localPlaybackTargetIsReadOnly() {
        val target =
            firstSoundPlaybackTargetPresentation()

        assertEquals(
            "This device",
            target.label,
        )

        assertFalse(
            target.interactive,
        )
    }

    @Test
    fun searchPlaceholderRemainsHonestAndNonInteractive() {
        val presentation =
            firstSoundPlaceholderPresentation(
                FirstSoundPrimaryDestination.SEARCH,
            )

        requireNotNull(presentation)

        assertEquals(
            "Search is not implemented yet.",
            presentation.message,
        )

        assertFalse(
            presentation.interactive,
        )
    }

    @Test
    fun discoverPlaceholderRemainsHonestAndNonInteractive() {
        val presentation =
            firstSoundPlaceholderPresentation(
                FirstSoundPrimaryDestination.DISCOVER,
            )

        requireNotNull(presentation)

        assertEquals(
            "Discover is not implemented yet.",
            presentation.message,
        )

        assertFalse(
            presentation.interactive,
        )
    }

    @Test
    fun miniPlayerUsesUnambiguousNowPlayingLabel() {
        assertEquals(
            "Open Now Playing",
            firstSoundOpenNowPlayingLabel(),
        )
    }

    private fun dirtyNavigation(): FirstSoundNavigationState =
        FirstSoundNavigationState(
            primaryDestination =
                FirstSoundPrimaryDestination.LIBRARY,
            selectedAlbumId =
                "opaque-album",
            nowPlayingVisible = true,
        )

    private fun defaultNavigation(): FirstSoundNavigationState =
        FirstSoundNavigationState(
            primaryDestination =
                FirstSoundPrimaryDestination.HOME,
            selectedAlbumId = null,
        )

    private fun identity(
        endpoint: String,
        username: String,
    ): ServerAccountIdentity {
        val parsed =
            ServerEndpoint.parse(
                endpoint,
            )

        assertTrue(
            parsed is EndpointParseResult.Valid,
        )

        return ServerAccountIdentity(
            endpoint =
                (parsed as EndpointParseResult.Valid)
                    .endpoint,
            username = username,
        )
    }
}
