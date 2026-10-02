package dev.devdigi.music.features.playback.data

import android.os.Bundle
import androidx.media3.session.SessionCommand
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.playback.domain.PlaybackTrack

internal data class PlaybackSessionPlayRequest(
    val account: ServerAccountIdentity,
    val track: PlaybackTrack,
)

internal data class PlaybackSessionReplaceQueueRequest(
    val account: ServerAccountIdentity,
    val entries: List<PlaybackTrack>,
    val selectedIndex: Int,
)

internal data class PlaybackSessionAppendQueueRequest(
    val account: ServerAccountIdentity,
    val entries: List<PlaybackTrack>,
)

internal data class PlaybackSessionRemoveQueueEntryRequest(
    val account: ServerAccountIdentity,
    val index: Int,
)

internal data class PlaybackSessionClearQueueRequest(
    val account: ServerAccountIdentity,
)

internal sealed interface PlaybackSessionReconcileRequest {
    data class Account(
        val account: ServerAccountIdentity,
    ) : PlaybackSessionReconcileRequest

    data object SignedOut :
        PlaybackSessionReconcileRequest
}

internal object PlaybackSessionProtocol {
    private const val ACTION_PREFIX =
        "dev.devdigi.music.playback."

    const val ACTION_PLAY_TRACK =
        "${ACTION_PREFIX}PLAY_TRACK"

    const val ACTION_RECONCILE_ACCOUNT =
        "${ACTION_PREFIX}RECONCILE_ACCOUNT"

    const val ACTION_STOP =
        "${ACTION_PREFIX}STOP"

    const val ACTION_REPLACE_QUEUE =
        "${ACTION_PREFIX}REPLACE_QUEUE"

    const val ACTION_APPEND_QUEUE =
        "${ACTION_PREFIX}APPEND_QUEUE"

    const val ACTION_REMOVE_QUEUE_ENTRY =
        "${ACTION_PREFIX}REMOVE_QUEUE_ENTRY"

    const val ACTION_CLEAR_QUEUE =
        "${ACTION_PREFIX}CLEAR_QUEUE"

    private const val KEY_HAS_ACCOUNT =
        "has_account"

    private const val KEY_ENDPOINT =
        "endpoint"

    private const val KEY_USERNAME =
        "username"

    private const val KEY_TRACK_ID =
        "track_id"

    private const val KEY_TRACK_TITLE =
        "track_title"

    private const val KEY_TRACK_ARTIST =
        "track_artist"

    val playTrackCommand =
        SessionCommand(
            ACTION_PLAY_TRACK,
            Bundle.EMPTY,
        )

    val reconcileAccountCommand =
        SessionCommand(
            ACTION_RECONCILE_ACCOUNT,
            Bundle.EMPTY,
        )

    val stopCommand =
        SessionCommand(
            ACTION_STOP,
            Bundle.EMPTY,
        )

    val replaceQueueCommand =
        SessionCommand(
            ACTION_REPLACE_QUEUE,
            Bundle.EMPTY,
        )

    val appendQueueCommand =
        SessionCommand(
            ACTION_APPEND_QUEUE,
            Bundle.EMPTY,
        )

    val removeQueueEntryCommand =
        SessionCommand(
            ACTION_REMOVE_QUEUE_ENTRY,
            Bundle.EMPTY,
        )

    val clearQueueCommand =
        SessionCommand(
            ACTION_CLEAR_QUEUE,
            Bundle.EMPTY,
        )

    fun playTrackArgs(
        account: ServerAccountIdentity,
        track: PlaybackTrack,
    ): Bundle =
        Bundle().apply {
            putString(
                KEY_ENDPOINT,
                account.endpoint.value,
            )
            putString(
                KEY_USERNAME,
                account.username,
            )
            putString(
                KEY_TRACK_ID,
                track.id,
            )
            putString(
                KEY_TRACK_TITLE,
                track.title,
            )
            putString(
                KEY_TRACK_ARTIST,
                track.artist,
            )
        }

    fun reconcileAccountArgs(account: ServerAccountIdentity?): Bundle =
        Bundle().apply {
            putBoolean(
                KEY_HAS_ACCOUNT,
                account != null,
            )

            if (account != null) {
                putString(
                    KEY_ENDPOINT,
                    account.endpoint.value,
                )
                putString(
                    KEY_USERNAME,
                    account.username,
                )
            }
        }

    fun parsePlayRequest(args: Bundle): PlaybackSessionPlayRequest? {
        val account =
            parseAccount(args)
                ?: return null

        val trackId =
            args
                .getString(KEY_TRACK_ID)
                ?.takeIf(String::isNotBlank)
                ?: return null

        val title =
            args
                .getString(KEY_TRACK_TITLE)
                ?.takeIf(String::isNotBlank)
                ?: return null

        return PlaybackSessionPlayRequest(
            account = account,
            track =
                PlaybackTrack(
                    id = trackId,
                    title = title,
                    artist =
                        args
                            .getString(KEY_TRACK_ARTIST)
                            ?.takeIf(String::isNotBlank),
                ),
        )
    }

    fun parseReconcileRequest(args: Bundle): PlaybackSessionReconcileRequest? {
        if (!args.containsKey(KEY_HAS_ACCOUNT)) {
            return null
        }

        if (!args.getBoolean(KEY_HAS_ACCOUNT)) {
            return PlaybackSessionReconcileRequest
                .SignedOut
        }

        return parseAccount(args)?.let {
            PlaybackSessionReconcileRequest
                .Account(it)
        }
    }

    private fun parseAccount(args: Bundle): ServerAccountIdentity? {
        val endpointValue =
            args
                .getString(KEY_ENDPOINT)
                ?.takeIf(String::isNotBlank)
                ?: return null

        val username =
            args
                .getString(KEY_USERNAME)
                ?.takeIf(String::isNotBlank)
                ?: return null

        val endpoint =
            (
                ServerEndpoint.parse(
                    endpointValue,
                ) as? EndpointParseResult.Valid
            )?.endpoint
                ?: return null

        return ServerAccountIdentity(
            endpoint = endpoint,
            username = username,
        )
    }
}
