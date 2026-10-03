package dev.devdigi.music.features.playback.data

import androidx.media3.common.Player
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueLifecyclePolicyTest {
    private val alice =
        account("alice")
    private val sameAlice =
        account("alice")
    private val bob =
        account("bob")

    @Test
    fun reconcileDecisionPreservesMatchesAndRestoresUnownedState() {
        assertEquals(
            QueueReconcileAction.NONE,
            queueReconcileAction(
                activeAccount = null,
                currentAccount = null,
            ),
        )

        assertEquals(
            QueueReconcileAction.RESTORE,
            queueReconcileAction(
                activeAccount = null,
                currentAccount = alice,
            ),
        )

        assertEquals(
            QueueReconcileAction.PRESERVE,
            queueReconcileAction(
                activeAccount = alice,
                currentAccount = sameAlice,
            ),
        )

        assertEquals(
            QueueReconcileAction.CLEAR,
            queueReconcileAction(
                activeAccount = alice,
                currentAccount = null,
            ),
        )

        assertEquals(
            QueueReconcileAction.CLEAR_AND_RESTORE,
            queueReconcileAction(
                activeAccount = alice,
                currentAccount = bob,
            ),
        )
    }

    @Test
    fun staleRestoreCannotOverwriteLaterRuntimeState() {
        assertTrue(
            canApplyQueueRestore(
                expectedGeneration = 7L,
                currentGeneration = 7L,
                activeAccount = null,
            ),
        )

        assertFalse(
            canApplyQueueRestore(
                expectedGeneration = 7L,
                currentGeneration = 8L,
                activeAccount = null,
            ),
        )

        assertFalse(
            canApplyQueueRestore(
                expectedGeneration = 7L,
                currentGeneration = 7L,
                activeAccount = alice,
            ),
        )
    }

    @Test
    fun standardMedia3NavigationCommandsMapToDomainDirections() {
        assertEquals(
            QueueNavigationDirection.NEXT,
            queueNavigationDirection(
                Player.COMMAND_SEEK_TO_NEXT,
            ),
        )

        assertEquals(
            QueueNavigationDirection.NEXT,
            queueNavigationDirection(
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            ),
        )

        assertEquals(
            QueueNavigationDirection.PREVIOUS,
            queueNavigationDirection(
                Player.COMMAND_SEEK_TO_PREVIOUS,
            ),
        )

        assertEquals(
            QueueNavigationDirection.PREVIOUS,
            queueNavigationDirection(
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            ),
        )

        assertNull(
            queueNavigationDirection(
                Player.COMMAND_SEEK_FORWARD,
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
