package dev.devdigi.music.features.playback.data

import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueRuntimeTest {
    private val alice = account("alice")
    private val bob = account("bob")

    private val one = track("opaque-1", "One")
    private val two = track("opaque-2", "Two")
    private val three = track("opaque-3", "Three")

    @Test
    fun replaceAndAppendKeepOneAccountOwnedQueue() {
        val runtime =
            PlaybackQueueRuntime()

        assertAccepted(
            runtime.replace(
                replace(
                    account = alice,
                    entries = listOf(one, two),
                    index = 1,
                ),
            ),
        )

        assertState(
            runtime = runtime,
            account = alice,
            entries = listOf(one, two),
            index = 1,
        )

        assertAccepted(
            runtime.append(
                PlaybackSessionAppendQueueRequest(
                    account = account("alice"),
                    entries = listOf(three),
                ),
            ),
        )

        assertState(
            runtime = runtime,
            account = alice,
            entries = listOf(one, two, three),
            index = 1,
        )
    }

    @Test
    fun differentAccountCannotMutateOwnedQueue() {
        val runtime =
            runtimeWith(
                listOf(one, two),
                index = 0,
            )
        val before =
            runtime.queue

        assertSame(
            QueueRuntimeMutationResult.ACCOUNT_MISMATCH,
            runtime.append(
                PlaybackSessionAppendQueueRequest(
                    account = bob,
                    entries = listOf(three),
                ),
            ),
        )

        assertSame(
            before,
            runtime.queue,
        )
        assertEquals(
            alice,
            runtime.activeAccount,
        )
    }

    @Test
    fun invalidMutationsFailClosed() {
        val empty =
            PlaybackQueueRuntime()

        assertSame(
            QueueRuntimeMutationResult.INVALID_REQUEST,
            empty.replace(
                replace(
                    account = alice,
                    entries = listOf(one),
                    index = 9,
                ),
            ),
        )
        assertNull(
            empty.activeAccount,
        )
        assertTrue(
            empty.queue.entries.isEmpty(),
        )

        val runtime =
            runtimeWith(
                listOf(one),
                index = 0,
            )
        val before =
            runtime.queue

        assertSame(
            QueueRuntimeMutationResult.INVALID_REQUEST,
            runtime.remove(
                PlaybackSessionRemoveQueueEntryRequest(
                    account = alice,
                    index = 8,
                ),
            ),
        )
        assertSame(
            before,
            runtime.queue,
        )
    }

    @Test
    fun removeAndClearUseDomainRules() {
        val runtime =
            runtimeWith(
                listOf(one, two, three),
                index = 1,
            )

        assertAccepted(
            runtime.remove(
                PlaybackSessionRemoveQueueEntryRequest(
                    account = alice,
                    index = 1,
                ),
            ),
        )

        assertState(
            runtime = runtime,
            account = alice,
            entries = listOf(one, three),
            index = 1,
        )

        assertAccepted(
            runtime.clear(
                PlaybackSessionClearQueueRequest(
                    account = alice,
                ),
            ),
        )

        assertNull(
            runtime.activeAccount,
        )
        assertTrue(
            runtime.queue.entries.isEmpty(),
        )
        assertNull(
            runtime.queue.currentIndex,
        )
    }

    @Test
    fun playTrackCompatibilityReplacesQueue() {
        val runtime =
            runtimeWith(
                listOf(one, two),
                index = 1,
            )

        assertAccepted(
            runtime.play(
                PlaybackSessionPlayRequest(
                    account = alice,
                    track = three,
                ),
            ),
        )

        assertState(
            runtime = runtime,
            account = alice,
            entries = listOf(three),
            index = 0,
        )
    }

    @Test
    fun navigationUsesNonWrappingDomainBoundaries() {
        val runtime =
            runtimeWith(
                listOf(one, two, three),
                index = 1,
            )

        assertTrue(runtime.next())
        assertEquals(
            2,
            runtime.queue.currentIndex,
        )

        assertFalse(runtime.next())
        assertEquals(
            2,
            runtime.queue.currentIndex,
        )

        assertTrue(runtime.previous())
        assertTrue(runtime.previous())
        assertEquals(
            0,
            runtime.queue.currentIndex,
        )

        assertFalse(runtime.previous())
        assertEquals(
            0,
            runtime.queue.currentIndex,
        )
    }

    @Test
    fun mismatchedClearCannotReleaseOwner() {
        val runtime =
            runtimeWith(
                listOf(one),
                index = 0,
            )

        assertSame(
            QueueRuntimeMutationResult.ACCOUNT_MISMATCH,
            runtime.clear(
                PlaybackSessionClearQueueRequest(
                    account = bob,
                ),
            ),
        )

        assertState(
            runtime = runtime,
            account = alice,
            entries = listOf(one),
            index = 0,
        )
    }

    @Test
    fun persistedQueueCanRestoreOnlyIntoCompatibleRuntimeOwner() {
        val restored =
            dev.devdigi.music.features.playback.domain
                .PlaybackQueue
                .empty()
                .replace(
                    entries =
                        listOf(
                            one,
                            two,
                            three,
                        ),
                    selectedIndex = 1,
                )

        val runtime =
            PlaybackQueueRuntime()

        assertAccepted(
            runtime.restore(
                account = alice,
                restoredQueue = restored,
            ),
        )

        assertState(
            runtime = runtime,
            account = alice,
            entries = listOf(one, two, three),
            index = 1,
        )

        assertTrue(
            runtime.hasPrevious(),
        )
        assertTrue(
            runtime.hasNext(),
        )

        assertSame(
            QueueRuntimeMutationResult.ACCOUNT_MISMATCH,
            runtime.restore(
                account = bob,
                restoredQueue = restored,
            ),
        )
    }

    private fun runtimeWith(
        entries: List<PlaybackTrack>,
        index: Int,
    ): PlaybackQueueRuntime =
        PlaybackQueueRuntime().also {
            assertAccepted(
                it.replace(
                    replace(
                        account = alice,
                        entries = entries,
                        index = index,
                    ),
                ),
            )
        }

    private fun replace(
        account: ServerAccountIdentity,
        entries: List<PlaybackTrack>,
        index: Int,
    ) = PlaybackSessionReplaceQueueRequest(
        account = account,
        entries = entries,
        selectedIndex = index,
    )

    private fun assertAccepted(result: QueueRuntimeMutationResult) {
        assertSame(
            QueueRuntimeMutationResult.ACCEPTED,
            result,
        )
    }

    private fun assertState(
        runtime: PlaybackQueueRuntime,
        account: ServerAccountIdentity,
        entries: List<PlaybackTrack>,
        index: Int,
    ) {
        assertEquals(
            account,
            runtime.activeAccount,
        )
        assertEquals(
            entries,
            runtime.queue.entries,
        )
        assertEquals(
            index,
            runtime.queue.currentIndex,
        )
        assertEquals(
            entries[index],
            runtime.queue.current,
        )
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

    private fun track(
        id: String,
        title: String,
    ) = PlaybackTrack(
        id = id,
        title = title,
        artist = "Synthetic Artist",
    )
}
