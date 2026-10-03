package dev.devdigi.music.features.playback.data

import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.playback.domain.PlaybackQueue
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import dev.devdigi.music.features.playback.persistence.PlaybackQueueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class PlaybackQueueRestoreTest {
    private val alice =
        account("alice")

    private val queue =
        PlaybackQueue
            .empty()
            .replace(
                entries =
                    listOf(
                        PlaybackTrack(
                            id = "opaque-1",
                            title = "One",
                            artist = "Synthetic Artist",
                        ),
                        PlaybackTrack(
                            id = "opaque-2",
                            title = "Two",
                            artist = null,
                        ),
                    ),
                selectedIndex = 1,
            )

    @Test
    fun matchingStoreSnapshotReturnsRestoredQueue() =
        runTest {
            val result =
                readQueueForRestore(
                    store =
                        FakeStore {
                            queue
                        },
                    account = alice,
                )

            val restored =
                result as
                    QueueRestoreReadResult.Restored

            assertEquals(
                queue,
                restored.queue,
            )
        }

    @Test
    fun missingSnapshotIsNotAnError() =
        runTest {
            assertSame(
                QueueRestoreReadResult.Missing,
                readQueueForRestore(
                    store =
                        FakeStore {
                            null
                        },
                    account = alice,
                ),
            )
        }

    @Test
    fun storageFailureIsRecoverableResult() =
        runTest {
            assertSame(
                QueueRestoreReadResult.Failed,
                readQueueForRestore(
                    store =
                        FakeStore {
                            throw IOException(
                                "synthetic read failure",
                            )
                        },
                    account = alice,
                ),
            )
        }

    @Test
    fun cancellationIsNeverConvertedToRecoverableFailure() =
        runTest {
            try {
                readQueueForRestore(
                    store =
                        FakeStore {
                            throw CancellationException(
                                "synthetic cancellation",
                            )
                        },
                    account = alice,
                )

                fail(
                    "Expected cancellation",
                )
            } catch (
                error: CancellationException,
            ) {
                assertEquals(
                    "synthetic cancellation",
                    error.message,
                )
            }
        }

    private class FakeStore(
        private val readBlock:
            suspend () -> PlaybackQueue?,
    ) : PlaybackQueueStore {
        override suspend fun save(
            account: ServerAccountIdentity,
            queue: PlaybackQueue,
        ) = Unit

        override suspend fun read(account: ServerAccountIdentity): PlaybackQueue? = readBlock()

        override suspend fun clear() = Unit
    }

    private fun account(username: String): ServerAccountIdentity =
        ServerAccountIdentity(
            endpoint =
                (
                    ServerEndpoint.parse(
                        "https://music.example.com",
                    ) as EndpointParseResult.Valid
                ).endpoint,
            username = username,
        )
}
