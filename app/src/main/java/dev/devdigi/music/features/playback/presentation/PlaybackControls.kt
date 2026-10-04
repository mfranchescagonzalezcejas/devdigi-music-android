package dev.devdigi.music.features.playback.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.devdigi.music.features.playback.domain.PlaybackFailure
import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState

internal data class PlaybackControlPolicy(
    val visible: Boolean,
    val pause: Boolean = false,
    val resume: Boolean = false,
    val stop: Boolean = false,
    val retry: Boolean = false,
)

internal fun playbackControlPolicy(state: PlaybackState): PlaybackControlPolicy {
    if (state.track == null) {
        return PlaybackControlPolicy(
            visible = false,
        )
    }

    return when (state.phase) {
        PlaybackPhase.IDLE -> {
            PlaybackControlPolicy(
                visible = false,
            )
        }

        PlaybackPhase.PREPARING -> {
            PlaybackControlPolicy(
                visible = true,
                stop = true,
            )
        }

        PlaybackPhase.PLAYING -> {
            PlaybackControlPolicy(
                visible = true,
                pause = true,
                stop = true,
            )
        }

        PlaybackPhase.PAUSED -> {
            PlaybackControlPolicy(
                visible = true,
                resume = true,
                stop = true,
            )
        }

        PlaybackPhase.STOPPED -> {
            PlaybackControlPolicy(
                visible = true,
                retry = true,
            )
        }

        PlaybackPhase.ERROR -> {
            PlaybackControlPolicy(
                visible = true,
                stop = true,
                retry = true,
            )
        }
    }
}

internal fun playbackControlsStacked(availableWidth: Dp): Boolean = availableWidth < 520.dp

internal fun playbackStatusLabel(phase: PlaybackPhase): String =
    when (phase) {
        PlaybackPhase.IDLE -> {
            "Idle"
        }

        PlaybackPhase.PREPARING -> {
            "Preparing playback…"
        }

        PlaybackPhase.PLAYING -> {
            "Playing"
        }

        PlaybackPhase.PAUSED -> {
            "Paused"
        }

        PlaybackPhase.STOPPED -> {
            "Stopped"
        }

        PlaybackPhase.ERROR -> {
            "Playback unavailable"
        }
    }

internal fun playbackFailureLabel(failure: PlaybackFailure): String =
    when (failure) {
        PlaybackFailure
            .AUTHENTICATION_REQUIRED,
        -> {
            "Playback authorization is no longer valid."
        }

        PlaybackFailure.NETWORK_OR_SOURCE -> {
            "The playback source is unavailable."
        }

        PlaybackFailure
            .UNSUPPORTED_PLAYBACK,
        -> {
            "This track cannot be played on this device."
        }

        PlaybackFailure.UNKNOWN -> {
            "Playback failed safely."
        }
    }

@Composable
internal fun PlaybackControls(
    state: PlaybackState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
) {
    val policy =
        playbackControlPolicy(state)

    val track =
        state.track

    if (
        !policy.visible ||
        track == null
    ) {
        return
    }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "playback-phase-${state.phase.name}",
                ),
        shape =
            MaterialTheme.shapes
                .large,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text =
                    track.title.ifBlank {
                        "Untitled track"
                    },
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis,
                style =
                    MaterialTheme.typography
                        .titleMedium,
            )

            track.artist
                ?.takeIf {
                    it.isNotBlank()
                }?.let { artist ->
                    Text(
                        text = artist,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography
                                .bodyMedium,
                    )
                }

            Text(
                text =
                    playbackStatusLabel(
                        state.phase,
                    ),
                style =
                    MaterialTheme.typography
                        .labelLarge,
            )

            state.failure?.let { failure ->
                Text(
                    text =
                        playbackFailureLabel(
                            failure,
                        ),
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                )
            }

            BoxWithConstraints(
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                if (
                    playbackControlsStacked(
                        maxWidth,
                    )
                ) {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp,
                            ),
                    ) {
                        PlaybackActionButtons(
                            policy = policy,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onPause = onPause,
                            onResume = onResume,
                            onStop = onStop,
                            onRetry = onRetry,
                            fillWidth = true,
                        )
                    }
                } else {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp,
                            ),
                    ) {
                        PlaybackActionButtons(
                            policy = policy,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onPause = onPause,
                            onResume = onResume,
                            onStop = onStop,
                            onRetry = onRetry,
                            fillWidth = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackActionButtons(
    policy: PlaybackControlPolicy,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    fillWidth: Boolean,
) {
    val modifier =
        if (fillWidth) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
        }

    OutlinedButton(
        onClick = onPrevious,
        modifier =
            modifier.testTag(
                "playback-previous",
            ),
    ) {
        Text("Previous")
    }

    OutlinedButton(
        onClick = onNext,
        modifier =
            modifier.testTag(
                "playback-next",
            ),
    ) {
        Text("Next")
    }

    if (policy.pause) {
        Button(
            onClick = onPause,
            modifier = modifier,
        ) {
            Text("Pause")
        }
    }

    if (policy.resume) {
        Button(
            onClick = onResume,
            modifier = modifier,
        ) {
            Text("Resume")
        }
    }

    if (policy.retry) {
        Button(
            onClick = onRetry,
            modifier = modifier,
        ) {
            Text("Retry")
        }
    }

    if (policy.stop) {
        OutlinedButton(
            onClick = onStop,
            modifier = modifier,
        ) {
            Text("Stop")
        }
    }
}
