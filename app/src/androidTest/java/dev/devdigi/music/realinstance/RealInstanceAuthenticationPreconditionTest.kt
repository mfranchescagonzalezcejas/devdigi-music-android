package dev.devdigi.music.realinstance

import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.connection.ServerProfile
import dev.devdigi.music.connection.ServerProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealInstanceAuthenticationPreconditionTest {
    @Test
    fun delayedProfileEmissionIsReadBeforeAuthenticationCanProceed() =
        runBlocking {
            val saved = profile("https://music.example.com/navidrome")
            val repository =
                FakeServerProfileRepository(
                    flow {
                        delay(50)
                        emit(saved)
                    },
                )

            val pendingRead =
                async {
                    AuthenticationProfilePrecondition.read(repository)
                }
            assertEquals(saved, pendingRead.await())
            assertEquals(0, repository.saveCalls)
            assertEquals(0, repository.deleteCalls)
        }

    @Test
    fun nullThenProfileEmissionIsAwaitedAfterSave() =
        runBlocking {
            val saved = profile("https://music.example.com/navidrome")
            val repository =
                FakeServerProfileRepository(
                    flow {
                        emit(null)
                        delay(50)
                        emit(saved)
                    },
                )

            assertEquals(
                saved,
                AuthenticationProfilePrecondition.awaitSavedProfile(repository),
            )
            assertEquals(0, repository.saveCalls)
            assertEquals(0, repository.deleteCalls)
        }

    @Test
    fun profileMismatchIsRejectedWithoutRepositoryMutation() {
        val repository =
            FakeServerProfileRepository(
                flow { emit(profile("https://other.example.com")) },
            )
        val persisted = runBlocking { AuthenticationProfilePrecondition.read(repository) }

        assertFalse(
            AuthenticationProfilePrecondition.matches(
                persisted!!,
                endpoint("https://music.example.com"),
            ),
        )
        assertEquals(0, repository.saveCalls)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun canonicalEquivalentEndpointMatchesPersistedProfile() {
        val repository =
            FakeServerProfileRepository(
                flow { emit(profile("https://music.example.com/navidrome")) },
            )
        val persisted = runBlocking { AuthenticationProfilePrecondition.read(repository) }
        val entered = endpoint("HTTPS://MUSIC.EXAMPLE.COM:443/navidrome/")

        assertTrue(AuthenticationProfilePrecondition.matches(persisted!!, entered))
        assertEquals(0, repository.saveCalls)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun absentProfileAllowsFreshSaveEligibilityWithoutSaving() {
        val repository = FakeServerProfileRepository(flow { emit(null) })

        assertNull(runBlocking { AuthenticationProfilePrecondition.read(repository) })
        assertEquals(0, repository.saveCalls)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun profileAppearingBeforeFinalSaveRecheckPreventsSave() =
        runBlocking {
            val profileState = MutableStateFlow<ServerProfile?>(null)
            val repository = FakeServerProfileRepository(profileState)
            assertNull(AuthenticationProfilePrecondition.read(repository))
            profileState.value = profile("https://music.example.com")

            val finalRecheck = AuthenticationProfilePrecondition.read(repository)
            assertTrue(
                AuthenticationProfilePrecondition.matches(
                    finalRecheck!!,
                    endpoint("https://music.example.com"),
                ),
            )
            assertEquals(0, repository.saveCalls)
            assertEquals(0, repository.deleteCalls)
        }

    private class FakeServerProfileRepository(
        override val profile: Flow<ServerProfile?>,
    ) : ServerProfileRepository {
        var saveCalls = 0
        var deleteCalls = 0

        override suspend fun save(profile: ServerProfile) {
            saveCalls += 1
        }

        override suspend fun delete() {
            deleteCalls += 1
        }
    }

    private companion object {
        fun profile(value: String) = ServerProfile(endpoint(value))

        fun endpoint(value: String) = (ServerEndpoint.parse(value) as EndpointParseResult.Valid).endpoint
    }
}

internal object AuthenticationProfilePrecondition {
    suspend fun awaitSavedProfile(repository: ServerProfileRepository): ServerProfile =
        withTimeout(10_000L) {
            repository.profile.filterNotNull().first()
        }

    suspend fun read(repository: ServerProfileRepository): ServerProfile? =
        withTimeout(10_000L) {
            repository.profile.first()
        }

    fun matches(
        profile: ServerProfile,
        expected: ServerEndpoint,
    ): Boolean = profile.endpoint == expected
}
