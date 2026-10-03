package dev.devdigi.music.features.playback.data

import android.os.Bundle
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.playback.domain.PlaybackTrack

internal fun replaceQueueArgs(request: PlaybackSessionReplaceQueueRequest): Bundle =
    queueArgs(
        account = request.account,
        entries = request.entries,
    ).apply {
        putInt(
            KEY_SELECTED_INDEX,
            request.selectedIndex,
        )
    }

internal fun appendQueueArgs(request: PlaybackSessionAppendQueueRequest): Bundle =
    queueArgs(
        account = request.account,
        entries = request.entries,
    )

internal fun removeQueueEntryArgs(request: PlaybackSessionRemoveQueueEntryRequest): Bundle =
    accountArgs(
        request.account,
    ).apply {
        putInt(
            KEY_QUEUE_INDEX,
            request.index,
        )
    }

internal fun clearQueueArgs(request: PlaybackSessionClearQueueRequest): Bundle =
    accountArgs(
        request.account,
    )

internal fun parseReplaceQueueRequest(args: Bundle): PlaybackSessionReplaceQueueRequest? {
    val account =
        parseQueueAccount(args)
            ?: return null
    val entries =
        parseQueueEntries(args)
            ?.takeIf(List<PlaybackTrack>::isNotEmpty)
            ?: return null

    if (!args.containsKey(KEY_SELECTED_INDEX)) {
        return null
    }

    val selectedIndex =
        args.getInt(
            KEY_SELECTED_INDEX,
        )

    if (selectedIndex !in entries.indices) {
        return null
    }

    return PlaybackSessionReplaceQueueRequest(
        account = account,
        entries = entries,
        selectedIndex = selectedIndex,
    )
}

internal fun parseAppendQueueRequest(args: Bundle): PlaybackSessionAppendQueueRequest? {
    val account =
        parseQueueAccount(args)
            ?: return null
    val entries =
        parseQueueEntries(args)
            ?: return null

    return PlaybackSessionAppendQueueRequest(
        account = account,
        entries = entries,
    )
}

internal fun parseRemoveQueueEntryRequest(args: Bundle): PlaybackSessionRemoveQueueEntryRequest? {
    val account =
        parseQueueAccount(args)
            ?: return null

    if (!args.containsKey(KEY_QUEUE_INDEX)) {
        return null
    }

    val index =
        args.getInt(
            KEY_QUEUE_INDEX,
        )

    if (index < 0) {
        return null
    }

    return PlaybackSessionRemoveQueueEntryRequest(
        account = account,
        index = index,
    )
}

internal fun parseClearQueueRequest(args: Bundle): PlaybackSessionClearQueueRequest? =
    parseQueueAccount(args)?.let {
        PlaybackSessionClearQueueRequest(
            account = it,
        )
    }

private fun queueArgs(
    account: ServerAccountIdentity,
    entries: List<PlaybackTrack>,
): Bundle =
    accountArgs(account).apply {
        putStringArrayList(
            KEY_QUEUE_IDS,
            ArrayList(
                entries.map(
                    PlaybackTrack::id,
                ),
            ),
        )
        putStringArrayList(
            KEY_QUEUE_TITLES,
            ArrayList(
                entries.map(
                    PlaybackTrack::title,
                ),
            ),
        )
        putStringArrayList(
            KEY_QUEUE_ARTISTS,
            ArrayList(
                entries.map {
                    it.artist.orEmpty()
                },
            ),
        )
    }

private fun accountArgs(account: ServerAccountIdentity): Bundle =
    Bundle().apply {
        putString(
            KEY_ENDPOINT,
            account.endpoint.value,
        )
        putString(
            KEY_USERNAME,
            account.username,
        )
    }

private fun parseQueueEntries(args: Bundle): List<PlaybackTrack>? {
    val ids =
        args.getStringArrayList(
            KEY_QUEUE_IDS,
        )
            ?: return null
    val titles =
        args.getStringArrayList(
            KEY_QUEUE_TITLES,
        )
            ?: return null
    val artists =
        args.getStringArrayList(
            KEY_QUEUE_ARTISTS,
        )
            ?: return null

    if (
        ids.size != titles.size ||
        ids.size != artists.size ||
        ids.size > MAX_SESSION_QUEUE_ENTRIES
    ) {
        return null
    }

    return ids.indices.map { index ->
        val id =
            ids[index]
                .takeIf {
                    it.isNotBlank() &&
                        it.length <=
                        MAX_SESSION_TRACK_ID_CHARS
                }
                ?: return null

        val title =
            titles[index]
                .takeIf {
                    it.isNotBlank() &&
                        it.length <=
                        MAX_SESSION_TRACK_TITLE_CHARS
                }
                ?: return null

        PlaybackTrack(
            id = id,
            title = title,
            artist =
                artists[index]
                    .takeIf(String::isNotBlank),
        )
    }
}

private fun parseQueueAccount(args: Bundle): ServerAccountIdentity? {
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

private const val KEY_ENDPOINT =
    "endpoint"

private const val KEY_USERNAME =
    "username"

private const val KEY_QUEUE_IDS =
    "queue_ids"

private const val KEY_QUEUE_TITLES =
    "queue_titles"

private const val KEY_QUEUE_ARTISTS =
    "queue_artists"

private const val KEY_SELECTED_INDEX =
    "selected_index"

private const val KEY_QUEUE_INDEX =
    "queue_index"

private const val MAX_SESSION_QUEUE_ENTRIES =
    500

private const val MAX_SESSION_TRACK_ID_CHARS =
    512

private const val MAX_SESSION_TRACK_TITLE_CHARS =
    1_024
