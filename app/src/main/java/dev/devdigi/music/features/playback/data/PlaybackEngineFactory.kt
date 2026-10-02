package dev.devdigi.music.features.playback.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dev.devdigi.music.connection.AuthSecretStore
import dev.devdigi.music.connection.SubsonicAuthSigner
import dev.devdigi.music.features.playback.domain.PlaybackEngine

@OptIn(UnstableApi::class)
fun createMedia3PlaybackEngine(
    context: Context,
    secretStore: AuthSecretStore,
    signer: SubsonicAuthSigner,
): PlaybackEngine =
    Media3PlaybackEngine(
        context = context,
        resolver =
            SecureStreamResolver(
                secretStore = secretStore,
                signer = signer,
            ),
    )
