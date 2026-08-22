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

    @Test
    fun deeplyNestedResponseMapsToAuthProtocolError() {
        val depth = SubsonicResponseParser.MAX_AUTH_RESPONSE_DEPTH + 1
        val json = envelopeWithExtraNesting(depth)

        assertTrue(
            "Expected AuthProtocolError for nesting above MAX_AUTH_RESPONSE_DEPTH",
            SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError,
        )
    }

    @Test
    fun nestedUnknownFieldBelowDepthLimitParsesNormally() {
        val json = envelopeWithExtraNesting(depth = 20)

        assertTrue(
            "Reasonable legal nesting below the limit must parse normally",
            SubsonicResponseParser.parse(json) is AuthResult.Authenticated,
        )
    }

    @Test
    fun structuralCharsInsideStringsDoNotAffectDepth() {
        val json = envelopeWithExtraString("\"text [ { ] }\"")

        assertTrue(
            "Structural characters inside a JSON string must not count as nesting",
            SubsonicResponseParser.parse(json) is AuthResult.Authenticated,
        )
    }

    @Test
    fun escapedQuotesInsideStringsDoNotAffectDepth() {
        val json = envelopeWithExtraString("\"escaped quote: \\\" [[[ {{{ \\\"\"")

        assertTrue(
            "Escaped quotes inside a JSON string must not end the string early",
            SubsonicResponseParser.parse(json) is AuthResult.Authenticated,
        )
    }

    @Test
    fun highDepthRegressionMapsToAuthProtocolError() {
        // Representative of the discovered vulnerability: ~10k nested arrays,
        // still below MAX_AUTH_RESPONSE_CHARS; must be rejected pre-parse with no StackOverflowError.
        val depth = 10_000
        val json = envelopeWithExtraNesting(depth)
        assertTrue(json.length < SubsonicResponseParser.MAX_AUTH_RESPONSE_CHARS)

        assertTrue(
            "Expected AuthProtocolError for pathologically deep nesting, got ${SubsonicResponseParser.parse(json)}",
            SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError,
        )
    }

    private fun envelopeWithExtraNesting(depth: Int): String =
        """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonic":true,"type":"navidrome","serverVersion":"0.54.1","extra":${"[".repeat(depth)}0${"]".repeat(depth)}}}"""

    private fun envelopeWithExtraString(extraValue: String): String =
        """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonic":true,"type":"navidrome","serverVersion":"0.54.1","extra":$extraValue}}"""

    private fun okJsonRaw(fields: String): String = """
        {
            "subsonic-response": {
                $fields
            }
        }
    """.trimIndent()

    @Test
    fun failedWithWrongTypedOpenSubsonicMapsToAuthProtocolError() {
        val json = failedJsonRaw(metadata = "\"openSubsonic\": \"true\"", code = 40)

        assertTrue("Expected AuthProtocolError for wrong-typed openSubsonic on failed envelope, got ${
            SubsonicResponseParser.parse(json)
        }", SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun failedWithWrongTypedTypeMapsToAuthProtocolError() {
        val json = failedJsonRaw(metadata = "\"type\": 123", code = 40)

        assertTrue("Expected AuthProtocolError for wrong-typed type on failed envelope, got ${
            SubsonicResponseParser.parse(json)
        }", SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun failedWithWrongTypedServerVersionMapsToAuthProtocolError() {
        val json = failedJsonRaw(metadata = "\"serverVersion\": {}", code = 40)

        assertTrue("Expected AuthProtocolError for wrong-typed serverVersion on failed envelope, got ${
            SubsonicResponseParser.parse(json)
        }", SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError)
    }

    @Test
    fun oversizedResponseMapsToAuthProtocolError() {
        val padding = "x".repeat(SubsonicResponseParser.MAX_AUTH_RESPONSE_CHARS)
        val json =
            """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonic":true,"type":"navidrome","serverVersion":"$padding"}}"""

        assertTrue(
            "Expected AuthProtocolError for oversized input",
            SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError,
        )
    }

    private fun failedJsonRaw(metadata: String, code: Int): String = """
        {
            "subsonic-response": {
                "status": "failed",
                "version": "1.16.1",
                $metadata,
                "error": { "code": $code }
            }
        }
    """.trimIndent()

    @Test
    fun duplicateStatusKeysMapToAuthProtocolError() {
        val json = """{"subsonic-response":{"status":"failed","status":"ok","version":"1.16.1","openSubsonic":true,"type":"navidrome","serverVersion":"0.54.1"}}"""

        assertTrue(
            "Duplicate status keys must never authenticate",
            SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError,
        )
    }

    @Test
    fun duplicateOpenSubsonicKeysMapToAuthProtocolError() {
        val json = """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonic":false,"openSubsonic":true,"type":"navidrome","serverVersion":"0.54.1"}}"""

        assertTrue(
            "Duplicate openSubsonic keys must fail closed",
            SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError,
        )
    }

    @Test
    fun duplicateErrorCodeKeysMapToAuthProtocolError() {
        val json = """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"code":41}}}"""

        assertTrue(
            "Duplicate error.code keys must fail closed",
            SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError,
        )
    }

    @Test
    fun escapeEquivalentDuplicateKeysMapToAuthProtocolError() {
        val json = """{"subsonic-response":{"status":"failed","sta\u0074us":"ok","version":"1.16.1","openSubsonic":true,"type":"navidrome","serverVersion":"0.54.1"}}"""

        assertTrue(
            "Escape-equivalent duplicate keys must collide",
            SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError,
        )
    }

    @Test
    fun sameKeyInDifferentObjectsIsAllowed() {
        val json = """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonic":true,"type":"navidrome","serverVersion":"0.54.1","extra":{"status":"inner"}}}"""

        assertTrue(
            "Same key spelling in a nested object must not conflict",
            SubsonicResponseParser.parse(json) is AuthResult.Authenticated,
        )
    }

    @Test
    fun keyLikeTextInsideStringIsIgnored() {
        val json = """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonic":true,"type":"navidrome","serverVersion":"0.54.1","extra":"\"status\":\"ok\""}}"""

        assertTrue(
            "Key-like text inside a string must not create duplicate keys",
            SubsonicResponseParser.parse(json) is AuthResult.Authenticated,
        )
    }

    @Test
    fun failedWithNumericErrorMessageMapsToAuthProtocolError() {
        val json = """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":123}}}"""

        assertTrue(
            "Non-string error.message must fail closed",
            SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError,
        )
    }

    @Test
    fun failedWithBooleanErrorMessageMapsToAuthProtocolError() {
        val json = """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":true}}}"""

        assertTrue(
            "Boolean error.message must fail closed",
            SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError,
        )
    }

    @Test
    fun failedWithObjectErrorMessageMapsToAuthProtocolError() {
        val json = """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":{}}}}"""

        assertTrue(
            "Object error.message must fail closed",
            SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError,
        )
    }

    @Test
    fun failedWithNullErrorMessageMapsToAuthProtocolError() {
        val json = """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":null}}}"""

        assertTrue(
            "Null error.message must fail closed",
            SubsonicResponseParser.parse(json) is AuthResult.AuthProtocolError,
        )
    }

    @Test
    fun blankErrorMessageRemainsInvalidCredentials() {
        val json = """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":""}}}"""

        assertTrue(
            "Empty-string error.message is still a valid optional String",
            SubsonicResponseParser.parse(json) is AuthResult.InvalidCredentials,
        )
    }
}
