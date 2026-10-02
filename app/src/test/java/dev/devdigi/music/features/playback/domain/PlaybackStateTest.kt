package dev.devdigi.music.features.playback.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTest {
    @Test
    fun stateTransitionsNeverRemainPlayingAfterTerminalFailure() {
        val track =
            PlaybackTrack(
                id = "track-opaque",
                title = "Synthetic Track",
                artist = "Synthetic Artist",
            )

        var state = PlaybackState()

        state =
            reducePlaybackState(
                state,
                PlaybackEvent.Preparing(track),
            )
        assertEquals(
            PlaybackPhase.PREPARING,
            state.phase,
        )

        state =
            reducePlaybackState(
                state,
                PlaybackEvent.Playing,
            )
        assertEquals(
            PlaybackPhase.PLAYING,
            state.phase,
        )

        state =
            reducePlaybackState(
                state,
                PlaybackEvent.Paused,
            )
        assertEquals(
            PlaybackPhase.PAUSED,
            state.phase,
        )

        state =
            reducePlaybackState(
                state,
                PlaybackEvent.Failed(
                    PlaybackFailure.NETWORK_OR_SOURCE,
                ),
            )

        assertEquals(
            PlaybackPhase.ERROR,
            state.phase,
        )
        assertEquals(
            PlaybackFailure.NETWORK_OR_SOURCE,
            state.failure,
        )
        assertTrue(
            state.phase != PlaybackPhase.PLAYING,
        )

        state =
            reducePlaybackState(
                state,
                PlaybackEvent.Stopped,
            )

        assertEquals(
            PlaybackPhase.STOPPED,
            state.phase,
        )
        assertNull(state.failure)

        state =
            reducePlaybackState(
                state,
                PlaybackEvent.Released,
            )

        assertEquals(
            PlaybackState(),
            state,
        )
    }

    @Test
    fun playbackDomainDoesNotCarryTransportSecretsOrLocations() {
        val fieldNames =
            (
                PlaybackTrack::class.java.declaredFields +
                    PlaybackState::class.java.declaredFields
            ).map { it.name.lowercase() }

        listOf(
            "url",
            "uri",
            "endpoint",
            "username",
            "password",
            "token",
            "salt",
        ).forEach { forbidden ->
            assertFalse(
                "Playback domain must not carry $forbidden",
                fieldNames.any {
                    forbidden in it
                },
            )
        }
    }
}
