package dev.devdigi.music.features.playback.presentation

import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.playback.domain.PlaybackEngine
import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PlaybackVmTestRequest(
    val account: ServerAccountIdentity,
    val track: PlaybackTrack,
)

internal class FakePlaybackEngine : PlaybackEngine {
    private val mutableState =
        MutableStateFlow(
            PlaybackState(),
        )

    override val state:
        StateFlow<PlaybackState> =
        mutableState.asStateFlow()

    val plays =
        mutableListOf<PlaybackVmTestRequest>()

    val reconciliations =
        mutableListOf<ServerAccountIdentity?>()

    private var activeAccount:
        ServerAccountIdentity? = null

    var pauseCalls = 0
    var resumeCalls = 0
    var stopCalls = 0

    override suspend fun play(
        account: ServerAccountIdentity,
        track: PlaybackTrack,
    ) {
        plays +=
            PlaybackVmTestRequest(
                account = account,
                track = track,
            )

        activeAccount = account

        mutableState.value =
            PlaybackState(
                phase = PlaybackPhase.PREPARING,
                track = track,
            )
    }

    override suspend fun reconcileAccount(account: ServerAccountIdentity?) {
        reconciliations += account

        if (
            activeAccount != null &&
            activeAccount != account
        ) {
            activeAccount = null
            mutableState.value =
                PlaybackState()
        }
    }

    override fun pause() {
        pauseCalls += 1

        mutableState.value =
            mutableState.value.copy(
                phase = PlaybackPhase.PAUSED,
                failure = null,
            )
    }

    override fun resume() {
        resumeCalls += 1

        mutableState.value =
            mutableState.value.copy(
                phase = PlaybackPhase.PLAYING,
                failure = null,
            )
    }

    override fun stop() {
        stopCalls += 1
        activeAccount = null

        mutableState.value =
            mutableState.value.copy(
                phase = PlaybackPhase.STOPPED,
                failure = null,
            )
    }

    override fun release() {
        activeAccount = null

        mutableState.value =
            PlaybackState()
    }

    fun emit(state: PlaybackState) {
        mutableState.value = state
    }
}

internal fun playbackVm(
    engine: FakePlaybackEngine,
    scope: CoroutineScope,
): PlaybackViewModel =
    PlaybackViewModel(
        engineFactory = { engine },
        scope = scope,
    )

internal fun playbackVmAccount(username: String): ServerAccountIdentity =
    ServerAccountIdentity(
        endpoint =
            (
                ServerEndpoint.parse(
                    "https://music.example.com",
                ) as EndpointParseResult.Valid
            ).endpoint,
        username = username,
    )

internal fun playbackVmTrack(id: String): PlaybackTrack =
    PlaybackTrack(
        id = id,
        title = "Track $id",
        artist = "Synthetic Artist",
    )
