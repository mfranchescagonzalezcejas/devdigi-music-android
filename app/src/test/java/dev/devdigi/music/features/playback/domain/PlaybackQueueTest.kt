package dev.devdigi.music.features.playback.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackQueueTest {
    private val first =
        PlaybackTrack(
            id = "track-1",
            title = "Synthetic One",
            artist = "Synthetic Artist",
        )

    private val second =
        PlaybackTrack(
            id = "track-2",
            title = "Synthetic Two",
            artist = "Synthetic Artist",
        )

    private val third =
        PlaybackTrack(
            id = "track-3",
            title = "Synthetic Three",
            artist = null,
        )

    @Test
    fun emptyQueueHasNoCurrentEntry() {
        val queue =
            PlaybackQueue.empty()

        assertEquals(
            emptyList<PlaybackTrack>(),
            queue.entries,
        )
        assertNull(queue.currentIndex)
        assertNull(queue.current)
    }

    @Test
    fun replacePreservesOrderAndSelectedIndex() {
        val queue =
            PlaybackQueue
                .empty()
                .replace(
                    entries =
                        listOf(
                            first,
                            second,
                            third,
                        ),
                    selectedIndex = 1,
                )

        assertEquals(
            listOf(
                first,
                second,
                third,
            ),
            queue.entries,
        )
        assertEquals(
            1,
            queue.currentIndex,
        )
        assertEquals(
            second,
            queue.current,
        )
    }

    @Test
    fun replaceWithEmptyEntriesClearsQueue() {
        val queue =
            PlaybackQueue
                .empty()
                .replace(
                    entries =
                        listOf(
                            first,
                            second,
                        ),
                    selectedIndex = 1,
                ).replace(
                    entries = emptyList(),
                    selectedIndex = null,
                )

        assertEquals(
            PlaybackQueue.empty(),
            queue,
        )
    }

    @Test
    fun nonEmptyReplacementRejectsMissingSelectedIndex() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            PlaybackQueue
                .empty()
                .replace(
                    entries = listOf(first),
                    selectedIndex = null,
                )
        }
    }

    @Test
    fun nonEmptyReplacementRejectsOutOfBoundsSelectedIndex() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            PlaybackQueue
                .empty()
                .replace(
                    entries =
                        listOf(
                            first,
                            second,
                        ),
                    selectedIndex = 2,
                )
        }
    }

    @Test
    fun appendPreservesCurrentSelection() {
        val queue =
            PlaybackQueue
                .empty()
                .replace(
                    entries =
                        listOf(
                            first,
                            second,
                        ),
                    selectedIndex = 1,
                ).append(
                    listOf(third),
                )

        assertEquals(
            listOf(
                first,
                second,
                third,
            ),
            queue.entries,
        )
        assertEquals(
            1,
            queue.currentIndex,
        )
        assertEquals(
            second,
            queue.current,
        )
    }

    @Test
    fun appendToEmptyQueueSelectsFirstAppendedEntry() {
        val queue =
            PlaybackQueue
                .empty()
                .append(
                    listOf(
                        first,
                        second,
                    ),
                )

        assertEquals(
            listOf(
                first,
                second,
            ),
            queue.entries,
        )
        assertEquals(
            0,
            queue.currentIndex,
        )
        assertEquals(
            first,
            queue.current,
        )
    }

    @Test
    fun appendEmptyEntriesIsNoOp() {
        val initial =
            PlaybackQueue
                .empty()
                .replace(
                    entries = listOf(first),
                    selectedIndex = 0,
                )

        assertEquals(
            initial,
            initial.append(emptyList()),
        )
    }

    @Test
    fun nextMovesForwardWithoutWrapping() {
        val initial =
            queueOfThree(
                selectedIndex = 1,
            )

        val moved =
            initial.next()

        assertEquals(
            2,
            moved.currentIndex,
        )
        assertEquals(
            third,
            moved.current,
        )

        assertEquals(
            moved,
            moved.next(),
        )
    }

    @Test
    fun previousMovesBackwardWithoutWrapping() {
        val initial =
            queueOfThree(
                selectedIndex = 1,
            )

        val moved =
            initial.previous()

        assertEquals(
            0,
            moved.currentIndex,
        )
        assertEquals(
            first,
            moved.current,
        )

        assertEquals(
            moved,
            moved.previous(),
        )
    }

    @Test
    fun removeBeforeCurrentDecrementsCurrentIndex() {
        val queue =
            queueOfThree(
                selectedIndex = 2,
            ).removeAt(0)

        assertEquals(
            listOf(
                second,
                third,
            ),
            queue.entries,
        )
        assertEquals(
            1,
            queue.currentIndex,
        )
        assertEquals(
            third,
            queue.current,
        )
    }

    @Test
    fun removeAfterCurrentPreservesCurrentIndex() {
        val queue =
            queueOfThree(
                selectedIndex = 0,
            ).removeAt(2)

        assertEquals(
            listOf(
                first,
                second,
            ),
            queue.entries,
        )
        assertEquals(
            0,
            queue.currentIndex,
        )
        assertEquals(
            first,
            queue.current,
        )
    }

    @Test
    fun removeCurrentUsesSuccessorWhenAvailable() {
        val queue =
            queueOfThree(
                selectedIndex = 1,
            ).removeAt(1)

        assertEquals(
            listOf(
                first,
                third,
            ),
            queue.entries,
        )
        assertEquals(
            1,
            queue.currentIndex,
        )
        assertEquals(
            third,
            queue.current,
        )
    }

    @Test
    fun removeFinalCurrentUsesPreviousEntry() {
        val queue =
            PlaybackQueue
                .empty()
                .replace(
                    entries =
                        listOf(
                            first,
                            second,
                        ),
                    selectedIndex = 1,
                ).removeAt(1)

        assertEquals(
            listOf(first),
            queue.entries,
        )
        assertEquals(
            0,
            queue.currentIndex,
        )
        assertEquals(
            first,
            queue.current,
        )
    }

    @Test
    fun removeOnlyEntryProducesEmptyQueue() {
        val queue =
            PlaybackQueue
                .empty()
                .replace(
                    entries = listOf(first),
                    selectedIndex = 0,
                ).removeAt(0)

        assertEquals(
            PlaybackQueue.empty(),
            queue,
        )
    }

    @Test
    fun removeRejectsOutOfBoundsIndex() {
        val queue =
            PlaybackQueue
                .empty()
                .replace(
                    entries = listOf(first),
                    selectedIndex = 0,
                )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            queue.removeAt(1)
        }
    }

    @Test
    fun clearAlwaysReturnsEmptyQueue() {
        val queue =
            queueOfThree(
                selectedIndex = 1,
            ).clear()

        assertEquals(
            PlaybackQueue.empty(),
            queue,
        )
    }

    private fun queueOfThree(selectedIndex: Int): PlaybackQueue =
        PlaybackQueue
            .empty()
            .replace(
                entries =
                    listOf(
                        first,
                        second,
                        third,
                    ),
                selectedIndex =
                selectedIndex,
            )
}
