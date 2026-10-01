package dev.devdigi.music.features.library.domain

import dev.devdigi.music.connection.ServerAccountIdentity

data class AlbumTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationSeconds: Int?,
    val coverArtId: String?,
)

data class AlbumDetails(
    val id: String,
    val title: String,
    val artist: String?,
    val coverArtId: String?,
    val tracks: List<AlbumTrack>,
)

data class AccountScopedAlbumDetails(
    val account: ServerAccountIdentity,
    val album: AlbumDetails,
)

sealed interface AlbumDetailsLoadResult {
    data class Success(
        val details: AccountScopedAlbumDetails,
    ) : AlbumDetailsLoadResult

    data object AuthenticationRequired :
        AlbumDetailsLoadResult

    data object NetworkError :
        AlbumDetailsLoadResult

    data object MalformedResponse :
        AlbumDetailsLoadResult

    data object ServerError :
        AlbumDetailsLoadResult
}

fun interface AlbumDetailsRepository {
    suspend fun loadAlbum(
        account: ServerAccountIdentity,
        albumId: String,
    ): AlbumDetailsLoadResult
}
