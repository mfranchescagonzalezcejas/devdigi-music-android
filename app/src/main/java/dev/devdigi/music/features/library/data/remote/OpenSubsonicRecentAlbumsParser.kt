package dev.devdigi.music.features.library.data.remote

import dev.devdigi.music.features.library.domain.RecentAlbum
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

sealed interface RecentAlbumsParseResult {
    data class Success(
        val albums: List<RecentAlbum>,
    ) : RecentAlbumsParseResult

    data object AuthenticationRequired : RecentAlbumsParseResult

    data object ServerError : RecentAlbumsParseResult

    data object MalformedResponse : RecentAlbumsParseResult
}

object OpenSubsonicRecentAlbumsParser {
    internal const val MAX_RESPONSE_CHARS = 262_144
    internal const val MAX_RESPONSE_DEPTH = 128

    private val strictJson = Json { isLenient = false }

    fun parse(json: String): RecentAlbumsParseResult {
        if (json.length > MAX_RESPONSE_CHARS) {
            return RecentAlbumsParseResult.MalformedResponse
        }

        if (
            exceedsJsonNestingDepth(
                json = json,
                maxDepth = MAX_RESPONSE_DEPTH,
            )
        ) {
            return RecentAlbumsParseResult.MalformedResponse
        }

        val envelope =
            try {
                val root =
                    strictJson.parseToJsonElement(json)
                        as? JsonObject
                        ?: return RecentAlbumsParseResult.MalformedResponse

                root["subsonic-response"]
                    as? JsonObject
                    ?: return RecentAlbumsParseResult.MalformedResponse
            } catch (_: SerializationException) {
                return RecentAlbumsParseResult.MalformedResponse
            } catch (_: IllegalArgumentException) {
                return RecentAlbumsParseResult.MalformedResponse
            }

        val status =
            envelope
                .stringField("status")
                ?.takeIf(String::isNotBlank)
                ?: return RecentAlbumsParseResult.MalformedResponse

        envelope
            .stringField("version")
            ?.takeIf(String::isNotBlank)
            ?: return RecentAlbumsParseResult.MalformedResponse

        return when (status) {
            "ok" -> parseSuccess(envelope)
            "failed" -> parseFailure(envelope)
            else -> RecentAlbumsParseResult.MalformedResponse
        }
    }

    private fun parseSuccess(envelope: JsonObject): RecentAlbumsParseResult {
        if (envelope.containsKey("error")) {
            return RecentAlbumsParseResult.MalformedResponse
        }

        val albumList =
            envelope["albumList2"]
                as? JsonObject
                ?: return RecentAlbumsParseResult.MalformedResponse

        val albumElement =
            albumList["album"]
                ?: return RecentAlbumsParseResult.Success(
                    emptyList(),
                )

        val albumArray =
            albumElement
                as? JsonArray
                ?: return RecentAlbumsParseResult.MalformedResponse

        val albums =
            ArrayList<RecentAlbum>(
                albumArray.size,
            )

        albumArray.forEach { element ->
            val album =
                element
                    as? JsonObject
                    ?: return RecentAlbumsParseResult.MalformedResponse

            val id =
                album
                    .stringField("id")
                    ?.takeIf(String::isNotBlank)
                    ?: return RecentAlbumsParseResult.MalformedResponse

            val title =
                album
                    .stringField("name")
                    ?.takeIf(String::isNotBlank)
                    ?: return RecentAlbumsParseResult.MalformedResponse

            val artist =
                album.optionalStringField("artist")
                    ?: if (album.containsKey("artist")) {
                        return RecentAlbumsParseResult.MalformedResponse
                    } else {
                        null
                    }

            val coverArtId =
                album.optionalStringField("coverArt")
                    ?: if (album.containsKey("coverArt")) {
                        return RecentAlbumsParseResult.MalformedResponse
                    } else {
                        null
                    }

            albums +=
                RecentAlbum(
                    id = id,
                    title = title,
                    artist = artist,
                    coverArtId = coverArtId,
                )
        }

        return RecentAlbumsParseResult.Success(
            albums,
        )
    }

    private fun parseFailure(envelope: JsonObject): RecentAlbumsParseResult {
        if (envelope.containsKey("albumList2")) {
            return RecentAlbumsParseResult.MalformedResponse
        }

        val error =
            envelope["error"]
                as? JsonObject
                ?: return RecentAlbumsParseResult.MalformedResponse

        val code =
            error.intField("code")
                ?: return RecentAlbumsParseResult.MalformedResponse

        return if (code == 40) {
            RecentAlbumsParseResult.AuthenticationRequired
        } else {
            RecentAlbumsParseResult.ServerError
        }
    }

    private fun JsonObject.stringField(key: String): String? =
        (get(key) as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content

    private fun JsonObject.optionalStringField(key: String): String? =
        if (!containsKey(key)) {
            null
        } else {
            (get(key) as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
        }

    private fun JsonObject.intField(key: String): Int? =
        (get(key) as? JsonPrimitive)
            ?.takeIf { !it.isString }
            ?.intOrNull

    private fun exceedsJsonNestingDepth(
        json: String,
        maxDepth: Int,
    ): Boolean {
        var depth = 0
        var inString = false
        var escaped = false

        for (character in json) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }

                continue
            }

            when (character) {
                '"' -> {
                    inString = true
                }

                '{', '[' -> {
                    depth += 1

                    if (depth > maxDepth) {
                        return true
                    }
                }

                '}', ']' -> {
                    if (depth > 0) {
                        depth -= 1
                    }
                }
            }
        }

        return false
    }
}
