package dev.devdigi.music.connection

import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    @Test
    fun debugAllowsOnlyTheExplicitLocalHttpHosts() {
        listOf("localhost", "127.0.0.1", "10.0.2.2").forEach { host ->
            assertTrue(ServerEndpoint.parse("http://$host:4533") is EndpointParseResult.Valid)
        }
        assertTrue(ServerEndpoint.parse("http://music.example.com") is EndpointParseResult.Invalid)
        assertTrue(ServerEndpoint.parse("https://music.example.com") is EndpointParseResult.Valid)
    }
}
