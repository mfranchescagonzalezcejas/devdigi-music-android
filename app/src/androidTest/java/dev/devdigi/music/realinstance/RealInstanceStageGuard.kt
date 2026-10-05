package dev.devdigi.music.realinstance

import androidx.compose.ui.test.ComposeTimeoutException

internal fun <T> realInstanceStage(
    marker: String,
    action: () -> T,
): T =
    try {
        action()
    } catch (_: ComposeTimeoutException) {
        throw AssertionError(marker)
    } catch (_: AssertionError) {
        throw AssertionError(marker)
    } catch (_: Exception) {
        throw AssertionError(marker)
    }
