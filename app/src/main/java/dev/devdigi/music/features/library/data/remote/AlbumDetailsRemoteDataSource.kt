package dev.devdigi.music.features.library.data.remote

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.library.domain.AlbumDetails

sealed interface AlbumDetailsRemoteResult {
    data class Success(
        val album: AlbumDetails,
    ) : AlbumDetailsRemoteResult

    data object AuthenticationRequired :
        AlbumDetailsRemoteResult

    data object NetworkError :
        AlbumDetailsRemoteResult

    data object MalformedResponse :
        AlbumDetailsRemoteResult

    data object ServerError :
        AlbumDetailsRemoteResult
}

fun interface AlbumDetailsRemoteDataSource {
    suspend fun loadAlbum(
        account: ServerAccountIdentity,
        credentials: AuthCredentials,
        albumId: String,
    ): AlbumDetailsRemoteResult
}
