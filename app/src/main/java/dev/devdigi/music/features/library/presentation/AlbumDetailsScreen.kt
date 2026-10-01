package dev.devdigi.music.features.library.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.devdigi.music.features.library.domain.AlbumDetails
import dev.devdigi.music.features.library.domain.AlbumTrack

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
    selectedTrackId: String?,
    onTrackSelected: (String) -> Unit,
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
                    selectedTrackId =
                    selectedTrackId,
                    onTrackSelected =
                    onTrackSelected,
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
    selectedTrackId: String?,
    onTrackSelected: (String) -> Unit,
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
                selectedTrackId =
                selectedTrackId,
                onTrackSelected =
                onTrackSelected,
            )
        }

        is AlbumDetailsUiState.Empty -> {
            AlbumDetailsOverview(
                album = state.album,
                layout = layout,
                selectedTrackId =
                selectedTrackId,
                onTrackSelected =
                onTrackSelected,
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
    selectedTrackId: String?,
    onTrackSelected: (String) -> Unit,
    emptyMessage: String? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                bottom = 24.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        item {
            AlbumOverviewHeader(
                album = album,
                layout = layout,
            )
        }

        item {
            Text(
                text = "Tracks",
                modifier =
                    Modifier.padding(
                        top = 12.dp,
                    ),
                style =
                    MaterialTheme.typography
                        .titleLarge,
            )
        }

        if (album.tracks.isEmpty()) {
            item {
                Text(
                    text =
                        emptyMessage
                            ?: "This album has no tracks.",
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                )
            }
        } else {
            itemsIndexed(
                items = album.tracks,
            ) { index, track ->
                AlbumTrackRow(
                    track = track,
                    albumArtist = album.artist,
                    position = index,
                    selected =
                        track.id ==
                            selectedTrackId,
                    onSelected =
                    onTrackSelected,
                )
            }
        }
    }
}

@Composable
private fun AlbumOverviewHeader(
    album: AlbumDetails,
    layout: AlbumDetailsLayoutSpec,
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
                modifier =
                    Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AlbumTrackRow(
    track: AlbumTrack,
    albumArtist: String?,
    position: Int,
    selected: Boolean,
    onSelected: (String) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    this.selected =
                        selected
                },
        onClick = {
            onSelected(track.id)
        },
        shape =
            RoundedCornerShape(14.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme
                    .secondaryContainer
            } else {
                MaterialTheme.colorScheme
                    .surfaceVariant
            },
        tonalElevation =
            if (selected) {
                2.dp
            } else {
                0.dp
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 14.dp,
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(16.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text =
                        trackPositionLabel(
                            trackNumber =
                                track.trackNumber,
                            discNumber =
                                track.discNumber,
                            position = position,
                        ),
                    style =
                        MaterialTheme.typography
                            .labelMedium,
                )

                Text(
                    text =
                        trackTitleLabel(
                            track.title,
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
                        trackArtistLabel(
                            trackArtist =
                                track.artist,
                            albumArtist =
                            albumArtist,
                        ),
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                )
            }

            Column(
                horizontalAlignment =
                    Alignment.End,
                verticalArrangement =
                    Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text =
                        trackDurationLabel(
                            track.durationSeconds,
                        ),
                    maxLines = 1,
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                )

                if (selected) {
                    Text(
                        text = "Selected",
                        style =
                            MaterialTheme.typography
                                .labelSmall,
                    )
                }
            }
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
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text =
                albumTitleLabel(
                    album.title,
                ),
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
    }
}

internal fun albumTitleLabel(title: String): String =
    title
        .takeIf {
            it.isNotBlank()
        }
        ?: "Untitled album"

internal fun albumArtworkFallback(title: String): String =
    title
        .trim()
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

internal fun trackTitleLabel(title: String): String =
    title
        .takeIf {
            it.isNotBlank()
        }
        ?: "Untitled track"

internal fun trackArtistLabel(
    trackArtist: String?,
    albumArtist: String?,
): String =
    trackArtist
        ?.takeIf {
            it.isNotBlank()
        }
        ?: albumArtist
            ?.takeIf {
                it.isNotBlank()
            }
        ?: "Unknown artist"

internal fun trackPositionLabel(
    trackNumber: Int?,
    discNumber: Int?,
    position: Int,
): String {
    val validTrack =
        trackNumber
            ?.takeIf {
                it > 0
            }

    val validDisc =
        discNumber
            ?.takeIf {
                it > 0
            }

    return when {
        validDisc != null &&
            validTrack != null -> {
            "Disc $validDisc · Track $validTrack"
        }

        validTrack != null -> {
            "Track $validTrack"
        }

        validDisc != null -> {
            "Disc $validDisc"
        }

        else -> {
            "Track ${position + 1}"
        }
    }
}

internal fun trackDurationLabel(durationSeconds: Int?): String {
    val totalSeconds =
        durationSeconds
            ?.takeIf {
                it >= 0
            }
            ?: return "Unknown duration"

    val hours =
        totalSeconds / 3_600

    val minutes =
        (totalSeconds % 3_600) / 60

    val seconds =
        totalSeconds % 60

    val paddedSeconds =
        seconds
            .toString()
            .padStart(
                length = 2,
                padChar = '0',
            )

    return if (hours > 0) {
        val paddedMinutes =
            minutes
                .toString()
                .padStart(
                    length = 2,
                    padChar = '0',
                )

        "$hours:$paddedMinutes:$paddedSeconds"
    } else {
        "$minutes:$paddedSeconds"
    }
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
