package dev.devdigi.music.features.library.data

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.AuthSecretStore
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.connection.StoredCredentials
import dev.devdigi.music.features.library.data.remote.AlbumDetailsRemoteDataSource
import dev.devdigi.music.features.library.data.remote.AlbumDetailsRemoteResult
import dev.devdigi.music.features.library.domain.AccountScopedAlbumDetails
import dev.devdigi.music.features.library.domain.AlbumDetails
import dev.devdigi.music.features.library.domain.AlbumDetailsLoadResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SecureAlbumDetailsRepositoryTest {
    @Test
    fun matchingStoredIdentityLoadsOpaqueAlbumForExactAccount() {
        val account = account("alice")

        val album =
            AlbumDetails(
                id = "album-opaque",
                title = "Synthetic Album",
                artist = "Example Artist",
                coverArtId = "synthetic-cover",
                tracks = emptyList(),
            )

        val secretStore =
            FakeSecretStore(
                Result.success(
                    StoredCredentials(
                        username = "alice",
                        secret = "synthetic-secret",
                    ),
                ),
            )

        val remote =
            RecordingRemoteDataSource(
                AlbumDetailsRemoteResult.Success(
                    album,
                ),
            )

        val result =
            runBlocking {
                SecureAlbumDetailsRepository(
                    secretStore = secretStore,
                    remote = remote,
                ).loadAlbum(
                    account = account,
                    albumId = "album-opaque",
                )
            }

        assertEquals(
            account.endpoint,
            secretStore.readEndpoint,
        )

        assertEquals(1, remote.calls)
        assertSame(account, remote.account)

        assertEquals(
            "album-opaque",
            remote.albumId,
        )

        assertEquals(
            "alice",
            remote.username,
        )

        assertEquals(
            "synthetic-secret",
            remote.secret,
        )

        assertEquals(
            AlbumDetailsLoadResult.Success(
                details =
                    AccountScopedAlbumDetails(
                        account = account,
                        album = album,
                    ),
            ),
            result,
        )
    }

    @Test
    fun unavailableMismatchedOrFailedCredentialLookupFailsClosed() {
        val account = account("alice")

        val cases =
            listOf(
                Result.success<StoredCredentials?>(
                    null,
                ),
                Result.success(
                    StoredCredentials(
                        username = "bob",
                        secret = "other-secret",
                    ),
                ),
                Result.failure(
                    IllegalStateException(
                        "synthetic store failure",
                    ),
                ),
            )

        cases.forEach { storedResult ->
            val secretStore =
                FakeSecretStore(storedResult)

            val remote =
                RecordingRemoteDataSource(
                    AlbumDetailsRemoteResult.Success(
                        AlbumDetails(
                            id = "album-1",
                            title = "Synthetic",
                            artist = null,
                            coverArtId = null,
                            tracks = emptyList(),
                        ),
                    ),
                )

            val result =
                runBlocking {
                    SecureAlbumDetailsRepository(
                        secretStore = secretStore,
                        remote = remote,
                    ).loadAlbum(
                        account = account,
                        albumId = "album-1",
                    )
                }

            assertEquals(
                AlbumDetailsLoadResult
                    .AuthenticationRequired,
                result,
            )

            assertEquals(
                account.endpoint,
                secretStore.readEndpoint,
            )

            assertEquals(
                0,
                remote.calls,
            )
        }
    }

    @Test
    fun usernameComparisonIsExactAndOpaque() {
        val account = account("Alice")

        val secretStore =
            FakeSecretStore(
                Result.success(
                    StoredCredentials(
                        username = "alice",
                        secret = "synthetic-secret",
                    ),
                ),
            )

        val remote =
            RecordingRemoteDataSource(
                AlbumDetailsRemoteResult.ServerError,
            )

        val result =
            runBlocking {
                SecureAlbumDetailsRepository(
                    secretStore = secretStore,
                    remote = remote,
                ).loadAlbum(
                    account = account,
                    albumId = "album-1",
                )
            }

        assertEquals(
            AlbumDetailsLoadResult
                .AuthenticationRequired,
            result,
        )

        assertEquals(0, remote.calls)
    }

    @Test
    fun mapsRemoteFailuresWithoutChangingTheirMeaning() {
        val account = account("alice")

        val cases =
            listOf(
                AlbumDetailsRemoteResult
                    .AuthenticationRequired to
                    AlbumDetailsLoadResult
                        .AuthenticationRequired,
                AlbumDetailsRemoteResult
                    .NetworkError to
                    AlbumDetailsLoadResult
                        .NetworkError,
                AlbumDetailsRemoteResult
                    .MalformedResponse to
                    AlbumDetailsLoadResult
                        .MalformedResponse,
                AlbumDetailsRemoteResult
                    .ServerError to
                    AlbumDetailsLoadResult
                        .ServerError,
            )

        cases.forEach { (remoteResult, expected) ->
            val remote =
                RecordingRemoteDataSource(
                    remoteResult,
                )

            val result =
                runBlocking {
                    SecureAlbumDetailsRepository(
                        secretStore =
                            FakeSecretStore(
                                Result.success(
                                    StoredCredentials(
                                        username =
                                            "alice",
                                        secret =
                                            "synthetic-secret",
                                    ),
                                ),
                            ),
                        remote = remote,
                    ).loadAlbum(
                        account = account,
                        albumId = "album-opaque",
                    )
                }

            assertEquals(expected, result)
            assertEquals(1, remote.calls)
            assertEquals(
                "album-opaque",
                remote.albumId,
            )
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
        private val result: AlbumDetailsRemoteResult,
    ) : AlbumDetailsRemoteDataSource {
        var calls = 0

        var account:
            ServerAccountIdentity? = null

        var albumId:
            String? = null

        var username:
            String? = null

        var secret:
            String? = null

        override suspend fun loadAlbum(
            account: ServerAccountIdentity,
            credentials: AuthCredentials,
            albumId: String,
        ): AlbumDetailsRemoteResult {
            calls += 1

            this.account = account
            this.albumId = albumId
            username = credentials.username
            secret = credentials.password

            return result
        }
    }
}
