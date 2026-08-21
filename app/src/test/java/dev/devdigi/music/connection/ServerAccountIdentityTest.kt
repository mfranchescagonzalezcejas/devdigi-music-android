package dev.devdigi.music.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServerAccountIdentityTest {
    @Test
    fun identityStaysStableAcrossMetadataChanges() {
        val endpoint = endpoint("https://music.example.com")
        val identity = ServerAccountIdentity(endpoint, "alice")

        val firstMetadata = ServerMetadata("navidrome", "0.54.1", true)
        val secondMetadata = ServerMetadata("subsonic", "0.53.0", false)

        assertEquals(identity, ServerAccountIdentity(endpoint, "alice"))
        assertEquals(identity.hashCode(), ServerAccountIdentity(endpoint, "alice").hashCode())
        assertNotEquals(identity, ServerAccountIdentity(endpoint, "bob"))

        // Identity does not depend on server metadata.
        assertEquals("alice", identity.username)
        assertEquals(endpoint, identity.endpoint)
        assertNotEquals(firstMetadata, secondMetadata)
    }

    @Test
    fun usernameIsCaseSensitiveOpaqueIdentifier() {
        val endpoint = endpoint("https://music.example.com")

        assertNotEquals(
            "identity must not fold case: alice vs Alice",
            ServerAccountIdentity(endpoint, "alice"),
            ServerAccountIdentity(endpoint, "Alice"),
        )
    }

    @Test
    fun distinctUnicodeUsernamesDoNotCollapse() {
        val endpoint = endpoint("https://music.example.com")

        assertNotEquals(
            "identity must not normalize distinct Unicode forms",
            ServerAccountIdentity(endpoint, "alice"),
            ServerAccountIdentity(endpoint, "alice\u0301"),
        )
        assertNotEquals(
            "identity must not trim or fold whitespace",
            ServerAccountIdentity(endpoint, "alice"),
            ServerAccountIdentity(endpoint, " alice "),
        )
    }

    private fun endpoint(value: String): ServerEndpoint =
        (ServerEndpoint.parse(value) as EndpointParseResult.Valid).endpoint
}
