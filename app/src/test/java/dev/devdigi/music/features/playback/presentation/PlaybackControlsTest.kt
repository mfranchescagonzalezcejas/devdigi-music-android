package dev.devdigi.music.features.playback.presentation

import androidx.compose.ui.unit.dp
import dev.devdigi.music.features.playback.domain.PlaybackFailure
import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlsTest {
    @Test
    fun playingOffersPauseAndStop() {
        val policy =
            playbackControlPolicy(
                state(PlaybackPhase.PLAYING),
            )

        assertTrue(policy.visible)
        assertTrue(policy.pause)
        assertTrue(policy.stop)
        assertFalse(policy.resume)
        assertFalse(policy.retry)
    }

    @Test
    fun pausedOffersResumeAndStop() {
        val policy =
            playbackControlPolicy(
                state(PlaybackPhase.PAUSED),
            )

        assertTrue(policy.resume)
        assertTrue(policy.stop)
        assertFalse(policy.pause)
        assertFalse(policy.retry)
    }

    @Test
    fun errorOffersSafeRetryAndStop() {
        val policy =
            playbackControlPolicy(
                state(
                    phase = PlaybackPhase.ERROR,
                    failure =
                        PlaybackFailure
                            .UNSUPPORTED_PLAYBACK,
                ),
            )

        assertTrue(policy.retry)
        assertTrue(policy.stop)
        assertFalse(policy.pause)
        assertFalse(policy.resume)
    }

    @Test
    fun controlsRemainHiddenWithoutTrack() {
        val policy =
            playbackControlPolicy(
                PlaybackState(),
            )

        assertFalse(policy.visible)
    }

    @Test
    fun controlsRespondToWidthNotDeviceType() {
        assertTrue(
            playbackControlsStacked(360.dp),
        )

        assertFalse(
            playbackControlsStacked(700.dp),
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
                    artist = "Synthetic Artist",
                ),
            failure = failure,
        )
}
