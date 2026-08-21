package dev.devdigi.music.connection

import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseEndpointPolicyTest {
    @Test
    fun rejectsHttpAndAcceptsHttps() {
        listOf("http://localhost", "http://127.0.0.1", "http://10.0.2.2", "http://music.example.com").forEach {
            assertTrue(ServerEndpoint.parse(it, BuildVariantEndpointPolicy) is EndpointParseResult.Invalid)
        }
        assertTrue(ServerEndpoint.parse("https://music.example.com", BuildVariantEndpointPolicy) is EndpointParseResult.Valid)
    }
}
