package dev.devdigi.music.features.playback.presentation

import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackViewModelCoreTest {
    @Test
    fun engineIsLazyAndSingleOwnedPerViewModel() =
        runTest {
            val fake = FakePlaybackEngine()
            var creations = 0

            val viewModel =
                PlaybackViewModel(
                    engineFactory = {
                        creations += 1
                        fake
                    },
                    scope = backgroundScope,
                )

            assertEquals(0, creations)

            val alice =
                playbackVmAccount("alice")
            val first =
                playbackVmTrack("track-1")
            val second =
                playbackVmTrack("track-2")

            viewModel.play(alice, first)
            testScheduler.runCurrent()

            viewModel.play(alice, second)
            testScheduler.runCurrent()

            assertEquals(1, creations)
            assertEquals(
                listOf(
                    PlaybackVmTestRequest(
                        alice,
                        first,
                    ),
                    PlaybackVmTestRequest(
                        alice,
                        second,
                    ),
                ),
                fake.plays,
            )
        }

    @Test
    fun currentTrackPlaybackStateIsPublished() =
        runTest {
            val fake = FakePlaybackEngine()
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

            assertEquals(
                PlaybackState(
                    phase =
                        PlaybackPhase.PREPARING,
                    track = selected,
                ),
                viewModel.state,
            )

            fake.emit(
                PlaybackState(
                    phase =
                        PlaybackPhase.PLAYING,
                    track = selected,
                ),
            )
            testScheduler.runCurrent()

            assertEquals(
                PlaybackPhase.PLAYING,
                viewModel.state.phase,
            )
            assertSame(
                selected,
                viewModel.state.track,
            )
        }

    @Test
    fun staleTrackStateCannotReplaceCurrentSelection() =
        runTest {
            val fake = FakePlaybackEngine()
            val viewModel =
                playbackVm(
                    fake,
                    backgroundScope,
                )
            val alice =
                playbackVmAccount("alice")
            val first =
                playbackVmTrack("track-1")
            val second =
                playbackVmTrack("track-2")

            viewModel.play(alice, first)
            testScheduler.runCurrent()

            viewModel.play(alice, second)
            testScheduler.runCurrent()

            fake.emit(
                PlaybackState(
                    phase =
                        PlaybackPhase.PLAYING,
                    track = first,
                ),
            )
            testScheduler.runCurrent()

            assertEquals(
                PlaybackState(
                    phase =
                        PlaybackPhase.PREPARING,
                    track = second,
                ),
                viewModel.state,
            )

            fake.emit(
                PlaybackState(
                    phase =
                        PlaybackPhase.PLAYING,
                    track = second,
                ),
            )
            testScheduler.runCurrent()

            assertEquals(
                PlaybackPhase.PLAYING,
                viewModel.state.phase,
            )
            assertEquals(
                second,
                viewModel.state.track,
            )
        }

    @Test
    fun accountChangeClearsUiAndReconcilesServicePlayback() =
        runTest {
            val fake = FakePlaybackEngine()
            val viewModel =
                playbackVm(
                    fake,
                    backgroundScope,
                )
            val alice =
                playbackVmAccount("alice")
            val bob =
                playbackVmAccount("bob")
            val selected =
                playbackVmTrack("shared-track")

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

            viewModel.onAccountChanged(bob)
            testScheduler.runCurrent()

            assertEquals(
                PlaybackState(),
                viewModel.state,
            )
            assertEquals(
                listOf(bob),
                fake.reconciliations,
            )
            assertEquals(
                0,
                fake.stopCalls,
            )

            fake.emit(
                PlaybackState(
                    phase =
                        PlaybackPhase.PLAYING,
                    track = selected,
                ),
            )
            testScheduler.runCurrent()

            assertEquals(
                PlaybackState(),
                viewModel.state,
            )
            viewModel.play(
                bob,
                selected,
            )
            testScheduler.runCurrent()

            assertEquals(
                PlaybackVmTestRequest(
                    bob,
                    selected,
                ),
                fake.plays.last(),
            )
        }

    @Test
    fun matchingAccountReconnectAdoptsSafeServicePlaybackState() =
        runTest {
            val fake =
                FakePlaybackEngine()
            val alice =
                playbackVmAccount("alice")
            val selected =
                playbackVmTrack("track-1")

            fake.play(
                alice,
                selected,
            )
            fake.emit(
                PlaybackState(
                    phase =
                        PlaybackPhase.PLAYING,
                    track = selected,
                ),
            )

            val viewModel =
                playbackVm(
                    fake,
                    backgroundScope,
                )

            viewModel.onAccountChanged(alice)
            testScheduler.runCurrent()

            assertEquals(
                listOf(alice),
                fake.reconciliations,
            )
            assertEquals(
                PlaybackPhase.PLAYING,
                viewModel.state.phase,
            )
            assertEquals(
                selected,
                viewModel.state.track,
            )
        }
}
