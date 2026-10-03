package dev.devdigi.music.features.navigation.presentation

import dev.devdigi.music.features.playback.domain.PlaybackFailure
import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstSoundPlaybackTest {
    @Test
    fun miniPlayerIsHiddenWithoutTrack() {
        assertFalse(
            firstSoundMiniPlayerPolicy(
                PlaybackState(),
            ).visible,
        )
    }

    @Test
    fun playingTrackOffersPause() {
        val policy =
            firstSoundMiniPlayerPolicy(
                state(
                    PlaybackPhase.PLAYING,
                ),
            )

        assertTrue(policy.visible)
        assertEquals(
            FirstSoundMiniPlayerAction.PAUSE,
            policy.action,
        )
    }

    @Test
    fun pausedTrackOffersResume() {
        assertEquals(
            FirstSoundMiniPlayerAction.RESUME,
            firstSoundMiniPlayerPolicy(
                state(
                    PlaybackPhase.PAUSED,
                ),
            ).action,
        )
    }

    @Test
    fun stoppedTrackOffersPlayThroughExistingRetryPath() {
        assertEquals(
            FirstSoundMiniPlayerAction.PLAY,
            firstSoundMiniPlayerPolicy(
                state(
                    PlaybackPhase.STOPPED,
                ),
            ).action,
        )
    }

    @Test
    fun failedTrackOffersRetry() {
        assertEquals(
            FirstSoundMiniPlayerAction.RETRY,
            firstSoundMiniPlayerPolicy(
                state(
                    phase =
                        PlaybackPhase.ERROR,
                    failure =
                        PlaybackFailure
                            .NETWORK_OR_SOURCE,
                ),
            ).action,
        )
    }

    @Test
    fun preparingTrackRemainsVisibleWithoutFakeDirectAction() {
        val policy =
            firstSoundMiniPlayerPolicy(
                state(
                    PlaybackPhase.PREPARING,
                ),
            )

        assertTrue(policy.visible)
        assertNull(policy.action)
    }

    @Test
    fun safeMetadataFallsBackWithoutInventingArtist() {
        val policy =
            firstSoundMiniPlayerPolicy(
                PlaybackState(
                    phase =
                        PlaybackPhase.PLAYING,
                    track =
                        PlaybackTrack(
                            id = "opaque-track",
                            title = " ",
                            artist = " ",
                        ),
                ),
            )

        assertEquals(
            "Untitled track",
            policy.title,
        )
        assertNull(policy.artist)
    }

    @Test
    fun openingNowPlayingPreservesUnderlyingAlbumContext() {
        val current =
            FirstSoundNavigationState(
                primaryDestination =
                    FirstSoundPrimaryDestination.LIBRARY,
                selectedAlbumId =
                    "opaque-album",
            )

        val opened =
            openFirstSoundNowPlaying(
                current,
            )

        assertTrue(
            opened.nowPlayingVisible,
        )
        assertEquals(
            "opaque-album",
            opened.selectedAlbumId,
        )

        assertEquals(
            current,
            closeFirstSoundNowPlaying(
                opened,
            ),
        )
    }

    @Test
    fun restoredPlaybackDoesNotFabricateAlbumRoute() {
        val current =
            FirstSoundNavigationState(
                primaryDestination =
                    FirstSoundPrimaryDestination.HOME,
                selectedAlbumId = null,
            )

        val opened =
            openFirstSoundNowPlaying(
                current,
            )

        assertTrue(
            opened.nowPlayingVisible,
        )
        assertNull(
            opened.selectedAlbumId,
        )
    }

    @Test
    fun changingPrimaryDestinationClosesNowPlaying() {
        val current =
            FirstSoundNavigationState(
                primaryDestination =
                    FirstSoundPrimaryDestination.HOME,
                selectedAlbumId = null,
                nowPlayingVisible = true,
            )

        val changed =
            selectFirstSoundPrimary(
                state = current,
                destination =
                    FirstSoundPrimaryDestination.SEARCH,
            )

        assertFalse(
            changed.nowPlayingVisible,
        )
    }

    private fun state(
        phase: PlaybackPhase,
        failure: PlaybackFailure? = null,
    ): PlaybackState =
        PlaybackState(
            phase = phase,
            track =
                PlaybackTrack(
                    id = "track-1",
                    title = "Synthetic Track",
                    artist =
                        "Synthetic Artist",
                ),
            failure = failure,
        )
}
