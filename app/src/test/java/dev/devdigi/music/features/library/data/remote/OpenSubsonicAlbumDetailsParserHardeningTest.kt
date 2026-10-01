package dev.devdigi.music.features.library.data.remote

import org.junit.Assert.assertSame
import org.junit.Test

class OpenSubsonicAlbumDetailsParserHardeningTest {
    @Test
    fun mapsProtocolFailuresDistinctly() {
        val auth =
            """
            {
              "subsonic-response": {
                "status": "failed",
                "version": "1.16.1",
                "error": {
                  "code": 40,
                  "message": "Synthetic auth failure"
                }
              }
            }
            """.trimIndent()

        val server =
            """
            {
              "subsonic-response": {
                "status": "failed",
                "version": "1.16.1",
                "error": {
                  "code": 70,
                  "message": "Synthetic server failure"
                }
              }
            }
            """.trimIndent()

        assertSame(
            AlbumDetailsParseResult.AuthenticationRequired,
            OpenSubsonicAlbumDetailsParser.parse(auth),
        )

        assertSame(
            AlbumDetailsParseResult.ServerError,
            OpenSubsonicAlbumDetailsParser.parse(server),
        )
    }

    @Test
    fun rejectsMalformedRequiredAlbumFields() {
        listOf(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "album": {
                  "name": "Missing id"
                }
              }
            }
            """.trimIndent(),
            """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "album": {
                  "id": "album-1"
                }
              }
            }
            """.trimIndent(),
            """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "album": {
                  "id": "",
                  "name": "Blank id"
                }
              }
            }
            """.trimIndent(),
        ).forEach(::assertMalformed)
    }

    @Test
    fun rejectsMalformedTrackFieldsAndOptionalTypes() {
        listOf(
            albumWithSong(
                """
                {
                  "title": "Missing id"
                }
                """,
            ),
            albumWithSong(
                """
                {
                  "id": "track-1"
                }
                """,
            ),
            albumWithSong(
                """
                {
                  "id": "track-1",
                  "title": "Synthetic",
                  "duration": "185"
                }
                """,
            ),
            albumWithSong(
                """
                {
                  "id": "track-1",
                  "title": "Synthetic",
                  "track": "1"
                }
                """,
            ),
            albumWithSong(
                """
                {
                  "id": "track-1",
                  "title": "Synthetic",
                  "artist": 42
                }
                """,
            ),
        ).forEach(::assertMalformed)
    }

    @Test
    fun rejectsMalformedOrContradictoryEnvelope() {
        listOf(
            "{}",
            """
            {
              "subsonic-response": {
                "status": "ok",
                "album": {}
              }
            }
            """.trimIndent(),
            """
            {
              "subsonic-response": {
                "status": "unknown",
                "version": "1.16.1"
              }
            }
            """.trimIndent(),
            """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "error": {
                  "code": 40
                },
                "album": {
                  "id": "album-1",
                  "name": "Synthetic"
                }
              }
            }
            """.trimIndent(),
            """
            {
              "subsonic-response": {
                "status": "failed",
                "version": "1.16.1",
                "album": {
                  "id": "album-1",
                  "name": "Synthetic"
                },
                "error": {
                  "code": 70
                }
              }
            }
            """.trimIndent(),
        ).forEach(::assertMalformed)
    }

    @Test
    fun rejectsNonArraySongValue() {
        assertMalformed(
            """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "album": {
                  "id": "album-1",
                  "name": "Synthetic Album",
                  "song": {
                    "id": "track-1"
                  }
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun rejectsOversizedResponseBeforeParsing() {
        val json =
            "x".repeat(
                OpenSubsonicAlbumDetailsParser
                    .MAX_RESPONSE_CHARS + 1,
            )

        assertMalformed(json)
    }

    @Test
    fun rejectsExcessiveJsonNestingDepth() {
        val depth =
            OpenSubsonicAlbumDetailsParser
                .MAX_RESPONSE_DEPTH + 1

        val json =
            "[".repeat(depth) +
                "0" +
                "]".repeat(depth)

        assertMalformed(json)
    }

    private fun assertMalformed(json: String) {
        assertSame(
            AlbumDetailsParseResult.MalformedResponse,
            OpenSubsonicAlbumDetailsParser.parse(json),
        )
    }

    private fun albumWithSong(song: String): String =
        """
        {
          "subsonic-response": {
            "status": "ok",
            "version": "1.16.1",
            "album": {
              "id": "album-1",
              "name": "Synthetic Album",
              "song": [
                $song
              ]
            }
          }
        }
        """.trimIndent()
}
