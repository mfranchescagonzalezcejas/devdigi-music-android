package dev.devdigi.music.realinstance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.devdigi.music.MainActivity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RealInstanceAuthenticationTest {
    @get:Rule
    val composeRule =
        createAndroidComposeRule<MainActivity>()

    @Test
    fun validatesDynamicMediaAndQueueAgainstRuntimeProvidedInstance() {
        val input =
            readRuntimeInput()

        safeStage(
            "REAL_STAGE_AUTH_FAILED",
        ) {
            authenticate(input)
        }

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
            "REAL_STAGE_ALBUM_OPEN_FAILED",
        ) {
            openAlbum(
                candidate.albumIndex,
            )
        }

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
    }

    private fun <T> safeStage(
        marker: String,
        action: () -> T,
    ): T =
        try {
            action()
        } catch (_: AssertionError) {
            throw AssertionError(marker)
        } catch (_: Exception) {
            throw AssertionError(marker)
        }

    private fun authenticate(input: RealInstanceInput) {
        composeRule
            .onNodeWithTag(
                "connection-server-url",
            ).assertExists()
            .performTextReplacement(
                input.endpoint,
            )

        waitUntilEnabled(
            "connection-save-server",
        )

        composeRule
            .onNodeWithTag(
                "connection-save-server",
            ).performClick()

        composeRule
            .onNodeWithTag(
                "connection-username",
            ).assertExists()
            .performTextReplacement(
                input.username,
            )

        composeRule
            .onNodeWithTag(
                "connection-password",
            ).assertExists()
            .performTextReplacement(
                input.password,
            )

        waitUntilEnabled(
            "connection-sign-in",
        )

        composeRule
            .onNodeWithTag(
                "connection-password",
            ).performImeAction()

        waitUntilDisplayed(
            tag = "recent-albums-screen",
            timeoutMillis = AUTH_TIMEOUT_MS,
        )
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

    private fun readRuntimeInput(): RealInstanceInput {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val inputFile =
            File(
                context.filesDir,
                INPUT_FILE_NAME,
            )

        if (!inputFile.isFile) {
            throw AssertionError(
                "REAL_INSTANCE_INPUT_MISSING",
            )
        }

        val payload =
            try {
                inputFile.readText()
            } catch (_: Throwable) {
                throw AssertionError(
                    "REAL_INSTANCE_INPUT_UNREADABLE",
                )
            } finally {
                inputFile.delete()
            }

        val json =
            try {
                JSONObject(payload)
            } catch (_: Throwable) {
                throw AssertionError(
                    "REAL_INSTANCE_INPUT_INVALID",
                )
            }

        val endpoint =
            requiredValue(
                json,
                "endpoint",
            )

        val username =
            requiredValue(
                json,
                "username",
            )

        val password =
            requiredValue(
                json,
                "password",
            )

        if (
            !endpoint.startsWith(
                "https://",
            )
        ) {
            throw AssertionError(
                "REAL_INSTANCE_ENDPOINT_INVALID",
            )
        }

        return RealInstanceInput(
            endpoint = endpoint,
            username = username,
            password = password,
        )
    }

    private fun requiredValue(
        json: JSONObject,
        key: String,
    ): String {
        val value =
            json.optString(
                key,
                "",
            )

        if (value.isBlank()) {
            throw AssertionError(
                "REAL_INSTANCE_INPUT_INVALID",
            )
        }

        return value
    }

    private data class RealInstanceInput(
        val endpoint: String,
        val username: String,
        val password: String,
    )

    private companion object {
        const val INPUT_FILE_NAME =
            "real_instance_input.json"

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
