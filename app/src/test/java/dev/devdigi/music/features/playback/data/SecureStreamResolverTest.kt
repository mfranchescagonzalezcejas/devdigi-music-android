package dev.devdigi.music.features.playback.data

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.AuthSecretStore
import dev.devdigi.music.connection.AuthSignature
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.connection.StoredCredentials
import dev.devdigi.music.connection.SubsonicAuthSigner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureStreamResolverTest {
    @Test
    fun exactAccountResolutionUsesFreshSignedRawStreamAndRedactsSource() {
        val store =
            FakeSecretStore(
                Result.success(
                    StoredCredentials(
                        username = "alice",
                        secret = "synthetic-secret",
                    ),
                ),
            )
        val signer = RecordingSigner()
        val resolver =
            SecureStreamResolver(
                secretStore = store,
                signer = signer,
            )
        val account = account("alice")

        val first =
            runBlocking {
                resolver.resolve(
                    account = account,
                    trackId = "track/id + 42",
                )
            } as StreamResolutionResult.Success

        val second =
            runBlocking {
                resolver.resolve(
                    account = account,
                    trackId = "track/id + 42",
                )
            } as StreamResolutionResult.Success

        assertEquals(
            account.endpoint,
            store.readEndpoint,
        )

        assertEquals(
            "/navidrome/rest/stream.view",
            first.source.url.encodedPath,
        )
        assertEquals(
            "track/id + 42",
            first.source.url.queryParameter("id"),
        )
        assertEquals(
            "alice",
            first.source.url.queryParameter("u"),
        )
        assertEquals(
            "token-1",
            first.source.url.queryParameter("t"),
        )
        assertEquals(
            "salt-1",
            first.source.url.queryParameter("s"),
        )
        assertEquals(
            "1.13.0",
            first.source.url.queryParameter("v"),
        )
        assertEquals(
            "devdigi-music",
            first.source.url.queryParameter("c"),
        )
        assertEquals(
            "raw",
            first.source.url.queryParameter("format"),
        )

        assertNull(
            first.source.url.queryParameter("p"),
        )
        assertFalse(
            first.source.url
                .toString()
                .contains("synthetic-secret"),
        )

        assertNotEquals(
            first.source.url.queryParameter("t"),
            second.source.url.queryParameter("t"),
        )
        assertNotEquals(
            first.source.url.queryParameter("s"),
            second.source.url.queryParameter("s"),
        )
        assertEquals(2, signer.calls)

        val redacted = first.source.toString()

        listOf(
            "alice",
            "token-1",
            "salt-1",
            "music.example.com",
        ).forEach { forbidden ->
            assertFalse(
                "Resolved source leaked $forbidden",
                redacted.contains(forbidden),
            )
        }
    }

    @Test
    fun unavailableMismatchedOrFailedCredentialsFailClosedBeforeSigning() {
        val cases =
            listOf(
                Result.success<StoredCredentials?>(null),
                Result.success(
                    StoredCredentials(
                        username = "Alice",
                        secret = "other-secret",
                    ),
                ),
                Result.failure(
                    IllegalStateException(
                        "synthetic store failure",
                    ),
                ),
            )

        cases.forEach { stored ->
            val signer = RecordingSigner()
            val store = FakeSecretStore(stored)
            val resolver =
                SecureStreamResolver(
                    secretStore = store,
                    signer = signer,
                )

            val result =
                runBlocking {
                    resolver.resolve(
                        account = account("alice"),
                        trackId = "track-1",
                    )
                }

            assertEquals(
                StreamResolutionResult.AuthenticationRequired,
                result,
            )
            assertEquals(0, signer.calls)
        }
    }

    @Test
    fun blankTrackIdFailsBeforeCredentialLookupOrSigning() {
        val signer = RecordingSigner()
        val store =
            FakeSecretStore(
                Result.success(
                    StoredCredentials(
                        username = "alice",
                        secret = "synthetic-secret",
                    ),
                ),
            )

        val result =
            runBlocking {
                SecureStreamResolver(
                    secretStore = store,
                    signer = signer,
                ).resolve(
                    account = account("alice"),
                    trackId = "   ",
                )
            }

        assertEquals(
            StreamResolutionResult.InvalidRequest,
            result,
        )
        assertNull(store.readEndpoint)
        assertEquals(0, signer.calls)
    }

    private fun account(username: String): ServerAccountIdentity =
        ServerAccountIdentity(
            endpoint =
                (
                    ServerEndpoint.parse(
                        "https://music.example.com/navidrome",
                    ) as EndpointParseResult.Valid
                ).endpoint,
            username = username,
        )

    private class FakeSecretStore(
        private val result: Result<StoredCredentials?>,
    ) : AuthSecretStore {
        var readEndpoint: ServerEndpoint? = null

        override suspend fun save(
            identity: ServerAccountIdentity,
            secret: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun read(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?> {
            readEndpoint = expectedEndpoint
            return result
        }

        override suspend fun clear() = Unit
    }

    private class RecordingSigner : SubsonicAuthSigner {
        var calls = 0

        override fun sign(credentials: AuthCredentials): AuthSignature {
            calls += 1

            return AuthSignature(
                salt = "salt-$calls",
                token = "token-$calls",
            )
        }
    }
}
