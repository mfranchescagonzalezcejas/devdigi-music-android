package dev.devdigi.music.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionTest {
    @Test
    fun normalizesHttpsEndpointAndPreservesReverseProxyPath() {
        val result = ServerEndpoint.parse(" HTTPS://music.example.com:443/navidrome ")

        assertEquals(
            EndpointParseResult.Valid(endpoint("https://music.example.com/navidrome")),
            result,
        )
    }

    @Test
    fun preservesEncodedReverseProxyPathAndNonDefaultPort() {
        val result = ServerEndpoint.parse("https://music.example.com:8443/navidrome%20music")

        assertEquals(
            EndpointParseResult.Valid(endpoint("https://music.example.com:8443/navidrome%20music")),
            result,
        )
    }

    @Test
    fun rejectsPortsOutsideTheValidRange() {
        listOf(
            "https://music.example.com:0/navidrome",
            "https://music.example.com:65536/navidrome",
        ).forEach { endpoint ->
            assertTrue("Expected $endpoint to be rejected", ServerEndpoint.parse(endpoint) is EndpointParseResult.Invalid)
        }
    }

    @Test
    fun rejectsTerminalDotLocalAliases() {
        listOf(
            "https://localhost.",
            "https://music.localhost.",
            "https://127.0.0.1.",
            "https://10.0.2.2.",
        ).forEach { endpoint ->
            assertTrue("Expected $endpoint to be rejected", ServerEndpoint.parse(endpoint) is EndpointParseResult.Invalid)
        }
    }

    @Test
    fun rejectsTerminalDotDebugHttpAliases() {
        val policy = EndpointPolicy { host -> host in setOf("localhost", "127.0.0.1", "10.0.2.2") }

        listOf("localhost.", "127.0.0.1.", "10.0.2.2.").forEach { host ->
            assertTrue(ServerEndpoint.parse("http://$host:4533", policy) is EndpointParseResult.Invalid)
        }
    }

    @Test
    fun rejectsNumericHostForms() {
        listOf(
            "https://2130706433",
            "https://9999999999",
            "https://8888888888",
            "https://7777777777",
        ).forEach { endpoint ->
            assertTrue("Expected $endpoint to be rejected", ServerEndpoint.parse(endpoint) is EndpointParseResult.Invalid)
        }
    }

    @Test
    fun rejectsUnsafeOrStructurallyInvalidEndpoints() {
        val unsafeEndpoints = listOf(
            "//music.example.com/navidrome",
            "http://music.example.com",
            "https://user@music.example.com",
            "https://music.example.com/navidrome?token=value",
            "https://music.example.com/navidrome#fragment",
            "https://localhost",
            "https://music.localhost",
            "https://server.local",
            "https://127.0.0.1",
            "https://[::1]",
            "https://10.0.0.1",
            "https://0.0.0.0",
            "https://169.254.1.1",
            "https://172.16.1.1",
            "https://192.168.1.1",
            "https:///navidrome",
            "https://music.example.com/navidrome\u0000",
            "https://music.example.com/navidrome/../rest",
            "https://music.example.com/%2e%2e/rest",
            "https://music.example.com/navidrome%5crest",
        )

        unsafeEndpoints.forEach { endpoint ->
            assertTrue("Expected $endpoint to be rejected", ServerEndpoint.parse(endpoint) is EndpointParseResult.Invalid)
        }
    }

    @Test
    fun profileContainsOnlyTheNormalizedEndpoint() {
        val endpoint = endpoint("https://music.example.com/navidrome")

        assertEquals(endpoint, ServerProfile(endpoint).endpoint)
    }

    @Test
    fun protocolShapedSyntheticResponseIsReachableButNotCompatibleOrAuthenticated() {
        val facts = reducePingObservation(PingObservation.ProtocolResponse("1.16.1"))

        assertEquals(Reachability.REACHABLE, facts.reachability)
        assertEquals(Compatibility.NOT_CHECKED, facts.compatibility)
        assertEquals(Authentication.NOT_CHECKED, facts.authentication)
    }

    @Test
    fun networkErrorAndUnexpectedResponseKeepCompatibilityAndAuthenticationUnchecked() {
        val networkFacts = reducePingObservation(PingObservation.NetworkError)
        val unexpectedFacts = reducePingObservation(PingObservation.UnexpectedResponse)

        assertEquals(Reachability.UNREACHABLE, networkFacts.reachability)
        assertEquals(Reachability.REACHABLE, unexpectedFacts.reachability)
        assertEquals(Compatibility.NOT_CHECKED, networkFacts.compatibility)
        assertEquals(Compatibility.NOT_CHECKED, unexpectedFacts.compatibility)
        assertEquals(Authentication.NOT_CHECKED, networkFacts.authentication)
        assertEquals(Authentication.NOT_CHECKED, unexpectedFacts.authentication)
    }

    @Test
    fun unauthenticatedSyntheticResponseDoesNotClaimAuthenticationOrCompatibility() {
        val facts = reducePingObservation(PingObservation.Unauthenticated)

        assertEquals(Reachability.REACHABLE, facts.reachability)
        assertEquals(Compatibility.NOT_CHECKED, facts.compatibility)
        assertEquals(Authentication.NOT_CHECKED, facts.authentication)
    }

    @Test
    fun admitsOnlyExplicitLocalHttpHostsWhenThePolicyAllowsThem() {
        val policy = EndpointPolicy { host -> host in setOf("localhost", "127.0.0.1", "10.0.2.2") }

        listOf("localhost", "127.0.0.1", "10.0.2.2").forEach { host ->
            assertEquals(
                EndpointParseResult.Valid(endpoint("http://$host:4533", policy)),
                ServerEndpoint.parse("http://$host:4533", policy),
            )
        }
    }

    @Test
    fun rejectsHttpWhenThePolicyDoesNotAllowTheHost() {
        val debugPolicy = EndpointPolicy { host -> host == "10.0.2.2" }

        assertTrue(ServerEndpoint.parse("http://music.example.com", HttpsOnlyEndpointPolicy) is EndpointParseResult.Invalid)
        assertTrue(ServerEndpoint.parse("http://music.example.com", debugPolicy) is EndpointParseResult.Invalid)
        assertTrue(ServerEndpoint.parse("http://localhost", HttpsOnlyEndpointPolicy) is EndpointParseResult.Invalid)
        assertTrue(ServerEndpoint.parse("https://music.example.com", HttpsOnlyEndpointPolicy) is EndpointParseResult.Valid)
    }

    private fun endpoint(value: String, policy: EndpointPolicy = BuildVariantEndpointPolicy): ServerEndpoint =
        (ServerEndpoint.parse(value, policy) as EndpointParseResult.Valid).endpoint
}
