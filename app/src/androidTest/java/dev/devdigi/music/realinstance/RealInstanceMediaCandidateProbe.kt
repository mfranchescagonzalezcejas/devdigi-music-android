package dev.devdigi.music.realinstance

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.DefaultSubsonicAuthSigner
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.connection.SubsonicAuthSigner
import dev.devdigi.music.features.library.data.remote.AlbumDetailsRemoteResult
import dev.devdigi.music.features.library.data.remote.OkHttpAlbumDetailsRemoteDataSource
import dev.devdigi.music.features.library.data.remote.OkHttpRecentAlbumsRemoteDataSource
import dev.devdigi.music.features.library.data.remote.RecentAlbumsRemoteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class RealInstanceMediaCandidate(
    val albumIndex: Int,
    val trackCount: Int,
    val flacTrackIndex: Int,
)

internal sealed interface RealInstanceMediaCandidateResult {
    data class Found(
        val candidate: RealInstanceMediaCandidate,
    ) : RealInstanceMediaCandidateResult

    data object Blocked :
        RealInstanceMediaCandidateResult

    data object Failed :
        RealInstanceMediaCandidateResult
}

internal class RealInstanceMediaCandidateProbe(
    private val signer: SubsonicAuthSigner =
        DefaultSubsonicAuthSigner(),
) {
    private val recentAlbums =
        OkHttpRecentAlbumsRemoteDataSource(
            signer = signer,
        )

    private val albumDetails =
        OkHttpAlbumDetailsRemoteDataSource(
            signer = signer,
        )

    private val transport =
        OkHttpClient
            .Builder()
            .readTimeout(
                10_000L,
                TimeUnit.MILLISECONDS,
            ).callTimeout(
                15_000L,
                TimeUnit.MILLISECONDS,
            ).followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

    suspend fun select(
        endpoint: String,
        username: String,
        password: String,
    ): RealInstanceMediaCandidateResult {
        val parsed =
            ServerEndpoint.parse(endpoint)

        val serverEndpoint =
            (parsed as? EndpointParseResult.Valid)
                ?.endpoint
                ?: return RealInstanceMediaCandidateResult
                    .Failed

        val account =
            ServerAccountIdentity(
                endpoint = serverEndpoint,
                username = username,
            )

        val credentials =
            AuthCredentials.create(
                username = username,
                password = password,
            )

        val albums =
            when (
                val result =
                    recentAlbums.loadRecentAlbums(
                        account = account,
                        credentials = credentials,
                    )
            ) {
                is RecentAlbumsRemoteResult.Success -> {
                    result.albums
                }

                else -> {
                    return RealInstanceMediaCandidateResult
                        .Failed
                }
            }

        for (
        (albumIndex, album) in
        albums.withIndex()
        ) {
            val details =
                when (
                    val result =
                        albumDetails.loadAlbum(
                            account = account,
                            credentials = credentials,
                            albumId = album.id,
                        )
                ) {
                    is AlbumDetailsRemoteResult.Success -> {
                        result.album
                    }

                    else -> {
                        return RealInstanceMediaCandidateResult
                            .Failed
                    }
                }

            if (details.tracks.size < MIN_TRACKS) {
                continue
            }

            when (
                val flac =
                    findFlacTrackIndex(
                        endpoint = serverEndpoint,
                        credentials = credentials,
                        albumId = album.id,
                    )
            ) {
                is FlacProbeResult.Found -> {
                    if (
                        flac.index !in
                        details.tracks.indices
                    ) {
                        return RealInstanceMediaCandidateResult
                            .Failed
                    }

                    return RealInstanceMediaCandidateResult
                        .Found(
                            RealInstanceMediaCandidate(
                                albumIndex = albumIndex,
                                trackCount =
                                    details.tracks.size,
                                flacTrackIndex =
                                    flac.index,
                            ),
                        )
                }

                FlacProbeResult.NotFound -> {
                    continue
                }

                FlacProbeResult.Failed -> {
                    return RealInstanceMediaCandidateResult
                        .Failed
                }
            }
        }

        return RealInstanceMediaCandidateResult.Blocked
    }

    private suspend fun findFlacTrackIndex(
        endpoint: ServerEndpoint,
        credentials: AuthCredentials,
        albumId: String,
    ): FlacProbeResult =
        withContext(Dispatchers.IO) {
            executeFlacTrackProbe(
                endpoint = endpoint,
                credentials = credentials,
                albumId = albumId,
            )
        }

    private fun executeFlacTrackProbe(
        endpoint: ServerEndpoint,
        credentials: AuthCredentials,
        albumId: String,
    ): FlacProbeResult {
        val baseUrl =
            endpoint.value
                .toHttpUrlOrNull()
                ?: return FlacProbeResult.Failed

        val signature =
            signer.sign(credentials)

        val url =
            baseUrl
                .newBuilder()
                .encodedPath(
                    "${baseUrl.encodedPath.trimEnd('/')}" +
                        "/rest/getAlbum.view",
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
                    "f",
                    "json",
                ).addQueryParameter(
                    "id",
                    albumId,
                ).build()

        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()

        val response =
            try {
                transport
                    .newCall(request)
                    .execute()
            } catch (_: IOException) {
                return FlacProbeResult.Failed
            }

        try {
            if (!response.isSuccessful) {
                return FlacProbeResult.Failed
            }

            val body =
                response.body
                    ?: return FlacProbeResult.Failed

            val bytes =
                ByteArray(
                    MAX_RESPONSE_BYTES + 1,
                )

            val stream =
                body.byteStream()

            var count = 0

            while (count < bytes.size) {
                val read =
                    stream.read(
                        bytes,
                        count,
                        bytes.size - count,
                    )

                if (read == -1) {
                    break
                }

                if (read == 0) {
                    return FlacProbeResult.Failed
                }

                count += read
            }

            if (count > MAX_RESPONSE_BYTES) {
                return FlacProbeResult.Failed
            }

            return parseFlacIndex(
                String(
                    bytes,
                    0,
                    count,
                    Charsets.UTF_8,
                ),
            )
        } catch (_: IOException) {
            return FlacProbeResult.Failed
        } finally {
            response.close()
        }
    }

    private fun parseFlacIndex(payload: String): FlacProbeResult {
        val envelope =
            runCatching {
                JSONObject(payload)
                    .getJSONObject(
                        "subsonic-response",
                    )
            }.getOrNull()
                ?: return FlacProbeResult.Failed

        if (
            envelope.optString(
                "status",
                "",
            ) != "ok"
        ) {
            return FlacProbeResult.Failed
        }

        val album =
            envelope.optJSONObject("album")
                ?: return FlacProbeResult.Failed

        val songs =
            album.optJSONArray("song")
                ?: return FlacProbeResult.NotFound

        for (index in 0 until songs.length()) {
            val song =
                songs.optJSONObject(index)
                    ?: return FlacProbeResult.Failed

            val suffix =
                (song.opt("suffix") as? String)
                    ?.trim()
                    ?.lowercase()

            val contentType =
                (song.opt("contentType") as? String)
                    ?.trim()
                    ?.lowercase()

            if (
                suffix == "flac" ||
                contentType == "audio/flac"
            ) {
                return FlacProbeResult.Found(
                    index,
                )
            }
        }

        return FlacProbeResult.NotFound
    }

    private sealed interface FlacProbeResult {
        data class Found(
            val index: Int,
        ) : FlacProbeResult

        data object NotFound :
            FlacProbeResult

        data object Failed :
            FlacProbeResult
    }

    private companion object {
        const val MIN_TRACKS = 3
        const val MAX_RESPONSE_BYTES = 262_144
    }
}
