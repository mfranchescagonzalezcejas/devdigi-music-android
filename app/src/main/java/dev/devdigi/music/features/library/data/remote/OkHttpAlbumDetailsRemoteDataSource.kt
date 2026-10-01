package dev.devdigi.music.features.library.data.remote

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.SubsonicAuthSigner
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class OkHttpAlbumDetailsRemoteDataSource(
    private val signer: SubsonicAuthSigner,
    readTimeoutMillis: Long = 10_000L,
    callTimeoutMillis: Long = 15_000L,
) : AlbumDetailsRemoteDataSource {
    private val transport =
        OkHttpClient
            .Builder()
            .readTimeout(
                readTimeoutMillis,
                TimeUnit.MILLISECONDS,
            ).callTimeout(
                callTimeoutMillis,
                TimeUnit.MILLISECONDS,
            ).followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

    override suspend fun loadAlbum(
        account: ServerAccountIdentity,
        credentials: AuthCredentials,
        albumId: String,
    ): AlbumDetailsRemoteResult {
        if (account.username != credentials.username) {
            return AlbumDetailsRemoteResult
                .AuthenticationRequired
        }

        val baseUrl =
            account.endpoint.value
                .toHttpUrlOrNull()
                ?: return AlbumDetailsRemoteResult
                    .MalformedResponse

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

        return suspendCancellableCoroutine { continuation ->
            val call =
                transport.newCall(request)

            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(
                                AlbumDetailsRemoteResult
                                    .NetworkError,
                            )
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val result =
                            try {
                                parseResponse(
                                    response,
                                )
                            } catch (_: IOException) {
                                AlbumDetailsRemoteResult
                                    .NetworkError
                            }

                        if (continuation.isActive) {
                            continuation.resume(
                                result,
                            )
                        }
                    }
                },
            )
        }
    }

    private fun parseResponse(response: Response): AlbumDetailsRemoteResult =
        response.use {
            if (
                response.code == 401 ||
                response.code == 403
            ) {
                return@use AlbumDetailsRemoteResult
                    .AuthenticationRequired
            }

            if (!response.isSuccessful) {
                return@use AlbumDetailsRemoteResult
                    .ServerError
            }

            val body =
                response.body
                    ?: return@use AlbumDetailsRemoteResult
                        .MalformedResponse

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
                    return@use AlbumDetailsRemoteResult
                        .MalformedResponse
                }

                count += read
            }

            if (count > MAX_RESPONSE_BYTES) {
                return@use AlbumDetailsRemoteResult
                    .MalformedResponse
            }

            val json =
                try {
                    Charsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(
                            CodingErrorAction.REPORT,
                        ).onUnmappableCharacter(
                            CodingErrorAction.REPORT,
                        ).decode(
                            ByteBuffer.wrap(
                                bytes,
                                0,
                                count,
                            ),
                        ).toString()
                } catch (_: CharacterCodingException) {
                    return@use AlbumDetailsRemoteResult
                        .MalformedResponse
                }

            when (
                val parsed =
                    OpenSubsonicAlbumDetailsParser
                        .parse(json)
            ) {
                is AlbumDetailsParseResult.Success -> {
                    AlbumDetailsRemoteResult.Success(
                        parsed.album,
                    )
                }

                AlbumDetailsParseResult
                    .AuthenticationRequired,
                -> {
                    AlbumDetailsRemoteResult
                        .AuthenticationRequired
                }

                AlbumDetailsParseResult.ServerError -> {
                    AlbumDetailsRemoteResult.ServerError
                }

                AlbumDetailsParseResult
                    .MalformedResponse,
                -> {
                    AlbumDetailsRemoteResult
                        .MalformedResponse
                }
            }
        }

    companion object {
        internal const val MAX_RESPONSE_BYTES =
            262_144
    }
}
