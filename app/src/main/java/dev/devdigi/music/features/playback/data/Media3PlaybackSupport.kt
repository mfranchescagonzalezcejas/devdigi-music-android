package dev.devdigi.music.features.playback.data

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import dev.devdigi.music.features.playback.domain.PlaybackFailure

@UnstableApi
private fun playbackFailure(error: PlaybackException): PlaybackFailure {
    val responseCode =
        findHttpResponseCode(error)

    if (
        responseCode == 401 ||
        responseCode == 403
    ) {
        return PlaybackFailure
            .AUTHENTICATION_REQUIRED
    }

    return playbackFailureForErrorCode(
        error.errorCode,
    )
}

@UnstableApi
private fun findHttpResponseCode(error: Throwable): Int? {
    var current: Throwable? = error

    repeat(8) {
        val value =
            current
                ?: return null

        if (
            value is
                HttpDataSource
                    .InvalidResponseCodeException
        ) {
            return value.responseCode
        }

        current = value.cause
    }

    return null
}

internal fun playbackFailureForErrorCode(errorCode: Int): PlaybackFailure =
    when (errorCode) {
        PlaybackException
            .ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException
            .ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException
            .ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException
            .ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException
            .ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        -> {
            PlaybackFailure
                .UNSUPPORTED_PLAYBACK
        }

        PlaybackException.ERROR_CODE_TIMEOUT -> {
            PlaybackFailure
                .NETWORK_OR_SOURCE
        }

        in 2000..2008 -> {
            PlaybackFailure
                .NETWORK_OR_SOURCE
        }

        else -> {
            PlaybackFailure.UNKNOWN
        }
    }
