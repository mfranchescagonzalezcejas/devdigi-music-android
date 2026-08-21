package dev.devdigi.music.connection

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

interface AuthKeyProvider {
    fun getOrCreateKey(): SecretKey
}

/**
 * Android Keystore-backed AES key provider for DevDigi Music auth.
 *
 * The key is generated and stored inside AndroidKeyStore, is non-exportable, and
 * never leaves the Keystore. It requires no biometric/user authentication for #14.
 */
class AndroidKeystoreAuthKeyProvider(
    private val alias: String = AUTH_KEY_ALIAS,
) : AuthKeyProvider {
    override fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val AUTH_KEY_ALIAS = "devdigi.music.auth.v1"
    }
}