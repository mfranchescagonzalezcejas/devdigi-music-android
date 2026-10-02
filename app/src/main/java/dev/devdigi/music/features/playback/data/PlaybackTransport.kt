package dev.devdigi.music.features.playback.data

import okhttp3.OkHttpClient

internal fun playbackHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
