package dev.devdigi.music.features.playback.data

import androidx.media3.common.Player
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueSessionPolicyTest {
    private val alice =
        account("alice")

    private val sameAlice =
        account("alice")

    private val bob =
        account("bob")

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
    fun queueMutationAllowsUnownedOrSameAccountAndRejectsDifferentOwner() {
        assertTrue(
            canMutateServiceQueue(
                activeAccount = null,
                requestedAccount = alice,
            ),
        )

        assertTrue(
            canMutateServiceQueue(
                activeAccount = alice,
                requestedAccount = sameAlice,
            ),
        )

        assertFalse(
            canMutateServiceQueue(
                activeAccount = alice,
                requestedAccount = bob,
            ),
        )
    }

    @Test
    fun queueMutationRequestModelsAlwaysCarryTransientAccountContext() {
        val replacement =
            PlaybackSessionReplaceQueueRequest(
                account = alice,
                entries =
                    listOf(
                        first,
                        second,
                    ),
                selectedIndex = 1,
            )

        val append =
            PlaybackSessionAppendQueueRequest(
                account = alice,
                entries =
                    listOf(
                        second,
                    ),
            )

        val remove =
            PlaybackSessionRemoveQueueEntryRequest(
                account = alice,
                index = 0,
            )

        val clear =
            PlaybackSessionClearQueueRequest(
                account = alice,
            )

        assertEquals(
            alice,
            replacement.account,
        )
        assertEquals(
            listOf(
                first,
                second,
            ),
            replacement.entries,
        )
        assertEquals(
            1,
            replacement.selectedIndex,
        )

        assertEquals(
            alice,
            append.account,
        )
        assertEquals(
            listOf(second),
            append.entries,
        )

        assertEquals(
            alice,
            remove.account,
        )
        assertEquals(
            0,
            remove.index,
        )

        assertEquals(
            alice,
            clear.account,
        )
    }

    @Test
    fun queueSessionMutationActionsAreStableAndUnique() {
        val actions =
            listOf(
                PlaybackSessionProtocol
                    .ACTION_REPLACE_QUEUE,
                PlaybackSessionProtocol
                    .ACTION_APPEND_QUEUE,
                PlaybackSessionProtocol
                    .ACTION_REMOVE_QUEUE_ENTRY,
                PlaybackSessionProtocol
                    .ACTION_CLEAR_QUEUE,
            )

        assertEquals(
            listOf(
                "dev.devdigi.music.playback.REPLACE_QUEUE",
                "dev.devdigi.music.playback.APPEND_QUEUE",
                "dev.devdigi.music.playback.REMOVE_QUEUE_ENTRY",
                "dev.devdigi.music.playback.CLEAR_QUEUE",
            ),
            actions,
        )

        assertEquals(
            actions.size,
            actions.toSet().size,
        )
    }

    @Test
    fun queueSafePlayerCommandsKeepNavigationButBlockMediaInjection() {
        val blocked =
            queueBlockedPlayerCommands

        assertTrue(
            blocked.contains(
                Player.COMMAND_SET_MEDIA_ITEM,
            ),
        )

        assertTrue(
            blocked.contains(
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
            ),
        )

        assertFalse(
            blocked.contains(
                Player.COMMAND_SEEK_TO_NEXT,
            ),
        )

        assertFalse(
            blocked.contains(
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            ),
        )

        assertFalse(
            blocked.contains(
                Player.COMMAND_SEEK_TO_PREVIOUS,
            ),
        )

        assertFalse(
            blocked.contains(
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            ),
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
}
