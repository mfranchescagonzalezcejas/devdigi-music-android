package dev.devdigi.music.features.playback.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.playback.domain.PlaybackQueue
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

private val Context.playbackQueueDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "playback_queue",
    corruptionHandler =
        ReplaceFileCorruptionHandler {
            emptyPreferences()
        },
)

interface PlaybackQueueStore {
    suspend fun save(
        account: ServerAccountIdentity,
        queue: PlaybackQueue,
    )

    suspend fun read(account: ServerAccountIdentity): PlaybackQueue?

    suspend fun clear()
}

object QueueAccountFingerprint {
    private const val DOMAIN =
        "devdigi.music.playback.queue.account.v1"

    fun forIdentity(identity: ServerAccountIdentity): String {
        val domainBytes =
            DOMAIN.toByteArray(
                Charsets.UTF_8,
            )
        val endpointBytes =
            identity.endpoint.value.toByteArray(
                Charsets.UTF_8,
            )
        val usernameBytes =
            identity.username.toByteArray(
                Charsets.UTF_8,
            )

        val canonical =
            ByteArrayOutputStream()
                .apply {
                    write(domainBytes)

                    writeUInt32Be(
                        endpointBytes.size,
                    )
                    write(endpointBytes)

                    writeUInt32Be(
                        usernameBytes.size,
                    )
                    write(usernameBytes)
                }.toByteArray()

        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(canonical)

        val hex =
            CharArray(
                digest.size * 2,
            )

        digest.forEachIndexed { index, byte ->
            val value =
                byte.toInt() and 0xff

            hex[index * 2] =
                HEX[value ushr 4]
            hex[index * 2 + 1] =
                HEX[value and 0x0f]
        }

        return String(hex)
    }

    private fun ByteArrayOutputStream.writeUInt32Be(value: Int) {
        write(
            value ushr 24 and 0xff,
        )
        write(
            value ushr 16 and 0xff,
        )
        write(
            value ushr 8 and 0xff,
        )
        write(
            value and 0xff,
        )
    }

    private const val HEX =
        "0123456789abcdef"
}

