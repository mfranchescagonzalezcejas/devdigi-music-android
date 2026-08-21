package dev.devdigi.music.connection

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

interface SecretCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedSecret
    fun decrypt(encrypted: EncryptedSecret, associatedData: ByteArray): ByteArray
}

data class EncryptedSecret(val ciphertext: ByteArray, val iv: ByteArray)

/**
 * AES/GCM/NoPadding secret cipher with Associated Authenticated Data.
 *
 * The Cipher generates a fresh random IV on every encryption. On both paths
 * [javax.crypto.Cipher.updateAAD] is called before [javax.crypto.Cipher.doFinal],
 * so the authenticated data (domain/version + endpoint + username) is bound to
 * the ciphertext. Any AAD mismatch, tampered ciphertext, or wrong key throws
 * [GeneralSecurityException]. No key material is ever persisted.
 */
class AesGcmSecretCipher(private val keyProvider: AuthKeyProvider) : SecretCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateKey())
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedSecret(ciphertext, cipher.iv)
    }

    override fun decrypt(encrypted: EncryptedSecret, associatedData: ByteArray): ByteArray {
        if (encrypted.iv.isEmpty() || encrypted.ciphertext.isEmpty()) {
            throw GeneralSecurityException("invalid encrypted payload")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider.getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, encrypted.iv),
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(encrypted.ciphertext)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}