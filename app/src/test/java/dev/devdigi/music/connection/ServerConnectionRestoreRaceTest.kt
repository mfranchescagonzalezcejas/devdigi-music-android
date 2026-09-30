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

class ServerConnectionRestoreRaceTest {
    @Test
    fun profileReplacementWhileRestorePingIsInFlightCannotPublishOldIdentity() =
        runTest {
            val oldProfile =
                profile("https://old.example.com/navidrome")
            val newProfile =
                profile("https://new.example.com/navidrome")

            val repository = FakeRepository(oldProfile)
            val store =
                StatefulSecretStore(
                    identity = identity(oldProfile, "Alice"),
                    secret = "old-password",
                )
            val client = ControlledFirstPingClient()

            val viewModel =
                viewModel(
                    repository = repository,
                    store = store,
                    client = client,
                )

            testScheduler.runCurrent()

            assertTrue(client.firstStarted.isCompleted)
            assertEquals(
                SessionStatus.RESTORING,
                viewModel.state.sessionStatus,
            )

            replaceProfile(viewModel, newProfile)
            testScheduler.runCurrent()

            client.firstResult.complete(
                AuthResult.Authenticated(metadata()),
            )
            testScheduler.runCurrent()

            assertEquals(newProfile, viewModel.state.profile)
            assertNull(viewModel.state.identity)
            assertNull(viewModel.state.metadata)
            assertTrue(
                viewModel.state.sessionStatus !=
                    SessionStatus.AUTHENTICATED,
            )
        }

    @Test
    fun staleInvalidRestoreCannotClearCredentialFromNewerAuthentication() =
        runTest {
            val oldProfile =
                profile("https://old.example.com/navidrome")
            val newProfile =
                profile("https://new.example.com/navidrome")

            val repository = FakeRepository(oldProfile)
            val store =
                StatefulSecretStore(
                    identity = identity(oldProfile, "Alice"),
                    secret = "old-password",
                )
            val client = ControlledFirstPingClient()

            val viewModel =
                viewModel(
                    repository = repository,
                    store = store,
                    client = client,
                )

            testScheduler.runCurrent()
            assertTrue(client.firstStarted.isCompleted)

            replaceProfile(viewModel, newProfile)
            testScheduler.runCurrent()

            authenticate(
                viewModel = viewModel,
                username = "Bob",
                password = "new-password",
            )
            testScheduler.runCurrent()

            val expectedIdentity =
                identity(newProfile, "Bob")

            assertEquals(expectedIdentity, store.savedIdentity)
            assertEquals("new-password", store.savedSecret)
            assertEquals(
                SessionStatus.AUTHENTICATED,
                viewModel.state.sessionStatus,
            )

            client.firstResult.complete(
                AuthResult.InvalidCredentials,
            )
            testScheduler.runCurrent()

            assertEquals(expectedIdentity, store.savedIdentity)
            assertEquals("new-password", store.savedSecret)
            assertEquals(expectedIdentity, viewModel.state.identity)
            assertEquals(
                SessionStatus.AUTHENTICATED,
                viewModel.state.sessionStatus,
            )
        }

    @Test
    fun signOutWhileRestoreIsInFlightPreventsRestoreFromReauthenticating() =
        runTest {
            val savedProfile =
                profile("https://music.example.com/navidrome")

            val repository = FakeRepository(savedProfile)
            val store =
                StatefulSecretStore(
                    identity = identity(savedProfile, "Alice"),
                    secret = "old-password",
                )
            val client = ControlledFirstPingClient()

            val viewModel =
                viewModel(
                    repository = repository,
                    store = store,
                    client = client,
                )

            testScheduler.runCurrent()
            assertTrue(client.firstStarted.isCompleted)

            viewModel.signOut()
            testScheduler.runCurrent()

            assertNull(store.savedIdentity)
            assertEquals(
                SessionStatus.SIGNED_OUT,
                viewModel.state.sessionStatus,
            )

            client.firstResult.complete(
                AuthResult.Authenticated(metadata()),
            )
            testScheduler.runCurrent()

            assertNull(viewModel.state.identity)
            assertNull(viewModel.state.metadata)
            assertEquals(
                SessionStatus.SIGNED_OUT,
                viewModel.state.sessionStatus,
            )
        }

    @Test
    fun invalidCredentialsForCurrentRestoreClearStoredCredential() =
        runTest {
            val savedProfile =
                profile("https://music.example.com/navidrome")

            val repository = FakeRepository(savedProfile)
            val store =
                StatefulSecretStore(
                    identity = identity(savedProfile, "Alice"),
                    secret = "old-password",
                )
            val client =
                ImmediatePingClient(
                    AuthResult.InvalidCredentials,
                )

            val viewModel =
                viewModel(
                    repository = repository,
                    store = store,
                    client = client,
                )

            testScheduler.runCurrent()

            assertEquals(1, store.clearCalls)
            assertNull(store.savedIdentity)
            assertNull(store.savedSecret)
            assertNull(viewModel.state.identity)
            assertEquals(
                SessionStatus.SIGNED_OUT,
                viewModel.state.sessionStatus,
            )
        }

    private fun TestScope.viewModel(
        repository: FakeRepository,
        store: StatefulSecretStore,
        client: AuthenticatedPingClient,
    ): ServerConnectionViewModel {
        val restorer =
            SessionRestorer(
                repository = repository,
                secretStore = store,
                pingClient = client,
            )

        return ServerConnectionViewModel(
            repository = repository,
            scope = backgroundScope,
            secretStore = store,
            pingClient = client,
            sessionRestorer = restorer,
        )
    }

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
        val profileState = MutableStateFlow(initial)

        override val profile = profileState

        override suspend fun save(profile: ServerProfile) {
            profileState.value = profile
        }

        override suspend fun delete() {
            profileState.value = null
        }
    }

    private class StatefulSecretStore(
        identity: ServerAccountIdentity?,
        secret: String?,
    ) : AuthSecretStore {
        var savedIdentity = identity
            private set

        var savedSecret = secret
            private set

        var clearCalls = 0
            private set

        override suspend fun save(
            identity: ServerAccountIdentity,
            secret: String,
        ): Result<Unit> {
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

    private class ControlledFirstPingClient : AuthenticatedPingClient {
        val firstStarted = CompletableDeferred<Unit>()
        val firstResult =
            CompletableDeferred<AuthResult>()

        private var calls = 0

        override suspend fun ping(
            credentials: AuthCredentials,
            profile: ServerProfile,
        ): AuthResult {
            calls += 1

            if (calls == 1) {
                firstStarted.complete(Unit)

                return withContext(NonCancellable) {
                    firstResult.await()
                }
            }

            return AuthResult.Authenticated(
                metadata(),
            )
        }

        private fun metadata(): ServerMetadata =
            ServerMetadata(
                serverType = "navidrome",
                serverVersion = "0.61.2",
                openSubsonic = true,
            )
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
