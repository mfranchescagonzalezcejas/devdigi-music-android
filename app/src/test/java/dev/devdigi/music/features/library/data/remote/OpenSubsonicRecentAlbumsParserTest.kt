package dev.devdigi.music.features.library.data.remote

import dev.devdigi.music.features.library.domain.RecentAlbum
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenSubsonicRecentAlbumsParserTest {
    @Test
    fun parsesAlbumsInServerOrder() {
        val result =
            OpenSubsonicRecentAlbumsParser.parse(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "albumList2": {
                      "album": [
                        {
                          "id": "album-a",
                          "name": "First",
                          "artist": "Artist A",
                          "coverArt": "cover-a"
                        },
                        {
                          "id": "album-b",
                          "name": "Second"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(
            RecentAlbumsParseResult.Success(
                listOf(
                    RecentAlbum(
                        id = "album-a",
                        title = "First",
                        artist = "Artist A",
                        coverArtId = "cover-a",
                    ),
                    RecentAlbum(
                        id = "album-b",
                        title = "Second",
                        artist = null,
                        coverArtId = null,
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun acceptsExplicitAndImplicitEmptyAlbumLists() {
        val payloads =
            listOf(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "albumList2": {
                      "album": []
                    }
                  }
                }
                """.trimIndent(),
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "albumList2": {}
                  }
                }
                """.trimIndent(),
            )

        payloads.forEach { payload ->
            assertEquals(
                RecentAlbumsParseResult.Success(emptyList()),
                OpenSubsonicRecentAlbumsParser.parse(payload),
            )
        }
    }

    @Test
    fun mapsAuthenticationFailureSeparately() {
        val result =
            OpenSubsonicRecentAlbumsParser.parse(
                failedResponse(code = 40),
            )

        assertEquals(
            RecentAlbumsParseResult.AuthenticationRequired,
            result,
        )
    }

    @Test
    fun preservesOtherWellFormedServerFailures() {
        listOf(10, 20, 30, 41, 42, 43, 99).forEach { code ->
            assertEquals(
                "Unexpected mapping for error code $code",
                RecentAlbumsParseResult.ServerError,
                OpenSubsonicRecentAlbumsParser.parse(
                    failedResponse(code),
                ),
            )
        }
    }

    @Test
    fun rejectsMalformedEnvelopeAndAlbumFields() {
        val malformedPayloads =
            listOf(
                "{not-json",
                """{"subsonic-response":{"status":"ok"}}""",
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "albumList2": []
                  }
                }
                """.trimIndent(),
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "albumList2": {
                      "album": "wrong-type"
                    }
                  }
                }
                """.trimIndent(),
                successAlbum("""{"name":"Missing id"}"""),
                successAlbum("""{"id":"","name":"Blank id"}"""),
                successAlbum("""{"id":"album-1","name":""}"""),
                successAlbum(
                    """{"id":"album-1","name":"Album","artist":42}""",
                ),
                successAlbum(
                    """{"id":"album-1","name":"Album","coverArt":false}""",
                ),
            )

        malformedPayloads.forEach { payload ->
            assertEquals(
                RecentAlbumsParseResult.MalformedResponse,
                OpenSubsonicRecentAlbumsParser.parse(payload),
            )
        }
    }

    @Test
    fun rejectsContradictorySuccessEnvelope() {
        val result =
            OpenSubsonicRecentAlbumsParser.parse(
                """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "error": {
                      "code": 40
                    },
                    "albumList2": {}
                  }
                }
                """.trimIndent(),
            )

        assertEquals(
            RecentAlbumsParseResult.MalformedResponse,
            result,
        )
    }

    @Test
    fun rejectsOversizedResponseBeforeJsonMaterialization() {
        val oversized =
            " ".repeat(
                OpenSubsonicRecentAlbumsParser.MAX_RESPONSE_CHARS + 1,
            )

        assertEquals(
            RecentAlbumsParseResult.MalformedResponse,
            OpenSubsonicRecentAlbumsParser.parse(oversized),
        )
    }

    @Test
    fun rejectsExcessiveNestingBeforeJsonMaterialization() {
        val depth =
            OpenSubsonicRecentAlbumsParser.MAX_RESPONSE_DEPTH + 1

        val payload =
            "[".repeat(depth) +
                "0" +
                "]".repeat(depth)

        assertEquals(
            RecentAlbumsParseResult.MalformedResponse,
            OpenSubsonicRecentAlbumsParser.parse(payload),
        )
    }

    private fun failedResponse(code: Int): String =
        """
        {
          "subsonic-response": {
            "status": "failed",
            "version": "1.16.1",
            "error": {
              "code": $code,
              "message": "Synthetic failure"
            }
          }
        }
        """.trimIndent()

    private fun successAlbum(album: String): String =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "version": "1.16.1",
            "albumList2": {
              "album": [
                $album
              ]
            }
          }
        }
        """.trimIndent()
}
