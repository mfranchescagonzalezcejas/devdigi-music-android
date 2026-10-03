package dev.devdigi.music.features.navigation.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.devdigi.music.features.playback.domain.PlaybackState

internal enum class FirstSoundPrimaryNavigationLayout {
    BOTTOM_BAR,
    RAIL,
}

internal fun firstSoundPrimaryNavigationLayout(availableWidth: Dp): FirstSoundPrimaryNavigationLayout =
    if (availableWidth < 720.dp) {
        FirstSoundPrimaryNavigationLayout.BOTTOM_BAR
    } else {
        FirstSoundPrimaryNavigationLayout.RAIL
    }

@Composable
internal fun FirstSoundShell(
    activeDestination: FirstSoundPrimaryDestination,
    playbackState: PlaybackState,
    showMiniPlayer: Boolean,
    onDestinationSelected: (FirstSoundPrimaryDestination) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onRetryPlayback: () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        when (
            firstSoundPrimaryNavigationLayout(
                maxWidth,
            )
        ) {
            FirstSoundPrimaryNavigationLayout
                .BOTTOM_BAR,
            -> {
                FirstSoundCompactShell(
                    activeDestination =
                    activeDestination,
                    playbackState =
                    playbackState,
                    showMiniPlayer =
                    showMiniPlayer,
                    onDestinationSelected =
                    onDestinationSelected,
                    onOpenNowPlaying =
                    onOpenNowPlaying,
                    onPausePlayback =
                    onPausePlayback,
                    onResumePlayback =
                    onResumePlayback,
                    onRetryPlayback =
                    onRetryPlayback,
                    content = content,
                )
            }

            FirstSoundPrimaryNavigationLayout
                .RAIL,
            -> {
                FirstSoundWideShell(
                    activeDestination =
                    activeDestination,
                    playbackState =
                    playbackState,
                    showMiniPlayer =
                    showMiniPlayer,
                    onDestinationSelected =
                    onDestinationSelected,
                    onOpenNowPlaying =
                    onOpenNowPlaying,
                    onPausePlayback =
                    onPausePlayback,
                    onResumePlayback =
                    onResumePlayback,
                    onRetryPlayback =
                    onRetryPlayback,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun FirstSoundCompactShell(
    activeDestination: FirstSoundPrimaryDestination,
    playbackState: PlaybackState,
    showMiniPlayer: Boolean,
    onDestinationSelected: (FirstSoundPrimaryDestination) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onRetryPlayback: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier =
            Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                FirstSoundMiniPlayerSlot(
                    playbackState =
                    playbackState,
                    showMiniPlayer =
                    showMiniPlayer,
                    onOpenNowPlaying =
                    onOpenNowPlaying,
                    onPausePlayback =
                    onPausePlayback,
                    onResumePlayback =
                    onResumePlayback,
                    onRetryPlayback =
                    onRetryPlayback,
                )

                FirstSoundNavigationBar(
                    activeDestination =
                    activeDestination,
                    onDestinationSelected =
                    onDestinationSelected,
                )
            }
        },
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            content()
        }
    }
}

@Composable
private fun FirstSoundWideShell(
    activeDestination: FirstSoundPrimaryDestination,
    playbackState: PlaybackState,
    showMiniPlayer: Boolean,
    onDestinationSelected: (FirstSoundPrimaryDestination) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onRetryPlayback: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        FirstSoundNavigationRail(
            activeDestination =
            activeDestination,
            onDestinationSelected =
            onDestinationSelected,
        )

        Scaffold(
            modifier =
                Modifier.weight(1f),
            bottomBar = {
                FirstSoundMiniPlayerSlot(
                    playbackState =
                    playbackState,
                    showMiniPlayer =
                    showMiniPlayer,
                    onOpenNowPlaying =
                    onOpenNowPlaying,
                    onPausePlayback =
                    onPausePlayback,
                    onResumePlayback =
                    onResumePlayback,
                    onRetryPlayback =
                    onRetryPlayback,
                )
            },
        ) { contentPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun FirstSoundMiniPlayerSlot(
    playbackState: PlaybackState,
    showMiniPlayer: Boolean,
    onOpenNowPlaying: () -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onRetryPlayback: () -> Unit,
) {
    if (!showMiniPlayer) {
        return
    }

    FirstSoundMiniPlayer(
        state =
        playbackState,
        onOpenNowPlaying =
        onOpenNowPlaying,
        onPause =
        onPausePlayback,
        onResume =
        onResumePlayback,
        onRetry =
        onRetryPlayback,
    )
}

@Composable
private fun FirstSoundNavigationBar(
    activeDestination: FirstSoundPrimaryDestination,
    onDestinationSelected: (FirstSoundPrimaryDestination) -> Unit,
) {
    NavigationBar {
        FirstSoundPrimaryDestination
            .entries
            .forEach { destination ->
                NavigationBarItem(
                    selected =
                        destination ==
                            activeDestination,
                    onClick = {
                        onDestinationSelected(
                            destination,
                        )
                    },
                    icon = {
                        FirstSoundDestinationIcon(
                            destination,
                        )
                    },
                    label = {
                        Text(
                            destination.label,
                        )
                    },
                    alwaysShowLabel = true,
                )
            }
    }
}

@Composable
private fun FirstSoundNavigationRail(
    activeDestination: FirstSoundPrimaryDestination,
    onDestinationSelected: (FirstSoundPrimaryDestination) -> Unit,
) {
    NavigationRail(
        modifier =
            Modifier.fillMaxHeight(),
    ) {
        FirstSoundPrimaryDestination
            .entries
            .forEach { destination ->
                NavigationRailItem(
                    selected =
                        destination ==
                            activeDestination,
                    onClick = {
                        onDestinationSelected(
                            destination,
                        )
                    },
                    icon = {
                        FirstSoundDestinationIcon(
                            destination,
                        )
                    },
                    label = {
                        Text(
                            destination.label,
                        )
                    },
                    alwaysShowLabel = true,
                )
            }
    }
}

@Composable
private fun FirstSoundDestinationIcon(destination: FirstSoundPrimaryDestination) {
    Text(
        text =
            destination
                .label
                .take(1),
        modifier =
            Modifier.clearAndSetSemantics {
                // The visible destination label carries the
                // meaningful accessibility name. The temporary
                // letter icon must not be announced separately.
            },
    )
}

@Composable
internal fun FirstSoundPlaceholderScreen(destination: FirstSoundPrimaryDestination) {
    val presentation =
        firstSoundPlaceholderPresentation(
            destination,
        )
            ?: return

    Box(
        modifier =
            Modifier.fillMaxSize(),
        contentAlignment =
            Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(
                        max = 420.dp,
                    ).padding(24.dp)
                    .clearAndSetSemantics {
                        contentDescription =
                            "${destination.label}. ${presentation.message}"
                    },
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            Text(
                text =
                    destination.label,
                style =
                    MaterialTheme.typography
                        .headlineMedium,
            )

            Text(
                text =
                    presentation.message,
                style =
                    MaterialTheme.typography
                        .bodyLarge,
            )
        }
    }
}
