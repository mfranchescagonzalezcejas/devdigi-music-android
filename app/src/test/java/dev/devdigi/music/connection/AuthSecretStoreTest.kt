package dev.devdigi.music.connection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthSecretStoreTest {

    private val endpointA = endpoint("https://music.example.com")
    private val endpointB = endpoint("https://other.example.com")

    @Test
    fun saveAndReadRoundTrip() = runBlocking {
        val cipher = FakeSecretCipher()
        val store = store(cipher)

        store.save(identity(endpointA, "alice"), "secret-password")
        val result = store.read(endpointA).getOrThrow()

        assertEquals(StoredCredentials("alice", "secret-password"), result)
    }

    @Test
    fun clearRemovesCredentials() = runBlocking {
        val cipher = FakeSecretCipher()
        val dataStore = dataStore()
        val store = DataStoreAuthSecretStore(dataStore, cipher)
        dataStore.edit {
            it[USERNAME_KEY] = "alice"
            it[AUTH_SECRET_KEY] = encode(cipher.encrypt("anything".toByteArray(), ByteArray(0)))
        }

        store.clear()
        val result = store.read(endpointA).getOrThrow()

        assertNull(result)
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
    }

    @Test
    fun plaintextSecretIsNeverPersisted() = runBlocking {
        val cipher = FakeSecretCipher()
        val dataStore = dataStore()
        val store = DataStoreAuthSecretStore(dataStore, cipher)
        val secret = "secret-password"

        store.save(identity(endpointA, "alice"), secret)
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

        val result = store.read(endpointA)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
    }

    @Test
    fun decryptFailureReturnsNoSecretAndClearsState() = runBlocking {
        val dataStore = dataStore()
        val encryptCipher = FakeSecretCipher()
        val payload = encode(encryptCipher.encrypt("secret-password".toByteArray(), ByteArray(0)))
        dataStore.edit { it[AUTH_SECRET_KEY] = payload }
        val store = DataStoreAuthSecretStore(dataStore, FailingSecretCipher())

        val result = store.read(endpointA)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
    }

    @Test
    fun decryptFailureWithValidUsernameReturnsNoSecretAndClearsState() = runBlocking {
        val dataStore = dataStore()
        val encryptCipher = FakeSecretCipher()
        val payload = encode(encryptCipher.encrypt("secret-password".toByteArray(), ByteArray(0)))
        dataStore.edit {
            it[USERNAME_KEY] = "alice"
            it[AUTH_SECRET_KEY] = payload
        }
        val store = DataStoreAuthSecretStore(dataStore, FailingSecretCipher())

        val result = store.read(endpointA)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
        assertNull(dataStore.data.first()[USERNAME_KEY])
    }

    @Test
    fun serverProfileStorageRemainsIndependent() = runBlocking {
        val serverProfileStore = dataStore()
        val authStore = dataStore()
        val cipher = FakeSecretCipher()
        val store = DataStoreAuthSecretStore(authStore, cipher)
        serverProfileStore.edit { it[SERVER_ENDPOINT_KEY] = "https://music.example.com" }

        store.save(identity(endpointA, "alice"), "secret-password")

        assertEquals(
            setOf(SERVER_ENDPOINT_KEY.name),
            serverProfileStore.data.first().asMap().keys.map { it.name }.toSet(),
        )
        assertNotNull(authStore.data.first()[AUTH_SECRET_KEY])
    }

    @Test
    fun clearIsIdempotent() = runBlocking {
        val store = store(FakeSecretCipher())
        store.save(identity(endpointA, "alice"), "secret-password")

        store.clear()
        store.clear()

        assertNull(store.read(endpointA).getOrNull())
    }

    @Test
    fun failedSaveRemovesStalePriorSnapshot() = runBlocking {
        val dataStore = dataStore()
        val goodCipher = FakeSecretCipher()
        val store = DataStoreAuthSecretStore(dataStore, goodCipher)
        store.save(identity(endpointA, "alice"), "secret-password")
        assertNotNull(dataStore.data.first()[AUTH_SECRET_KEY])

        val failingStore = DataStoreAuthSecretStore(dataStore, FailingSecretCipher())
        val saveResult = failingStore.save(identity(endpointA, "bob"), "new-secret")

        assertTrue(saveResult.isFailure)
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
        assertNull(dataStore.data.first()[USERNAME_KEY])
        assertNull(failingStore.read(endpointA).getOrNull())
    }

    @Test
    fun conditionalCleanupDoesNotEraseReplacementSnapshot() = runBlocking {
        val dataStore = dataStore()
        val store = DataStoreAuthSecretStore(dataStore, FakeSecretCipher())
        // Old invalid snapshot A is present.
        dataStore.edit { it[AUTH_SECRET_KEY] = "invalid-A" }
        // A concurrent valid save lands, replacing the snapshot.
        store.save(identity(endpointA, "bob"), "newer-secret")

        // A read must see the valid replacement and never erase it.
        val result = store.read(endpointA)

        assertEquals(StoredCredentials("bob", "newer-secret"), result.getOrNull())
        assertNotNull("replacement snapshot must survive reads", dataStore.data.first()[AUTH_SECRET_KEY])
        assertEquals("bob", dataStore.data.first()[USERNAME_KEY])
    }

    @Test
    fun tamperedUsernameFailsGcmAuthenticationAndClears() = runBlocking {
        val dataStore = dataStore()
        val store = DataStoreAuthSecretStore(dataStore, AesGcmSecretCipher(StoreFakeAuthKeyProvider(aesKey())))
        store.save(identity(endpointA, "alice"), "secret-password")

        dataStore.edit { it[USERNAME_KEY] = "bob" }

        val result = store.read(endpointA)

        assertTrue(result.isSuccess)
        assertNull("tampered username must fail GCM authentication", result.getOrNull())
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
        assertNull(dataStore.data.first()[USERNAME_KEY])
    }

    @Test
    fun secretFromServerAIsUnusableUnderServerB() = runBlocking {
        val dataStore = dataStore()
        val store = DataStoreAuthSecretStore(dataStore, AesGcmSecretCipher(StoreFakeAuthKeyProvider(aesKey())))
        store.save(identity(endpointA, "alice"), "secret-password")

        val result = store.read(endpointB)

        assertTrue(result.isSuccess)
        assertNull("secret bound to A must not decrypt under B", result.getOrNull())
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
    }

    @Test
    fun providerExceptionFailsClosed() = runBlocking {
        val dataStore = dataStore()
        val encryptCipher = FakeSecretCipher()
        val payload = encode(encryptCipher.encrypt("secret-password".toByteArray(), ByteArray(0)))
        dataStore.edit {
            it[USERNAME_KEY] = "alice"
            it[AUTH_SECRET_KEY] = payload
        }
        val store = DataStoreAuthSecretStore(dataStore, ProviderFailingSecretCipher())

        val result = store.read(endpointA)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        assertNull(dataStore.data.first()[AUTH_SECRET_KEY])
        assertNull(dataStore.data.first()[USERNAME_KEY])
    }

    @Test
    fun cancellationPropagatesFromSave() = runBlocking {
        val store = DataStoreAuthSecretStore(dataStore(), CancellingSecretCipher())

        try {
            store.save(identity(endpointA, "alice"), "secret-password")
            fail("expected CancellationException to propagate")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun cancellationPropagatesFromRead() = runBlocking {
        val dataStore = dataStore()
        val encryptCipher = FakeSecretCipher()
        val payload = encode(encryptCipher.encrypt("secret-password".toByteArray(), ByteArray(0)))
        dataStore.edit {
            it[USERNAME_KEY] = "alice"
            it[AUTH_SECRET_KEY] = payload
        }
        val store = DataStoreAuthSecretStore(dataStore, CancellingSecretCipher())

        try {
            store.read(endpointA)
            fail("expected CancellationException to propagate")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun corruptedPreferencesRecoversToEmptyAndPermitsLaterSave() = runBlocking {
        val file = temporaryFile()
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x7f))
        val dataStore = AuthSecretDataStoreFactory.create { file }
        val store = DataStoreAuthSecretStore(dataStore, FakeSecretCipher())

        val read = store.read(endpointA)

        assertTrue("corrupt store must not throw", read.isSuccess)
        assertNull(read.getOrNull())

        val save = store.save(identity(endpointA, "alice"), "secret-password")
        assertTrue("later save must work after corruption recovery", save.isSuccess)
        assertEquals(StoredCredentials("alice", "secret-password"), store.read(endpointA).getOrThrow())
    }

    private fun store(cipher: SecretCipher): AuthSecretStore =
        DataStoreAuthSecretStore(dataStore(), cipher)

    private fun dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { temporaryFile() }

    private fun temporaryFile(): File = File.createTempFile("auth-secret", ".preferences_pb").apply { delete() }

    private fun identity(endpoint: ServerEndpoint, username: String): ServerAccountIdentity =
        ServerAccountIdentity(endpoint, username)

    private fun endpoint(value: String): ServerEndpoint =
        (ServerEndpoint.parse(value) as EndpointParseResult.Valid).endpoint

    private fun encode(secret: EncryptedSecret): String =
        "${Base64.getEncoder().encodeToString(secret.iv)}:${Base64.getEncoder().encodeToString(secret.ciphertext)}"

    private fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private companion object {
        val AUTH_SECRET_KEY = stringPreferencesKey("auth_secret")
        val SERVER_ENDPOINT_KEY = stringPreferencesKey("server_endpoint")
        val USERNAME_KEY = stringPreferencesKey("username")
    }
}

private class FakeSecretCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedSecret {
        val iv = ByteArray(12).apply { Random.nextBytes(this) }
        val ciphertext = ByteArray(plaintext.size)
        for (index in plaintext.indices) {
            ciphertext[index] = (plaintext[index].toInt() xor iv[index % iv.size].toInt()).toByte()
        }
        return EncryptedSecret(ciphertext, iv)
    }

    override fun decrypt(encrypted: EncryptedSecret, associatedData: ByteArray): ByteArray {
        val plaintext = ByteArray(encrypted.ciphertext.size)
        for (index in plaintext.indices) {
            plaintext[index] = (encrypted.ciphertext[index].toInt() xor encrypted.iv[index % encrypted.iv.size].toInt()).toByte()
        }
        return plaintext
    }
}

private class FailingSecretCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedSecret =
        throw GeneralSecurityException("fake failure")

    override fun decrypt(encrypted: EncryptedSecret, associatedData: ByteArray): ByteArray =
        throw GeneralSecurityException("fake failure")
}

private class ProviderFailingSecretCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedSecret =
        throw ProviderException("fake provider failure")

    override fun decrypt(encrypted: EncryptedSecret, associatedData: ByteArray): ByteArray =
        throw ProviderException("fake provider failure")
}

private class CancellingSecretCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedSecret =
        throw CancellationException("cancelled")

    override fun decrypt(encrypted: EncryptedSecret, associatedData: ByteArray): ByteArray =
        throw CancellationException("cancelled")
}

private class StoreFakeAuthKeyProvider(private val key: SecretKey) : AuthKeyProvider {
    override fun getOrCreateKey(): SecretKey = key
    override fun deleteKey() = Unit
}