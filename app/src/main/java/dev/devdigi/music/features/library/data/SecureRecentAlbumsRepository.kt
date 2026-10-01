package dev.devdigi.music.features.library.data

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.AuthSecretStore
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.library.data.remote.RecentAlbumsRemoteDataSource
import dev.devdigi.music.features.library.data.remote.RecentAlbumsRemoteResult
import dev.devdigi.music.features.library.domain.AccountScopedRecentAlbums
import dev.devdigi.music.features.library.domain.RecentAlbumsLoadResult
import dev.devdigi.music.features.library.domain.RecentAlbumsRepository

class SecureRecentAlbumsRepository(
    private val secretStore: AuthSecretStore,
    private val remote: RecentAlbumsRemoteDataSource,
) : RecentAlbumsRepository {
    override suspend fun loadRecentAlbums(account: ServerAccountIdentity): RecentAlbumsLoadResult {
        val stored =
            secretStore
                .read(account.endpoint)
                .getOrElse {
                    return RecentAlbumsLoadResult.AuthenticationRequired
                }
                ?: return RecentAlbumsLoadResult.AuthenticationRequired

        if (stored.username != account.username) {
            return RecentAlbumsLoadResult.AuthenticationRequired
        }

        val credentials =
            AuthCredentials.create(
                username = stored.username,
                password = stored.secret,
            )

        return when (
            val remoteResult =
                remote.loadRecentAlbums(
                    account = account,
                    credentials = credentials,
                )
        ) {
            is RecentAlbumsRemoteResult.Success -> {
                RecentAlbumsLoadResult.Success(
                    catalogue =
                        AccountScopedRecentAlbums(
                            account = account,
                            albums = remoteResult.albums,
                        ),
                )
            }

            RecentAlbumsRemoteResult.AuthenticationRequired -> {
                RecentAlbumsLoadResult.AuthenticationRequired
            }

            RecentAlbumsRemoteResult.NetworkError -> {
                RecentAlbumsLoadResult.NetworkError
            }

            RecentAlbumsRemoteResult.MalformedResponse -> {
                RecentAlbumsLoadResult.MalformedResponse
            }

            RecentAlbumsRemoteResult.ServerError -> {
                RecentAlbumsLoadResult.ServerError
            }
        }
    }
}
