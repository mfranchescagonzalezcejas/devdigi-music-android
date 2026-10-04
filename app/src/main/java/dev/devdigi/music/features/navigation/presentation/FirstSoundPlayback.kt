package dev.devdigi.music.features.navigation.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState
import dev.devdigi.music.features.playback.presentation.PlaybackControls
import dev.devdigi.music.features.playback.presentation.playbackControlPolicy
import dev.devdigi.music.features.playback.presentation.playbackStatusLabel

internal enum class FirstSoundMiniPlayerAction {
    PAUSE,
    RESUME,
    PLAY,
    RETRY,
}

internal data class FirstSoundMiniPlayerPolicy(
    val visible: Boolean,
    val action: FirstSoundMiniPlayerAction? = null,
    val title: String? = null,
    val artist: String? = null,
    val status: String? = null,
)

internal data class FirstSoundPlaybackTargetPresentation(
    val label: String,
    val interactive: Boolean = false,
)

internal fun firstSoundPlaybackTargetPresentation(): FirstSoundPlaybackTargetPresentation =
    FirstSoundPlaybackTargetPresentation(
        label = "This device",
    )

internal fun firstSoundOpenNowPlayingLabel(): String = "Open Now Playing"

internal fun firstSoundMiniPlayerPolicy(state: PlaybackState): FirstSoundMiniPlayerPolicy {
    val controls =
        playbackControlPolicy(state)

    val track =
        state.track

    if (
        !controls.visible ||
        track == null
    ) {
        return FirstSoundMiniPlayerPolicy(
            visible = false,
        )
    }

    val action =
        when {
            controls.pause -> {
                FirstSoundMiniPlayerAction.PAUSE
            }

            controls.resume -> {
                FirstSoundMiniPlayerAction.RESUME
            }

            controls.retry &&
                state.phase ==
                PlaybackPhase.STOPPED -> {
                FirstSoundMiniPlayerAction.PLAY
            }

            controls.retry -> {
                FirstSoundMiniPlayerAction.RETRY
            }

            else -> {
                null
            }
        }

    return FirstSoundMiniPlayerPolicy(
        visible = true,
        action = action,
        title =
            firstSoundTrackTitle(
                track.title,
            ),
        artist =
            firstSoundTrackArtist(
                track.artist,
            ),
        status =
            playbackStatusLabel(
                state.phase,
            ),
    )
}

internal fun firstSoundTrackTitle(title: String): String =
    title
        .takeIf {
            it.isNotBlank()
        }
        ?: "Untitled track"

internal fun firstSoundTrackArtist(artist: String?): String? =
    artist
        ?.takeIf {
            it.isNotBlank()
        }

internal fun firstSoundMiniPlayerActionLabel(action: FirstSoundMiniPlayerAction): String =
    when (action) {
        FirstSoundMiniPlayerAction.PAUSE -> {
            "Pause"
        }

        FirstSoundMiniPlayerAction.RESUME -> {
            "Resume"
        }

        FirstSoundMiniPlayerAction.PLAY -> {
            "Play"
        }

        FirstSoundMiniPlayerAction.RETRY -> {
            "Retry"
        }
    }

@Composable
internal fun FirstSoundMiniPlayer(
    state: PlaybackState,
    onOpenNowPlaying: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
) {
    val policy =
        firstSoundMiniPlayerPolicy(
            state,
        )

    if (!policy.visible) {
        return
    }

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 10.dp,
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(
                        2.dp,
                    ),
            ) {
                Text(
                    text =
                        policy.title
                            ?: "Untitled track",
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography
                            .titleSmall,
                )

                policy.artist?.let {
                    Text(
                        text = it,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography
                                .bodySmall,
                    )
                }

                policy.status?.let {
                    Text(
                        text = it,
                        maxLines = 1,
                        style =
                            MaterialTheme.typography
                                .labelSmall,
                    )
                }
            }

            OutlinedButton(
                onClick =
                onOpenNowPlaying,
                modifier =
                    Modifier.testTag(
                        "first-sound-open-now-playing",
                    ),
            ) {
                Text(
                    firstSoundOpenNowPlayingLabel(),
                )
            }

            policy.action?.let { action ->
                Button(
                    onClick = {
                        when (action) {
                            FirstSoundMiniPlayerAction
                                .PAUSE,
                            -> onPause()

                            FirstSoundMiniPlayerAction
                                .RESUME,
                            -> onResume()

                            FirstSoundMiniPlayerAction
                                .PLAY,
                            FirstSoundMiniPlayerAction
                                .RETRY,
                            -> onRetry()
                        }
                    },
                ) {
                    Text(
                        firstSoundMiniPlayerActionLabel(
                            action,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun FirstSoundNowPlayingScreen(
    state: PlaybackState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    "first-sound-now-playing",
                ),
        topBar = {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp,
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        16.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier =
                        Modifier.testTag(
                            "first-sound-now-playing-back",
                        ),
                ) {
                    Text("Back")
                }

                Text(
                    text = "Now Playing",
                    style =
                        MaterialTheme.typography
                            .headlineSmall,
                )
            }
        },
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            contentAlignment =
                Alignment.TopCenter,
        ) {
            if (state.track == null) {
                Text(
                    text =
                        "Nothing is playing.",
                    modifier =
                        Modifier.padding(24.dp),
                    style =
                        MaterialTheme.typography
                            .bodyLarge,
                )
            } else {
                val target =
                    firstSoundPlaybackTargetPresentation()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(
                                max = 720.dp,
                            ).padding(20.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            16.dp,
                        ),
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clearAndSetSemantics {
                                    contentDescription =
                                        "Playback target: ${target.label}"
                                },
                        shape =
                            MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp,
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(12.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    4.dp,
                                ),
                        ) {
                            Text(
                                text =
                                    "Playback target",
                                style =
                                    MaterialTheme.typography
                                        .labelMedium,
                            )

                            Text(
                                text =
                                    target.label,
                                style =
                                    MaterialTheme.typography
                                        .titleMedium,
                            )
                        }
                    }

                    PlaybackControls(
                        state = state,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onPause = onPause,
                        onResume = onResume,
                        onStop = onStop,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}
