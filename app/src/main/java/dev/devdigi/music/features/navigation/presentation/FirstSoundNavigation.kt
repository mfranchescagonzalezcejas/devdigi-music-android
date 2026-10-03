package dev.devdigi.music.features.navigation.presentation

internal enum class FirstSoundPrimaryDestination(
    val label: String,
) {
    HOME(
        label = "Home",
    ),
    LIBRARY(
        label = "Library",
    ),
    SEARCH(
        label = "Search",
    ),
    DISCOVER(
        label = "Discover",
    ),
}

internal data class FirstSoundNavigationState(
    val primaryDestination: FirstSoundPrimaryDestination,
    val selectedAlbumId: String?,
)

private val FirstSoundPrimaryDestination
.supportsLibraryFlow: Boolean
    get() =
        this ==
            FirstSoundPrimaryDestination.HOME ||
            this ==
            FirstSoundPrimaryDestination.LIBRARY

internal fun firstSoundNavigationState(
    savedPrimaryDestination: String?,
    selectedAlbumId: String?,
): FirstSoundNavigationState {
    val primaryDestination =
        FirstSoundPrimaryDestination
            .entries
            .firstOrNull {
                it.name ==
                    savedPrimaryDestination
            }
            ?: FirstSoundPrimaryDestination.HOME

    return FirstSoundNavigationState(
        primaryDestination =
        primaryDestination,
        selectedAlbumId =
            if (
                primaryDestination
                    .supportsLibraryFlow
            ) {
                selectedAlbumId
            } else {
                null
            },
    )
}

internal fun selectFirstSoundPrimary(
    state: FirstSoundNavigationState,
    destination: FirstSoundPrimaryDestination,
): FirstSoundNavigationState =
    state.copy(
        primaryDestination =
        destination,
        selectedAlbumId = null,
    )

internal fun openFirstSoundAlbum(
    state: FirstSoundNavigationState,
    albumId: String,
): FirstSoundNavigationState =
    if (
        state.primaryDestination
            .supportsLibraryFlow
    ) {
        state.copy(
            selectedAlbumId = albumId,
        )
    } else {
        state.copy(
            selectedAlbumId = null,
        )
    }

internal fun backFromFirstSoundSecondary(state: FirstSoundNavigationState): FirstSoundNavigationState =
    state.copy(
        selectedAlbumId = null,
    )

internal fun firstSoundPlaceholderMessage(destination: FirstSoundPrimaryDestination): String? =
    when (destination) {
        FirstSoundPrimaryDestination.SEARCH -> {
            "Search is not implemented yet."
        }

        FirstSoundPrimaryDestination.DISCOVER -> {
            "Discover is not implemented yet."
        }

        FirstSoundPrimaryDestination.HOME,
        FirstSoundPrimaryDestination.LIBRARY,
        -> {
            null
        }
    }
