package dev.devdigi.music.connection

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

interface AuthKeyProvider {
    fun getOrCreateKey(): SecretKey
    fun deleteKey()
}

/**
 * Android Keystore-backed AES key provider for DevDigi Music auth.
 *
 * The key is generated and stored inside AndroidKeyStore, is non-exportable, and
 * never leaves the Keystore. It requires no biometric/user authentication for #14.
 *
 * First-time key creation is serialized process-wide (shared companion lock), so
 * two callers cannot both generate a key and leave ciphertext under a lost alias.
 * The app is single-process for this feature; cross-process key creation is out
 * of scope for #14.
 */
class AndroidKeystoreAuthKeyProvider(
    private val alias: String = AUTH_KEY_ALIAS,
) : AuthKeyProvider {
    override fun getOrCreateKey(): SecretKey = synchronized(KEY_CREATION_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return@synchronized it }
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
        generator.generateKey()
    }

    override fun deleteKey() {
        synchronized(KEY_CREATION_LOCK) {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            keyStore.deleteEntry(alias)
        }
    }

    companion object {
        const val AUTH_KEY_ALIAS = "devdigi.music.auth.v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"

        // Process-wide atomic first-time key creation. The app is single-process for
        // this feature; cross-process key creation is out of scope for #14.
        private val KEY_CREATION_LOCK = Any()
    }
}