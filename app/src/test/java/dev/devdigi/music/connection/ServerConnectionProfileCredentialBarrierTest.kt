package dev.devdigi.music.connection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionProfileCredentialBarrierTest {
    @Test
    fun staleCleanupFailureIsRetriedBeforeProfileReplacementCommits() =
        runTest {
            val oldProfile =
                profile("https://old.example.com/navidrome")
            val newProfile =
                profile("https://new.example.com/navidrome")

            val repository = FakeRepository(oldProfile)
            val store =
                BarrierSecretStore(
                    blockFirstSave = true,
                    failingClearCalls = setOf(1),
                )

            val viewModel =
                viewModel(
                    repository = repository,
                    store = store,
                )

            testScheduler.runCurrent()

            authenticate(
                viewModel = viewModel,
                username = "Alice",
                password = "old-password",
            )

            testScheduler.runCurrent()

            assertTrue(store.firstSaveStarted.isCompleted)

            replaceProfile(
                viewModel = viewModel,
                profile = newProfile,
            )

            testScheduler.runCurrent()

            assertEquals(oldProfile, repository.profileState.value)
            assertEquals(0, repository.saveCalls)

            store.releaseFirstSave.complete(Unit)
            testScheduler.runCurrent()

            assertEquals(2, store.clearCalls)
            assertEquals(1, repository.saveCalls)
            assertEquals(newProfile, repository.profileState.value)
            assertEquals(newProfile, viewModel.state.profile)
            assertNull(store.savedIdentity)
            assertNull(store.savedSecret)
            assertNull(viewModel.state.identity)
            assertEquals(
                SessionStatus.SIGNED_OUT,
                viewModel.state.sessionStatus,
            )
        }

    @Test
    fun clearFailurePreventsProfileReplacementFromCommitting() =
        runTest {
            val oldProfile =
                profile("https://old.example.com/navidrome")
            val newProfile =
                profile("https://new.example.com/navidrome")

            val oldIdentity =
                identity(oldProfile, "Alice")

            val repository = FakeRepository(oldProfile)
            val store =
                BarrierSecretStore(
                    initialIdentity = oldIdentity,
                    initialSecret = "old-password",
                    failEveryClear = true,
                )

            val viewModel =
                viewModel(
                    repository = repository,
                    store = store,
                )

            testScheduler.runCurrent()

            replaceProfile(
                viewModel = viewModel,
                profile = newProfile,
            )

            testScheduler.runCurrent()

            assertEquals(1, store.clearCalls)
            assertEquals(0, repository.saveCalls)
            assertEquals(oldProfile, repository.profileState.value)
            assertEquals(oldProfile, viewModel.state.profile)
            assertEquals(oldIdentity, store.savedIdentity)
            assertEquals("old-password", store.savedSecret)
            assertNull(viewModel.state.identity)
            assertEquals(
                SessionStatus.SIGNED_OUT,
                viewModel.state.sessionStatus,
            )
            assertEquals(
                "Unable to clear saved credentials. Server was not changed.",
                viewModel.state.statusMessage,
            )
        }

    @Test
    fun clearFailurePreventsProfileDeletionFromCommitting() =
        runTest {
            val savedProfile =
                profile("https://music.example.com/navidrome")

            val savedIdentity =
                identity(savedProfile, "Alice")

            val repository = FakeRepository(savedProfile)
            val store =
                BarrierSecretStore(
                    initialIdentity = savedIdentity,
                    initialSecret = "old-password",
                    failEveryClear = true,
                )

            val viewModel =
                viewModel(
                    repository = repository,
                    store = store,
                )

            testScheduler.runCurrent()

            viewModel.delete()
            testScheduler.runCurrent()

            assertEquals(1, store.clearCalls)
            assertEquals(0, repository.deleteCalls)
            assertEquals(savedProfile, repository.profileState.value)
            assertEquals(savedProfile, viewModel.state.profile)
            assertEquals(savedIdentity, store.savedIdentity)
            assertEquals("old-password", store.savedSecret)
            assertEquals(
                "Unable to clear saved credentials. Server was not deleted.",
                viewModel.state.statusMessage,
            )
        }

    @Test
    fun signInDuringPendingProfileMutationDoesNotPingOldServer() =
        runTest {
            val oldProfile =
                profile("https://old.example.com/navidrome")
            val newProfile =
                profile("https://new.example.com/navidrome")

            val repository = FakeRepository(oldProfile)
            val store =
                BarrierSecretStore(
                    blockFirstSave = true,
                )
            val client = CountingPingClient()

            val viewModel =
                viewModel(
                    repository = repository,
                    store = store,
                    client = client,
                )

            testScheduler.runCurrent()

            authenticate(
                viewModel = viewModel,
                username = "Alice",
                password = "old-password",
            )

            testScheduler.runCurrent()

            assertTrue(store.firstSaveStarted.isCompleted)
            assertEquals(1, client.calls)
            assertEquals(
                listOf(oldProfile),
                client.profiles,
            )

            replaceProfile(
                viewModel = viewModel,
                profile = newProfile,
            )

            testScheduler.runCurrent()

            authenticate(
                viewModel = viewModel,
                username = "Bob",
                password = "new-password",
            )

            testScheduler.runCurrent()

            assertEquals(1, client.calls)
            assertEquals(
                listOf(oldProfile),
                client.profiles,
            )
            assertEquals(
                "Wait for the server change to finish.",
                viewModel.state.statusMessage,
            )

            store.releaseFirstSave.complete(Unit)
            testScheduler.runCurrent()

            assertEquals(newProfile, viewModel.state.profile)

            authenticate(
                viewModel = viewModel,
                username = "Bob",
                password = "new-password",
            )

            testScheduler.runCurrent()

            assertEquals(2, client.calls)
            assertEquals(
                listOf(oldProfile, newProfile),
                client.profiles,
            )
            assertEquals(
                SessionStatus.AUTHENTICATED,
                viewModel.state.sessionStatus,
            )
        }

    private fun TestScope.viewModel(
        repository: FakeRepository,
        store: BarrierSecretStore,
        client: AuthenticatedPingClient =
            ImmediatePingClient(
                AuthResult.Authenticated(metadata()),
            ),
    ): ServerConnectionViewModel =
        ServerConnectionViewModel(
            repository = repository,
            scope = backgroundScope,
            secretStore = store,
            pingClient = client,
        )

    private fun authenticate(
        viewModel: ServerConnectionViewModel,
        username: String,
        password: String,
    ) {
        viewModel.onUsernameChanged(username)
        viewModel.onPasswordChanged(password)
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
        initial: ServerProfile?,
    ) : ServerProfileRepository {
        val profileState =
            MutableStateFlow(initial)

        var saveCalls = 0
            private set

        var deleteCalls = 0
            private set

        override val profile = profileState

        override suspend fun save(profile: ServerProfile) {
            saveCalls += 1
            profileState.value = profile
        }

        override suspend fun delete() {
            deleteCalls += 1
            profileState.value = null
        }
    }

    private class BarrierSecretStore(
        initialIdentity: ServerAccountIdentity? = null,
        initialSecret: String? = null,
        private val blockFirstSave: Boolean = false,
        private val failingClearCalls: Set<Int> = emptySet(),
        private val failEveryClear: Boolean = false,
    ) : AuthSecretStore {
        val firstSaveStarted =
            CompletableDeferred<Unit>()

        val releaseFirstSave =
            CompletableDeferred<Unit>()

        var savedIdentity = initialIdentity
            private set

        var savedSecret = initialSecret
            private set

        var saveCalls = 0
            private set

        var clearCalls = 0
            private set

        override suspend fun save(
            identity: ServerAccountIdentity,
            secret: String,
        ): Result<Unit> {
            saveCalls += 1

            if (blockFirstSave && saveCalls == 1) {
                firstSaveStarted.complete(Unit)
                releaseFirstSave.await()
            }

            savedIdentity = identity
            savedSecret = secret

            return Result.success(Unit)
        }

        override suspend fun read(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?> {
            val identity =
                savedIdentity
                    ?: return Result.success(null)

            if (identity.endpoint != expectedEndpoint) {
                return Result.success(null)
            }

            return Result.success(
                StoredCredentials(
                    username = identity.username,
                    secret = checkNotNull(savedSecret),
                ),
            )
        }

        override suspend fun clear() {
            clearCalls += 1

            if (
                failEveryClear ||
                clearCalls in failingClearCalls
            ) {
                throw IllegalStateException(
                    "synthetic clear failure",
                )
            }

            savedIdentity = null
            savedSecret = null
        }
    }

    private class CountingPingClient : AuthenticatedPingClient {
        var calls = 0
            private set

        val profiles =
            mutableListOf<ServerProfile>()

        override suspend fun ping(
            credentials: AuthCredentials,
            profile: ServerProfile,
        ): AuthResult {
            calls += 1
            profiles += profile

            return AuthResult.Authenticated(
                ServerMetadata(
                    serverType = "navidrome",
                    serverVersion = "0.61.2",
                    openSubsonic = true,
                ),
            )
        }
    }

    private class ImmediatePingClient(
        private val result: AuthResult,
    ) : AuthenticatedPingClient {
        override suspend fun ping(
            credentials: AuthCredentials,
            profile: ServerProfile,
        ): AuthResult = result
    }

    private fun profile(value: String): ServerProfile = ServerProfile(endpoint(value))

    private fun identity(
        profile: ServerProfile,
        username: String,
    ): ServerAccountIdentity =
        ServerAccountIdentity(
            endpoint = profile.endpoint,
            username = username,
        )

    private fun metadata(): ServerMetadata =
        ServerMetadata(
            serverType = "navidrome",
            serverVersion = "0.61.2",
            openSubsonic = true,
        )

    private fun endpoint(value: String): ServerEndpoint =
        (
            ServerEndpoint.parse(value)
                as EndpointParseResult.Valid
        ).endpoint
}
