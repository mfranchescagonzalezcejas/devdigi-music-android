package dev.devdigi.music.features.playback.data

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.devdigi.music.connection.ServerAccountIdentity

internal fun isOwnApplicationController(
    controllerPackageName: String,
    controllerUid: Int,
    applicationPackageName: String,
    applicationUid: Int,
): Boolean =
    controllerUid == applicationUid &&
        controllerPackageName == applicationPackageName

internal fun canMutateServiceQueue(
    activeAccount: ServerAccountIdentity?,
    requestedAccount: ServerAccountIdentity,
): Boolean =
    activeAccount == null ||
        activeAccount == requestedAccount

internal val queueBlockedPlayerCommands: Set<Int> =
    setOf(
        Player.COMMAND_SET_MEDIA_ITEM,
        Player.COMMAND_CHANGE_MEDIA_ITEMS,
    )

@OptIn(UnstableApi::class)
internal fun queueSafePlayerCommands(baseCommands: Player.Commands): Player.Commands =
    baseCommands
        .buildUpon()
        .apply {
            queueBlockedPlayerCommands
                .forEach { command ->
                    remove(command)
                }
        }.build()

internal fun shouldClearServicePlayback(
    activeAccount: ServerAccountIdentity?,
    currentAccount: ServerAccountIdentity?,
): Boolean =
    activeAccount != null &&
        activeAccount != currentAccount
