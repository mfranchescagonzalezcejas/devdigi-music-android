package dev.devdigi.music.connection

import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SecretCipherTest {

    private val aad = "devdigi.music.auth.aad.v1-https://music.example.com-alice".toByteArray(Charsets.UTF_8)

    @Test
    fun encryptDecryptRoundTrip() {
        val cipher = AesGcmSecretCipher(FakeAuthKeyProvider(aesKey()))
        val plaintext = "secret-password".toByteArray(Charsets.UTF_8)

        val encrypted = cipher.encrypt(plaintext, aad)
        val decrypted = cipher.decrypt(encrypted, aad)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun freshIvPerEncryption() {
        val cipher = AesGcmSecretCipher(FakeAuthKeyProvider(aesKey()))
        val plaintext = "same-plaintext".toByteArray(Charsets.UTF_8)

        val first = cipher.encrypt(plaintext, aad)
        val second = cipher.encrypt(plaintext, aad)

        assertFalse("IV reused across encryptions", first.iv.contentEquals(second.iv))
        assertFalse("ciphertext identical for same plaintext", first.ciphertext.contentEquals(second.ciphertext))
        assertTrue(first.iv.size == 12)
        assertTrue(second.iv.size == 12)
    }

    @Test
    fun tamperedCiphertextThrows() {
        val cipher = AesGcmSecretCipher(FakeAuthKeyProvider(aesKey()))
        val plaintext = "secret-password".toByteArray(Charsets.UTF_8)
        val encrypted = cipher.encrypt(plaintext, aad)
        assertTrue("ciphertext must not be empty", encrypted.ciphertext.isNotEmpty())
        val tampered = encrypted.ciphertext.clone()
        tampered[0] = (tampered[0] + 1).toByte()

        try {
            cipher.decrypt(EncryptedSecret(tampered, encrypted.iv), aad)
            fail("expected GCM authentication failure")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun wrongKeyThrows() {
        val encryptCipher = AesGcmSecretCipher(FakeAuthKeyProvider(aesKey()))
        val decryptCipher = AesGcmSecretCipher(FakeAuthKeyProvider(aesKey()))
        val plaintext = "secret-password".toByteArray(Charsets.UTF_8)
        val encrypted = encryptCipher.encrypt(plaintext, aad)

        try {
            decryptCipher.decrypt(encrypted, aad)
            fail("expected decryption failure with wrong key")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun wrongAssociatedDataThrows() {
        val cipher = AesGcmSecretCipher(FakeAuthKeyProvider(aesKey()))
        val plaintext = "secret-password".toByteArray(Charsets.UTF_8)
        val encrypted = cipher.encrypt(plaintext, aad)
        val otherAad = "devdigi.music.auth.aad.v1-https://other.example.com-alice".toByteArray(Charsets.UTF_8)

        try {
            cipher.decrypt(encrypted, otherAad)
            fail("expected GCM authentication failure with wrong AAD")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun missingKeyFailsClosed() {
        val cipher = AesGcmSecretCipher(FailingAuthKeyProvider())

        try {
            cipher.encrypt("secret".toByteArray(), aad)
            fail("expected key failure on encrypt")
        } catch (_: GeneralSecurityException) {
        }

        try {
            cipher.decrypt(EncryptedSecret(ByteArray(0), ByteArray(0)), aad)
            fail("expected key failure on decrypt")
        } catch (_: GeneralSecurityException) {
        }
    }

    private fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}

private class FakeAuthKeyProvider(private val key: SecretKey) : AuthKeyProvider {
    override fun getOrCreateKey(): SecretKey = key
    override fun deleteKey() = Unit
}

private class FailingAuthKeyProvider : AuthKeyProvider {
    override fun getOrCreateKey(): SecretKey = throw GeneralSecurityException("no key")
    override fun deleteKey() = Unit
}