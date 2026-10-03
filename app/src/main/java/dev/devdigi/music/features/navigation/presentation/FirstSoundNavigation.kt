package dev.devdigi.music.features.navigation.presentation

import dev.devdigi.music.connection.ServerAccountIdentity

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
    val nowPlayingVisible: Boolean = false,
)

internal data class FirstSoundPlaceholderPresentation(
    val message: String,
    val interactive: Boolean = false,
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
    nowPlayingVisible: Boolean = false,
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
        nowPlayingVisible =
        nowPlayingVisible,
    )
}

internal fun firstSoundNavigationAfterAccountChange(
    state: FirstSoundNavigationState,
    previousIdentity: ServerAccountIdentity?,
    currentIdentity: ServerAccountIdentity?,
    authenticated: Boolean,
): FirstSoundNavigationState =
    if (
        authenticated &&
        previousIdentity != null &&
        previousIdentity ==
        currentIdentity
    ) {
        state
    } else {
        FirstSoundNavigationState(
            primaryDestination =
                FirstSoundPrimaryDestination.HOME,
            selectedAlbumId = null,
            nowPlayingVisible = false,
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
        nowPlayingVisible = false,
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
            nowPlayingVisible = false,
        )
    } else {
        state.copy(
            selectedAlbumId = null,
            nowPlayingVisible = false,
        )
    }

internal fun backFromFirstSoundSecondary(state: FirstSoundNavigationState): FirstSoundNavigationState =
    state.copy(
        selectedAlbumId = null,
        nowPlayingVisible = false,
    )

internal fun openFirstSoundNowPlaying(state: FirstSoundNavigationState): FirstSoundNavigationState =
    state.copy(
        nowPlayingVisible = true,
    )

internal fun closeFirstSoundNowPlaying(state: FirstSoundNavigationState): FirstSoundNavigationState =
    state.copy(
        nowPlayingVisible = false,
    )

internal fun backFromFirstSoundNavigation(state: FirstSoundNavigationState): FirstSoundNavigationState? =
    when {
        state.nowPlayingVisible -> {
            closeFirstSoundNowPlaying(
                state,
            )
        }

        state.selectedAlbumId != null -> {
            backFromFirstSoundSecondary(
                state,
            )
        }

        else -> {
            null
        }
    }

internal fun firstSoundPlaceholderPresentation(destination: FirstSoundPrimaryDestination): FirstSoundPlaceholderPresentation? =
    when (destination) {
        FirstSoundPrimaryDestination.SEARCH -> {
            FirstSoundPlaceholderPresentation(
                message =
                    "Search is not implemented yet.",
            )
        }

        FirstSoundPrimaryDestination.DISCOVER -> {
            FirstSoundPlaceholderPresentation(
                message =
                    "Discover is not implemented yet.",
            )
        }

        FirstSoundPrimaryDestination.HOME,
        FirstSoundPrimaryDestination.LIBRARY,
        -> {
            null
        }
    }

internal fun firstSoundPlaceholderMessage(destination: FirstSoundPrimaryDestination): String? =
    firstSoundPlaceholderPresentation(
        destination,
    )?.message
