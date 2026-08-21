# Design: Navidrome Account Authentication

## Technical Approach

Add a parallel authenticated seam on the archived #13 boundary. Credentials never persist in the clear: only an AES/GCM-encrypted password lives in a separate `auth_secret` DataStore backed by Android Keystore. Sign-in pings `/rest/ping.view`, persists the secret only on success, then exposes `ServerAccountIdentity`. Cold-start restoration re-authenticates before rendering authenticated UI. The #13 `PingClient`, `PingObservation`, and `reducePingObservation` remain untouched.

## Architecture Decisions

| Choice | Rejected + Tradeoff | Rationale |
|--------|---------------------|-----------|
| OkHttp 5.4.0, no logging-interceptor | Older OkHttp / logging interceptor | No credential leakage to logs |
| `org.json` runtime + `org.json:json` testImplementation | Kotlinx serialization / Gson runtime | Minimal runtime; JVM tests need real `org.json` (android.jar stubs it) |
| Subsonic token/salt `md5(password + salt)`, per-request salt | API-key (#44) / persisted token | Password not sent over wire; salt not stored |
| Android Keystore AES/GCM/NoPadding, separate `auth_secret` DataStore | `EncryptedSharedPreferences` / combined store | Precise key lifecycle, separate store, backup exclusion |
| Crypto seams + in-memory fakes | Faking `AndroidKeyStore` on JVM via reflection | Deterministic tests, no reflection |
| Fail-closed sign-in: ping → persist → expose identity | Optimistic persist before ping | No durable state on partial success |
| `ServerAccountIdentity` = normalized endpoint + username; `ServerMetadata` separate | Bundling metadata into identity | Identity stable across version changes |
| `AuthCredentials` guarded class with redacted `toString` | Plain `data class` exposing password | Blocks accidental secret leakage |
| Keep #13 `PingClient`; add `AuthenticatedPingClient` + `reduceAuthResult` | Folding auth into `PingObservation` | Preserves #13 contract and tests |

## Data Flow

Sign-in: `SubsonicAuthSigner` builds token/salt; `AuthenticatedPingClient` calls `/rest/ping.view`; on `Authenticated` the password is encrypted into `auth_secret`, then `ServerAccountIdentity` is exposed. Cold start repeats the authenticated ping before exposing identity.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `app/src/main/java/dev/devdigi/music/connection/ServerConnection.kt` | Modify | Auth domain types and ping reducer |
| `app/src/main/java/dev/devdigi/music/connection/SubsonicAuthSigner.kt` | Create | Token/salt signer |
| `app/src/main/java/dev/devdigi/music/connection/AuthSecretStore.kt` | Create | Encrypted password store |
| `app/src/main/java/dev/devdigi/music/connection/AuthSecretCipher.kt` | Create | Encryption + Keystore impl |
| `app/src/main/java/dev/devdigi/music/connection/AuthKeyProvider.kt` | Create | Keystore key + impl |
| `app/src/main/java/dev/devdigi/music/connection/OkHttpAuthenticatedPingClient.kt` | Create | OkHttp ping client |
| `app/src/main/java/dev/devdigi/music/connection/SessionRestorer.kt` | Create | Cold-start re-authentication |
| `app/src/main/java/dev/devdigi/music/connection/ServerConnectionViewModel.kt` | Modify | Sign-in/sign-out/restore |
| `app/src/main/java/dev/devdigi/music/connection/ServerConnectionScreen.kt` | Modify | Sign-in form fields |
| `app/src/main/java/dev/devdigi/music/MainActivity.kt` | Modify | DI wiring |
| `app/src/main/AndroidManifest.xml` | Modify | `INTERNET`, backup rules |
| `app/src/main/res/xml/backup_rules.xml` | Create | Exclude secret DataStore |
| `app/src/main/res/xml/data_extraction_rules.xml` | Create | Exclude secret DataStore |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | Modify | OkHttp, `org.json:json`, mockwebserver |
| `app/src/test/java/dev/devdigi/music/connection/` | Create | RED tests |

## Interfaces / Contracts

```kotlin
data class ServerAccountIdentity(val endpoint: ServerEndpoint, val username: String)
data class ServerMetadata(val serverType: String, val serverVersion: String, val openSubsonic: Boolean)

class AuthCredentials private constructor(val username: String, internal val password: String) {
    override fun toString(): String = "AuthCredentials(username=$username, password=***)"
}

sealed interface AuthResult {
    data class Authenticated(val metadata: ServerMetadata) : AuthResult
    data object InvalidCredentials : AuthResult
    data object UnsupportedAuthentication : AuthResult
    data object AuthProtocolError : AuthResult
    data object IncompatibleServer : AuthResult
    data object NetworkError : AuthResult
}

fun interface AuthenticatedPingClient {
    suspend fun ping(credentials: AuthCredentials, profile: ServerProfile): AuthResult
}

fun reduceAuthResult(result: AuthResult): ConnectionFacts

interface SubsonicAuthSigner {
    fun sign(credentials: AuthCredentials): AuthSignature
}
data class AuthSignature(val salt: String, val token: String)

interface AuthSecretStore {
    suspend fun save(credentials: AuthCredentials): Result<Unit>
    suspend fun read(): Result<AuthCredentials?>
    suspend fun clear()
}

interface AuthSecretCipher {
    fun encrypt(plaintext: ByteArray): EncryptedSecret
    fun decrypt(encrypted: EncryptedSecret): ByteArray
}
data class EncryptedSecret(val ciphertext: ByteArray, val iv: ByteArray)

interface AuthKeyProvider {
    fun getOrCreateKey(): javax.crypto.SecretKey
}

class SessionRestorer(
    private val profileRepository: ServerProfileRepository,
    private val secretStore: AuthSecretStore,
    private val pingClient: AuthenticatedPingClient,
) {
    suspend fun restore(): Result<Pair<ServerAccountIdentity, ServerMetadata>>
}
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | Subsonic vector/salt; `AuthCredentials.toString` redaction; `reduceAuthResult` mapping | JUnit |
| Unit | Cipher/key-provider fail-closed behavior | In-memory fakes, exception injection |
| Integration | `AuthSecretStore` round-trip; `SessionRestorer` with fake ping client | In-memory DataStore, coroutines-test |
| Integration | `OkHttpAuthenticatedPingClient` vs `MockWebServer` for every error code + malformed JSON + timeout | MockWebServer enqueue |
| UI/VM | Sign-in/out, restore emits `Restoring` first | Fake dependencies, coroutines-test |

## Threat Matrix

N/A — no routing/shell/process boundary. Secret handling uses Keystore, separate DataStore, backup exclusion, and redacted `toString`.

## Migration / Rollout

No migration required. `auth_secret` DataStore is created on first sign-in. Backup exclusion ensures an invalid or missing Keystore key on a new device forces re-login.

## Open Questions

- [ ] WU5 integration credential injection mechanism (Gradle property / environment / CI secret)?
