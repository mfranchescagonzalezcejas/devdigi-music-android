package dev.devdigi.music.connection

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicResponseParserTest {
    @Test
    fun okResponseMapsToAuthenticatedWithMetadata() {
        val json = """
            {
                "subsonic-response": {
                    "status": "ok",
                    "type": "navidrome",
                    "serverVersion": "0.54.1",
                    "openSubsonic": true
                }
            }
        """.trimIndent()

        val root = JSONObject(json).getJSONObject("subsonic-response")
        assertEquals("ok", root.getString("status"))

        val result = SubsonicResponseParser.parse(json)

        assertTrue(result is AuthResult.Authenticated)
        val metadata = (result as AuthResult.Authenticated).metadata
        assertEquals(ServerMetadata("navidrome", "0.54.1", true), metadata)
    }

    @Test
    fun errorCode40MapsToInvalidCredentials() {
        val json = errorJson(40, "Wrong username or password")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.InvalidCredentials)
    }

    @Test
    fun errorCode41MapsToUnsupportedAuthentication() {
        val json = errorJson(41, "Token authentication not supported")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.UnsupportedAuthentication)
    }

    @Test
    fun errorCode42MapsToUnsupportedAuthentication() {
        val json = errorJson(42, "Token authentication not supported for LDAP users")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.UnsupportedAuthentication)
    }

    @Test
    fun errorCode43MapsToAuthProtocolError() {
        val json = errorJson(43, "Missing required parameter")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun errorCode20MapsToIncompatibleServer() {
        val json = errorJson(20, "Incompatible Sonic API version")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.IncompatibleServer)
    }

    @Test
    fun errorCode30MapsToIncompatibleServer() {
        val json = errorJson(30, "Unsupported OpenSubsonic extension")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.IncompatibleServer)
    }

    @Test
    fun malformedJsonMapsToAuthProtocolOrIncompatibleServer() {
        val result = SubsonicResponseParser.parse("not valid json")

        assertTrue(
            "Expected AuthProtocolError or IncompatibleServer, got $result",
            result is AuthResult.AuthProtocolError || result is AuthResult.IncompatibleServer,
        )
    }

    private fun errorJson(code: Int, message: String): String = """
        {
            "subsonic-response": {
                "status": "failed",
                "error": {
                    "code": $code,
                    "message": "$message"
                }
            }
        }
    """.trimIndent()
}
