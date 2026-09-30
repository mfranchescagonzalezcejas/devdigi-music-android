package dev.devdigi.music.connection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class ServerConnectionSessionViewModelTest {
    @Test
    fun coldStartRemainsRestoringUntilFreshAuthenticatedPingCompletes() =
        runTest {
            val profile = profile()
            val identity = identity(profile)
            val store =
                FakeSecretStore(
                    stored =
                        StoredCredentials(
                            username = "Alice",
                            secret = "synthetic-password",
                        ),
                )
            val client = ControlledPingClient()
            val repository = FakeRepository(profile)
            val restorer =
                SessionRestorer(
                    repository = repository,
                    secretStore = store,
                    pingClient = client,
                )

            val viewModel =
                viewModel(
                    repository = repository,
                    store = store,
                    client = client,
                    restorer = restorer,
                )

            testScheduler.runCurrent()

            assertEquals(
                SessionStatus.RESTORING,
                viewModel.state.sessionStatus,
            )
            assertNull(viewModel.state.identity)

            client.complete(
                AuthResult.Authenticated(metadata()),
            )
            testScheduler.runCurrent()

            assertEquals(
                SessionStatus.AUTHENTICATED,
                viewModel.state.sessionStatus,
            )
            assertEquals(identity, viewModel.state.identity)
            assertEquals(metadata(), viewModel.state.metadata)
            assertEquals("", viewModel.state.passwordInput)
        }

    @Test
    fun failedRestoreNeverExposesAuthenticatedIdentity() =
        runTest {
            val repository = FakeRepository(profile())
            val store = FakeSecretStore(stored = null)
            val client =
                ImmediatePingClient(
                    AuthResult.Authenticated(metadata()),
                )
            val restorer =
                SessionRestorer(
                    repository = repository,
                    secretStore = store,
                    pingClient = client,
                )

            val viewModel =
                viewModel(
                    repository = repository,
                    store = store,
                    client = client,
                    restorer = restorer,
                )

            testScheduler.runCurrent()

            assertEquals(
                SessionStatus.SIGNED_OUT,
                viewModel.state.sessionStatus,
            )
            assertNull(viewModel.state.identity)
            assertNull(viewModel.state.metadata)
            assertEquals(0, client.calls)
        }

    @Test
    fun retainedCredentialCanRestoreOnRetryAfterNetworkFailure() =
        runTest {
            val profile = profile()
            val repository = FakeRepository(profile)
            val store =
                FakeSecretStore(
                    stored =
                        StoredCredentials(
                            username = "Alice",
                            secret = "synthetic-password",
                        ),
                )
            val client =
                SequencePingClient(
                    AuthResult.NetworkError,
                    AuthResult.Authenticated(metadata()),
                )
            val restorer =
                SessionRestorer(
                    repository = repository,
                    secretStore = store,
                    pingClient = client,
                )

            val viewModel =
                viewModel(
                    repository = repository,
                    store = store,
                    client = client,
                    restorer = restorer,
                )

            testScheduler.runCurrent()

            assertEquals(
                SessionStatus.SIGNED_OUT,
                viewModel.state.sessionStatus,
            )
            assertNull(viewModel.state.identity)
            assertEquals(0, store.clearCalls)

            viewModel.restoreSession()
            testScheduler.runCurrent()

            assertEquals(
                SessionStatus.AUTHENTICATED,
                viewModel.state.sessionStatus,
            )
            assertEquals(
                identity(profile),
                viewModel.state.identity,
            )
            assertEquals(0, store.clearCalls)
            assertEquals(2, client.calls)
        }

    @Test
    fun successfulSignOutClearsSecretBeforePublishingSignedOut() =
        runTest {
            val store = FakeSecretStore()
            val viewModel =
                authenticatedViewModel(
                    store = store,
                )

            testScheduler.runCurrent()
            authenticate(viewModel)
            testScheduler.runCurrent()

            assertEquals(
                SessionStatus.AUTHENTICATED,
                viewModel.state.sessionStatus,
            )

            viewModel.signOut()
            testScheduler.runCurrent()

            assertEquals(1, store.clearCalls)
            assertEquals(
                SessionStatus.SIGNED_OUT,
                viewModel.state.sessionStatus,
            )
            assertNull(viewModel.state.identity)
            assertNull(viewModel.state.metadata)
            assertEquals(
                Authentication.NOT_CHECKED,
                viewModel.state.connectionFacts.authentication,
            )
        }

    @Test
    fun clearFailureDoesNotReportSuccessfulSignOutAndRetryCanSucceed() =
        runTest {
            val store = FakeSecretStore()
            val viewModel =
                authenticatedViewModel(
                    store = store,
                )

            testScheduler.runCurrent()
            authenticate(viewModel)
            testScheduler.runCurrent()

            store.clearFailure =
                IllegalStateException(
                    "synthetic clear failure",
                )

            viewModel.signOut()
            testScheduler.runCurrent()

            assertEquals(
                SessionStatus.SIGN_OUT_FAILED,
                viewModel.state.sessionStatus,
            )
            assertTrue(viewModel.state.identity != null)
            assertEquals(
                Authentication.AUTHENTICATED,
                viewModel.state.connectionFacts.authentication,
            )

            store.clearFailure = null

            viewModel.signOut()
            testScheduler.runCurrent()

            assertEquals(2, store.clearCalls)
            assertEquals(
                SessionStatus.SIGNED_OUT,
                viewModel.state.sessionStatus,
            )
            assertNull(viewModel.state.identity)
        }

    @Test
    fun signOutCancellationPropagatesWithoutBecomingSignOutFailure() =
        runTest {
            val clearStarted = CompletableDeferred<Unit>()
            val releaseClear = CompletableDeferred<Unit>()
            val store =
                FakeSecretStore(
                    beforeClear = {
                        clearStarted.complete(Unit)
                        releaseClear.await()
                    },
                )
            val owner = Job()
            val ownedScope =
                CoroutineScope(
                    backgroundScope.coroutineContext + owner,
                )
            val viewModel =
                ServerConnectionViewModel(
                    repository =
                        FakeRepository(profile()),
                    scope = ownedScope,
                    secretStore = store,
                    pingClient =
                        ImmediatePingClient(
                            AuthResult.Authenticated(metadata()),
                        ),
                )

            testScheduler.runCurrent()
            authenticate(viewModel)
            testScheduler.runCurrent()

            viewModel.signOut()
            testScheduler.runCurrent()

            assertTrue(clearStarted.isCompleted)

            owner.cancel(
                CancellationException(
                    "synthetic cancellation",
                ),
            )
            testScheduler.runCurrent()

            assertEquals(
                SessionStatus.AUTHENTICATED,
                viewModel.state.sessionStatus,
            )
            assertTrue(
                viewModel.state.sessionStatus !=
                    SessionStatus.SIGN_OUT_FAILED,
            )
        }

    private fun TestScope.authenticatedViewModel(store: FakeSecretStore): ServerConnectionViewModel =
        viewModel(
            repository = FakeRepository(profile()),
            store = store,
            client =
                ImmediatePingClient(
                    AuthResult.Authenticated(metadata()),
                ),
        )

    private fun TestScope.viewModel(
        repository: FakeRepository,
        store: FakeSecretStore,
        client: AuthenticatedPingClient,
        restorer: SessionRestorer? = null,
    ): ServerConnectionViewModel =
        ServerConnectionViewModel(
            repository = repository,
            scope = backgroundScope,
            secretStore = store,
            pingClient = client,
            sessionRestorer = restorer,
        )

    private fun authenticate(viewModel: ServerConnectionViewModel) {
        viewModel.onUsernameChanged("Alice")
        viewModel.onPasswordChanged("synthetic-password")
        viewModel.signIn()
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

    private class FakeSecretStore(
        var stored: StoredCredentials? = null,
        private val beforeClear: (suspend () -> Unit)? = null,
    ) : AuthSecretStore {
        var clearCalls = 0
            private set

        var clearFailure: Throwable? = null

        override suspend fun save(
            identity: ServerAccountIdentity,
            secret: String,
        ): Result<Unit> {
            stored =
                StoredCredentials(
                    username = identity.username,
                    secret = secret,
                )
            return Result.success(Unit)
        }

        override suspend fun read(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?> = Result.success(stored)

        override suspend fun clear() {
            clearCalls += 1
            beforeClear?.invoke()
            clearFailure?.let { throw it }
            stored = null
        }
    }

    private class ImmediatePingClient(
        private val result: AuthResult,
    ) : AuthenticatedPingClient {
        var calls = 0
            private set

        override suspend fun ping(
            credentials: AuthCredentials,
            profile: ServerProfile,
        ): AuthResult {
            calls += 1
            return result
        }
    }

    private class SequencePingClient(
        vararg results: AuthResult,
    ) : AuthenticatedPingClient {
        private val queue = results.toMutableList()

        var calls = 0
            private set

        override suspend fun ping(
            credentials: AuthCredentials,
            profile: ServerProfile,
        ): AuthResult {
            calls += 1
            check(queue.isNotEmpty())
            return queue.removeAt(0)
        }
    }

    private class ControlledPingClient : AuthenticatedPingClient {
        private val result =
            CompletableDeferred<AuthResult>()

        override suspend fun ping(
            credentials: AuthCredentials,
            profile: ServerProfile,
        ): AuthResult = result.await()

        fun complete(value: AuthResult) {
            result.complete(value)
        }
    }

    private fun profile(): ServerProfile =
        ServerProfile(
            endpoint(
                "https://music.example.com/navidrome",
            ),
        )

    private fun identity(profile: ServerProfile): ServerAccountIdentity =
        ServerAccountIdentity(
            endpoint = profile.endpoint,
            username = "Alice",
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
