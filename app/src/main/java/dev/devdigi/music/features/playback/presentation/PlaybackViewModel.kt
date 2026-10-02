package dev.devdigi.music.features.playback.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.playback.domain.PlaybackEngine
import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PlaybackViewModel(
    private val engineFactory: () -> PlaybackEngine,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    var state by mutableStateOf(
        PlaybackState(),
    )
        private set

    private var ownedEngine:
        PlaybackEngine? = null

    private var stateJob:
        Job? = null

    private var playJob:
        Job? = null

    private var generation = 0L

    private var activeTarget:
        PlaybackTarget? = null

    private var acceptedGeneration:
        Long? = null

    fun play(
        account: ServerAccountIdentity,
        track: PlaybackTrack,
    ) {
        generation += 1

        val requestGeneration =
            generation

        val target =
            PlaybackTarget(
                account = account,
                track = track,
            )

        activeTarget = target
        acceptedGeneration = null

        state =
            PlaybackState(
                phase =
                    PlaybackPhase.PREPARING,
                track = track,
            )

        val engine =
            engine()

        val previous =
            playJob

        playJob =
            coroutineScope.launch {
                previous?.cancelAndJoin()

                if (
                    !isCurrent(
                        requestGeneration,
                        target,
                    )
                ) {
                    return@launch
                }

                engine.play(
                    account = account,
                    track = track,
                )

                if (
                    !isCurrent(
                        requestGeneration,
                        target,
                    )
                ) {
                    return@launch
                }

                acceptedGeneration =
                    requestGeneration

                publish(
                    candidate =
                        engine.state.value,
                    requestGeneration =
                    requestGeneration,
                    target = target,
                )
            }
    }

    fun retry() {
        activeTarget?.let { target ->
            play(
                account = target.account,
                track = target.track,
            )
        }
    }

    fun pause() {
        if (
            state.phase !=
            PlaybackPhase.PLAYING
        ) {
            return
        }

        ownedEngine?.pause()
    }

    fun resume() {
        if (
            state.phase !=
            PlaybackPhase.PAUSED
        ) {
            return
        }

        ownedEngine?.resume()
    }

    fun stop() {
        val target =
            activeTarget
                ?: return

        generation += 1

        val requestGeneration =
            generation

        acceptedGeneration = null

        state =
            PlaybackState(
                phase =
                    PlaybackPhase.STOPPED,
                track = target.track,
            )

        val engine =
            ownedEngine
                ?: return

        val previous =
            playJob

        playJob =
            coroutineScope.launch {
                previous?.cancelAndJoin()

                if (
                    !isCurrent(
                        requestGeneration,
                        target,
                    )
                ) {
                    return@launch
                }

                engine.stop()

                if (
                    !isCurrent(
                        requestGeneration,
                        target,
                    )
                ) {
                    return@launch
                }

                acceptedGeneration =
                    requestGeneration

                publish(
                    candidate =
                        engine.state.value,
                    requestGeneration =
                    requestGeneration,
                    target = target,
                )
            }
    }

    fun onAccountChanged(account: ServerAccountIdentity?) {
        val target =
            activeTarget
                ?: return

        if (target.account != account) {
            clearPlayback()
        }
    }

    fun clearPlayback() {
        generation += 1

        val requestGeneration =
            generation

        acceptedGeneration = null
        activeTarget = null

        state = PlaybackState()

        val engine =
            ownedEngine
                ?: return

        val previous =
            playJob

        playJob =
            coroutineScope.launch {
                previous?.cancelAndJoin()

                if (
                    requestGeneration !=
                    generation ||
                    activeTarget != null
                ) {
                    return@launch
                }

                engine.stop()

                state = PlaybackState()
            }
    }

    override fun onCleared() {
        generation += 1
        acceptedGeneration = null
        activeTarget = null

        playJob?.cancel()
        stateJob?.cancel()

        ownedEngine?.release()
        ownedEngine = null

        super.onCleared()
    }

    private fun engine(): PlaybackEngine {
        ownedEngine?.let {
            return it
        }

        val created =
            engineFactory()

        ownedEngine = created

        stateJob =
            coroutineScope.launch {
                created.state.collect { candidate ->
                    val target =
                        activeTarget
                            ?: return@collect

                    val accepted =
                        acceptedGeneration
                            ?: return@collect

                    publish(
                        candidate =
                        candidate,
                        requestGeneration =
                        accepted,
                        target = target,
                    )
                }
            }

        return created
    }

    private fun publish(
        candidate: PlaybackState,
        requestGeneration: Long,
        target: PlaybackTarget,
    ) {
        if (
            !isCurrent(
                requestGeneration,
                target,
            ) ||
            acceptedGeneration !=
            requestGeneration ||
            candidate.track != target.track
        ) {
            return
        }

        state = candidate
    }

    private fun isCurrent(
        requestGeneration: Long,
        target: PlaybackTarget,
    ): Boolean =
        requestGeneration == generation &&
            target == activeTarget

    private val coroutineScope:
        CoroutineScope
        get() = scope ?: viewModelScope

    private data class PlaybackTarget(
        val account: ServerAccountIdentity,
        val track: PlaybackTrack,
    )

    companion object {
        fun factory(engineFactory: () -> PlaybackEngine): ViewModelProvider.Factory =
            object :
                ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    check(
                        modelClass.isAssignableFrom(
                            PlaybackViewModel::class.java,
                        ),
                    )

                    @Suppress("UNCHECKED_CAST")
                    return PlaybackViewModel(
                        engineFactory =
                        engineFactory,
                    ) as T
                }
            }
    }
}
