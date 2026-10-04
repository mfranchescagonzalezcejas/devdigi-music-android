package dev.devdigi.music.features.playback.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.playback.domain.PlaybackQueue
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class PlaybackQueuePersistenceTest {
    private val accountA =
        identity(
            endpoint = "https://music.example.com",
            username = "alice",
        )

    private val accountB =
        identity(
            endpoint = "https://music.example.com",
            username = "Alice",
        )

    private val first =
        PlaybackTrack(
            id = "opaque-track-1",
            title = "Synthetic One",
            artist = "Synthetic Artist",
        )

    private val second =
        PlaybackTrack(
            id = "opaque-track-2",
            title = "Synthetic Two",
            artist = null,
        )

    @Test
    fun fingerprintUsesCanonicalVersionedLengthPrefixedIdentity() {
        val normalized =
            identity(
                endpoint = "https://MUSIC.example.com/",
                username = "alice",
            )

        assertEquals(
            "19bd4f97698aa549c6f39f7d8ea20a453d94deeefc6209c9e13ba138d0891bf5",
            QueueAccountFingerprint.forIdentity(normalized),
        )
    }

    @Test
    fun queueRoundTripsForMatchingAccount() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)
            val queue = queue()

            store.save(
                account = accountA,
                queue = queue,
            )

            assertEquals(
                queue,
                store.read(accountA),
            )
        }

    @Test
    fun mismatchedAccountCannotRestoreQueueAndDoesNotDestroySnapshot() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)
            val queue = queue()

            store.save(
                account = accountA,
                queue = queue,
            )

            assertNull(
                store.read(accountB),
            )

            assertEquals(
                queue,
                store.read(accountA),
            )
        }

    @Test
    fun sameUsernameOnDifferentServerCannotRestoreQueueAndDoesNotDestroySnapshot() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)
            val queue = queue()

            val otherServerAlice =
                identity(
                    endpoint = "https://music-alt.example.com",
                    username = "alice",
                )

            store.save(
                account = accountA,
                queue = queue,
            )

            assertNull(
                store.read(otherServerAlice),
            )

            assertEquals(
                queue,
                store.read(accountA),
            )
        }

    @Test
    fun durableSnapshotContainsOnlySafeQueueMaterial() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)

            store.save(
                account = accountA,
                queue = queue(),
            )

            val values =
                dataStore
                    .data
                    .first()
                    .asMap()

            assertEquals(
                setOf("queue_snapshot"),
                values.keys
                    .map { it.name }
                    .toSet(),
            )

            val raw =
                values.values
                    .single() as String

            assertFalse(
                "raw endpoint persisted",
                raw.contains(accountA.endpoint.value),
            )
            assertFalse(
                "raw username persisted",
                raw.contains(accountA.username),
            )

            listOf(
                "password",
                "credential",
                "token",
                "salt",
                "\"url\"",
                "\"uri\"",
                "authorization",
            ).forEach { forbidden ->
                assertFalse(
                    "forbidden queue persistence material: $forbidden",
                    raw.lowercase().contains(forbidden),
                )
            }

            assertTrue(
                "expected opaque track id",
                raw.contains(first.id),
            )
            assertTrue(
                "expected safe title",
                raw.contains(first.title),
            )
        }

    @Test
    fun savingEmptyQueueRemovesDurableSnapshot() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)

            store.save(
                account = accountA,
                queue = queue(),
            )

            store.save(
                account = accountA,
                queue = PlaybackQueue.empty(),
            )

            assertNull(
                dataStore
                    .data
                    .first()[SNAPSHOT_KEY],
            )
            assertNull(store.read(accountA))
        }

    @Test
    fun malformedJsonFailsClosed() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)

            dataStore.edit {
                it[SNAPSHOT_KEY] =
                    "{not-json"
            }

            assertNull(
                store.read(accountA),
            )
        }

    @Test
    fun unsupportedSchemaFailsClosed() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)

            dataStore.edit {
                it[SNAPSHOT_KEY] =
                    """
                    {
                      "schema": 999,
                      "accountFingerprint": "${QueueAccountFingerprint.forIdentity(accountA)}",
                      "currentIndex": 0,
                      "entries": [
                        {
                          "id": "opaque-track-1",
                          "title": "Synthetic One",
                          "artist": null
                        }
                      ]
                    }
                    """.trimIndent()
            }

            assertNull(
                store.read(accountA),
            )
        }

    @Test
    fun outOfBoundsCurrentIndexFailsClosed() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)

            dataStore.edit {
                it[SNAPSHOT_KEY] =
                    """
                    {
                      "schema": 1,
                      "accountFingerprint": "${QueueAccountFingerprint.forIdentity(accountA)}",
                      "currentIndex": 8,
                      "entries": [
                        {
                          "id": "opaque-track-1",
                          "title": "Synthetic One",
                          "artist": null
                        }
                      ]
                    }
                    """.trimIndent()
            }

            assertNull(
                store.read(accountA),
            )
        }

    @Test
    fun missingRequiredTrackTitleFailsClosed() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)

            dataStore.edit {
                it[SNAPSHOT_KEY] =
                    """
                    {
                      "schema": 1,
                      "accountFingerprint": "${QueueAccountFingerprint.forIdentity(accountA)}",
                      "currentIndex": 0,
                      "entries": [
                        {
                          "id": "opaque-track-1",
                          "artist": null
                        }
                      ]
                    }
                    """.trimIndent()
            }

            assertNull(
                store.read(accountA),
            )
        }

    @Test
    fun oversizedRequiredTrackFieldsAreRejectedOnSave() =
        runBlocking {
            val store =
                DataStorePlaybackQueueStore(
                    dataStore(),
                )

            val oversizedId =
                first.copy(
                    id =
                        "x".repeat(
                            DataStorePlaybackQueueStore.MAX_TRACK_ID_CHARS + 1,
                        ),
                )

            val oversizedTitle =
                first.copy(
                    title =
                        "x".repeat(
                            DataStorePlaybackQueueStore.MAX_TRACK_TITLE_CHARS + 1,
                        ),
                )

            listOf(
                oversizedId,
                oversizedTitle,
            ).forEach { track ->
                val queue =
                    PlaybackQueue
                        .empty()
                        .replace(
                            entries = listOf(track),
                            selectedIndex = 0,
                        )

                try {
                    store.save(
                        account = accountA,
                        queue = queue,
                    )

                    fail(
                        "expected oversized required field rejection",
                    )
                } catch (_: IllegalArgumentException) {
                }
            }
        }

    @Test
    fun oversizedRequiredTrackFieldsFailClosedOnRestore() =
        runBlocking {
            val dataStore = dataStore()
            val store =
                DataStorePlaybackQueueStore(
                    dataStore,
                )

            val fingerprint =
                QueueAccountFingerprint
                    .forIdentity(accountA)

            val oversizedId =
                "x".repeat(
                    DataStorePlaybackQueueStore.MAX_TRACK_ID_CHARS + 1,
                )

            dataStore.edit {
                it[SNAPSHOT_KEY] =
                    """{"schema":1,"accountFingerprint":"$fingerprint","currentIndex":0,"entries":[{"id":"$oversizedId","title":"Synthetic One","artist":null}]}"""
            }

            assertNull(
                store.read(accountA),
            )

            val oversizedTitle =
                "x".repeat(
                    DataStorePlaybackQueueStore.MAX_TRACK_TITLE_CHARS + 1,
                )

            dataStore.edit {
                it[SNAPSHOT_KEY] =
                    """{"schema":1,"accountFingerprint":"$fingerprint","currentIndex":0,"entries":[{"id":"opaque-track-1","title":"$oversizedTitle","artist":null}]}"""
            }

            assertNull(
                store.read(accountA),
            )
        }

    @Test
    fun maximumRequiredTrackFieldLengthsRoundTrip() =
        runBlocking {
            val store =
                DataStorePlaybackQueueStore(
                    dataStore(),
                )

            val track =
                PlaybackTrack(
                    id =
                        "i".repeat(
                            DataStorePlaybackQueueStore.MAX_TRACK_ID_CHARS,
                        ),
                    title =
                        "t".repeat(
                            DataStorePlaybackQueueStore.MAX_TRACK_TITLE_CHARS,
                        ),
                    artist = null,
                )

            val queue =
                PlaybackQueue
                    .empty()
                    .replace(
                        entries = listOf(track),
                        selectedIndex = 0,
                    )

            store.save(
                account = accountA,
                queue = queue,
            )

            assertEquals(
                queue,
                store.read(accountA),
            )
        }

    @Test
    fun excessivelyNestedSnapshotFailsClosedBeforeJsonParsing() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)
            val depth =
                DataStorePlaybackQueueStore.MAX_JSON_NESTING_DEPTH + 1

            dataStore.edit {
                it[SNAPSHOT_KEY] =
                    "[".repeat(depth) +
                    "0" +
                    "]".repeat(depth)
            }

            assertNull(
                store.read(accountA),
            )
        }

    @Test
    fun oversizedSerializedSnapshotFailsClosed() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)

            dataStore.edit {
                it[SNAPSHOT_KEY] =
                    "x".repeat(
                        DataStorePlaybackQueueStore.MAX_SNAPSHOT_CHARS + 1,
                    )
            }

            assertNull(
                store.read(accountA),
            )
        }

    @Test
    fun savingTooManyEntriesIsRejected() =
        runBlocking {
            val dataStore = dataStore()
            val store = DataStorePlaybackQueueStore(dataStore)

            val tracks =
                List(
                    DataStorePlaybackQueueStore.MAX_QUEUE_ENTRIES + 1,
                ) { index ->
                    PlaybackTrack(
                        id = "opaque-$index",
                        title = "Synthetic $index",
                        artist = null,
                    )
                }

            val oversizedQueue =
                PlaybackQueue
                    .empty()
                    .replace(
                        entries = tracks,
                        selectedIndex = 0,
                    )

            try {
                store.save(
                    account = accountA,
                    queue = oversizedQueue,
                )

                fail("expected oversized queue rejection")
            } catch (_: IllegalArgumentException) {
            }
        }

    @Test
    fun cancellationDuringReadPropagates() =
        runBlocking {
            val cancellation =
                CancellationException(
                    "synthetic read cancellation",
                )

            val store =
                DataStorePlaybackQueueStore(
                    ThrowingReadDataStore(
                        delegate = dataStore(),
                        error = cancellation,
                    ),
                )

            try {
                store.read(accountA)
                fail("expected CancellationException")
            } catch (caught: CancellationException) {
                assertTrue(caught === cancellation)
            }
        }

    @Test
    fun cancellationDuringSavePropagates() =
        runBlocking {
            val cancellation =
                CancellationException(
                    "synthetic write cancellation",
                )

            val store =
                DataStorePlaybackQueueStore(
                    ThrowingWriteDataStore(
                        delegate = dataStore(),
                        error = cancellation,
                    ),
                )

            try {
                store.save(
                    account = accountA,
                    queue = queue(),
                )

                fail("expected CancellationException")
            } catch (caught: CancellationException) {
                assertTrue(caught === cancellation)
            }
        }

    private fun queue(): PlaybackQueue =
        PlaybackQueue
            .empty()
            .replace(
                entries =
                    listOf(
                        first,
                        second,
                    ),
                selectedIndex = 1,
            )

    private fun dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File
                .createTempFile(
                    "playback-queue",
                    ".preferences_pb",
                ).apply {
                    delete()
                }
        }

    private fun identity(
        endpoint: String,
        username: String,
    ): ServerAccountIdentity =
        ServerAccountIdentity(
            endpoint =
                (
                    ServerEndpoint.parse(endpoint)
                        as EndpointParseResult.Valid
                ).endpoint,
            username = username,
        )

    private companion object {
        val SNAPSHOT_KEY =
            stringPreferencesKey(
                "queue_snapshot",
            )
    }
}

private class ThrowingReadDataStore(
    private val delegate: DataStore<Preferences>,
    private val error: Throwable,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> =
        flow {
            throw error
        }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = delegate.updateData(transform)
}

private class ThrowingWriteDataStore(
    private val delegate: DataStore<Preferences>,
    private val error: Throwable,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> =
        delegate.data

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw error
}
