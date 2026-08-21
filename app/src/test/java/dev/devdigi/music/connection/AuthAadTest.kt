package dev.devdigi.music.connection

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthAadTest {

    @Test
    fun aadIsDeterministicForSameIdentity() {
        val first = AuthAad.forIdentity("https://music.example.com", "alice")
        val second = AuthAad.forIdentity("https://music.example.com", "alice")

        assertArrayEquals("same identity must produce identical AAD", first, second)
    }

    @Test
    fun aadDiffersWhenEndpointChanges() {
        val a = AuthAad.forIdentity("https://music.example.com", "alice")
        val b = AuthAad.forIdentity("https://other.example.com", "alice")

        assertFalse("endpoint change must change AAD", a.contentEquals(b))
    }

    @Test
    fun aadDiffersWhenUsernameChanges() {
        val a = AuthAad.forIdentity("https://music.example.com", "alice")
        val b = AuthAad.forIdentity("https://music.example.com", "bob")

        assertFalse("username change must change AAD", a.contentEquals(b))
    }

    @Test
    fun lengthPrefixesAreAmbiguityFree() {
        // "ab" + "c" must not collide with "a" + "bc" as separate length-prefixed fields.
        val abC = AuthAad.forIdentity("ab", "c")
        val aBc = AuthAad.forIdentity("a", "bc")

        assertFalse("length-prefixed fields must not collide", abC.contentEquals(aBc))
    }

    @Test
    fun aadCarriesDomainVersionPrefix() {
        val bytes = AuthAad.forIdentity("https://music.example.com", "alice")
        val header = "devdigi.music.auth.aad.v1".toByteArray(Charsets.UTF_8)

        assertTrue(bytes.size > header.size)
        for (index in header.indices) {
            assertTrue("header prefix mismatch at $index", bytes[index] == header[index])
        }
    }
}