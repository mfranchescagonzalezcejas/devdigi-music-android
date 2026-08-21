package dev.devdigi.music.connection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.security.GeneralSecurityException
import java.util.Base64
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthSecretStoreTest {

    @Test
    fun saveAndReadRoundTrip() = runBlocking {
        val cipher = FakeSecretCipher()
        val store = store(cipher)

        store.save("alice", "secret-password")
        val result = store.read().getOrThrow()

        assertEquals(StoredCredentials("alice", "secret-password"), result)
    }

    @Test
    fun clearRemovesCredentials() = runBlocking {
        val cipher = FakeSecretCipher()
        val dataStore = dataStore()
        val store = DataStoreAuthSecretStore(dataStore, cipher)
        dataStore.edit { it[AUTH_SECRET_KEY] = encode(cipher.encrypt("anything".toByteArray())) }

        store.clear()
        val result = store.read().getOrThrow()

        assertNull(result)
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
    }

    @Test
    fun plaintextSecretIsNeverPersisted() = runBlocking {
        val cipher = FakeSecretCipher()
        val dataStore = dataStore()
        val store = DataStoreAuthSecretStore(dataStore, cipher)
        val secret = "secret-password"

        store.save("alice", secret)
        val stored = dataStore.data.first()[AUTH_SECRET_KEY]

        assertNotNull("expected an encrypted payload to be persisted", stored)
        assertFalse("secret persisted in the clear", stored!!.contains(secret))
        assertFalse("username persisted in the clear", stored.contains("alice"))
    }

    @Test
    fun malformedStoredPayloadReturnsNoCredentialsAndClearsState() = runBlocking {
        val dataStore = dataStore()
        val store = DataStoreAuthSecretStore(dataStore, FakeSecretCipher())
        dataStore.edit { it[AUTH_SECRET_KEY] = "not-a-valid-payload" }

        val result = store.read()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
    }

    @Test
    fun decryptFailureReturnsNoSecretAndClearsState() = runBlocking {
        val dataStore = dataStore()
        val encryptCipher = FakeSecretCipher()
        val payload = encode(encryptCipher.encrypt("secret-password".toByteArray()))
        dataStore.edit { it[AUTH_SECRET_KEY] = payload }
        val store = DataStoreAuthSecretStore(dataStore, FailingSecretCipher())

        val result = store.read()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
    }

    @Test
    fun serverProfileStorageRemainsIndependent() = runBlocking {
        val serverProfileStore = dataStore()
        val authStore = dataStore()
        val cipher = FakeSecretCipher()
        val store = DataStoreAuthSecretStore(authStore, cipher)
        serverProfileStore.edit { it[SERVER_ENDPOINT_KEY] = "https://music.example.com" }

        store.save("alice", "secret-password")

        assertEquals(
            setOf(SERVER_ENDPOINT_KEY.name),
            serverProfileStore.data.first().asMap().keys.map { it.name }.toSet(),
        )
        assertNotNull(authStore.data.first()[AUTH_SECRET_KEY])
    }

    @Test
    fun decryptFailureWithValidUsernameReturnsNoSecretAndClearsState() = runBlocking {
        val dataStore = dataStore()
        val encryptCipher = FakeSecretCipher()
        val payload = encode(encryptCipher.encrypt("secret-password".toByteArray()))
        dataStore.edit {
            it[USERNAME_KEY] = "alice"
            it[AUTH_SECRET_KEY] = payload
        }
        val store = DataStoreAuthSecretStore(dataStore, FailingSecretCipher())

        val result = store.read()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
        assertNull(dataStore.data.first()[USERNAME_KEY])
    }

    @Test
    fun clearIsIdempotent() = runBlocking {
        val store = store(FakeSecretCipher())
        store.save("alice", "secret-password")

        store.clear()
        store.clear()

        assertNull(store.read().getOrNull())
    }

    private fun store(cipher: SecretCipher): AuthSecretStore =
        DataStoreAuthSecretStore(dataStore(), cipher)

    private fun dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { temporaryFile() }

    private fun temporaryFile(): File = File.createTempFile("auth-secret", ".preferences_pb").apply { delete() }

    private fun encode(secret: EncryptedSecret): String =
        "${Base64.getEncoder().encodeToString(secret.iv)}:${Base64.getEncoder().encodeToString(secret.ciphertext)}"

    private companion object {
        val AUTH_SECRET_KEY = stringPreferencesKey("auth_secret")
        val SERVER_ENDPOINT_KEY = stringPreferencesKey("server_endpoint")
        val USERNAME_KEY = stringPreferencesKey("username")
    }
}

private class FakeSecretCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedSecret {
        val iv = ByteArray(12).apply { Random.nextBytes(this) }
        val ciphertext = ByteArray(plaintext.size)
        for (index in plaintext.indices) {
            ciphertext[index] = (plaintext[index].toInt() xor iv[index % iv.size].toInt()).toByte()
        }
        return EncryptedSecret(ciphertext, iv)
    }

    override fun decrypt(encrypted: EncryptedSecret): ByteArray {
        val plaintext = ByteArray(encrypted.ciphertext.size)
        for (index in plaintext.indices) {
            plaintext[index] = (encrypted.ciphertext[index].toInt() xor encrypted.iv[index % encrypted.iv.size].toInt()).toByte()
        }
        return plaintext
    }
}

private class FailingSecretCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedSecret = throw GeneralSecurityException("fake failure")
    override fun decrypt(encrypted: EncryptedSecret): ByteArray = throw GeneralSecurityException("fake failure")
}
