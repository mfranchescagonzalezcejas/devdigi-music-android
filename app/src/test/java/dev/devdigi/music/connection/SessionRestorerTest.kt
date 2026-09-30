package dev.devdigi.music.connection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRestorerTest {
    @Test
    fun validStoredSessionReauthenticatesBeforeRestoringIdentity() =
        runTest {
            val profile = profile()
            val secretStore = FakeSecretStore(stored = storedCredentials())
            val metadata = metadata()
            val pingClient =
                SequencePingClient(
                    AuthResult.Authenticated(metadata),
                )

            val result =
                restorer(
                    profile = profile,
                    secretStore = secretStore,
                    pingClient = pingClient,
                ).restore()

            assertEquals(
                SessionRestoreResult.Restored(
                    identity =
                        ServerAccountIdentity(
                            endpoint = profile.endpoint,
                            username = "Alice",
                        ),
                    metadata = metadata,
                ),
                result,
            )
            assertEquals(1, secretStore.readCalls)
            assertEquals(0, secretStore.clearCalls)
            assertEquals(1, pingClient.calls)
            assertEquals(profile, pingClient.lastProfile)
            assertEquals("Alice", pingClient.lastCredentials?.username)
        }

    @Test
    fun missingProfileFailsClosedWithoutReadingSecretOrPinging() =
        runTest {
            val secretStore = FakeSecretStore(stored = storedCredentials())
            val pingClient =
                SequencePingClient(
                    AuthResult.Authenticated(metadata()),
                )

            val result =
                restorer(
                    profile = null,
                    secretStore = secretStore,
                    pingClient = pingClient,
                ).restore()

            assertEquals(SessionRestoreResult.NotRestored, result)
            assertEquals(0, secretStore.readCalls)
            assertEquals(0, secretStore.clearCalls)
            assertEquals(0, pingClient.calls)
        }

    @Test
    fun missingStoredCredentialFailsClosedWithoutPinging() =
        runTest {
            val secretStore = FakeSecretStore(stored = null)
            val pingClient =
                SequencePingClient(
                    AuthResult.Authenticated(metadata()),
                )

            val result =
                restorer(
                    profile = profile(),
                    secretStore = secretStore,
                    pingClient = pingClient,
                ).restore()

            assertEquals(SessionRestoreResult.NotRestored, result)
            assertEquals(1, secretStore.readCalls)
            assertEquals(0, secretStore.clearCalls)
            assertEquals(0, pingClient.calls)
        }

    @Test
    fun secretStoreReadFailureFailsClosedWithoutPinging() =
        runTest {
            val secretStore =
                FakeSecretStore(
                    stored = storedCredentials(),
                    readFailure =
                        IllegalStateException(
                            "synthetic storage failure",
                        ),
                )
            val pingClient =
                SequencePingClient(
                    AuthResult.Authenticated(metadata()),
                )

            val result =
                restorer(
                    profile = profile(),
                    secretStore = secretStore,
                    pingClient = pingClient,
                ).restore()

            assertEquals(SessionRestoreResult.NotRestored, result)
            assertEquals(1, secretStore.readCalls)
            assertEquals(0, pingClient.calls)
        }

    @Test
    fun invalidCredentialsReturnRejectionWithoutMutatingStore() =
        runTest {
            val secretStore =
                FakeSecretStore(
                    stored = storedCredentials(),
                )
            val pingClient =
                SequencePingClient(
                    AuthResult.InvalidCredentials,
                )

            val result =
                restorer(
                    profile = profile(),
                    secretStore = secretStore,
                    pingClient = pingClient,
                ).restore()

            assertEquals(
                SessionRestoreResult.CredentialRejected,
                result,
            )
            assertEquals(1, pingClient.calls)
            assertEquals(0, secretStore.clearCalls)
            assertEquals(
                storedCredentials(),
                secretStore.currentStored,
            )
        }

    @Test
    fun nonCredentialFailuresRetainStoredCredential() =
        runTest {
            val failures =
                listOf(
                    AuthResult.NetworkError,
                    AuthResult.AuthProtocolError,
                    AuthResult.UnsupportedAuthentication,
                    AuthResult.IncompatibleServer,
                )

            for (failure in failures) {
                val secretStore =
                    FakeSecretStore(
                        stored = storedCredentials(),
                    )
                val pingClient =
                    SequencePingClient(failure)

                val result =
                    restorer(
                        profile = profile(),
                        secretStore = secretStore,
                        pingClient = pingClient,
                    ).restore()

                assertEquals(
                    SessionRestoreResult.NotRestored,
                    result,
                )
                assertEquals(1, pingClient.calls)
                assertEquals(0, secretStore.clearCalls)
                assertEquals(
                    storedCredentials(),
                    secretStore.currentStored,
                )
            }
        }

    @Test
    fun retainedCredentialCanAuthenticateOnRetryWithoutReentry() =
        runTest {
            val profile = profile()
            val metadata = metadata()
            val secretStore =
                FakeSecretStore(
                    stored = storedCredentials(),
                )
            val pingClient =
                SequencePingClient(
                    AuthResult.NetworkError,
                    AuthResult.Authenticated(metadata),
                )
            val restorer =
                restorer(
                    profile = profile,
                    secretStore = secretStore,
                    pingClient = pingClient,
                )

            val first = restorer.restore()
            val second = restorer.restore()

            assertEquals(
                SessionRestoreResult.NotRestored,
                first,
            )
            assertEquals(
                SessionRestoreResult.Restored(
                    identity =
                        ServerAccountIdentity(
                            endpoint = profile.endpoint,
                            username = "Alice",
                        ),
                    metadata = metadata,
                ),
                second,
            )
            assertEquals(2, secretStore.readCalls)
            assertEquals(0, secretStore.clearCalls)
            assertEquals(2, pingClient.calls)
        }

    private fun restorer(
        profile: ServerProfile?,
        secretStore: FakeSecretStore,
        pingClient: AuthenticatedPingClient,
    ): SessionRestorer =
        SessionRestorer(
            repository = FakeRepository(profile),
            secretStore = secretStore,
            pingClient = pingClient,
        )

    private class FakeRepository(
        initial: ServerProfile?,
    ) : ServerProfileRepository {
        override val profile = MutableStateFlow(initial)

        override suspend fun save(profile: ServerProfile) = error("save() must not be used during restoration")

        override suspend fun delete() = error("delete() must not be used during restoration")
    }

    private class FakeSecretStore(
        stored: StoredCredentials?,
        private val readFailure: Throwable? = null,
    ) : AuthSecretStore {
        var currentStored = stored
            private set

        var readCalls = 0
            private set

        var clearCalls = 0
            private set

        override suspend fun save(
            identity: ServerAccountIdentity,
            secret: String,
        ): Result<Unit> = error("save() must not be used during restoration")

        override suspend fun read(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?> {
            readCalls += 1

            return readFailure?.let {
                Result.failure(it)
            } ?: Result.success(currentStored)
        }

        override suspend fun clear() {
            clearCalls += 1
            currentStored = null
        }
    }

    private class SequencePingClient(
        vararg initialResults: AuthResult,
    ) : AuthenticatedPingClient {
        private val results = initialResults.toMutableList()

        var calls = 0
            private set

        var lastCredentials: AuthCredentials? = null
            private set

        var lastProfile: ServerProfile? = null
            private set

        override suspend fun ping(
            credentials: AuthCredentials,
            profile: ServerProfile,
        ): AuthResult {
            calls += 1
            lastCredentials = credentials
            lastProfile = profile

            check(results.isNotEmpty()) {
                "No synthetic AuthResult remaining"
            }

            return results.removeAt(0)
        }
    }

    private fun profile(): ServerProfile =
        ServerProfile(
            endpoint(
                "https://music.example.com/navidrome",
            ),
        )

    private fun storedCredentials(): StoredCredentials =
        StoredCredentials(
            username = "Alice",
            secret = "synthetic-password",
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
