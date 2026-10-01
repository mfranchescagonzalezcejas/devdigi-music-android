package dev.devdigi.music.features.library.data.remote

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.library.domain.RecentAlbum

sealed interface RecentAlbumsRemoteResult {
    data class Success(
        val albums: List<RecentAlbum>,
    ) : RecentAlbumsRemoteResult

    data object AuthenticationRequired : RecentAlbumsRemoteResult

    data object NetworkError : RecentAlbumsRemoteResult

    data object MalformedResponse : RecentAlbumsRemoteResult

    data object ServerError : RecentAlbumsRemoteResult
}

fun interface RecentAlbumsRemoteDataSource {
    suspend fun loadRecentAlbums(
        account: ServerAccountIdentity,
        credentials: AuthCredentials,
    ): RecentAlbumsRemoteResult
}
