package dev.devdigi.music.features.library.data

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.AuthSecretStore
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.connection.StoredCredentials
import dev.devdigi.music.features.library.data.remote.RecentAlbumsRemoteDataSource
import dev.devdigi.music.features.library.data.remote.RecentAlbumsRemoteResult
import dev.devdigi.music.features.library.domain.RecentAlbum
import dev.devdigi.music.features.library.domain.RecentAlbumsLoadResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SecureRecentAlbumsRepositoryTest {
    @Test
    fun matchingStoredIdentityLoadsRemoteCatalogue() {
        val account = account("alice")

        val secretStore =
            FakeSecretStore(
                Result.success(
                    StoredCredentials(
                        username = "alice",
                        secret = "synthetic-secret",
                    ),
                ),
            )

        val albums =
            listOf(
                RecentAlbum(
                    id = "album-1",
                    title = "Synthetic Album",
                    artist = "Example Artist",
                    coverArtId = "cover-1",
                ),
            )

        val remote =
            RecordingRemoteDataSource(
                RecentAlbumsRemoteResult.Success(albums),
            )

        val result =
            runBlocking {
                SecureRecentAlbumsRepository(
                    secretStore = secretStore,
                    remote = remote,
                ).loadRecentAlbums(account)
            }

        assertEquals(account.endpoint, secretStore.readEndpoint)
        assertEquals(1, remote.calls)
        assertSame(account, remote.account)
        assertEquals("alice", remote.username)
        assertEquals("synthetic-secret", remote.secret)

        assertEquals(
            RecentAlbumsLoadResult.Success(
                catalogue =
                    dev.devdigi.music.features.library.domain.AccountScopedRecentAlbums(
                        account = account,
                        albums = albums,
                    ),
            ),
            result,
        )
    }

    @Test
    fun unavailableOrMismatchedStoredCredentialFailsClosed() {
        val account = account("alice")

        val cases =
            listOf(
                Result.success<StoredCredentials?>(null),
                Result.success(
                    StoredCredentials(
                        username = "bob",
                        secret = "other-secret",
                    ),
                ),
                Result.failure(
                    IllegalStateException("synthetic store failure"),
                ),
            )

        cases.forEach { storedResult ->
            val secretStore =
                FakeSecretStore(storedResult)

            val remote =
                RecordingRemoteDataSource(
                    RecentAlbumsRemoteResult.Success(
                        emptyList(),
                    ),
                )

            val result =
                runBlocking {
                    SecureRecentAlbumsRepository(
                        secretStore = secretStore,
                        remote = remote,
                    ).loadRecentAlbums(account)
                }

            assertEquals(
                RecentAlbumsLoadResult.AuthenticationRequired,
                result,
            )

            assertEquals(account.endpoint, secretStore.readEndpoint)
            assertEquals(0, remote.calls)
        }
    }

    @Test
    fun mapsRemoteFailuresWithoutChangingTheirMeaning() {
        val account = account("alice")

        val cases =
            listOf(
                RecentAlbumsRemoteResult.AuthenticationRequired to
                    RecentAlbumsLoadResult.AuthenticationRequired,
                RecentAlbumsRemoteResult.NetworkError to
                    RecentAlbumsLoadResult.NetworkError,
                RecentAlbumsRemoteResult.MalformedResponse to
                    RecentAlbumsLoadResult.MalformedResponse,
                RecentAlbumsRemoteResult.ServerError to
                    RecentAlbumsLoadResult.ServerError,
            )

        cases.forEach { (remoteResult, expected) ->
            val remote =
                RecordingRemoteDataSource(remoteResult)

            val result =
                runBlocking {
                    SecureRecentAlbumsRepository(
                        secretStore =
                            FakeSecretStore(
                                Result.success(
                                    StoredCredentials(
                                        username = "alice",
                                        secret = "synthetic-secret",
                                    ),
                                ),
                            ),
                        remote = remote,
                    ).loadRecentAlbums(account)
                }

            assertEquals(expected, result)
            assertEquals(1, remote.calls)
        }
    }

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

    private class FakeSecretStore(
        private val readResult: Result<StoredCredentials?>,
    ) : AuthSecretStore {
        var readEndpoint: ServerEndpoint? = null

        override suspend fun save(
            identity: ServerAccountIdentity,
            secret: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun read(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?> {
            readEndpoint = expectedEndpoint
            return readResult
        }

        override suspend fun clear() = Unit
    }

    private class RecordingRemoteDataSource(
        private val result: RecentAlbumsRemoteResult,
    ) : RecentAlbumsRemoteDataSource {
        var calls = 0
        var account: ServerAccountIdentity? = null
        var username: String? = null
        var secret: String? = null

        override suspend fun loadRecentAlbums(
            account: ServerAccountIdentity,
            credentials: AuthCredentials,
        ): RecentAlbumsRemoteResult {
            calls += 1
            this.account = account
            username = credentials.username
            secret = credentials.password
            return result
        }
    }
}
