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

class ServerConnectionCredentialRaceTest {
    @Test
    fun profileReplacementWhilePersistenceIsSuspendedClearsStaleCredential() =
        runTest {
            val oldProfile =
                profile("https://old.example.com/navidrome")
            val newProfile =
                profile("https://new.example.com/navidrome")

            val repository = FakeRepository(oldProfile)
            val store =
                ControlledSecretStore(
                    blockFirstSave = true,
                    ignoreFirstSaveCancellation = true,
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
            assertEquals(1, store.saveCalls)

            replaceProfile(
                viewModel = viewModel,
                profile = newProfile,
            )

            testScheduler.runCurrent()

            assertEquals(newProfile, viewModel.state.profile)

            store.releaseFirstSave.complete(Unit)
            testScheduler.runCurrent()

            assertNull(store.savedIdentity)
            assertNull(store.savedSecret)
            assertTrue(store.clearCalls >= 1)
            assertNull(viewModel.state.identity)
            assertNull(viewModel.state.metadata)
            assertTrue(
                viewModel.state.sessionStatus !=
                    SessionStatus.AUTHENTICATED,
            )
        }

    @Test
    fun newerAuthenticationWinsOverOlderSuspendedPersistence() =
        runTest {
            val oldProfile =
                profile("https://old.example.com/navidrome")
            val newProfile =
                profile("https://new.example.com/navidrome")

            val repository = FakeRepository(oldProfile)
            val store =
                ControlledSecretStore(
                    blockFirstSave = true,
                    ignoreFirstSaveCancellation = true,
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

            authenticate(
                viewModel = viewModel,
                username = "Bob",
                password = "new-password",
            )

            testScheduler.runCurrent()

            store.releaseFirstSave.complete(Unit)
            testScheduler.runCurrent()

            val expectedIdentity =
                ServerAccountIdentity(
                    endpoint = newProfile.endpoint,
                    username = "Bob",
                )

            assertEquals(expectedIdentity, store.savedIdentity)
            assertEquals("new-password", store.savedSecret)
            assertEquals(expectedIdentity, viewModel.state.identity)
            assertEquals(
                SessionStatus.AUTHENTICATED,
                viewModel.state.sessionStatus,
            )
            assertTrue(store.clearCalls >= 1)
        }

    private fun TestScope.viewModel(
        repository: FakeRepository,
        store: ControlledSecretStore,
    ): ServerConnectionViewModel =
        ServerConnectionViewModel(
            repository = repository,
            scope = backgroundScope,
            secretStore = store,
            pingClient =
                ImmediatePingClient(
                    AuthResult.Authenticated(metadata()),
                ),
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
        initial: ServerProfile,
    ) : ServerProfileRepository {
        val profileState =
            MutableStateFlow<ServerProfile?>(initial)

        override val profile = profileState

        override suspend fun save(profile: ServerProfile) {
            profileState.value = profile
        }

        override suspend fun delete() {
            profileState.value = null
        }
    }

    private class ControlledSecretStore(
        private val blockFirstSave: Boolean,
        private val ignoreFirstSaveCancellation: Boolean,
    ) : AuthSecretStore {
        val firstSaveStarted = CompletableDeferred<Unit>()
        val releaseFirstSave = CompletableDeferred<Unit>()

        var saveCalls = 0
            private set

        var clearCalls = 0
            private set

        var savedIdentity: ServerAccountIdentity? = null
            private set

        var savedSecret: String? = null
            private set

        override suspend fun save(
            identity: ServerAccountIdentity,
            secret: String,
        ): Result<Unit> {
            saveCalls += 1
            val call = saveCalls

            if (blockFirstSave && call == 1) {
                firstSaveStarted.complete(Unit)

                if (ignoreFirstSaveCancellation) {
                    withContext(NonCancellable) {
                        releaseFirstSave.await()
                    }
                } else {
                    releaseFirstSave.await()
                }
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
            savedIdentity = null
            savedSecret = null
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
