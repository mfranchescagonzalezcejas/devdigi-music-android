package dev.devdigi.music.connection

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
                    "version": "1.16.1",
                    "type": "navidrome",
                    "serverVersion": "0.54.1",
                    "openSubsonic": true
                }
            }
        """.trimIndent()

        val result = SubsonicResponseParser.parse(json)

        assertTrue(result is AuthResult.Authenticated)
        val metadata = (result as AuthResult.Authenticated).metadata
        assertEquals(ServerMetadata("navidrome", "0.54.1", true), metadata)
    }

    @Test
    fun singleQuotedJsonMapsToAuthProtocolError() {
        val json = "{ 'subsonic-response': { 'status': 'ok', 'version': '1.16.1' } }"

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun unquotedObjectKeyMapsToAuthProtocolError() {
        val json = "{ subsonic-response: { status: \"ok\", version: \"1.16.1\" } }"

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun trailingCommaMapsToAuthProtocolError() {
        val json = """
            {
                "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                }
            }
        """.trimIndent()

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun commentSyntaxMapsToAuthProtocolError() {
        val json = """
            {
                // protocol envelope
                "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1"
                }
            }
        """.trimIndent()

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun trailingTokensMapToAuthProtocolError() {
        val json = """
            {
                "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1"
                }
            } extra
        """.trimIndent()

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun okWithoutOpenSubsonicMapsToIncompatibleServer() {
        val json = okJson(version = "1.16.1", type = "navidrome", serverVersion = "0.54.1", openSubsonic = null)

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.IncompatibleServer)
    }

    @Test
    fun okWithOpenSubsonicFalseMapsToIncompatibleServer() {
        val json = okJson(version = "1.16.1", type = "navidrome", serverVersion = "0.54.1", openSubsonic = false)

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.IncompatibleServer)
    }

    @Test
    fun okWithMissingTypeMapsToAuthProtocolError() {
        val json = okJson(version = "1.16.1", type = null, serverVersion = "0.54.1", openSubsonic = true)

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun okWithBlankTypeMapsToAuthProtocolError() {
        val json = okJson(version = "1.16.1", type = "", serverVersion = "0.54.1", openSubsonic = true)

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun okWithMissingServerVersionMapsToAuthProtocolError() {
        val json = okJson(version = "1.16.1", type = "navidrome", serverVersion = null, openSubsonic = true)

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun okWithBlankServerVersionMapsToAuthProtocolError() {
        val json = okJson(version = "1.16.1", type = "navidrome", serverVersion = "", openSubsonic = true)

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun okWithMissingVersionMapsToAuthProtocolError() {
        val json = okJson(version = null, type = "navidrome", serverVersion = "0.54.1", openSubsonic = true)

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun okWithBlankVersionMapsToAuthProtocolError() {
        val json = okJson(version = "", type = "navidrome", serverVersion = "0.54.1", openSubsonic = true)

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
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
    fun malformedJsonMapsToAuthProtocolError() {
        val result = SubsonicResponseParser.parse("not valid json")

        assertTrue("Expected exactly AuthProtocolError, got $result", result is AuthResult.AuthProtocolError)
    }

    private fun okJson(
        version: String?,
        type: String?,
        serverVersion: String?,
        openSubsonic: Boolean?,
    ): String {
        val fields = buildList {
            add("\"status\": \"ok\"")
            version?.let { add("\"version\": \"$it\"") }
            type?.let { add("\"type\": \"$it\"") }
            serverVersion?.let { add("\"serverVersion\": \"$it\"") }
            openSubsonic?.let { add("\"openSubsonic\": $it") }
        }
        return """
            {
                "subsonic-response": {
                    ${fields.joinToString(", ")}
                }
            }
        """.trimIndent()
    }

    private fun errorJson(code: Int, message: String): String = """
        {
            "subsonic-response": {
                "status": "failed",
                "version": "1.16.1",
                "error": {
                    "code": $code,
                    "message": "$message"
                }
            }
        }
    """.trimIndent()

    @Test
    fun versionNumberMapsToAuthProtocolError() {
        val json = okJsonRaw("\"status\": \"ok\", \"version\": 1, \"type\": \"navidrome\", \"serverVersion\": \"0.54.1\", \"openSubsonic\": true")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun typeObjectMapsToAuthProtocolError() {
        val json = okJsonRaw("\"status\": \"ok\", \"version\": \"1.16.1\", \"type\": {}, \"serverVersion\": \"0.54.1\", \"openSubsonic\": true")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun serverVersionNumberMapsToAuthProtocolError() {
        val json = okJsonRaw("\"status\": \"ok\", \"version\": \"1.16.1\", \"type\": \"navidrome\", \"serverVersion\": 54, \"openSubsonic\": true")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun openSubsonicStringMapsToAuthProtocolError() {
        val json = okJsonRaw("\"status\": \"ok\", \"version\": \"1.16.1\", \"type\": \"navidrome\", \"serverVersion\": \"0.54.1\", \"openSubsonic\": \"true\"")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun nonStringStatusMapsToAuthProtocolError() {
        val json = okJsonRaw("\"status\": 1, \"version\": \"1.16.1\", \"type\": \"navidrome\", \"serverVersion\": \"0.54.1\", \"openSubsonic\": true")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun failedEnvelopeWithoutVersionMapsToAuthProtocolError() {
        val json = """
            {
                "subsonic-response": {
                    "status": "failed",
                    "error": { "code": 40, "message": "Wrong username or password" }
                }
            }
        """.trimIndent()

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun failedEnvelopeWithNumericVersionMapsToAuthProtocolError() {
        val json = """
            {
                "subsonic-response": {
                    "status": "failed",
                    "version": 1,
                    "error": { "code": 40, "message": "Wrong username or password" }
                }
            }
        """.trimIndent()

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun failedEnvelopeWithStringErrorCodeMapsToAuthProtocolError() {
        val json = """
            {
                "subsonic-response": {
                    "status": "failed",
                    "version": "1.16.1",
                    "error": { "code": "40", "message": "Wrong username or password" }
                }
            }
        """.trimIndent()

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun failedEnvelopeWithValidEnvelopeAndCode40MapsToInvalidCredentials() {
        val json = errorJson(40, "Wrong username or password")

        assertTrue(SubsonicResponseParser.parse(json) is AuthResult.InvalidCredentials)
    }

    @Test
    fun okEnvelopeWithErrorMapsToAuthProtocolError() {
        val json = """
            {
                "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "type": "navidrome",
                    "serverVersion": "0.54.1",
                    "openSubsonic": true,
                    "error": { "code": 40 }
                }
            }
        """.trimIndent()

        val result = SubsonicResponseParser.parse(json)

        assertTrue("Expected AuthProtocolError, got $result", result is AuthResult.AuthProtocolError)
    }

    @Test
    fun okEnvelopeWithAnyErrorMemberMapsToAuthProtocolError() {
        val json = """
            {
                "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "type": "navidrome",
                    "serverVersion": "0.54.1",
                    "openSubsonic": true,
                    "error": { "code": 70 }
                }
            }
        """.trimIndent()

        val result = SubsonicResponseParser.parse(json)

        assertTrue("Expected AuthProtocolError, got $result", result is AuthResult.AuthProtocolError)
    }

    private fun okJsonRaw(fields: String): String = """
        {
            "subsonic-response": {
                $fields
            }
        }
    """.trimIndent()
}
