package dev.devdigi.music.connection

import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SecretCipherTest {

    @Test
    fun encryptDecryptRoundTrip() {
        val cipher = AesGcmSecretCipher(FakeAuthKeyProvider(aesKey()))
        val plaintext = "secret-password".toByteArray(Charsets.UTF_8)

        val encrypted = cipher.encrypt(plaintext)
        val decrypted = cipher.decrypt(encrypted)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun freshIvPerEncryption() {
        val cipher = AesGcmSecretCipher(FakeAuthKeyProvider(aesKey()))
        val plaintext = "same-plaintext".toByteArray(Charsets.UTF_8)

        val first = cipher.encrypt(plaintext)
        val second = cipher.encrypt(plaintext)

        assertFalse("IV reused across encryptions", first.iv.contentEquals(second.iv))
        assertFalse("ciphertext identical for same plaintext", first.ciphertext.contentEquals(second.ciphertext))
        assertEquals(12, first.iv.size)
        assertEquals(12, second.iv.size)
    }

    @Test
    fun tamperedCiphertextThrows() {
        val cipher = AesGcmSecretCipher(FakeAuthKeyProvider(aesKey()))
        val plaintext = "secret-password".toByteArray(Charsets.UTF_8)
        val encrypted = cipher.encrypt(plaintext)
        assertTrue("ciphertext must not be empty", encrypted.ciphertext.isNotEmpty())
        val tampered = encrypted.ciphertext.clone()
        tampered[0] = (tampered[0] + 1).toByte()

        try {
            cipher.decrypt(EncryptedSecret(tampered, encrypted.iv))
            fail("expected GCM authentication failure")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun wrongKeyThrows() {
        val encryptCipher = AesGcmSecretCipher(FakeAuthKeyProvider(aesKey()))
        val decryptCipher = AesGcmSecretCipher(FakeAuthKeyProvider(aesKey()))
        val plaintext = "secret-password".toByteArray(Charsets.UTF_8)
        val encrypted = encryptCipher.encrypt(plaintext)

        try {
            decryptCipher.decrypt(encrypted)
            fail("expected decryption failure with wrong key")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun missingKeyFailsClosed() {
        val cipher = AesGcmSecretCipher(FailingAuthKeyProvider())

        try {
            cipher.encrypt("secret".toByteArray())
            fail("expected key failure on encrypt")
        } catch (_: GeneralSecurityException) {
        }

        try {
            cipher.decrypt(EncryptedSecret(ByteArray(0), ByteArray(0)))
            fail("expected key failure on decrypt")
        } catch (_: GeneralSecurityException) {
        }
    }

    private fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}

private class FakeAuthKeyProvider(private val key: SecretKey) : AuthKeyProvider {
    override fun getOrCreateKey(): SecretKey = key
}

private class FailingAuthKeyProvider : AuthKeyProvider {
    override fun getOrCreateKey(): SecretKey = throw GeneralSecurityException("no key")
}
