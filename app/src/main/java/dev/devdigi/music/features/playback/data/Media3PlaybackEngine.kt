package dev.devdigi.music.features.playback.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.playback.domain.PlaybackEngine
import dev.devdigi.music.features.playback.domain.PlaybackEvent
import dev.devdigi.music.features.playback.domain.PlaybackFailure
import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import dev.devdigi.music.features.playback.domain.reducePlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

@UnstableApi
internal class Media3PlaybackEngine(
    context: Context,
    private val resolver: PlaybackSourceResolver,
    transport: OkHttpClient = playbackHttpClient(),
) : PlaybackEngine {
    private val mutableState =
        MutableStateFlow(
            PlaybackState(),
        )

    override val state:
        StateFlow<PlaybackState> =
        mutableState.asStateFlow()

    private val player =
        ExoPlayer
            .Builder(
                context.applicationContext,
            ).setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    OkHttpDataSource.Factory(
                        transport,
                    ),
                ),
            ).build()

    private var activeAccount:
        ServerAccountIdentity? = null

    private var released = false

    private val listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (released) {
                    return
                }

                if (isPlaying) {
                    update(
                        PlaybackEvent.Playing,
                    )
                } else if (
                    player.playbackState ==
                    Player.STATE_READY &&
                    !player.playWhenReady &&
                    mutableState.value.track != null &&
                    mutableState.value.phase !=
                    PlaybackPhase.ERROR
                ) {
                    update(
                        PlaybackEvent.Paused,
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (released) {
                    return
                }

                val track =
                    mutableState.value.track

                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (
                            track != null &&
                            mutableState.value.phase !=
                            PlaybackPhase.ERROR
                        ) {
                            update(
                                PlaybackEvent
                                    .Preparing(track),
                            )
                        }
                    }

                    Player.STATE_READY -> {
                        if (
                            track != null &&
                            mutableState.value.phase !=
                            PlaybackPhase.ERROR
                        ) {
                            when {
                                player.isPlaying -> {
                                    update(
                                        PlaybackEvent.Playing,
                                    )
                                }

                                !player.playWhenReady -> {
                                    update(
                                        PlaybackEvent.Paused,
                                    )
                                }
                            }
                        }
                    }

                    Player.STATE_ENDED -> {
                        activeAccount = null
                        player.clearMediaItems()

                        update(
                            PlaybackEvent.Stopped,
                        )
                    }

                    Player.STATE_IDLE -> {
                        if (
                            track != null &&
                            mutableState.value.phase !=
                            PlaybackPhase.ERROR &&
                            mutableState.value.phase !=
                            PlaybackPhase.STOPPED
                        ) {
                            update(
                                PlaybackEvent.Stopped,
                            )
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (released) {
                    return
                }

                activeAccount = null

                update(
                    PlaybackEvent.Failed(
                        playbackFailure(error),
                    ),
                )

                player.clearMediaItems()
            }
        }

    init {
        player.addListener(listener)
    }

    override suspend fun play(
        account: ServerAccountIdentity,
        track: PlaybackTrack,
    ) {
        player.stop()
        player.clearMediaItems()

        if (released) {
            return
        }

        activeAccount = account

        update(
            PlaybackEvent.Preparing(track),
        )

        when (
            val resolved =
                resolver.resolve(
                    account = account,
                    trackId = track.id,
                )
        ) {
            is StreamResolutionResult.Success -> {
                player.setMediaItem(
                    MediaItem.fromUri(
                        resolved.source.url
                            .toString(),
                    ),
                )

                player.prepare()
                player.play()
            }

            StreamResolutionResult
                .AuthenticationRequired,
            -> {
                activeAccount = null

                update(
                    PlaybackEvent.Failed(
                        PlaybackFailure
                            .AUTHENTICATION_REQUIRED,
                    ),
                )
            }

            StreamResolutionResult.InvalidRequest -> {
                activeAccount = null

                update(
                    PlaybackEvent.Failed(
                        PlaybackFailure.UNKNOWN,
                    ),
                )
            }
        }
    }

    override suspend fun reconcileAccount(account: ServerAccountIdentity?) {
        if (
            activeAccount != null &&
            activeAccount != account
        ) {
            stop()
        }
    }

    override fun pause() {
        if (
            released ||
            mutableState.value.track == null
        ) {
            return
        }

        player.pause()

        update(
            PlaybackEvent.Paused,
        )
    }

    override fun resume() {
        if (mutableState.value.phase == PlaybackPhase.PAUSED) {
            player.play()
        }
    }

    override fun stop() {
        if (released) {
            return
        }

        player.stop()
        player.clearMediaItems()
        activeAccount = null

        update(
            PlaybackEvent.Stopped,
        )
    }

    override fun release() {
        if (released) {
            return
        }

        player.removeListener(listener)
        player.clearMediaItems()
        player.release()

        activeAccount = null
        released = true

        update(
            PlaybackEvent.Released,
        )
    }

    private fun update(event: PlaybackEvent) {
        mutableState.value =
            reducePlaybackState(
                mutableState.value,
                event,
            )
    }
}