class DataStorePlaybackQueueStore(
    private val dataStore: DataStore<Preferences>,
) : PlaybackQueueStore {
    override suspend fun save(
        account: ServerAccountIdentity,
        queue: PlaybackQueue,
    ) {
        if (queue.entries.isEmpty()) {
            clear()
            return
        }

        require(
            queue.entries.size <=
                MAX_QUEUE_ENTRIES,
        ) {
            "queue exceeds maximum entry count"
        }

        val currentIndex =
            requireNotNull(
                queue.currentIndex,
            )

        require(
            currentIndex in
                queue.entries.indices,
        )

        queue.entries.forEach { track ->
            require(
                track.id.isValidRequiredField(
                    MAX_TRACK_ID_CHARS,
                ),
            ) {
                "track id is invalid"
            }
            require(
                track.title.isValidRequiredField(
                    MAX_TRACK_TITLE_CHARS,
                ),
            ) {
                "track title is invalid"
            }
        }

        val snapshot =
            encode(
                fingerprint =
                    QueueAccountFingerprint
                        .forIdentity(account),
                currentIndex =
                currentIndex,
                entries =
                    queue.entries,
            )

        require(
            snapshot.length <=
                MAX_SNAPSHOT_CHARS,
        ) {
            "queue snapshot exceeds maximum size"
        }

        dataStore.edit { preferences ->
            preferences[SNAPSHOT_KEY] =
                snapshot
        }
    }

    override suspend fun read(account: ServerAccountIdentity): PlaybackQueue? {
        val snapshot =
            dataStore
                .data
                .first()[SNAPSHOT_KEY]
                ?: return null

        if (
            snapshot.length >
            MAX_SNAPSHOT_CHARS
        ) {
            return null
        }

        if (
            exceedsJsonNestingDepth(
                snapshot,
            )
        ) {
            return null
        }

        return decode(
            snapshot = snapshot,
            expectedFingerprint =
                QueueAccountFingerprint
                    .forIdentity(account),
        )
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(
                SNAPSHOT_KEY,
            )
        }
    }

    private fun exceedsJsonNestingDepth(value: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false

        value.forEach { char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }

                return@forEach
            }

            when (char) {
                '"' -> {
                    inString = true
                }

                '{',
                '[',
                -> {
                    depth++

                    if (
                        depth >
                        MAX_JSON_NESTING_DEPTH
                    ) {
                        return true
                    }
                }

                '}',
                ']',
                -> {
                    if (depth > 0) {
                        depth--
                    }
                }
            }
        }

        return false
    }

    private fun encode(
        fingerprint: String,
        currentIndex: Int,
        entries: List<PlaybackTrack>,
    ): String =
        buildJsonObject {
            put(
                "schema",
                JsonPrimitive(
                    SCHEMA_VERSION,
                ),
            )
            put(
                "accountFingerprint",
                JsonPrimitive(
                    fingerprint,
                ),
            )
            put(
                "currentIndex",
                JsonPrimitive(
                    currentIndex,
                ),
            )
            put(
                "entries",
                buildJsonArray {
                    entries.forEach { track ->
                        add(
                            buildJsonObject {
                                put(
                                    "id",
                                    JsonPrimitive(
                                        track.id,
                                    ),
                                )
                                put(
                                    "title",
                                    JsonPrimitive(
                                        track.title,
                                    ),
                                )
                                put(
                                    "artist",
                                    track.artist
                                        ?.let(
                                            ::JsonPrimitive,
                                        )
                                        ?: JsonNull,
                                )
                            },
                        )
                    }
                },
            )
        }.toString()

    private fun decode(
        snapshot: String,
        expectedFingerprint: String,
    ): PlaybackQueue? {
        val root =
            try {
                STRICT_JSON
                    .parseToJsonElement(
                        snapshot,
                    ) as? JsonObject
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
                ?: return null

        if (
            root.intField("schema") !=
            SCHEMA_VERSION
        ) {
            return null
        }

        if (
            root.stringField(
                "accountFingerprint",
            ) != expectedFingerprint
        ) {
            return null
        }

        val entries =
            root["entries"]
                as? JsonArray
                ?: return null

        if (
            entries.isEmpty() ||
            entries.size > MAX_QUEUE_ENTRIES
        ) {
            return null
        }

        val currentIndex =
            root.intField(
                "currentIndex",
            )
                ?: return null

        if (
            currentIndex !in
            entries.indices
        ) {
            return null
        }

        val tracks =
            ArrayList<PlaybackTrack>(
                entries.size,
            )

        entries.forEach { element ->
            val trackObject =
                element as? JsonObject
                    ?: return null

            val id =
                trackObject
                    .stringField("id")
                    ?.takeIf {
                        it.isValidRequiredField(
                            MAX_TRACK_ID_CHARS,
                        )
                    }
                    ?: return null

            val title =
                trackObject
                    .stringField("title")
                    ?.takeIf {
                        it.isValidRequiredField(
                            MAX_TRACK_TITLE_CHARS,
                        )
                    }
                    ?: return null

            val artist =
                when (
                    val value =
                        trackObject["artist"]
                ) {
                    null,
                    JsonNull,
                    -> {
                        null
                    }

                    is JsonPrimitive -> {
                        value
                            .takeIf {
                                it.isString
                            }?.content
                            ?: return null
                    }

                    else -> {
                        return null
                    }
                }

            tracks +=
                PlaybackTrack(
                    id = id,
                    title = title,
                    artist = artist,
                )
        }

        return try {
            PlaybackQueue
                .empty()
                .replace(
                    entries = tracks,
                    selectedIndex =
                    currentIndex,
                )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun String.isValidRequiredField(maxChars: Int): Boolean =
        isNotBlank() &&
            length <= maxChars

    private fun JsonObject.stringField(key: String): String? =
        (
            get(key)
                as? JsonPrimitive
        )?.takeIf {
            it.isString
        }?.content

    private fun JsonObject.intField(key: String): Int? =
        (
            get(key)
                as? JsonPrimitive
        )?.takeIf {
            !it.isString
        }?.intOrNull

    companion object {
        const val MAX_QUEUE_ENTRIES =
            500

        const val MAX_SNAPSHOT_CHARS =
            262_144

        const val MAX_JSON_NESTING_DEPTH =
            64

        const val MAX_TRACK_ID_CHARS =
            512

        const val MAX_TRACK_TITLE_CHARS =
            1_024

        private const val SCHEMA_VERSION =
            1

        private val STRICT_JSON =
            Json {
                isLenient = false
            }

        private val SNAPSHOT_KEY =
            stringPreferencesKey(
                "queue_snapshot",
            )
    }
}

fun playbackQueueStore(context: Context): PlaybackQueueStore =
    DataStorePlaybackQueueStore(
        context.applicationContext
            .playbackQueueDataStore,
    )
