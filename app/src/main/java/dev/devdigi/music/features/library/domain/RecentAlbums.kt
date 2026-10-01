package dev.devdigi.music.features.library.domain

import dev.devdigi.music.connection.ServerAccountIdentity

data class RecentAlbum(
    val id: String,
    val title: String,
    val artist: String?,
    val coverArtId: String?,
)

data class AccountScopedRecentAlbums(
    val account: ServerAccountIdentity,
    val albums: List<RecentAlbum>,
)

sealed interface RecentAlbumsLoadResult {
    data class Success(
        val catalogue: AccountScopedRecentAlbums,
    ) : RecentAlbumsLoadResult

    data object AuthenticationRequired : RecentAlbumsLoadResult

    data object NetworkError : RecentAlbumsLoadResult

    data object MalformedResponse : RecentAlbumsLoadResult

    data object ServerError : RecentAlbumsLoadResult
}

fun interface RecentAlbumsRepository {
    suspend fun loadRecentAlbums(account: ServerAccountIdentity): RecentAlbumsLoadResult
}
