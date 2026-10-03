package dev.devdigi.music.features.playback.presentation

import dev.devdigi.music.features.playback.domain.PlaybackPhase
import dev.devdigi.music.features.playback.domain.PlaybackState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackViewModelQueueTest {
    @Test
    fun albumSelectionSendsOrderedQueueAndTappedIndex() =
        runTest {
            val fake =
                FakePlaybackEngine()
            val viewModel =
                playbackVm(
                    fake,
                    backgroundScope,
                )
            val account =
                playbackVmAccount(
                    "alice",
                )
            val tracks =
                listOf(
                    playbackVmTrack(
                        "track-1",
                    ),
                    playbackVmTrack(
                        "track-2",
                    ),
                    playbackVmTrack(
                        "track-3",
                    ),
                )

            viewModel.playQueue(
                account = account,
                entries = tracks,
                selectedIndex = 1,
            )

            testScheduler.runCurrent()

            assertEquals(
                listOf(
                    PlaybackVmQueueRequest(
                        account = account,
                        entries = tracks,
                        selectedIndex = 1,
                    ),
                ),
                fake.queueReplacements,
            )

            assertEquals(
                tracks[1],
                viewModel.state.track,
            )

            assertEquals(
                PlaybackPhase.PREPARING,
                viewModel.state.phase,
            )
        }

    @Test
    fun acceptedQueueFollowsServiceTrackTransition() =
        runTest {
            val fake =
                FakePlaybackEngine()
            val viewModel =
                playbackVm(
                    fake,
                    backgroundScope,
                )
            val account =
                playbackVmAccount(
                    "alice",
                )
            val first =
                playbackVmTrack(
                    "track-1",
                )
            val second =
                playbackVmTrack(
                    "track-2",
                )

            viewModel.playQueue(
                account = account,
                entries =
                    listOf(
                        first,
                        second,
                    ),
                selectedIndex = 0,
            )

            testScheduler.runCurrent()

            fake.emit(
                PlaybackState(
                    phase =
                        PlaybackPhase.PLAYING,
                    track = second,
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                second,
                viewModel.state.track,
            )

            assertEquals(
                PlaybackPhase.PLAYING,
                viewModel.state.phase,
            )
        }

    @Test
    fun appPreviousAndNextDelegateToServiceClient() =
        runTest {
            val fake =
                FakePlaybackEngine()
            val viewModel =
                playbackVm(
                    fake,
                    backgroundScope,
                )
            val account =
                playbackVmAccount(
                    "alice",
                )
            val track =
                playbackVmTrack(
                    "track-1",
                )

            viewModel.playQueue(
                account = account,
                entries = listOf(track),
                selectedIndex = 0,
            )

            testScheduler.runCurrent()

            viewModel.previous()
            viewModel.next()

            assertEquals(
                1,
                fake.previousCalls,
            )

            assertEquals(
                1,
                fake.nextCalls,
            )
        }

    @Test
    fun invalidSelectedIndexDoesNotCreateEngine() =
        runTest {
            var creations = 0

            val viewModel =
                PlaybackViewModel(
                    engineFactory = {
                        creations += 1
                        FakePlaybackEngine()
                    },
                    scope =
                    backgroundScope,
                )

            viewModel.playQueue(
                account =
                    playbackVmAccount(
                        "alice",
                    ),
                entries =
                    listOf(
                        playbackVmTrack(
                            "track-1",
                        ),
                    ),
                selectedIndex = 4,
            )

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
    fun delayedNonPlayingRestoreIsAdoptedFromService() =
        runTest {
            val fake =
                FakePlaybackEngine()
            val viewModel =
                playbackVm(
                    fake,
                    backgroundScope,
                )
            val account =
                playbackVmAccount(
                    "alice",
                )
            val restored =
                playbackVmTrack(
                    "restored-track",
                )

            viewModel.onAccountChanged(
                account,
            )

            testScheduler.runCurrent()

            fake.emit(
                PlaybackState(
                    phase =
                        PlaybackPhase.PAUSED,
                    track = restored,
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                restored,
                viewModel.state.track,
            )

            assertEquals(
                PlaybackPhase.PAUSED,
                viewModel.state.phase,
            )
        }

    @Test
    fun stalePlayingStateAfterAccountSwitchIsNotAdoptedAsRestore() =
        runTest {
            val fake =
                FakePlaybackEngine()
            val viewModel =
                playbackVm(
                    fake,
                    backgroundScope,
                )
            val alice =
                playbackVmAccount(
                    "alice",
                )
            val bob =
                playbackVmAccount(
                    "bob",
                )
            val oldTrack =
                playbackVmTrack(
                    "old-track",
                )

            viewModel.playQueue(
                account = alice,
                entries = listOf(oldTrack),
                selectedIndex = 0,
            )

            testScheduler.runCurrent()

            viewModel.onAccountChanged(
                bob,
            )

            testScheduler.runCurrent()

            fake.emit(
                PlaybackState(
                    phase =
                        PlaybackPhase.PLAYING,
                    track = oldTrack,
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                PlaybackState(),
                viewModel.state,
            )
        }
}
