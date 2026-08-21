package dev.devdigi.music.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicAuthSignerTest {
    @Test
    fun matchesPublishedSubsonicTestVector() {
        val signer = DefaultSubsonicAuthSigner(saltSource = { "c19b2d" })

        val signature = signer.sign(AuthCredentials.create("alice", "sesame"))

        assertEquals("c19b2d", signature.salt)
        assertEquals("26719a1196d2a940705a59634eb18eab", signature.token)
    }

    @Test
    fun producesAUrlSafePerRequestSalt() {
        val signer = DefaultSubsonicAuthSigner()
        val credentials = AuthCredentials.create("alice", "sesame")

        val first = signer.sign(credentials)
        val second = signer.sign(credentials)

        assertTrue("salt length ${first.salt.length}", first.salt.length >= 6)
        assertTrue("salt contains only hex chars", first.salt.matches(HEX_REGEX))
        assertNotEquals("consecutive salts differ", first.salt, second.salt)
    }

    @Test
    fun signatureToStringDoesNotExposeSaltTokenOrPassword() {
        val signer = DefaultSubsonicAuthSigner(saltSource = { "c19b2d" })
        val signature = signer.sign(AuthCredentials.create("alice", "sesame"))

        val text = signature.toString()

        assertTrue("toString must not leak the salt", !text.contains(signature.salt))
        assertTrue("toString must not leak the token", !text.contains(signature.token))
        assertTrue("toString must not leak the password", !text.contains("sesame"))
        assertTrue("toString must be redacted", text.contains("***"))
    }

    private companion object {
        val HEX_REGEX = Regex("^[0-9a-f]+$")
    }
}
