package dev.devdigi.music.features.playback.data

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.playback.domain.PlaybackEvent
import dev.devdigi.music.features.playback.domain.PlaybackFailure
import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import dev.devdigi.music.features.playback.domain.QueuePlaybackEngine
import dev.devdigi.music.features.playback.domain.reducePlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

@OptIn(UnstableApi::class)
internal class Media3ControllerPlaybackEngine(
    context: Context,
) : QueuePlaybackEngine {
    private val applicationContext =
        context.applicationContext

    private val mutableState =
        MutableStateFlow(
            PlaybackState(),
        )

    override val state:
        StateFlow<PlaybackState> =
        mutableState.asStateFlow()

    private var controller:
        MediaController? = null

    private var released = false

    private var exposeControllerState = false

    private val controllerListener =
        object : MediaController.Listener {
            override fun onError(
                controller: MediaController,
                sessionError: SessionError,
            ) {
                if (
                    released ||
                    !exposeControllerState
                ) {
                    return
                }

                update(
                    PlaybackEvent.Failed(
                        if (
                            sessionError.code ==
                            SessionError
                                .ERROR_SESSION_AUTHENTICATION_EXPIRED
                        ) {
                            PlaybackFailure
                                .AUTHENTICATION_REQUIRED
                        } else {
                            PlaybackFailure.UNKNOWN
                        },
                    ),
                )
            }

            override fun onDisconnected(controller: MediaController) {
                if (this@Media3ControllerPlaybackEngine.controller === controller) {
                    this@Media3ControllerPlaybackEngine.controller =
                        null
                }

                if (
                    !released &&
                    exposeControllerState &&
                    mutableState.value.track !=
                    null
                ) {
                    update(
                        PlaybackEvent.Failed(
                            PlaybackFailure.UNKNOWN,
                        ),
                    )
                }
            }
        }

    private val playerListener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (
                    released ||
                    !exposeControllerState
                ) {
                    return
                }

                syncFromController(
                    clearWhenEmpty = false,
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                if (
                    released ||
                    !exposeControllerState
                ) {
                    return
                }

                update(
                    PlaybackEvent.Failed(
                        playbackFailure(error),
                    ),
                )
            }
        }

    private val controllerFuture =
        MediaController
            .Builder(
                applicationContext,
                SessionToken(
                    applicationContext,
                    ComponentName(
                        applicationContext,
                        PlaybackService::class.java,
                    ),
                ),
            ).setListener(
                controllerListener,
            ).buildAsync()

    override suspend fun play(
        account: ServerAccountIdentity,
        track: PlaybackTrack,
    ) {
        if (released) {
            return
        }

        exposeControllerState = true

        mutableState.value =
            PlaybackState(
                phase =
                    PlaybackPhase.PREPARING,
                track = track,
            )

        val connected =
            runCatching {
                connectedController()
            }.getOrElse {
                update(
                    PlaybackEvent.Failed(
                        PlaybackFailure.UNKNOWN,
                    ),
                )
                return
            }

        val result =
            runCatching {
                connected
                    .sendCustomCommand(
                        PlaybackSessionProtocol
                            .playTrackCommand,
                        PlaybackSessionProtocol
                            .playTrackArgs(
                                account = account,
                                track = track,
                            ),
                    ).awaitValue()
            }.getOrElse {
                update(
                    PlaybackEvent.Failed(
                        PlaybackFailure.UNKNOWN,
                    ),
                )
                return
            }

        if (
            result.resultCode !=
            SessionResult.RESULT_SUCCESS
        ) {
            update(
                PlaybackEvent.Failed(
                    resultFailure(
                        result.resultCode,
                    ),
                ),
            )
        }
    }

    override suspend fun replaceQueue(
        account: ServerAccountIdentity,
        entries: List<PlaybackTrack>,
        selectedIndex: Int,
    ) {
        if (released) {
            return
        }

        val selected =
            entries.getOrNull(
                selectedIndex,
            )
                ?: return

        val safeEntries =
            entries.toList()

        exposeControllerState = false

        mutableState.value =
            PlaybackState(
                phase =
                    PlaybackPhase.PREPARING,
                track = selected,
            )

        val connected =
            runCatching {
                connectedController()
            }.getOrElse {
                exposeControllerState = true

                update(
                    PlaybackEvent.Failed(
                        PlaybackFailure.UNKNOWN,
                    ),
                )
                return
            }

        val result =
            runCatching {
                connected
                    .sendCustomCommand(
                        PlaybackSessionProtocol
                            .replaceQueueCommand,
                        replaceQueueArgs(
                            PlaybackSessionReplaceQueueRequest(
                                account = account,
                                entries = safeEntries,
                                selectedIndex =
                                selectedIndex,
                            ),
                        ),
                    ).awaitValue()
            }.getOrElse {
                exposeControllerState = true

                update(
                    PlaybackEvent.Failed(
                        PlaybackFailure.UNKNOWN,
                    ),
                )
                return
            }

        exposeControllerState = true

        if (
            result.resultCode !=
            SessionResult.RESULT_SUCCESS
        ) {
            update(
                PlaybackEvent.Failed(
                    resultFailure(
                        result.resultCode,
                    ),
                ),
            )
        }
    }

    override suspend fun reconcileAccount(account: ServerAccountIdentity?) {
        if (released) {
            return
        }

        exposeControllerState = false

        val connected =
            runCatching {
                connectedController()
            }.getOrElse {
                mutableState.value =
                    PlaybackState()
                return
            }

        val result =
            runCatching {
                connected
                    .sendCustomCommand(
                        PlaybackSessionProtocol
                            .reconcileAccountCommand,
                        PlaybackSessionProtocol
                            .reconcileAccountArgs(
                                account,
                            ),
                    ).awaitValue()
            }.getOrElse {
                mutableState.value =
                    PlaybackState()
                return
            }

        if (
            result.resultCode !=
            SessionResult.RESULT_SUCCESS
        ) {
            mutableState.value =
                PlaybackState()
            return
        }

        if (account == null) {
            mutableState.value =
                PlaybackState()
            return
        }

        exposeControllerState = true

        syncFromController(
            clearWhenEmpty = true,
        )
    }

    override fun previous() {
        if (released) {
            return
        }

        controller
            ?.seekToPreviousMediaItem()
    }

    override fun next() {
        if (released) {
            return
        }

        controller
            ?.seekToNextMediaItem()
    }

    override fun pause() {
        if (
            released ||
            mutableState.value.phase !=
            PlaybackPhase.PLAYING
        ) {
            return
        }

        controller?.pause()
    }

    override fun resume() {
        if (
            released ||
            mutableState.value.phase !=
            PlaybackPhase.PAUSED
        ) {
            return
        }

        controller?.play()
    }

    override fun stop() {
        if (released) {
            return
        }

        mutableState.value.track?.let {
            mutableState.value =
                PlaybackState(
                    phase =
                        PlaybackPhase.STOPPED,
                    track = it,
                )
        }

        controller?.sendCustomCommand(
            PlaybackSessionProtocol
                .stopCommand,
            Bundle.EMPTY,
        )
    }

    override fun release() {
        if (released) {
            return
        }

        exposeControllerState = false

        controller?.removeListener(
            playerListener,
        )

        controller = null

        MediaController.releaseFuture(
            controllerFuture,
        )

        released = true

        mutableState.value =
            PlaybackState()
    }

    private suspend fun connectedController(): MediaController {
        controller?.let {
            return it
        }

        val connected =
            controllerFuture.awaitValue()

        if (released) {
            throw IllegalStateException(
                "Playback controller released",
            )
        }

        if (controller !== connected) {
            controller = connected
            connected.addListener(
                playerListener,
            )
        }

        return connected
    }

    private fun syncFromController(clearWhenEmpty: Boolean) {
        val connected =
            controller
                ?: return

        val item =
            connected.currentMediaItem

        if (item == null) {
            mutableState.value =
                if (clearWhenEmpty) {
                    PlaybackState()
                } else {
                    mutableState.value.track?.let {
                        PlaybackState(
                            phase =
                                PlaybackPhase.STOPPED,
                            track = it,
                        )
                    } ?: PlaybackState()
                }

            return
        }

        val track =
            item.toPlaybackTrack()
                ?: return

        val phase =
            when {
                connected.isPlaying -> {
                    PlaybackPhase.PLAYING
                }

                connected.playbackState ==
                    Player.STATE_BUFFERING -> {
                    PlaybackPhase.PREPARING
                }

                connected.playbackState ==
                    Player.STATE_READY &&
                    !connected.playWhenReady -> {
                    PlaybackPhase.PAUSED
                }

                connected.playbackState ==
                    Player.STATE_ENDED ||
                    connected.playbackState ==
                    Player.STATE_IDLE -> {
                    PlaybackPhase.STOPPED
                }

                else -> {
                    PlaybackPhase.PREPARING
                }
            }

        mutableState.value =
            PlaybackState(
                phase = phase,
                track = track,
            )
    }

    private fun MediaItem.toPlaybackTrack(): PlaybackTrack? {
        val safeId =
            mediaId
                .takeIf(String::isNotBlank)
                ?: return null

        val title =
            mediaMetadata.title
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?: mutableState.value.track
                    ?.takeIf {
                        it.id == safeId
                    }?.title
                ?: return null

        return PlaybackTrack(
            id = safeId,
            title = title,
            artist =
                mediaMetadata.artist
                    ?.toString()
                    ?.takeIf(String::isNotBlank),
        )
    }

    private fun update(event: PlaybackEvent) {
        mutableState.value =
            reducePlaybackState(
                mutableState.value,
                event,
            )
    }

    private fun resultFailure(resultCode: Int): PlaybackFailure =
        if (
            resultCode ==
            SessionResult
                .RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED
        ) {
            PlaybackFailure
                .AUTHENTICATION_REQUIRED
        } else {
            PlaybackFailure.UNKNOWN
        }
}

private val directExecutor =
    Executor { command ->
        command.run()
    }

private suspend fun <T> ListenableFuture<T>.awaitValue(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                if (!continuation.isActive) {
                    return@addListener
                }

                try {
                    continuation.resumeWith(
                        Result.success(
                            get(),
                        ),
                    )
                } catch (
                    error: ExecutionException,
                ) {
                    continuation.resumeWith(
                        Result.failure(
                            error.cause ?: error,
                        ),
                    )
                } catch (error: Throwable) {
                    continuation.resumeWith(
                        Result.failure(error),
                    )
                }
            },
            directExecutor,
        )

        continuation.invokeOnCancellation {
            cancel(false)
        }
    }
