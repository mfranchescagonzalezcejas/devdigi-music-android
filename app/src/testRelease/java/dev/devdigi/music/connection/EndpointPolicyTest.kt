package dev.devdigi.music.connection

import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    @Test
    fun releaseRejectsEveryHttpHostWhileKeepingHttpsAvailable() {
        listOf("localhost", "127.0.0.1", "10.0.2.2", "music.example.com").forEach { host ->
            assertTrue(ServerEndpoint.parse("http://$host:4533") is EndpointParseResult.Invalid)
        }
        assertTrue(ServerEndpoint.parse("https://music.example.com") is EndpointParseResult.Valid)
    }
}
