package dev.devdigi.music.realinstance

import android.content.ComponentName
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import dev.devdigi.music.features.playback.data.PlaybackService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class RealInstancePlaybackObserver private constructor(
    private val controller: MediaController,
) : AutoCloseable {
    private val instrumentation =
        InstrumentationRegistry
            .getInstrumentation()

    private val transitionEvents =
        AtomicInteger(0)

    private val listener =
        object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                transitionEvents.incrementAndGet()
            }
        }

    init {
        instrumentation.runOnMainSync {
            controller.addListener(listener)
        }
    }

    fun transitionCount(): Int = transitionEvents.get()

    fun isPlaying(): Boolean =
        read {
            isPlaying
        }

    fun isPausedWithMedia(): Boolean =
        read {
            currentMediaItem != null &&
                !playWhenReady &&
                playbackState ==
                Player.STATE_READY
        }

    fun hasMediaItem(): Boolean =
        read {
            currentMediaItem != null
        }

    fun hasPreviousCommand(): Boolean =
        read {
            isCommandAvailable(
                Player.COMMAND_SEEK_TO_PREVIOUS,
            ) ||
                isCommandAvailable(
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                )
        }

    fun hasNextCommand(): Boolean =
        read {
            isCommandAvailable(
                Player.COMMAND_SEEK_TO_NEXT,
            ) ||
                isCommandAvailable(
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                )
        }

    override fun close() {
        try {
            instrumentation.runOnMainSync {
                controller.removeListener(listener)
                controller.release()
            }
        } catch (_: Throwable) {
            throw AssertionError(
                "REAL_STAGE_SYSTEM_CONTROLLER_RELEASE_FAILED",
            )
        }
    }

    private fun <T> read(block: MediaController.() -> T): T {
        val result =
            AtomicReference<Result<T>>()

        instrumentation.runOnMainSync {
            result.set(
                runCatching {
                    controller.block()
                },
            )
        }

        return result
            .get()
            .getOrThrow()
    }

    companion object {
        fun connect(): RealInstancePlaybackObserver {
            val instrumentation =
                InstrumentationRegistry
                    .getInstrumentation()

            val context =
                instrumentation.targetContext

            val future =
                MediaController
                    .Builder(
                        context,
                        SessionToken(
                            context,
                            ComponentName(
                                context,
                                PlaybackService::class.java,
                            ),
                        ),
                    ).setApplicationLooper(
                        Looper.getMainLooper(),
                    ).buildAsync()

            val controller =
                try {
                    future.get(
                        CONNECTION_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS,
                    )
                } catch (error: Throwable) {
                    instrumentation.runOnMainSync {
                        MediaController.releaseFuture(
                            future,
                        )
                    }

                    throw error
                }

            return RealInstancePlaybackObserver(
                controller,
            )
        }

        private const val CONNECTION_TIMEOUT_SECONDS =
            10L
    }
}
