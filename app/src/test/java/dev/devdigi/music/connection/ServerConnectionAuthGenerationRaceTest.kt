package dev.devdigi.music.connection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionAuthGenerationRaceTest {
    @Test
    fun profileReplacementAfterCompletedPingCannotPersistOldAuthentication() =
        runTest {
            val oldProfile = profile("https://old.example.com/navidrome")
            val newProfile = profile("https://new.example.com/navidrome")
            val repository = FakeRepository(oldProfile)
            val store = RecordingStore()
            val ping = ControlledPing()

            val viewModel = viewModel(repository, store, ping)
            testScheduler.runCurrent()

            authenticate(viewModel)
            testScheduler.runCurrent()

            assertTrue(ping.completedRemotely.isCompleted)

            replaceProfile(viewModel, newProfile)
            testScheduler.runCurrent()

            ping.deliver.complete(Unit)
            testScheduler.runCurrent()

            assertEquals(newProfile, viewModel.state.profile)
            assertEquals(0, store.saveCalls)
            assertNull(viewModel.state.identity)
            assertTrue(
                viewModel.state.sessionStatus !=
                    SessionStatus.AUTHENTICATED,
            )
        }

    @Test
    fun externalProfileReplacementInvalidatesCompletedInFlightPing() =
        runTest {
            val oldProfile = profile("https://old.example.com/navidrome")
            val newProfile = profile("https://new.example.com/navidrome")
            val repository = FakeRepository(oldProfile)
            val store = RecordingStore()
            val ping = ControlledPing()

            val viewModel = viewModel(repository, store, ping)
            testScheduler.runCurrent()

            authenticate(viewModel)
            testScheduler.runCurrent()

            assertTrue(ping.completedRemotely.isCompleted)

            repository.profileState.value = newProfile
            testScheduler.runCurrent()

            ping.deliver.complete(Unit)
            testScheduler.runCurrent()

            assertEquals(newProfile, viewModel.state.profile)
            assertEquals(0, store.saveCalls)
            assertNull(viewModel.state.identity)
            assertTrue(
                viewModel.state.sessionStatus !=
                    SessionStatus.AUTHENTICATED,
            )
        }

    private fun TestScope.viewModel(
        repository: FakeRepository,
        store: RecordingStore,
        ping: ControlledPing,
    ) = ServerConnectionViewModel(
        repository = repository,
        scope = backgroundScope,
        secretStore = store,
        pingClient = ping,
    )

    private fun authenticate(viewModel: ServerConnectionViewModel) {
        viewModel.onUsernameChanged("Alice")
        viewModel.onPasswordChanged("synthetic-password")
        viewModel.signIn()
    }

    private fun replaceProfile(
        viewModel: ServerConnectionViewModel,
        profile: ServerProfile,
    ) {
        viewModel.onEndpointChanged(profile.endpoint.value)
        viewModel.confirm()
    }

    private class FakeRepository(
        initial: ServerProfile,
    ) : ServerProfileRepository {
        val profileState = MutableStateFlow<ServerProfile?>(initial)

        override val profile = profileState

        override suspend fun save(profile: ServerProfile) {
            profileState.value = profile
        }

        override suspend fun delete() {
            profileState.value = null
        }
    }

    private class RecordingStore : AuthSecretStore {
        var saveCalls = 0
            private set

        override suspend fun save(
            identity: ServerAccountIdentity,
            secret: String,
        ): Result<Unit> {
            saveCalls += 1
            return Result.success(Unit)
        }

        override suspend fun read(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?> = Result.success(null)

        override suspend fun clear() = Unit
    }

    private class ControlledPing : AuthenticatedPingClient {
        val completedRemotely = CompletableDeferred<Unit>()
        val deliver = CompletableDeferred<Unit>()

        override suspend fun ping(
            credentials: AuthCredentials,
            profile: ServerProfile,
        ): AuthResult {
            completedRemotely.complete(Unit)

            withContext(NonCancellable) {
                deliver.await()
            }

            return AuthResult.Authenticated(metadata())
        }

        private fun metadata() =
            ServerMetadata(
                serverType = "navidrome",
                serverVersion = "0.61.2",
                openSubsonic = true,
            )
    }

    private fun profile(value: String) = ServerProfile(endpoint(value))

    private fun endpoint(value: String): ServerEndpoint =
        (
            ServerEndpoint.parse(value)
                as EndpointParseResult.Valid
        ).endpoint
}
