package dev.devdigi.music.connection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.GeneralSecurityException
import java.util.Base64
import kotlinx.coroutines.flow.first

interface AuthSecretStore {
    suspend fun save(username: String, secret: String): Result<Unit>
    suspend fun read(): Result<StoredCredentials?>
    suspend fun clear()
}

data class StoredCredentials(val username: String, val secret: String) {
    override fun toString(): String = "StoredCredentials(username=$username, secret=***)"
}

/**
 * Encrypted secret store backed by a DEDICATED Preferences DataStore (`auth_secret`),
 * completely independent of the non-secret `server_profile` DataStore from #13.
 *
 * Persists only: username (non-secret, separate key) + the encrypted password payload
 * (base64 IV + ':' + base64 ciphertext) + the GCM IV carried inside that payload.
 * Never persists: plaintext password, salt, token, ConnectionFacts, authenticated
 * identity, or server metadata.
 *
 * `read()` is fail-closed: credentials are returned only when the entire stored state
 * is valid; malformed payload, bad encoding, missing IV, corrupt ciphertext, AEAD/GCM
 * tag failure, wrong/missing/replaced key, or any [GeneralSecurityException] yields
 * no credentials and a best-effort clear of the encrypted state. A failed clear after
 * a crypto failure still returns no credentials.
 */
class DataStoreAuthSecretStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : AuthSecretStore {
    override suspend fun save(username: String, secret: String): Result<Unit> = runCatching {
        val encrypted = cipher.encrypt(secret.toByteArray(Charsets.UTF_8))
        dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = username
            preferences[SECRET_KEY] = encode(encrypted)
        }
    }

    override suspend fun read(): Result<StoredCredentials?> = runCatching {
        val preferences = dataStore.data.first()
        val payload = preferences[SECRET_KEY] ?: return@runCatching null
        val username = preferences[USERNAME_KEY]
        val encrypted = decode(payload)
        if (username == null || encrypted == null) {
            clear()
            return@runCatching null
        }
        val secret = try {
            String(cipher.decrypt(encrypted), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            clear()
            return@runCatching null
        }
        StoredCredentials(username, secret)
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(USERNAME_KEY)
            preferences.remove(SECRET_KEY)
        }
    }

    private fun encode(encrypted: EncryptedSecret): String =
        "${Base64.getEncoder().encodeToString(encrypted.iv)}:${Base64.getEncoder().encodeToString(encrypted.ciphertext)}"

    private fun decode(payload: String): EncryptedSecret? {
        val parts = payload.split(':')
        if (parts.size != 2) return null
        val iv = runCatching { Base64.getDecoder().decode(parts[0]) }.getOrNull() ?: return null
        val ciphertext = runCatching { Base64.getDecoder().decode(parts[1]) }.getOrNull() ?: return null
        if (iv.isEmpty() || ciphertext.isEmpty()) return null
        return EncryptedSecret(ciphertext, iv)
    }

    private companion object {
        val USERNAME_KEY = stringPreferencesKey("username")
        val SECRET_KEY = stringPreferencesKey("auth_secret")
    }
}