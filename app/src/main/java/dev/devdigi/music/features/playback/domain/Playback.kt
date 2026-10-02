package dev.devdigi.music.features.playback.domain

import dev.devdigi.music.connection.ServerAccountIdentity
import kotlinx.coroutines.flow.StateFlow

data class PlaybackTrack(
    val id: String,
    val title: String,
    val artist: String?,
)

enum class PlaybackPhase {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
    STOPPED,
    ERROR,
}

enum class PlaybackFailure {
    AUTHENTICATION_REQUIRED,
    NETWORK_OR_SOURCE,
    UNSUPPORTED_PLAYBACK,
    UNKNOWN,
}

data class PlaybackState(
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val track: PlaybackTrack? = null,
    val failure: PlaybackFailure? = null,
)

sealed interface PlaybackEvent {
    data class Preparing(
        val track: PlaybackTrack,
    ) : PlaybackEvent

    data object Playing :
        PlaybackEvent

    data object Paused :
        PlaybackEvent

    data object Stopped :
        PlaybackEvent

    data class Failed(
        val failure: PlaybackFailure,
    ) : PlaybackEvent

    data object Released :
        PlaybackEvent
}

fun reducePlaybackState(
    current: PlaybackState,
    event: PlaybackEvent,
): PlaybackState =
    when (event) {
        is PlaybackEvent.Preparing -> {
            PlaybackState(
                phase = PlaybackPhase.PREPARING,
                track = event.track,
            )
        }

        PlaybackEvent.Playing -> {
            current.copy(
                phase = PlaybackPhase.PLAYING,
                failure = null,
            )
        }

        PlaybackEvent.Paused -> {
            current.copy(
                phase = PlaybackPhase.PAUSED,
                failure = null,
            )
        }

        PlaybackEvent.Stopped -> {
            current.copy(
                phase = PlaybackPhase.STOPPED,
                failure = null,
            )
        }

        is PlaybackEvent.Failed -> {
            current.copy(
                phase = PlaybackPhase.ERROR,
                failure = event.failure,
            )
        }

        PlaybackEvent.Released -> {
            PlaybackState()
        }
    }

interface PlaybackEngine {
    val state: StateFlow<PlaybackState>

    suspend fun play(
        account: ServerAccountIdentity,
        track: PlaybackTrack,
    )

    fun pause()

    fun resume()

    fun stop()

    fun release()
}
