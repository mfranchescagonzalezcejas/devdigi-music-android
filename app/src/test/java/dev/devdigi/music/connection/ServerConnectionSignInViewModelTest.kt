package dev.devdigi.music.connection

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionSignInViewModelTest {
    @Test
    fun usernameAndPasswordRemainTransientDraftState() =
        runTest {
            val viewModel =
                viewModel(
                    repository = FakeRepository(profile()),
                    store = FakeSecretStore(),
                    client = RecordingPingClient(AuthResult.NetworkError),
                )

            testScheduler.runCurrent()

            viewModel.onUsernameChanged("Alice")
            viewModel.onPasswordChanged("synthetic-password")

            assertEquals("Alice", viewModel.state.usernameInput)
            assertEquals(
                "synthetic-password",
                viewModel.state.passwordInput,
            )
        }

    @Test
    fun successfulSignInPingsBeforePersistingThenPublishesAuthenticated() =
        runTest {
            val profile = profile()
            val events = mutableListOf<String>()
            val metadata = metadata()
            val store = FakeSecretStore(events = events)
            val client =
                RecordingPingClient(
                    result = AuthResult.Authenticated(metadata),
                    events = events,
                )
            val viewModel =
                viewModel(
                    repository = FakeRepository(profile),
                    store = store,
                    client = client,
                )

            testScheduler.runCurrent()

            viewModel.onUsernameChanged("Alice")
            viewModel.onPasswordChanged("synthetic-password")
            viewModel.signIn()
            testScheduler.runCurrent()

            assertEquals(listOf("ping", "save"), events)
            assertEquals(1, store.saveCalls)
            assertEquals(
                ServerAccountIdentity(
                    endpoint = profile.endpoint,
                    username = "Alice",
                ),
                store.lastIdentity,
            )
            assertEquals(
                SessionStatus.AUTHENTICATED,
                viewModel.state.sessionStatus,
            )
            assertEquals(store.lastIdentity, viewModel.state.identity)
            assertEquals(metadata, viewModel.state.metadata)
            assertEquals("", viewModel.state.passwordInput)
            assertEquals(
                Authentication.AUTHENTICATED,
                viewModel.state.connectionFacts.authentication,
            )
        }

    @Test
    fun rejectedCredentialsAreNeverPersisted() =
        runTest {
            val store = FakeSecretStore()
            val viewModel =
                viewModel(
                    repository = FakeRepository(profile()),
                    store = store,
                    client =
                        RecordingPingClient(
                            AuthResult.InvalidCredentials,
                        ),
                )

            testScheduler.runCurrent()

            viewModel.onUsernameChanged("Alice")
            viewModel.onPasswordChanged("wrong-password")
            viewModel.signIn()
            testScheduler.runCurrent()

            assertEquals(0, store.saveCalls)
            assertNull(viewModel.state.identity)
            assertNull(viewModel.state.metadata)
            assertEquals(
                SessionStatus.SIGNED_OUT,
                viewModel.state.sessionStatus,
            )
            assertEquals(
                Authentication.REJECTED,
                viewModel.state.connectionFacts.authentication,
            )
        }

    @Test
    fun networkFailureIsNeverPersistedOrAuthenticated() =
        runTest {
            val store = FakeSecretStore()
            val viewModel =
                viewModel(
                    repository = FakeRepository(profile()),
                    store = store,
                    client =
                        RecordingPingClient(
                            AuthResult.NetworkError,
                        ),
                )

            testScheduler.runCurrent()

            viewModel.onUsernameChanged("Alice")
            viewModel.onPasswordChanged("synthetic-password")
            viewModel.signIn()
            testScheduler.runCurrent()

            assertEquals(0, store.saveCalls)
            assertNull(viewModel.state.identity)
            assertEquals(
                Reachability.UNREACHABLE,
                viewModel.state.connectionFacts.reachability,
            )
            assertTrue(
                viewModel.state.sessionStatus !=
                    SessionStatus.AUTHENTICATED,
            )
        }

    @Test
    fun securePersistenceFailureAfterSuccessfulPingFailsClosed() =
        runTest {
            val store =
                FakeSecretStore(
                    saveFailure =
                        IllegalStateException(
                            "synthetic save failure",
                        ),
                )
            val viewModel =
                viewModel(
                    repository = FakeRepository(profile()),
                    store = store,
                    client =
                        RecordingPingClient(
                            AuthResult.Authenticated(metadata()),
                        ),
                )

            testScheduler.runCurrent()

            viewModel.onUsernameChanged("Alice")
            viewModel.onPasswordChanged("synthetic-password")
            viewModel.signIn()
            testScheduler.runCurrent()

            assertEquals(1, store.saveCalls)
            assertNull(viewModel.state.identity)
            assertNull(viewModel.state.metadata)
            assertEquals(
                SessionStatus.SIGNED_OUT,
                viewModel.state.sessionStatus,
            )
            assertTrue(
                viewModel.state.connectionFacts.authentication !=
                    Authentication.AUTHENTICATED,
            )
        }

    @Test
    fun signInWithoutSavedProfileDoesNotPingOrPersist() =
        runTest {
            val store = FakeSecretStore()
            val client =
                RecordingPingClient(
                    AuthResult.Authenticated(metadata()),
                )
            val viewModel =
                viewModel(
                    repository = FakeRepository(null),
                    store = store,
                    client = client,
                )

            testScheduler.runCurrent()

            viewModel.onUsernameChanged("Alice")
            viewModel.onPasswordChanged("synthetic-password")
            viewModel.signIn()
            testScheduler.runCurrent()

            assertEquals(0, client.calls)
            assertEquals(0, store.saveCalls)
            assertNull(viewModel.state.identity)
        }

    private fun TestScope.viewModel(
        repository: FakeRepository,
        store: FakeSecretStore,
        client: AuthenticatedPingClient,
    ): ServerConnectionViewModel =
        ServerConnectionViewModel(
            repository = repository,
            scope = backgroundScope,
            secretStore = store,
            pingClient = client,
        )

    private class FakeRepository(
        initial: ServerProfile?,
    ) : ServerProfileRepository {
        override val profile =
            kotlinx.coroutines.flow.MutableStateFlow(initial)

        override suspend fun save(profile: ServerProfile) {
            this.profile.value = profile
        }

        override suspend fun delete() {
            profile.value = null
        }
    }

    private class FakeSecretStore(
        private val saveFailure: Throwable? = null,
        private val events: MutableList<String>? = null,
    ) : AuthSecretStore {
        var saveCalls = 0
            private set

        var lastIdentity: ServerAccountIdentity? = null
            private set

        override suspend fun save(
            identity: ServerAccountIdentity,
            secret: String,
        ): Result<Unit> {
            saveCalls += 1
            lastIdentity = identity
            events?.add("save")

            return saveFailure
                ?.let { Result.failure<Unit>(it) }
                ?: Result.success(Unit)
        }

        override suspend fun read(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?> = Result.success(null)

        override suspend fun clear() = Unit
    }

    private class RecordingPingClient(
        private val result: AuthResult,
        private val events: MutableList<String>? = null,
    ) : AuthenticatedPingClient {
        var calls = 0
            private set

        override suspend fun ping(
            credentials: AuthCredentials,
            profile: ServerProfile,
        ): AuthResult {
            calls += 1
            events?.add("ping")
            return result
        }
    }

    private fun profile(): ServerProfile =
        ServerProfile(
            endpoint("https://music.example.com/navidrome"),
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
