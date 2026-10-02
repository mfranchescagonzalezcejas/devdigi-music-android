package dev.devdigi.music.features.playback.presentation

import dev.devdigi.music.features.playback.domain.PlaybackFailure
import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackViewModelCommandsTest {
    @Test
    fun signOutClearsPlaybackWithoutCreatingUnusedEngine() =
        runTest {
            var creations = 0

            val viewModel =
                PlaybackViewModel(
                    engineFactory = {
                        creations += 1
                        FakePlaybackEngine()
                    },
                    scope = backgroundScope,
                )

            viewModel.onAccountChanged(null)
            viewModel.clearPlayback()

            testScheduler.runCurrent()

            assertEquals(
                0,
                creations,
            )
            assertEquals(
                PlaybackState(),
                viewModel.state,
            )
        }

    @Test
    fun pauseResumeStopAndRetryStayOnCurrentTarget() =
        runTest {
            val fake =
                FakePlaybackEngine()
            val viewModel =
                playbackVm(
                    fake,
                    backgroundScope,
                )
            val alice =
                playbackVmAccount("alice")
            val selected =
                playbackVmTrack("track-1")

            viewModel.play(
                alice,
                selected,
            )
            testScheduler.runCurrent()

            fake.emit(
                PlaybackState(
                    phase =
                        PlaybackPhase.PLAYING,
                    track = selected,
                ),
            )
            testScheduler.runCurrent()

            viewModel.pause()
            testScheduler.runCurrent()

            assertEquals(
                1,
                fake.pauseCalls,
            )
            assertEquals(
                PlaybackPhase.PAUSED,
                viewModel.state.phase,
            )

            viewModel.resume()
            testScheduler.runCurrent()

            assertEquals(
                1,
                fake.resumeCalls,
            )
            assertEquals(
                PlaybackPhase.PLAYING,
                viewModel.state.phase,
            )

            viewModel.stop()

            assertEquals(
                PlaybackPhase.STOPPED,
                viewModel.state.phase,
            )

            testScheduler.runCurrent()

            assertEquals(
                1,
                fake.stopCalls,
            )

            viewModel.retry()
            testScheduler.runCurrent()

            assertEquals(
                2,
                fake.plays.size,
            )
            assertEquals(
                PlaybackVmTestRequest(
                    alice,
                    selected,
                ),
                fake.plays.last(),
            )
        }

    @Test
    fun safeFailureIsRetryableAndNeverLooksPlaying() =
        runTest {
            val fake =
                FakePlaybackEngine()
            val viewModel =
                playbackVm(
                    fake,
                    backgroundScope,
                )
            val alice =
                playbackVmAccount("alice")
            val selected =
                playbackVmTrack("track-1")

            viewModel.play(
                alice,
                selected,
            )
            testScheduler.runCurrent()

            fake.emit(
                PlaybackState(
                    phase =
                        PlaybackPhase.ERROR,
                    track = selected,
                    failure =
                        PlaybackFailure
                            .NETWORK_OR_SOURCE,
                ),
            )
            testScheduler.runCurrent()

            assertEquals(
                PlaybackPhase.ERROR,
                viewModel.state.phase,
            )
            assertEquals(
                PlaybackFailure
                    .NETWORK_OR_SOURCE,
                viewModel.state.failure,
            )

            viewModel.retry()
            testScheduler.runCurrent()

            assertEquals(
                2,
                fake.plays.size,
            )
            assertEquals(
                PlaybackPhase.PREPARING,
                viewModel.state.phase,
            )
        }
}
