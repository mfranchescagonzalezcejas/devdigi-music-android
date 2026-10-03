package dev.devdigi.music.features.playback.data

import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.playback.domain.PlaybackQueue
import dev.devdigi.music.features.playback.domain.PlaybackTrack

internal enum class QueueRuntimeMutationResult {
    ACCEPTED,
    ACCOUNT_MISMATCH,
    INVALID_REQUEST,
}

internal class PlaybackQueueRuntime {
    var queue: PlaybackQueue =
        PlaybackQueue.empty()
        private set

    var activeAccount: ServerAccountIdentity? =
        null
        private set

    fun replace(request: PlaybackSessionReplaceQueueRequest): QueueRuntimeMutationResult {
        if (
            request.entries.isEmpty() ||
            !request.entries.isRuntimeSafe() ||
            request.selectedIndex !in
            request.entries.indices
        ) {
            return QueueRuntimeMutationResult.INVALID_REQUEST
        }

        return mutate(request.account) {
            queue.replace(
                entries = request.entries,
                selectedIndex = request.selectedIndex,
            )
        }
    }

    fun append(request: PlaybackSessionAppendQueueRequest): QueueRuntimeMutationResult {
        if (
            !request.entries.isRuntimeSafe() ||
            queue.entries.size +
            request.entries.size >
            MAX_QUEUE_ENTRIES
        ) {
            return QueueRuntimeMutationResult.INVALID_REQUEST
        }

        if (
            !canMutateServiceQueue(
                activeAccount = activeAccount,
                requestedAccount = request.account,
            )
        ) {
            return QueueRuntimeMutationResult.ACCOUNT_MISMATCH
        }

        if (request.entries.isEmpty()) {
            return QueueRuntimeMutationResult.ACCEPTED
        }

        queue =
            queue.append(
                request.entries,
            )

        activeAccount =
            request.account

        return QueueRuntimeMutationResult.ACCEPTED
    }

    fun remove(request: PlaybackSessionRemoveQueueEntryRequest): QueueRuntimeMutationResult {
        if (
            !canMutateServiceQueue(
                activeAccount = activeAccount,
                requestedAccount = request.account,
            )
        ) {
            return QueueRuntimeMutationResult.ACCOUNT_MISMATCH
        }

        if (request.index !in queue.entries.indices) {
            return QueueRuntimeMutationResult.INVALID_REQUEST
        }

        queue =
            queue.removeAt(
                request.index,
            )

        activeAccount =
            if (queue.entries.isEmpty()) {
                null
            } else {
                request.account
            }

        return QueueRuntimeMutationResult.ACCEPTED
    }

    fun clear(request: PlaybackSessionClearQueueRequest): QueueRuntimeMutationResult {
        if (
            !canMutateServiceQueue(
                activeAccount = activeAccount,
                requestedAccount = request.account,
            )
        ) {
            return QueueRuntimeMutationResult.ACCOUNT_MISMATCH
        }

        reset()

        return QueueRuntimeMutationResult.ACCEPTED
    }

    fun play(request: PlaybackSessionPlayRequest): QueueRuntimeMutationResult {
        if (!request.track.isRuntimeSafe()) {
            return QueueRuntimeMutationResult.INVALID_REQUEST
        }

        return replace(
            PlaybackSessionReplaceQueueRequest(
                account = request.account,
                entries = listOf(request.track),
                selectedIndex = 0,
            ),
        )
    }

    fun next(): Boolean =
        navigate {
            it.next()
        }

    fun previous(): Boolean =
        navigate {
            it.previous()
        }

    fun reset() {
        queue =
            PlaybackQueue.empty()
        activeAccount =
            null
    }

    private fun mutate(
        account: ServerAccountIdentity,
        mutation: () -> PlaybackQueue,
    ): QueueRuntimeMutationResult {
        if (
            !canMutateServiceQueue(
                activeAccount = activeAccount,
                requestedAccount = account,
            )
        ) {
            return QueueRuntimeMutationResult.ACCOUNT_MISMATCH
        }

        val updated =
            try {
                mutation()
            } catch (_: IllegalArgumentException) {
                return QueueRuntimeMutationResult.INVALID_REQUEST
            }

        queue =
            updated
        activeAccount =
            if (updated.entries.isEmpty()) {
                null
            } else {
                account
            }

        return QueueRuntimeMutationResult.ACCEPTED
    }

    private fun navigate(mutation: (PlaybackQueue) -> PlaybackQueue): Boolean {
        if (activeAccount == null) {
            return false
        }

        val updated =
            mutation(queue)

        if (updated == queue) {
            return false
        }

        queue =
            updated

        return true
    }
}

private fun List<PlaybackTrack>.isRuntimeSafe(): Boolean =
    size <= MAX_QUEUE_ENTRIES &&
        all {
            it.isRuntimeSafe()
        }

private fun PlaybackTrack.isRuntimeSafe(): Boolean =
    id.isNotBlank() &&
        id.length <= MAX_TRACK_ID_CHARS &&
        title.isNotBlank() &&
        title.length <= MAX_TRACK_TITLE_CHARS

private const val MAX_QUEUE_ENTRIES =
    500

private const val MAX_TRACK_ID_CHARS =
    512

private const val MAX_TRACK_TITLE_CHARS =
    1_024
