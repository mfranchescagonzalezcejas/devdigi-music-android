package dev.devdigi.music.realinstance

import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.devdigi.music.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealInstanceAuthenticationTest {
    @get:Rule
    val composeRule =
        createAndroidComposeRule<MainActivity>()

    @Test
    fun validatesDynamicMediaAndQueueAgainstRuntimeProvidedInstance() {
        val input =
            RealInstanceRuntimeInputLoader.read()

        authenticate(
            endpoint = input.endpoint,
            username = input.username,
            password = input.password,
            reportSmokeCases = true,
        )
        emitSmokeCase("MUSIC-65")

        val probeResult =
            safeStage(
                "REAL_STAGE_PROBE_EXECUTION_FAILED",
            ) {
                runBlocking {
                    RealInstanceMediaCandidateProbe()
                        .select(
                            endpoint =
                                input.endpoint,
                            username =
                                input.username,
                            password =
                                input.password,
                        )
                }
            }

        val candidate =
            when (probeResult) {
                is RealInstanceMediaCandidateResult.Found -> {
                    probeResult.candidate
                }

                RealInstanceMediaCandidateResult.Blocked -> {
                    throw AssertionError(
                        "REAL_MEDIA_CANDIDATE_BLOCKED",
                    )
                }

                RealInstanceMediaCandidateResult.Failed -> {
                    throw AssertionError(
                        "REAL_MEDIA_CANDIDATE_PROBE_FAILED",
                    )
                }
            }

        if (candidate.trackCount < 3) {
            throw AssertionError(
                "REAL_MEDIA_CANDIDATE_PROBE_FAILED",
            )
        }

        safeStage(
            "REAL_STAGE_RECENT_ALBUMS_FAILED",
        ) {
            composeRule
                .onNodeWithTag("recent-album-grid")
                .assertIsDisplayed()
        }
        emitSmokeCase("MUSIC-72")

        safeStage(
            "REAL_STAGE_ALBUM_OPEN_FAILED",
        ) {
            openAlbum(
                candidate.albumIndex,
            )
        }

        emitSmokeCase("MUSIC-75")

        safeStage(
            "REAL_STAGE_FLAC_SELECTION_FAILED",
        ) {
            playTrack(
                candidate.flacTrackIndex,
            )

            waitTrackSelected(
                candidate.flacTrackIndex,
            )
        }

        safeStage(
            "REAL_STAGE_FLAC_PLAYBACK_FAILED",
        ) {
            openNowPlayingAndWaitForPlayback()
            backToAlbum()

            waitTrackSelected(
                candidate.flacTrackIndex,
            )
        }

        emitSmokeCase("MUSIC-80")

        safeStage(
            "REAL_STAGE_QUEUE_SEED_FAILED",
        ) {
            playTrack(0)
            waitTrackSelected(0)
            openNowPlayingAndWaitForPlayback()
        }

        safeStage(
            "REAL_STAGE_QUEUE_NEXT_1_FAILED",
        ) {
            composeRule
                .onNodeWithTag(
                    "playback-next",
                ).performClick()

            backToAlbum()
            waitTrackSelected(1)
            openNowPlayingAndWaitForPlayback()
        }

        safeStage(
            "REAL_STAGE_QUEUE_NEXT_2_FAILED",
        ) {
            composeRule
                .onNodeWithTag(
                    "playback-next",
                ).performClick()

            backToAlbum()
            waitTrackSelected(2)
            openNowPlayingAndWaitForPlayback()
        }

        emitSmokeCase("MUSIC-81")
        emitSmokeCase("MUSIC-82")

        safeStage(
            "REAL_STAGE_QUEUE_PREVIOUS_FAILED",
        ) {
            composeRule
                .onNodeWithTag(
                    "playback-previous",
                ).performClick()

            backToAlbum()
            waitTrackSelected(1)
        }

        emitSmokeCase("MUSIC-83")

        val observer =
            safeStage(
                "REAL_STAGE_SYSTEM_CONTROLLER_FAILED",
            ) {
                RealInstancePlaybackObserver.connect()
            }

        observer.use { observer ->
            safeStage(
                "REAL_STAGE_BACKGROUND_PLAYBACK_FAILED",
            ) {
                openNowPlayingAndWaitForPlayback()

                backgroundActivity()

                waitUntilCondition(
                    timeoutMillis =
                    PLAYBACK_TIMEOUT_MS,
                    condition =
                        observer::isPlaying,
                )
            }

            emitSmokeCase("MUSIC-85")

            safeStage(
                "REAL_STAGE_SYSTEM_PAUSE_FAILED",
            ) {
                sendMediaKey(
                    KeyEvent.KEYCODE_MEDIA_PAUSE,
                )

                waitUntilCondition(
                    timeoutMillis =
                    PLAYBACK_TIMEOUT_MS,
                    condition =
                        observer::isPausedWithMedia,
                )

                foregroundActivity()

                waitUntilDisplayed(
                    tag =
                        "playback-phase-PAUSED",
                    timeoutMillis =
                    PLAYBACK_TIMEOUT_MS,
                )
            }

            safeStage(
                "REAL_STAGE_SYSTEM_PLAY_FAILED",
            ) {
                backgroundActivity()

                sendMediaKey(
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                )

                waitUntilCondition(
                    timeoutMillis =
                    PLAYBACK_TIMEOUT_MS,
                    condition =
                        observer::isPlaying,
                )

                foregroundActivity()

                waitUntilDisplayed(
                    tag =
                        "playback-phase-PLAYING",
                    timeoutMillis =
                    PLAYBACK_TIMEOUT_MS,
                )
            }

            emitSmokeCase("MUSIC-86")

            safeStage(
                "REAL_STAGE_SYSTEM_NEXT_FAILED",
            ) {
                val transitionBefore =
                    observer.transitionCount()

                backgroundActivity()

                sendMediaKey(
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                )

                waitUntilCondition(
                    timeoutMillis =
                    PLAYBACK_TIMEOUT_MS,
                    condition = {
                        observer.transitionCount() >
                            transitionBefore
                    },
                )

                foregroundActivity()
                backToAlbum()
                waitTrackSelected(2)
            }

            safeStage(
                "REAL_STAGE_SYSTEM_PREVIOUS_FAILED",
            ) {
                openNowPlayingAndWaitForPlayback()

                val transitionBefore =
                    observer.transitionCount()

                backgroundActivity()

                sendMediaKey(
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                )

                waitUntilCondition(
                    timeoutMillis =
                    PLAYBACK_TIMEOUT_MS,
                    condition = {
                        observer.transitionCount() >
                            transitionBefore
                    },
                )

                foregroundActivity()
                backToAlbum()
                waitTrackSelected(1)
            }

            emitSmokeCase("MUSIC-87")

            safeStage(
                "REAL_STAGE_SIGN_OUT_FAILED",
            ) {
                composeRule
                    .onNodeWithText(
                        "Back",
                    ).assertIsDisplayed()
                    .performClick()

                waitUntilDisplayed(
                    tag =
                        "recent-albums-screen",
                    timeoutMillis =
                    UI_TIMEOUT_MS,
                )

                composeRule
                    .onNodeWithText(
                        "Sign out",
                    ).assertIsDisplayed()
                    .performClick()

                waitUntilDisplayed(
                    tag =
                        "connection-server-url",
                    timeoutMillis =
                    UI_TIMEOUT_MS,
                )
            }

            safeStage(
                "REAL_STAGE_SIGN_OUT_PRESENTATION_CLEAR_FAILED",
            ) {
                assertTagAbsent(
                    "recent-albums-screen",
                )

                assertTagAbsent(
                    "album-details-screen",
                )

                assertTagAbsent(
                    "first-sound-now-playing",
                )

                assertTagAbsent(
                    "first-sound-open-now-playing",
                )
            }

            safeStage(
                "REAL_STAGE_SIGN_OUT_RUNTIME_CLEAR_FAILED",
            ) {
                waitUntilCondition(
                    timeoutMillis =
                    PLAYBACK_TIMEOUT_MS,
                    condition = {
                        !observer.hasMediaItem() &&
                            !observer.hasPreviousCommand() &&
                            !observer.hasNextCommand()
                    },
                )
            }
        }
        emitSmokeCase("MUSIC-89")
        emitSmokeCase("MUSIC-69")

        input.secondary?.let { secondary ->
            safeStage(
                "REAL_STAGE_SECOND_IDENTITY_AUTH_FAILED",
            ) {
                authenticate(
                    endpoint = secondary.endpoint,
                    username = secondary.username,
                    password = secondary.password,
                )
            }

            safeStage(
                "REAL_STAGE_SECOND_IDENTITY_ISOLATION_FAILED",
            ) {
                waitUntilDisplayed(
                    tag =
                        "recent-albums-screen",
                    timeoutMillis =
                    AUTH_TIMEOUT_MS,
                )

                assertTagAbsent(
                    "album-details-screen",
                )

                assertTagAbsent(
                    "first-sound-now-playing",
                )

                assertTagAbsent(
                    "first-sound-open-now-playing",
                )
            }

            safeStage(
                "REAL_STAGE_SECOND_IDENTITY_RUNTIME_ISOLATION_FAILED",
            ) {
                RealInstancePlaybackObserver
                    .connect()
                    .use { secondaryObserver ->
                        waitUntilCondition(
                            timeoutMillis =
                            PLAYBACK_TIMEOUT_MS,
                            condition = {
                                !secondaryObserver.isPlaying() &&
                                    !secondaryObserver.hasMediaItem() &&
                                    !secondaryObserver.hasPreviousCommand() &&
                                    !secondaryObserver.hasNextCommand()
                            },
                        )
                    }
            }
        }
    }

    private fun emitSmokeCase(key: String) {
        val status =
            Bundle().apply {
                putString("devdigi.rc.case", key)
            }

        InstrumentationRegistry
            .getInstrumentation()
            .sendStatus(0, status)
    }

    private fun <T> safeStage(
        marker: String,
        action: () -> T,
    ): T =
        realInstanceStage(
            marker = marker,
            action = action,
        )

    private fun authenticate(
        endpoint: String,
        username: String,
        password: String,
        reportSmokeCases: Boolean = false,
    ) {
        realInstanceStage(
            marker =
                "REAL_STAGE_AUTH_SERVER_INPUT_FAILED",
        ) {
            composeRule
                .onNodeWithTag(
                    "connection-server-url",
                ).assertExists()
                .performTextReplacement(
                    endpoint,
                )
        }

        realInstanceStage(
            marker =
                "REAL_STAGE_AUTH_SERVER_ENABLE_FAILED",
        ) {
            waitUntilEnabled(
                "connection-save-server",
            )
        }

        realInstanceStage(
            marker =
                "REAL_STAGE_AUTH_SERVER_SAVE_FAILED",
        ) {
            composeRule
                .onNodeWithTag(
                    "connection-save-server",
                ).assertIsEnabled()
                .performClick()
        }

        realInstanceStage(
            marker =
                "REAL_STAGE_AUTH_SERVER_PERSIST_FAILED",
        ) {
            composeRule.waitUntil(
                timeoutMillis = UI_TIMEOUT_MS,
            ) {
                runCatching {
                    composeRule
                        .onNodeWithTag(
                            "connection-save-server",
                        ).assertIsNotEnabled()

                    true
                }.getOrDefault(false)
            }
        }

        realInstanceStage(
            marker =
                "REAL_STAGE_AUTH_SERVER_NOT_AUTHENTICATED_FAILED",
        ) {
            assertTagAbsent(
                "recent-albums-screen",
            )

            composeRule
                .onNodeWithTag(
                    "connection-username",
                ).assertIsDisplayed()

            composeRule
                .onNodeWithTag(
                    "connection-password",
                ).assertIsDisplayed()
        }

        if (reportSmokeCases) {
            emitSmokeCase("MUSIC-64")
        }

        realInstanceStage(
            marker =
                "REAL_STAGE_AUTH_CREDENTIALS_FAILED",
        ) {
            composeRule
                .onNodeWithTag(
                    "connection-username",
                ).assertExists()
                .performTextReplacement(
                    username,
                )

            composeRule
                .onNodeWithTag(
                    "connection-password",
                ).assertExists()
                .performTextReplacement(
                    password,
                )

            waitUntilEnabled(
                "connection-sign-in",
            )
        }

        realInstanceStage(
            marker =
                "REAL_STAGE_AUTH_SUBMIT_FAILED",
        ) {
            composeRule
                .onNodeWithTag(
                    "connection-password",
                ).performImeAction()
        }

        realInstanceStage(
            marker =
                "REAL_STAGE_AUTH_DESTINATION_FAILED",
        ) {
            waitUntilDisplayed(
                tag =
                    "recent-albums-screen",
                timeoutMillis =
                AUTH_TIMEOUT_MS,
            )
        }
    }

    private fun openAlbum(index: Int) {
        val tag =
            "recent-album-card-$index"

        composeRule
            .onNodeWithTag(
                "recent-album-grid",
            ).performScrollToNode(
                hasTestTag(tag),
            )

        composeRule
            .onNodeWithTag(tag)
            .performClick()

        waitUntilDisplayed(
            "album-details-screen",
        )

        waitUntilDisplayed(
            tag = "album-track-list",
            timeoutMillis = ALBUM_LOAD_TIMEOUT_MS,
        )
    }

    private fun playTrack(index: Int) {
        val tag =
            "album-track-row-$index"

        composeRule
            .onNodeWithTag(
                "album-track-list",
            ).performScrollToNode(
                hasTestTag(tag),
            )

        composeRule
            .onNodeWithTag(tag)
            .performClick()
    }

    private fun waitTrackSelected(index: Int) {
        val tag =
            "album-track-row-$index"

        composeRule
            .onNodeWithTag(
                "album-track-list",
            ).performScrollToNode(
                hasTestTag(tag),
            )

        composeRule.waitUntil(
            timeoutMillis = PLAYBACK_TIMEOUT_MS,
        ) {
            runCatching {
                composeRule
                    .onNodeWithTag(tag)
                    .assertIsSelected()

                true
            }.getOrDefault(false)
        }
    }

    private fun openNowPlayingAndWaitForPlayback() {
        waitUntilDisplayed(
            "first-sound-open-now-playing",
        )

        composeRule
            .onNodeWithTag(
                "first-sound-open-now-playing",
            ).performClick()

        waitUntilDisplayed(
            "first-sound-now-playing",
        )

        waitUntilDisplayed(
            tag = "playback-phase-PLAYING",
            timeoutMillis = PLAYBACK_TIMEOUT_MS,
        )
    }

    private fun backToAlbum() {
        composeRule
            .onNodeWithTag(
                "first-sound-now-playing-back",
            ).performClick()

        waitUntilDisplayed(
            "album-details-screen",
        )
    }

    private fun backgroundActivity() {
        val moved =
            composeRule
                .activity
                .moveTaskToBack(true)

        if (!moved) {
            throw AssertionError(
                "REAL_BACKGROUND_FAILED",
            )
        }
    }

    private fun foregroundActivity() {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val intent =
            Intent(
                context,
                MainActivity::class.java,
            ).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )

        context.startActivity(intent)

        waitUntilDisplayed(
            tag = "first-sound-now-playing",
            timeoutMillis = UI_TIMEOUT_MS,
        )
    }

    private fun sendMediaKey(keyCode: Int) {
        val instrumentation =
            InstrumentationRegistry
                .getInstrumentation()

        val descriptor =
            instrumentation
                .uiAutomation
                .executeShellCommand(
                    "input keyevent $keyCode",
                )

        ParcelFileDescriptor
            .AutoCloseInputStream(
                descriptor,
            ).use { stream ->
                while (stream.read() != -1) {
                    Unit
                }
            }
    }

    private fun waitUntilCondition(
        timeoutMillis: Long,
        condition: () -> Boolean,
    ) {
        composeRule.waitUntil(
            timeoutMillis = timeoutMillis,
            condition = condition,
        )
    }

    private fun assertTagAbsent(tag: String) {
        val nodes =
            composeRule
                .onAllNodesWithTag(tag)
                .fetchSemanticsNodes()

        if (nodes.isNotEmpty()) {
            throw AssertionError(
                "REAL_PRESENTATION_STATE_REMAINS",
            )
        }
    }

    private fun waitUntilDisplayed(
        tag: String,
        timeoutMillis: Long = UI_TIMEOUT_MS,
    ) {
        composeRule.waitUntil(
            timeoutMillis = timeoutMillis,
        ) {
            runCatching {
                composeRule
                    .onAllNodesWithTag(tag)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }.getOrDefault(false)
        }

        composeRule
            .onNodeWithTag(tag)
            .assertIsDisplayed()
    }

    private fun waitUntilEnabled(tag: String) {
        composeRule.waitUntil(
            timeoutMillis = UI_TIMEOUT_MS,
        ) {
            runCatching {
                composeRule
                    .onNodeWithTag(tag)
                    .assertExists()
                    .assertIsEnabled()

                true
            }.getOrDefault(false)
        }
    }

    private companion object {
        const val UI_TIMEOUT_MS =
            10_000L

        const val AUTH_TIMEOUT_MS =
            30_000L

        const val PLAYBACK_TIMEOUT_MS =
            30_000L

        const val ALBUM_LOAD_TIMEOUT_MS =
            30_000L
    }
}
