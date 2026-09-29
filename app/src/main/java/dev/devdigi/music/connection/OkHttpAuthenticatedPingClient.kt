package dev.devdigi.music.connection

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
import kotlin.coroutines.resumeWithException

class OkHttpAuthenticatedPingClient(
    private val signer: SubsonicAuthSigner,
    readTimeoutMillis: Long = 10_000L,
    callTimeoutMillis: Long = 15_000L,
) : AuthenticatedPingClient {
    private val transport =
        OkHttpClient
            .Builder()
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

    override suspend fun ping(
        credentials: AuthCredentials,
        profile: ServerProfile,
    ): AuthResult {
        val endpoint =
            (
                ServerEndpoint.parse(profile.endpoint.value)
                    as? EndpointParseResult.Valid
            )?.endpoint ?: return AuthResult.AuthProtocolError

        val baseUrl =
            endpoint.value.toHttpUrlOrNull()
                ?: return AuthResult.AuthProtocolError

        val signature = signer.sign(credentials)

        val url =
            baseUrl
                .newBuilder()
                .encodedPath(
                    "${baseUrl.encodedPath.trimEnd('/')}/rest/ping.view",
                ).addQueryParameter("u", credentials.username)
                .addQueryParameter("t", signature.token)
                .addQueryParameter("s", signature.salt)
                .addQueryParameter("v", "1.13.0")
                .addQueryParameter("c", "devdigi-music")
                .addQueryParameter("f", "json")
                .build()

        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()

        return suspendCancellableCoroutine { continuation ->
            val call = transport.newCall(request)

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
                            continuation.resume(AuthResult.NetworkError)
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val result =
                            try {
                                parseResponse(response)
                            } catch (_: IOException) {
                                AuthResult.NetworkError
                            } catch (error: RuntimeException) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(error)
                                }
                                return
                            }

                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                },
            )
        }
    }

    private fun parseResponse(response: Response): AuthResult =
        response.use {
            if (!response.isSuccessful) {
                return@use AuthResult.AuthProtocolError
            }

            val body =
                response.body
                    ?: return@use AuthResult.AuthProtocolError

            val bytes = ByteArray(MAX_RESPONSE_BYTES + 1)
            val stream = body.byteStream()
            var count = 0

            while (count < bytes.size) {
                val read =
                    stream.read(
                        bytes,
                        count,
                        bytes.size - count,
                    )

                if (read == -1) break

                if (read == 0) {
                    return@use AuthResult.AuthProtocolError
                }

                count += read
            }

            if (count > MAX_RESPONSE_BYTES) {
                return@use AuthResult.AuthProtocolError
            }

            val json =
                try {
                    Charsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes, 0, count))
                        .toString()
                } catch (_: CharacterCodingException) {
                    return@use AuthResult.AuthProtocolError
                }

            SubsonicResponseParser.parse(json)
        }

    private companion object {
        const val MAX_RESPONSE_BYTES = 65_536
    }
}
