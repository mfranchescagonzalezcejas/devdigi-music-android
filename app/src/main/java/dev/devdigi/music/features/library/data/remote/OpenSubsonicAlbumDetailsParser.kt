package dev.devdigi.music.features.library.data.remote

import dev.devdigi.music.features.library.domain.AlbumDetails
import dev.devdigi.music.features.library.domain.AlbumTrack
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

sealed interface AlbumDetailsParseResult {
    data class Success(
        val album: AlbumDetails,
    ) : AlbumDetailsParseResult

    data object AuthenticationRequired :
        AlbumDetailsParseResult

    data object ServerError :
        AlbumDetailsParseResult

    data object MalformedResponse :
        AlbumDetailsParseResult
}

object OpenSubsonicAlbumDetailsParser {
    internal const val MAX_RESPONSE_CHARS = 262_144
    internal const val MAX_RESPONSE_DEPTH = 128

    private val strictJson =
        Json {
            isLenient = false
        }

    fun parse(json: String): AlbumDetailsParseResult {
        if (json.length > MAX_RESPONSE_CHARS) {
            return AlbumDetailsParseResult
                .MalformedResponse
        }

        if (
            exceedsJsonNestingDepth(
                json = json,
                maxDepth = MAX_RESPONSE_DEPTH,
            )
        ) {
            return AlbumDetailsParseResult
                .MalformedResponse
        }

        val envelope =
            try {
                val root =
                    strictJson
                        .parseToJsonElement(json)
                        as? JsonObject
                        ?: return AlbumDetailsParseResult
                            .MalformedResponse

                root["subsonic-response"]
                    as? JsonObject
                    ?: return AlbumDetailsParseResult
                        .MalformedResponse
            } catch (_: SerializationException) {
                return AlbumDetailsParseResult
                    .MalformedResponse
            } catch (_: IllegalArgumentException) {
                return AlbumDetailsParseResult
                    .MalformedResponse
            }

        val status =
            envelope
                .stringField("status")
                ?.takeIf(String::isNotBlank)
                ?: return AlbumDetailsParseResult
                    .MalformedResponse

        envelope
            .stringField("version")
            ?.takeIf(String::isNotBlank)
            ?: return AlbumDetailsParseResult
                .MalformedResponse

        return when (status) {
            "ok" -> {
                parseSuccess(envelope)
            }

            "failed" -> {
                parseFailure(envelope)
            }

            else -> {
                AlbumDetailsParseResult
                    .MalformedResponse
            }
        }
    }

    private fun parseSuccess(envelope: JsonObject): AlbumDetailsParseResult {
        if (envelope.containsKey("error")) {
            return AlbumDetailsParseResult
                .MalformedResponse
        }

        val album =
            envelope["album"]
                as? JsonObject
                ?: return AlbumDetailsParseResult
                    .MalformedResponse

        val id =
            album
                .stringField("id")
                ?.takeIf(String::isNotBlank)
                ?: return AlbumDetailsParseResult
                    .MalformedResponse

        val title =
            album
                .stringField("name")
                ?.takeIf(String::isNotBlank)
                ?: return AlbumDetailsParseResult
                    .MalformedResponse

        val artist =
            album.optionalStringField("artist")
                ?: if (album.containsKey("artist")) {
                    return AlbumDetailsParseResult
                        .MalformedResponse
                } else {
                    null
                }

        val coverArtId =
            album.optionalStringField("coverArt")
                ?: if (album.containsKey("coverArt")) {
                    return AlbumDetailsParseResult
                        .MalformedResponse
                } else {
                    null
                }

        val tracks =
            when (val song = album["song"]) {
                null -> {
                    emptyList()
                }

                is JsonArray -> {
                    parseTracks(song)
                        ?: return AlbumDetailsParseResult
                            .MalformedResponse
                }

                else -> {
                    return AlbumDetailsParseResult
                        .MalformedResponse
                }
            }

        return AlbumDetailsParseResult.Success(
            album =
                AlbumDetails(
                    id = id,
                    title = title,
                    artist = artist,
                    coverArtId = coverArtId,
                    tracks = tracks,
                ),
        )
    }

    private fun parseTracks(songs: JsonArray): List<AlbumTrack>? {
        val tracks =
            ArrayList<AlbumTrack>(
                songs.size,
            )

        songs.forEach { element ->
            val song =
                element
                    as? JsonObject
                    ?: return null

            val id =
                song
                    .stringField("id")
                    ?.takeIf(String::isNotBlank)
                    ?: return null

            val title =
                song
                    .stringField("title")
                    ?.takeIf(String::isNotBlank)
                    ?: return null

            val artist =
                song.optionalStringField("artist")
                    ?: if (song.containsKey("artist")) {
                        return null
                    } else {
                        null
                    }

            val trackNumber =
                song.optionalIntField("track")
                    ?: if (song.containsKey("track")) {
                        return null
                    } else {
                        null
                    }

            val discNumber =
                song.optionalIntField("discNumber")
                    ?: if (song.containsKey("discNumber")) {
                        return null
                    } else {
                        null
                    }

            val durationSeconds =
                song.optionalIntField("duration")
                    ?: if (song.containsKey("duration")) {
                        return null
                    } else {
                        null
                    }

            val coverArtId =
                song.optionalStringField("coverArt")
                    ?: if (song.containsKey("coverArt")) {
                        return null
                    } else {
                        null
                    }

            tracks +=
                AlbumTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    trackNumber = trackNumber,
                    discNumber = discNumber,
                    durationSeconds =
                    durationSeconds,
                    coverArtId = coverArtId,
                )
        }

        return tracks
    }

    private fun parseFailure(envelope: JsonObject): AlbumDetailsParseResult {
        if (envelope.containsKey("album")) {
            return AlbumDetailsParseResult
                .MalformedResponse
        }

        val error =
            envelope["error"]
                as? JsonObject
                ?: return AlbumDetailsParseResult
                    .MalformedResponse

        val code =
            error.intField("code")
                ?: return AlbumDetailsParseResult
                    .MalformedResponse

        return if (code == 40) {
            AlbumDetailsParseResult
                .AuthenticationRequired
        } else {
            AlbumDetailsParseResult.ServerError
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

    private fun JsonObject.optionalIntField(key: String): Int? =
        if (!containsKey(key)) {
            null
        } else {
            (get(key) as? JsonPrimitive)
                ?.takeIf { !it.isString }
                ?.intOrNull
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
                    escaped -> {
                        escaped = false
                    }

                    character == '\\' -> {
                        escaped = true
                    }

                    character == '"' -> {
                        inString = false
                    }
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
