package dev.devdigi.music.features.library.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.devdigi.music.features.library.domain.AlbumDetails

internal data class AlbumDetailsLayoutSpec(
    val horizontalPadding: Dp,
    val artworkSize: Dp,
    val maxContentWidth: Dp,
    val stackedContent: Boolean,
)

internal fun albumDetailsLayoutSpec(availableWidth: Dp): AlbumDetailsLayoutSpec =
    when {
        availableWidth < 600.dp -> {
            AlbumDetailsLayoutSpec(
                horizontalPadding = 16.dp,
                artworkSize = 208.dp,
                maxContentWidth = 1_200.dp,
                stackedContent = true,
            )
        }

        availableWidth < 900.dp -> {
            AlbumDetailsLayoutSpec(
                horizontalPadding = 24.dp,
                artworkSize = 240.dp,
                maxContentWidth = 1_200.dp,
                stackedContent = false,
            )
        }

        else -> {
            AlbumDetailsLayoutSpec(
                horizontalPadding = 32.dp,
                artworkSize = 280.dp,
                maxContentWidth = 1_200.dp,
                stackedContent = false,
            )
        }
    }

@Composable
fun AlbumDetailsScreen(
    state: AlbumDetailsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AlbumDetailsHeader(
                onBack = onBack,
            )
        },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            val layout =
                albumDetailsLayoutSpec(
                    availableWidth = maxWidth,
                )

            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxSize()
                        .widthIn(
                            max = layout.maxContentWidth,
                        ).padding(
                            horizontal =
                                layout.horizontalPadding,
                            vertical = 20.dp,
                        ),
            ) {
                AlbumDetailsShellContent(
                    state = state,
                    layout = layout,
                    onRetry = onRetry,
                    onSignOut = onSignOut,
                )
            }
        }
    }
}

@Composable
private fun AlbumDetailsHeader(onBack: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val layout =
            albumDetailsLayoutSpec(
                availableWidth = maxWidth,
            )

        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(
                        max = layout.maxContentWidth,
                    ).padding(
                        horizontal =
                            layout.horizontalPadding,
                        vertical = 16.dp,
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(16.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onBack,
            ) {
                Text("Back")
            }

            Text(
                text = "Album details",
                style =
                    MaterialTheme.typography
                        .headlineSmall,
            )
        }
    }
}

@Composable
private fun AlbumDetailsShellContent(
    state: AlbumDetailsUiState,
    layout: AlbumDetailsLayoutSpec,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    when (state) {
        AlbumDetailsUiState.Idle,
        is AlbumDetailsUiState.Loading,
        -> {
            CenteredAlbumMessage(
                title = "Loading album…",
                loading = true,
            )
        }

        AlbumDetailsUiState.AuthenticationRequired -> {
            CenteredAlbumMessage(
                title = "Sign in again",
                message =
                    "Your server rejected the saved session.",
                actionLabel = "Sign in again",
                onAction = onSignOut,
            )
        }

        AlbumDetailsUiState.NetworkError -> {
            CenteredAlbumMessage(
                title = "Server unavailable",
                message =
                    "DevDigi Music could not load this album.",
                actionLabel = "Retry",
                onAction = onRetry,
            )
        }

        AlbumDetailsUiState.MalformedResponse,
        AlbumDetailsUiState.ServerError,
        -> {
            CenteredAlbumMessage(
                title = "Unable to load album",
                message =
                    "The album could not be loaded safely.",
                actionLabel = "Retry",
                onAction = onRetry,
            )
        }

        is AlbumDetailsUiState.Content -> {
            AlbumDetailsOverview(
                album = state.album,
                layout = layout,
            )
        }

        is AlbumDetailsUiState.Empty -> {
            AlbumDetailsOverview(
                album = state.album,
                layout = layout,
                emptyMessage =
                    "This album has no tracks.",
            )
        }
    }
}

@Composable
private fun AlbumDetailsOverview(
    album: AlbumDetails,
    layout: AlbumDetailsLayoutSpec,
    emptyMessage: String? = null,
) {
    if (layout.stackedContent) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(24.dp),
        ) {
            AlbumArtworkPlaceholder(
                album = album,
                size = layout.artworkSize,
            )

            AlbumMetadata(
                album = album,
                emptyMessage = emptyMessage,
                modifier =
                    Modifier.fillMaxWidth(),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(32.dp),
            verticalAlignment =
                Alignment.Top,
        ) {
            AlbumArtworkPlaceholder(
                album = album,
                size = layout.artworkSize,
            )

            AlbumMetadata(
                album = album,
                emptyMessage = emptyMessage,
                modifier =
                    Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AlbumArtworkPlaceholder(
    album: AlbumDetails,
    size: Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .background(
                    color =
                        MaterialTheme.colorScheme
                            .secondaryContainer,
                    shape =
                        RoundedCornerShape(20.dp),
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        Text(
            text =
                albumArtworkFallback(
                    album.title,
                ),
            style =
                MaterialTheme.typography
                    .displayLarge,
        )
    }
}

@Composable
private fun AlbumMetadata(
    album: AlbumDetails,
    emptyMessage: String?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = album.title,
            maxLines = 3,
            overflow =
                TextOverflow.Ellipsis,
            style =
                MaterialTheme.typography
                    .headlineMedium,
        )

        Text(
            text =
                albumArtistLabel(
                    album.artist,
                ),
            maxLines = 2,
            overflow =
                TextOverflow.Ellipsis,
            style =
                MaterialTheme.typography
                    .titleMedium,
        )

        Text(
            text =
                albumTrackCountLabel(
                    album.tracks.size,
                ),
            style =
                MaterialTheme.typography
                    .bodyMedium,
        )

        emptyMessage?.let { message ->
            Text(
                text = message,
                style =
                    MaterialTheme.typography
                        .bodyMedium,
            )
        }
    }
}

internal fun albumArtworkFallback(title: String): String =
    title
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "♪"

internal fun albumArtistLabel(artist: String?): String =
    artist
        ?.takeIf {
            it.isNotBlank()
        }
        ?: "Unknown artist"

internal fun albumTrackCountLabel(trackCount: Int): String =
    when (trackCount) {
        0 -> "No tracks"
        1 -> "1 track"
        else -> "$trackCount tracks"
    }

@Composable
private fun CenteredAlbumMessage(
    title: String,
    message: String? = null,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier.widthIn(
                    max = 420.dp,
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                )
            }

            Text(
                text = title,
                style =
                    MaterialTheme.typography
                        .titleLarge,
            )

            message?.let {
                Text(
                    text = it,
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                )
            }

            if (
                actionLabel != null &&
                onAction != null
            ) {
                Button(
                    onClick = onAction,
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
