package dev.devdigi.music.realinstance

import androidx.compose.ui.test.ComposeTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RealInstanceStageGuardTest {
    @Test
    fun composeTimeoutIsMappedToStageMarker() {
        val marker =
            "REAL_STAGE_SYNTHETIC_TIMEOUT_FAILED"

        try {
            realInstanceStage(marker) {
                throw ComposeTimeoutException(
                    "synthetic timeout",
                )
            }

            fail(
                "Expected stage assertion",
            )
        } catch (error: AssertionError) {
            assertEquals(
                marker,
                error.message,
            )
        }
    }
}
