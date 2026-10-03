package dev.devdigi.music.features.playback.data

import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.playback.domain.PlaybackQueue
import dev.devdigi.music.features.playback.persistence.PlaybackQueueStore
import kotlinx.coroutines.CancellationException

internal sealed interface QueueRestoreReadResult {
    data class Restored(
        val queue: PlaybackQueue,
    ) : QueueRestoreReadResult

    data object Missing :
        QueueRestoreReadResult

    data object Failed :
        QueueRestoreReadResult
}

internal suspend fun readQueueForRestore(
    store: PlaybackQueueStore,
    account: ServerAccountIdentity,
): QueueRestoreReadResult =
    try {
        store
            .read(account)
            ?.let(
                QueueRestoreReadResult::Restored,
            )
            ?: QueueRestoreReadResult.Missing
    } catch (
        error: CancellationException,
    ) {
        throw error
    } catch (_: Exception) {
        QueueRestoreReadResult.Failed
    }
