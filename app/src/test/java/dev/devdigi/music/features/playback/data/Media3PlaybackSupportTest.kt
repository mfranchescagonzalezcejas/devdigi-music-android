package dev.devdigi.music.features.playback.data

import androidx.media3.common.PlaybackException
import dev.devdigi.music.features.playback.domain.PlaybackFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Media3PlaybackSupportTest {
    @Test
    fun playbackTransportDisablesRedirectsAndAutomaticRetry() {
        val client = playbackHttpClient()

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
    }

    @Test
    fun media3ErrorsMapToSafePlaybackFailures() {
        assertEquals(
            PlaybackFailure.UNSUPPORTED_PLAYBACK,
            playbackFailureForErrorCode(
                media3Code(
                    "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED",
                ),
            ),
        )

        assertEquals(
            PlaybackFailure.NETWORK_OR_SOURCE,
            playbackFailureForErrorCode(
                media3Code(
                    "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT",
                ),
            ),
        )

        assertEquals(
            PlaybackFailure.UNKNOWN,
            playbackFailureForErrorCode(
                media3Code(
                    "ERROR_CODE_UNSPECIFIED",
                ),
            ),
        )
    }

    private fun media3Code(fieldName: String): Int =
        PlaybackException::class.java
            .getField(fieldName)
            .getInt(null)
}
