package dev.devdigi.music.features.playback.data

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

internal enum class QueueNavigationDirection {
    PREVIOUS,
    NEXT,
}

internal fun queueNavigationDirection(playerCommand: Int): QueueNavigationDirection? =
    when (playerCommand) {
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        -> QueueNavigationDirection.PREVIOUS

        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        -> QueueNavigationDirection.NEXT

        else -> null
    }

@OptIn(UnstableApi::class)
internal class PlaybackQueueNavigationPlayer(
    player: Player,
    private val canNavigate: (QueueNavigationDirection) -> Boolean,
    private val navigate: (QueueNavigationDirection) -> Unit,
) : ForwardingSimpleBasePlayer(player) {
    override fun getState(): State {
        val state =
            super.getState()

        val commands =
            state.availableCommands
                .buildUpon()

        setNavigationAvailable(
            commands = commands,
            direction =
                QueueNavigationDirection.PREVIOUS,
            commandsToUpdate =
                intArrayOf(
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                ),
        )

        setNavigationAvailable(
            commands = commands,
            direction =
                QueueNavigationDirection.NEXT,
            commandsToUpdate =
                intArrayOf(
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                ),
        )

        return state
            .buildUpon()
            .setAvailableCommands(
                commands.build(),
            ).build()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        val direction =
            queueNavigationDirection(
                seekCommand,
            )

        if (direction == null) {
            return super.handleSeek(
                mediaItemIndex,
                positionMs,
                seekCommand,
            )
        }

        if (canNavigate(direction)) {
            navigate(direction)
        }

        // Queue navigation is service-owned. Do not delegate these
        // commands to the one-item ExoPlayer playlist.
        return Futures.immediateVoidFuture()
    }

    fun refreshQueueCommands() {
        invalidateState()
    }

    private fun setNavigationAvailable(
        commands: Player.Commands.Builder,
        direction: QueueNavigationDirection,
        commandsToUpdate: IntArray,
    ) {
        val available =
            canNavigate(direction)

        commandsToUpdate.forEach { command ->
            if (available) {
                commands.add(command)
            } else {
                commands.remove(command)
            }
        }
    }
}
