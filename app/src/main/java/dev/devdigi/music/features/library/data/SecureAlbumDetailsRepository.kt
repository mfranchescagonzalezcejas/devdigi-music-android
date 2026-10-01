package dev.devdigi.music.features.library.data

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.AuthSecretStore
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.library.data.remote.AlbumDetailsRemoteDataSource
import dev.devdigi.music.features.library.data.remote.AlbumDetailsRemoteResult
import dev.devdigi.music.features.library.domain.AccountScopedAlbumDetails
import dev.devdigi.music.features.library.domain.AlbumDetailsLoadResult
import dev.devdigi.music.features.library.domain.AlbumDetailsRepository

class SecureAlbumDetailsRepository(
    private val secretStore: AuthSecretStore,
    private val remote: AlbumDetailsRemoteDataSource,
) : AlbumDetailsRepository {
    override suspend fun loadAlbum(
        account: ServerAccountIdentity,
        albumId: String,
    ): AlbumDetailsLoadResult {
        val stored =
            secretStore
                .read(account.endpoint)
                .getOrElse {
                    return AlbumDetailsLoadResult
                        .AuthenticationRequired
                }
                ?: return AlbumDetailsLoadResult
                    .AuthenticationRequired

        if (stored.username != account.username) {
            return AlbumDetailsLoadResult
                .AuthenticationRequired
        }

        val credentials =
            AuthCredentials.create(
                username = stored.username,
                password = stored.secret,
            )

        return when (
            val remoteResult =
                remote.loadAlbum(
                    account = account,
                    credentials = credentials,
                    albumId = albumId,
                )
        ) {
            is AlbumDetailsRemoteResult.Success -> {
                AlbumDetailsLoadResult.Success(
                    details =
                        AccountScopedAlbumDetails(
                            account = account,
                            album =
                                remoteResult.album,
                        ),
                )
            }

            AlbumDetailsRemoteResult
                .AuthenticationRequired,
            -> {
                AlbumDetailsLoadResult
                    .AuthenticationRequired
            }

            AlbumDetailsRemoteResult.NetworkError -> {
                AlbumDetailsLoadResult.NetworkError
            }

            AlbumDetailsRemoteResult
                .MalformedResponse,
            -> {
                AlbumDetailsLoadResult
                    .MalformedResponse
            }

            AlbumDetailsRemoteResult.ServerError -> {
                AlbumDetailsLoadResult.ServerError
            }
        }
    }
}
