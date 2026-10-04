package dev.devdigi.music.features.library.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.devdigi.music.features.library.domain.RecentAlbum

internal data class RecentAlbumsLayoutSpec(
    val horizontalPadding: Dp,
    val minimumCellWidth: Dp,
    val maxContentWidth: Dp,
    val stackedHeader: Boolean,
)

internal fun recentAlbumsLayoutSpec(availableWidth: Dp): RecentAlbumsLayoutSpec =
    when {
        availableWidth < 400.dp -> {
            RecentAlbumsLayoutSpec(
                horizontalPadding = 16.dp,
                minimumCellWidth = 136.dp,
                maxContentWidth = 1_200.dp,
                stackedHeader = true,
            )
        }

        availableWidth < 700.dp -> {
            RecentAlbumsLayoutSpec(
                horizontalPadding = 24.dp,
                minimumCellWidth = 152.dp,
                maxContentWidth = 1_200.dp,
                stackedHeader = false,
            )
        }

        else -> {
            RecentAlbumsLayoutSpec(
                horizontalPadding = 32.dp,
                minimumCellWidth = 176.dp,
                maxContentWidth = 1_200.dp,
                stackedHeader = false,
            )
        }
    }

@Composable
fun RecentAlbumsScreen(
    state: RecentAlbumsUiState,
    username: String,
    selectedAlbumId: String?,
    onAlbumSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("recent-albums-screen"),
        topBar = {
            RecentAlbumsHeader(
                username = username,
                onSignOut = onSignOut,
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
                recentAlbumsLayoutSpec(maxWidth)

            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxSize()
                        .widthIn(
                            max =
                                layout.maxContentWidth,
                        ).padding(
                            horizontal =
                                layout.horizontalPadding,
                            vertical = 12.dp,
                        ),
            ) {
                when (state) {
                    RecentAlbumsUiState.Idle,
                    RecentAlbumsUiState.Loading,
                    -> {
                        LoadingContent(
                            modifier =
                                Modifier.fillMaxSize(),
                        )
                    }

                    RecentAlbumsUiState.Empty -> {
                        MessageContent(
                            title =
                                "No recent albums",
                            message =
                                "Your server did not return any recent albums.",
                            actionLabel =
                                "Refresh",
                            onAction = onRetry,
                            modifier =
                                Modifier.fillMaxSize(),
                        )
                    }

                    RecentAlbumsUiState.AuthenticationRequired -> {
                        MessageContent(
                            title =
                                "Sign in again",
                            message =
                                "Your server rejected the saved session.",
                            actionLabel =
                                "Sign in again",
                            onAction = onSignOut,
                            modifier =
                                Modifier.fillMaxSize(),
                        )
                    }

                    RecentAlbumsUiState.NetworkError -> {
                        MessageContent(
                            title =
                                "Server unavailable",
                            message =
                                "DevDigi Music could not reach your server.",
                            actionLabel =
                                "Retry",
                            onAction = onRetry,
                            modifier =
                                Modifier.fillMaxSize(),
                        )
                    }

                    RecentAlbumsUiState.MalformedResponse -> {
                        MessageContent(
                            title =
                                "Invalid server response",
                            message =
                                "The recent-albums response could not be read safely.",
                            actionLabel =
                                "Retry",
                            onAction = onRetry,
                            modifier =
                                Modifier.fillMaxSize(),
                        )
                    }

                    RecentAlbumsUiState.ServerError -> {
                        MessageContent(
                            title =
                                "Server error",
                            message =
                                "Your server could not load recent albums.",
                            actionLabel =
                                "Retry",
                            onAction = onRetry,
                            modifier =
                                Modifier.fillMaxSize(),
                        )
                    }

                    is RecentAlbumsUiState.Content -> {
                        LazyVerticalGrid(
                            columns =
                                GridCells.Adaptive(
                                    minSize =
                                        layout.minimumCellWidth,
                                ),
                            modifier =
                                Modifier.fillMaxSize(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    16.dp,
                                ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    20.dp,
                                ),
                        ) {
                            items(
                                items = state.albums,
                                key = RecentAlbum::id,
                            ) { album ->
                                AlbumCard(
                                    album = album,
                                    selected =
                                        album.id ==
                                            selectedAlbumId,
                                    onClick = {
                                        onAlbumSelected(
                                            album.id,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentAlbumsHeader(
    username: String,
    onSignOut: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val layout =
            recentAlbumsLayoutSpec(maxWidth)

        if (layout.stackedHeader) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .widthIn(
                            max =
                                layout.maxContentWidth,
                        ).padding(
                            horizontal =
                                layout.horizontalPadding,
                            vertical = 20.dp,
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                LibraryHeading(username)

                OutlinedButton(
                    onClick = onSignOut,
                ) {
                    Text("Sign out")
                }
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .widthIn(
                            max =
                                layout.maxContentWidth,
                        ).padding(
                            horizontal =
                                layout.horizontalPadding,
                            vertical = 20.dp,
                        ),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                LibraryHeading(username)

                OutlinedButton(
                    onClick = onSignOut,
                ) {
                    Text("Sign out")
                }
            }
        }
    }
}

@Composable
private fun LibraryHeading(username: String) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "DevDigi Music",
            style =
                MaterialTheme.typography
                    .headlineSmall,
        )

        Text(
            text = "Recent albums",
            style =
                MaterialTheme.typography
                    .titleLarge,
        )

        Text(
            text = "Connected as $username",
            style =
                MaterialTheme.typography
                    .bodySmall,
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
            )

            Text("Loading recent albums…")
        }
    }
}

@Composable
private fun MessageContent(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier,
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
            Text(
                text = title,
                style =
                    MaterialTheme.typography
                        .titleLarge,
            )

            Text(
                text = message,
                style =
                    MaterialTheme.typography
                        .bodyMedium,
            )

            Button(
                onClick = onAction,
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: RecentAlbum,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                    } else {
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    },
            ),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(
                            MaterialTheme
                                .colorScheme
                                .secondaryContainer,
                        ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Text(
                    text =
                        album.title
                            .firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: "♪",
                    style =
                        MaterialTheme.typography
                            .displaySmall,
                )
            }

            Column(
                modifier =
                    Modifier.padding(12.dp),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = album.title,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography
                            .titleMedium,
                )

                album.artist?.let { artist ->
                    Text(
                        text = artist,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography
                                .bodySmall,
                    )
                }

                if (selected) {
                    Text(
                        text = "Selected",
                        style =
                            MaterialTheme.typography
                                .labelMedium,
                    )
                }
            }
        }
    }
}
