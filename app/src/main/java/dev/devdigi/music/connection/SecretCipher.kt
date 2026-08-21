package dev.devdigi.music.connection

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

interface SecretCipher {
    fun encrypt(plaintext: ByteArray): EncryptedSecret
    fun decrypt(encrypted: EncryptedSecret): ByteArray
}

data class EncryptedSecret(val ciphertext: ByteArray, val iv: ByteArray)

/**
 * AES/GCM/NoPadding secret cipher. The Cipher generates a fresh random IV on every
 * encryption; decryption authenticates via a 128-bit GCM tag and throws
 * [GeneralSecurityException] on any authentication failure, wrong key, or malformed input.
 * No key material is ever persisted by this class.
 */
class AesGcmSecretCipher(private val keyProvider: AuthKeyProvider) : SecretCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedSecret(ciphertext, cipher.iv)
    }

    override fun decrypt(encrypted: EncryptedSecret): ByteArray {
        if (encrypted.iv.isEmpty() || encrypted.ciphertext.isEmpty()) {
            throw GeneralSecurityException("invalid encrypted payload")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider.getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, encrypted.iv),
        )
        return cipher.doFinal(encrypted.ciphertext)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}