package dev.devdigi.music.features.playback.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import dev.devdigi.music.features.playback.domain.QueuePlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PlaybackViewModel(
    private val engineFactory: () -> QueuePlaybackEngine,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    var state by mutableStateOf(
        PlaybackState(),
    )
        private set

    private var ownedEngine:
        QueuePlaybackEngine? = null

    private var stateJob:
        Job? = null

    private var playJob:
        Job? = null

    private var accountJob:
        Job? = null

    private var currentAccount:
        ServerAccountIdentity? = null

    private var restoreAdoptionAccount:
        ServerAccountIdentity? = null

    private var generation = 0L

    private var activeTarget:
        PlaybackTarget? = null

    private var acceptedGeneration:
        Long? = null

    private var serviceTransitionsUnlocked =
        false

    fun play(
        account: ServerAccountIdentity,
        track: PlaybackTrack,
    ) {
        accountJob?.cancel()
        currentAccount = account
        restoreAdoptionAccount = null
        serviceTransitionsUnlocked = false

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

    fun playQueue(
        account: ServerAccountIdentity,
        entries: List<PlaybackTrack>,
        selectedIndex: Int,
    ) {
        val selected =
            entries.getOrNull(
                selectedIndex,
            )
                ?: return

        val requestEntries =
            entries.toList()

        accountJob?.cancel()
        currentAccount = account
        restoreAdoptionAccount = null
        serviceTransitionsUnlocked = false

        generation += 1

        val requestGeneration =
            generation

        val target =
            PlaybackTarget(
                account = account,
                track = selected,
                allowServiceTransitions =
                true,
            )

        activeTarget = target
        acceptedGeneration = null

        state =
            PlaybackState(
                phase =
                    PlaybackPhase.PREPARING,
                track = selected,
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

                engine.replaceQueue(
                    account = account,
                    entries = requestEntries,
                    selectedIndex =
                    selectedIndex,
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

    fun previous() {
        if (
            acceptedGeneration == null ||
            state.track == null
        ) {
            return
        }

        activeTarget
            ?.takeIf {
                it.allowServiceTransitions
            }?.let {
                serviceTransitionsUnlocked =
                    true
            }

        ownedEngine?.previous()
    }

    fun next() {
        if (
            acceptedGeneration == null ||
            state.track == null
        ) {
            return
        }

        activeTarget
            ?.takeIf {
                it.allowServiceTransitions
            }?.let {
                serviceTransitionsUnlocked =
                    true
            }

        ownedEngine?.next()
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
        restoreAdoptionAccount = null
        serviceTransitionsUnlocked = false

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
        currentAccount = account
        restoreAdoptionAccount = null

        val target =
            activeTarget

        val mayRestorePlayback =
            target == null

        if (
            target != null &&
            target.account != account
        ) {
            generation += 1
            acceptedGeneration = null
            activeTarget = null
            serviceTransitionsUnlocked = false
            playJob?.cancel()
            state = PlaybackState()
        }

        val engine =
            if (account != null) {
                engine()
            } else {
                ownedEngine
                    ?: run {
                        state =
                            PlaybackState()
                        return
                    }
            }

        accountJob?.cancel()

        accountJob =
            coroutineScope.launch {
                engine.reconcileAccount(
                    account,
                )

                if (
                    currentAccount !=
                    account
                ) {
                    return@launch
                }

                if (
                    activeTarget != null ||
                    !mayRestorePlayback
                ) {
                    return@launch
                }

                if (account == null) {
                    state =
                        PlaybackState()
                    return@launch
                }

                restoreAdoptionAccount =
                    account

                val immediate =
                    engine.state.value

                if (
                    immediate.track != null
                ) {
                    adoptRestoredState(
                        account = account,
                        candidate = immediate,
                        allowPlaying = true,
                    )
                } else {
                    restoreAdoptionAccount =
                        account
                }
            }
    }

    fun clearPlayback() {
        generation += 1

        val requestGeneration =
            generation

        acceptedGeneration = null
        activeTarget = null
        restoreAdoptionAccount = null
        serviceTransitionsUnlocked = false
        accountJob?.cancel()

        state =
            PlaybackState()

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

                state =
                    PlaybackState()
            }
    }

    override fun onCleared() {
        generation += 1
        acceptedGeneration = null
        activeTarget = null
        restoreAdoptionAccount = null
        serviceTransitionsUnlocked = false

        playJob?.cancel()
        accountJob?.cancel()
        stateJob?.cancel()

        ownedEngine?.release()
        ownedEngine = null

        super.onCleared()
    }

    private fun engine(): QueuePlaybackEngine {
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

                    if (target == null) {
                        val account =
                            restoreAdoptionAccount
                                ?: return@collect

                        adoptRestoredState(
                            account = account,
                            candidate = candidate,
                            allowPlaying = false,
                        )

                        return@collect
                    }

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

    private fun adoptRestoredState(
        account: ServerAccountIdentity,
        candidate: PlaybackState,
        allowPlaying: Boolean,
    ) {
        if (
            account != currentAccount ||
            restoreAdoptionAccount !=
            account ||
            activeTarget != null
        ) {
            return
        }

        val track =
            candidate.track
                ?: return

        if (
            !allowPlaying &&
            candidate.phase !=
            PlaybackPhase.PREPARING &&
            candidate.phase !=
            PlaybackPhase.PAUSED
        ) {
            return
        }

        activeTarget =
            PlaybackTarget(
                account = account,
                track = track,
                allowServiceTransitions =
                true,
            )

        acceptedGeneration =
            generation

        serviceTransitionsUnlocked =
            true

        restoreAdoptionAccount =
            null

        state =
            candidate
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
            requestGeneration
        ) {
            return
        }

        val candidateTrack =
            candidate.track
                ?: return

        if (
            candidateTrack ==
            target.track
        ) {
            if (
                target
                    .allowServiceTransitions &&
                candidate.phase !=
                PlaybackPhase.ERROR
            ) {
                serviceTransitionsUnlocked =
                    true
            }

            state =
                candidate

            return
        }

        if (
            !target
                .allowServiceTransitions ||
            !serviceTransitionsUnlocked
        ) {
            return
        }

        // The service remains the queue authority. Only the
        // current presentation target follows its transition.
        activeTarget =
            target.copy(
                track = candidateTrack,
            )

        state =
            candidate
    }

    private fun isCurrent(
        requestGeneration: Long,
        target: PlaybackTarget,
    ): Boolean =
        requestGeneration ==
            generation &&
            target ==
            activeTarget

    private val coroutineScope:
        CoroutineScope
        get() =
            scope
                ?: viewModelScope

    private data class PlaybackTarget(
        val account: ServerAccountIdentity,
        val track: PlaybackTrack,
        val allowServiceTransitions: Boolean = false,
    )

    companion object {
        fun factory(engineFactory: () -> QueuePlaybackEngine): ViewModelProvider.Factory =
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
