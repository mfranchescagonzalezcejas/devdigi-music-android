package dev.devdigi.music.realinstance

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

internal object RealInstanceRuntimeInputLoader {
    fun read(): RealInstanceInput {
        val instrumentation =
            InstrumentationRegistry
                .getInstrumentation()

        val source =
            InstrumentationRegistry
                .getArguments()
                .getString(
                    INPUT_SOURCE_ARGUMENT,
                ).orEmpty()

        val filesDir =
            when (source) {
                "",
                INPUT_SOURCE_TARGET,
                -> {
                    instrumentation
                        .targetContext
                        .filesDir
                }

                INPUT_SOURCE_INSTRUMENTATION -> {
                    instrumentation
                        .context
                        .filesDir
                }

                else -> {
                    throw AssertionError(
                        "REAL_INSTANCE_INPUT_SOURCE_INVALID",
                    )
                }
            }

        return parse(
            readAndDelete(
                File(
                    filesDir,
                    INPUT_FILE_NAME,
                ),
            ),
        )
    }

    private fun readAndDelete(inputFile: File): String {
        if (!inputFile.isFile) {
            throw AssertionError(
                "REAL_INSTANCE_INPUT_MISSING",
            )
        }

        return try {
            inputFile.readText()
        } catch (_: Throwable) {
            throw AssertionError(
                "REAL_INSTANCE_INPUT_UNREADABLE",
            )
        } finally {
            inputFile.delete()
        }
    }

    private fun parse(payload: String): RealInstanceInput {
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

        val secondary =
            json
                .optJSONObject(
                    "secondary",
                )?.let { secondaryJson ->
                    parseSecondary(
                        secondaryJson,
                        endpoint,
                        username,
                    )
                }

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
            secondary = secondary,
        )
    }

    private fun parseSecondary(
        json: JSONObject,
        primaryEndpoint: String,
        primaryUsername: String,
    ): RealSecondaryIdentityInput {
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
                "REAL_SECOND_IDENTITY_INVALID",
            )
        }

        if (
            endpoint == primaryEndpoint &&
            username == primaryUsername
        ) {
            throw AssertionError(
                "REAL_SECOND_IDENTITY_NOT_DISTINCT",
            )
        }

        return RealSecondaryIdentityInput(
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

    private const val INPUT_SOURCE_ARGUMENT =
        "devdigiInputSource"

    private const val INPUT_SOURCE_TARGET =
        "target"

    private const val INPUT_SOURCE_INSTRUMENTATION =
        "instrumentation"

    private const val INPUT_FILE_NAME =
        "real_instance_input.json"
}

internal data class RealInstanceInput(
    val endpoint: String,
    val username: String,
    val password: String,
    val secondary: RealSecondaryIdentityInput?,
)

internal data class RealSecondaryIdentityInput(
    val endpoint: String,
    val username: String,
    val password: String,
)
