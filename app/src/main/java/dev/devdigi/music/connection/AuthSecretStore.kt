package dev.devdigi.music.connection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.ProviderException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface AuthSecretStore {
    suspend fun save(identity: ServerAccountIdentity, secret: String): Result<Unit>
    suspend fun read(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?>
    suspend fun clear()
}

data class StoredCredentials(val username: String, val secret: String) {
    override fun toString(): String = "StoredCredentials(username=$username, secret=***)"
}

/**
 * Encrypted secret store backed by a DEDICATED Preferences DataStore (`auth_secret`),
 * completely independent of the non-secret `server_profile` DataStore from #13.
 *
 * The encrypted credential is cryptographically bound to the normalized endpoint and
 * the exact opaque username via AES-GCM AAD ([AuthAad]). A secret created for server A
 * can never decrypt or be used under server B: GCM authentication fails, no credentials
 * are returned, and the invalid snapshot is cleared conditionally.
 *
 * `read()` is fail-closed and cancellation-safe: `CancellationException` is rethrown
 * (it is not a storage failure); malformed payload, bad encoding, missing IV, corrupt
 * ciphertext, AEAD/GCM tag failure, wrong/missing/replaced key, `ProviderException`,
 * or any [GeneralSecurityException] yields no credentials and a best-effort conditional
 * clear. `save()` failure removes the prior snapshot only if it still matches what was
 * captured, so a concurrently committed newer credential is never erased.
 */
@OptIn(ExperimentalEncodingApi::class)
class DataStoreAuthSecretStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : AuthSecretStore {

    override suspend fun save(identity: ServerAccountIdentity, secret: String): Result<Unit> = mutex.withLock {
        var priorUsername: String? = null
        var priorPayload: String? = null
        var snapshotRead = false
        val aad = AuthAad.forIdentity(identity.endpoint.value, identity.username)
        return@withLock try {
            val snapshot = dataStore.data.first()
            priorUsername = snapshot[USERNAME_KEY]
            priorPayload = snapshot[SECRET_KEY]
            snapshotRead = true
            val encrypted = cipher.encrypt(secret.toByteArray(Charsets.UTF_8), aad)
            dataStore.edit { preferences ->
                preferences[USERNAME_KEY] = identity.username
                preferences[SECRET_KEY] = encode(encrypted)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            // Cancellation AFTER the prior snapshot was captured: perform best-effort
            // CONDITIONAL cleanup of the captured prior snapshot in a tightly scoped
            // NonCancellable context, then rethrow the ORIGINAL CancellationException.
            // A cancelled replacement persistence must not silently leave the prior
            // credential restorable.
            if (snapshotRead) {
                try {
                    withContext(NonCancellable) {
                        clearIfSnapshotStillMatches(priorUsername, priorPayload)
                    }
                } catch (cleanup: CancellationException) {
                    if (cleanup !== e) e.addSuppressed(cleanup)
                } catch (cleanup: Exception) {
                    e.addSuppressed(cleanup)
                }
            }
            throw e
        } catch (e: Exception) {
            try {
                if (snapshotRead) {
                    clearIfSnapshotStillMatches(priorUsername, priorPayload)
                } else {
                    clearCredentialsNoLock()
                }
            } catch (cleanup: CancellationException) {
                throw cleanup
            } catch (cleanup: Exception) {
                e.addSuppressed(cleanup)
            }
            Result.failure(e)
        }
    }

    override suspend fun read(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?> = mutex.withLock {
        return@withLock try {
            readCredentials(expectedEndpoint)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readCredentials(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?> {
        val preferences = dataStore.data.first()
        val payload = preferences[SECRET_KEY] ?: return Result.success(null)
        val username = preferences[USERNAME_KEY]
        val encrypted = decode(payload)
        if (username == null || encrypted == null) {
            clearIfSnapshotStillMatches(username, payload)
            return Result.success(null)
        }
        val aad = AuthAad.forIdentity(expectedEndpoint.value, username)
        val secret = try {
            String(cipher.decrypt(encrypted, aad), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            clearIfSnapshotStillMatches(username, payload)
            return Result.success(null)
        } catch (e: ProviderException) {
            clearIfSnapshotStillMatches(username, payload)
            return Result.success(null)
        }
        return Result.success(StoredCredentials(username, secret))
    }

    override suspend fun clear() {
        mutex.withLock {
            clearCredentialsNoLock()
        }
    }

    private suspend fun clearCredentialsNoLock() {
        dataStore.edit { preferences ->
            preferences.remove(USERNAME_KEY)
            preferences.remove(SECRET_KEY)
        }
    }

    private suspend fun clearIfSnapshotStillMatches(username: String?, payload: String?) {
        dataStore.edit { preferences ->
            if (preferences[USERNAME_KEY] == username && preferences[SECRET_KEY] == payload) {
                preferences.remove(USERNAME_KEY)
                preferences.remove(SECRET_KEY)
            }
        }
    }

    private fun encode(encrypted: EncryptedSecret): String =
        "${Base64.Default.encode(encrypted.iv)}:${Base64.Default.encode(encrypted.ciphertext)}"

    private fun decode(payload: String): EncryptedSecret? {
        val parts = payload.split(':')
        if (parts.size != 2) return null
        val iv = runCatching { Base64.Default.decode(parts[0]) }.getOrNull() ?: return null
        val ciphertext = runCatching { Base64.Default.decode(parts[1]) }.getOrNull() ?: return null
        if (iv.isEmpty() || ciphertext.isEmpty()) return null
        return EncryptedSecret(ciphertext, iv)
    }

    private companion object {
        val USERNAME_KEY = stringPreferencesKey("username")
        val SECRET_KEY = stringPreferencesKey("auth_secret")
        val mutex = Mutex()
    }
}

/**
 * Deterministic, length-prefixed AAD that binds a credential to the domain/version,
 * the normalized endpoint, and the exact opaque username. Length prefixes avoid
 * delimiter ambiguity without normalizing the username.
 */
object AuthAad {
    private const val DOMAIN = "devdigi.music.auth.aad.v1"

    fun forIdentity(endpoint: String, username: String): ByteArray {
        val header = DOMAIN.toByteArray(Charsets.UTF_8)
        val endpointBytes = endpoint.toByteArray(Charsets.UTF_8)
        val usernameBytes = username.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(header)
        writeUInt32Be(out, endpointBytes.size)
        out.write(endpointBytes)
        writeUInt32Be(out, usernameBytes.size)
        out.write(usernameBytes)
        return out.toByteArray()
    }

    private fun writeUInt32Be(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}