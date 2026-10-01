package dev.devdigi.music.features.library.presentation

internal sealed interface LibraryDestination {
    data object RecentAlbums :
        LibraryDestination

    data class AlbumDetails(
        val albumId: String,
    ) : LibraryDestination
}

internal fun libraryDestination(selectedAlbumId: String?): LibraryDestination =
    selectedAlbumId
        ?.let(
            LibraryDestination::AlbumDetails,
        )
        ?: LibraryDestination.RecentAlbums
