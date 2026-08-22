package dev.devdigi.music.connection

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canonical auth-secret DataStore storage-binding contract:
 * production creation must derive the canonical excluded path internally
 * (files/datastore/auth_secret.preferences_pb) rather than accepting an
 * arbitrary file callback, and the corruption handler must be preserved.
 */
class AuthSecretDataStoreFactoryTest {

    @Test
    fun canonicalStoreNameAndBackupPathMatchBackupExclusions() {
        assertEquals("auth_secret", AuthSecretDataStoreFactory.AUTH_SECRET_STORE_NAME)
        assertEquals(
            "datastore/auth_secret.preferences_pb",
            AuthSecretDataStoreFactory.AUTH_SECRET_BACKUP_RELATIVE_PATH,
        )
    }

    @Test
    fun corruptionHandlerRecoversToEmptyAndAllowsLaterSave() = runBlocking {
        val file = File.createTempFile("auth-secret", ".preferences_pb").apply { delete() }
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x7f))
        val dataStore = AuthSecretDataStoreFactory.createForTest { file }
        val store = DataStoreAuthSecretStore(dataStore, FactoryFakeSecretCipher())
        val endpoint = (ServerEndpoint.parse("https://music.example.com") as EndpointParseResult.Valid).endpoint

        val read = store.read(endpoint)
        assertTrue("corrupt store must not throw", read.isSuccess)
        assertEquals(null, read.getOrNull())

        val save = store.save(ServerAccountIdentity(endpoint, "alice"), "secret-password")
        assertTrue("later save must work after corruption recovery", save.isSuccess)
    }
}

private class FactoryFakeSecretCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedSecret {
        val iv = ByteArray(12) { 0x01 }
        return EncryptedSecret(plaintext, iv)
    }

    override fun decrypt(encrypted: EncryptedSecret, associatedData: ByteArray): ByteArray {
        require(encrypted.iv.size == 12) { "unexpected iv" }
        return encrypted.ciphertext
    }
}
