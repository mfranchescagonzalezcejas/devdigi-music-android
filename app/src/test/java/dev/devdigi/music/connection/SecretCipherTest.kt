package dev.devdigi.music.connection

import android.security.keystore.KeyPermanentlyInvalidatedException
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
        val encryptCipher = AesGcmSecretCipher(FailingAuthKeyProvider())

        try {
            encryptCipher.encrypt("secret".toByteArray(), aad)
            fail("expected key failure on encrypt")
        } catch (_: GeneralSecurityException) {
        }

        // The decrypt half must actually reach the key provider: a structurally valid
        // payload (non-empty ciphertext, 12-byte IV) passes input validation and the
        // missing key is the reason decryption fails.
        val decryptProvider = CountingFailingAuthKeyProvider()
        val decryptCipher = AesGcmSecretCipher(decryptProvider)

        try {
            decryptCipher.decrypt(EncryptedSecret(ByteArray(16) { 1 }, ByteArray(12) { 2 }), aad)
            fail("expected key failure on decrypt")
        } catch (_: GeneralSecurityException) {
        }

        assertTrue("decrypt must invoke the key provider", decryptProvider.getOrCreateCalls > 0)
    }

    @Test
    fun keyPermanentlyInvalidatedDeletesAliasAndFailsClosed() {
        val provider = InvalidatedAuthKeyProvider()
        val cipher = AesGcmSecretCipher(provider)
        val encrypted = EncryptedSecret(ByteArray(16) { 1 }, ByteArray(12) { 2 })

        try {
            cipher.decrypt(encrypted, aad)
            fail("expected decryption failure after key invalidation")
        } catch (_: GeneralSecurityException) {
        }

        assertTrue("invalidated alias must be deleted", provider.deleteCalled)
    }

    @Test
    fun keyPermanentlyInvalidatedDuringEncryptDeletesAliasAndFailsClosedAndLaterSucceeds() {
        val provider = RecoveringInvalidatedAuthKeyProvider(aesKey())
        val cipher = AesGcmSecretCipher(provider)
        val plaintext = "secret-password".toByteArray(Charsets.UTF_8)

        try {
            cipher.encrypt(plaintext, aad)
            fail("expected encryption failure after key invalidation")
        } catch (_: GeneralSecurityException) {
        }

        assertTrue("invalidated alias must be deleted", provider.deleteCalled)
        assertEquals("must not retry getOrCreateKey in same operation", 1, provider.getKeyCallCount)

        val encrypted = cipher.encrypt(plaintext, aad)
        assertTrue("subsequent encrypt must succeed with replacement key", encrypted.ciphertext.isNotEmpty())
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

private class CountingFailingAuthKeyProvider : AuthKeyProvider {
    var getOrCreateCalls = 0
    override fun getOrCreateKey(): SecretKey {
        getOrCreateCalls++
        throw GeneralSecurityException("no key")
    }
    override fun deleteKey() = Unit
}

private class InvalidatedAuthKeyProvider : AuthKeyProvider {
    var deleteCalled = false
    override fun getOrCreateKey(): SecretKey = throw KeyPermanentlyInvalidatedException("invalidated")
    override fun deleteKey() {
        deleteCalled = true
    }
}

private class RecoveringInvalidatedAuthKeyProvider(private val replacementKey: SecretKey) : AuthKeyProvider {
    var deleteCalled = false
    var getKeyCallCount = 0
    override fun getOrCreateKey(): SecretKey {
        getKeyCallCount++
        if (!deleteCalled) throw KeyPermanentlyInvalidatedException("invalidated")
        return replacementKey
    }
    override fun deleteKey() {
        deleteCalled = true
    }
}