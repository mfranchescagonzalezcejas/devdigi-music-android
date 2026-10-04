package dev.devdigi.music.features.playback.data

import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionPolicyTest {
    @Test
    fun ownApplicationControllerRequiresMatchingUidAndPackage() {
        assertTrue(
            isOwnApplicationController(
                controllerPackageName = "dev.devdigi.music",
                controllerUid = 1234,
                applicationPackageName = "dev.devdigi.music",
                applicationUid = 1234,
            ),
        )

        assertFalse(
            isOwnApplicationController(
                controllerPackageName = "example.external",
                controllerUid = 1234,
                applicationPackageName = "dev.devdigi.music",
                applicationUid = 1234,
            ),
        )

        assertFalse(
            isOwnApplicationController(
                controllerPackageName = "dev.devdigi.music",
                controllerUid = 5678,
                applicationPackageName = "dev.devdigi.music",
                applicationUid = 1234,
            ),
        )
    }

    @Test
    fun servicePlaybackClearsOnlyForOwnedPlaybackWithDifferentCurrentAccount() {
        val alice = account("alice")
        val sameAlice = account("alice")
        val bob = account("bob")

        assertFalse(
            shouldClearServicePlayback(
                activeAccount = null,
                currentAccount = alice,
            ),
        )

        assertFalse(
            shouldClearServicePlayback(
                activeAccount = alice,
                currentAccount = sameAlice,
            ),
        )

        assertTrue(
            shouldClearServicePlayback(
                activeAccount = alice,
                currentAccount = bob,
            ),
        )

        assertTrue(
            shouldClearServicePlayback(
                activeAccount = alice,
                currentAccount = null,
            ),
        )
    }

    @Test
    fun resolvedStreamAppliesOnlyToCurrentGenerationAccountAndTrack() {
        val alice = account("alice")
        val bob = account("bob")
        val expected = track("expected")
        val replacement = track("replacement")

        assertTrue(
            canApplyResolvedStream(
                expectedGeneration = 7L,
                currentGeneration = 7L,
                expectedAccount = alice,
                activeAccount = alice,
                expectedTrack = expected,
                currentTrack = expected,
            ),
        )

        assertFalse(
            canApplyResolvedStream(
                expectedGeneration = 7L,
                currentGeneration = 8L,
                expectedAccount = alice,
                activeAccount = alice,
                expectedTrack = expected,
                currentTrack = expected,
            ),
        )

        assertFalse(
            canApplyResolvedStream(
                expectedGeneration = 7L,
                currentGeneration = 7L,
                expectedAccount = alice,
                activeAccount = bob,
                expectedTrack = expected,
                currentTrack = expected,
            ),
        )

        assertFalse(
            canApplyResolvedStream(
                expectedGeneration = 7L,
                currentGeneration = 7L,
                expectedAccount = alice,
                activeAccount = alice,
                expectedTrack = expected,
                currentTrack = replacement,
            ),
        )
    }

    private fun track(id: String): PlaybackTrack =
        PlaybackTrack(
            id = id,
            title = "Track $id",
            artist = "Synthetic Artist",
        )

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
