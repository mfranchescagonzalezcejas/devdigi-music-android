package dev.devdigi.music.features.library.domain

import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecentAlbumsDomainTest {
    @Test
    fun successfulCatalogueCarriesItsOwningAccount() {
        val alice =
            ServerAccountIdentity(
                endpoint = endpoint("https://music.example.com"),
                username = "alice",
            )
        val bob =
            ServerAccountIdentity(
                endpoint = endpoint("https://music.example.com"),
                username = "bob",
            )

        val catalogue =
            AccountScopedRecentAlbums(
                account = alice,
                albums =
                    listOf(
                        RecentAlbum(
                            id = "album-1",
                            title = "Synthetic Album",
                            artist = "Example Artist",
                            coverArtId = "cover-1",
                        ),
                    ),
            )

        assertEquals(alice, catalogue.account)
        assertNotEquals(bob, catalogue.account)
        assertEquals("album-1", catalogue.albums.single().id)
    }

    @Test
    fun albumModelContainsNoTransportOrCredentialState() {
        val album =
            RecentAlbum(
                id = "album-1",
                title = "Synthetic Album",
                artist = null,
                coverArtId = null,
            )

        assertEquals("album-1", album.id)
        assertEquals("Synthetic Album", album.title)
        assertEquals(null, album.artist)
        assertEquals(null, album.coverArtId)
    }

    private fun endpoint(value: String): ServerEndpoint = (ServerEndpoint.parse(value) as EndpointParseResult.Valid).endpoint
}
