package dev.devdigi.music.features.playback.data

import dev.devdigi.music.connection.ServerAccountIdentity

internal fun isOwnApplicationController(
    controllerPackageName: String,
    controllerUid: Int,
    applicationPackageName: String,
    applicationUid: Int,
): Boolean =
    controllerUid == applicationUid &&
        controllerPackageName == applicationPackageName

internal fun shouldClearServicePlayback(
    activeAccount: ServerAccountIdentity?,
    currentAccount: ServerAccountIdentity?,
): Boolean =
    activeAccount != null &&
        activeAccount != currentAccount
