package dev.devdigi.music.realinstance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.devdigi.music.MainActivity
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
    fun authenticatesAgainstRuntimeProvidedInstance() {
        val input =
            readRuntimeInput()

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

        composeRule.waitUntil(
            timeoutMillis = AUTH_TIMEOUT_MS,
        ) {
            runCatching {
                composeRule
                    .onAllNodesWithTag(
                        "recent-albums-screen",
                    ).fetchSemanticsNodes()
                    .isNotEmpty()
            }.getOrDefault(false)
        }

        composeRule
            .onNodeWithTag(
                "recent-albums-screen",
            ).assertIsDisplayed()
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
    }
}
