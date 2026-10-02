package dev.devdigi.music.features.playback.data

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.AuthSecretStore
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.SubsonicAuthSigner
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class ResolvedStreamSource(
    internal val url: HttpUrl,
) {
    override fun toString(): String = "ResolvedStreamSource(url=***)"
}

internal sealed interface StreamResolutionResult {
    data class Success(
        val source: ResolvedStreamSource,
    ) : StreamResolutionResult

    data object AuthenticationRequired :
        StreamResolutionResult

    data object InvalidRequest :
        StreamResolutionResult
}

internal fun interface PlaybackSourceResolver {
    suspend fun resolve(
        account: ServerAccountIdentity,
        trackId: String,
    ): StreamResolutionResult
}

internal class SecureStreamResolver(
    private val secretStore: AuthSecretStore,
    private val signer: SubsonicAuthSigner,
) : PlaybackSourceResolver {
    override suspend fun resolve(
        account: ServerAccountIdentity,
        trackId: String,
    ): StreamResolutionResult {
        if (trackId.isBlank()) {
            return StreamResolutionResult.InvalidRequest
        }

        val stored =
            secretStore
                .read(account.endpoint)
                .getOrElse {
                    return StreamResolutionResult
                        .AuthenticationRequired
                }
                ?: return StreamResolutionResult
                    .AuthenticationRequired

        if (stored.username != account.username) {
            return StreamResolutionResult
                .AuthenticationRequired
        }

        val credentials =
            AuthCredentials.create(
                username = stored.username,
                password = stored.secret,
            )

        val signature =
            signer.sign(credentials)

        val baseUrl =
            account.endpoint.value
                .toHttpUrlOrNull()
                ?: return StreamResolutionResult
                    .InvalidRequest

        val url =
            baseUrl
                .newBuilder()
                .encodedPath(
                    "${baseUrl.encodedPath.trimEnd('/')}" +
                        "/rest/stream.view",
                ).addQueryParameter(
                    "u",
                    credentials.username,
                ).addQueryParameter(
                    "t",
                    signature.token,
                ).addQueryParameter(
                    "s",
                    signature.salt,
                ).addQueryParameter(
                    "v",
                    "1.13.0",
                ).addQueryParameter(
                    "c",
                    "devdigi-music",
                ).addQueryParameter(
                    "id",
                    trackId,
                ).addQueryParameter(
                    "format",
                    "raw",
                ).build()

        return StreamResolutionResult.Success(
            ResolvedStreamSource(url),
        )
    }
}
