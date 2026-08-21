# Design: Navidrome Account Authentication

## Technical Approach

Add a parallel authenticated seam on the archived #13 boundary. Credentials never persist in the clear: only an AES/GCM-encrypted password lives in a separate `auth_secret` DataStore backed by Android Keystore. Sign-in pings `/rest/ping.view`, persists the secret only on success, then exposes `ServerAccountIdentity`. Cold-start restoration re-authenticates before rendering authenticated UI. The #13 `PingClient`, `PingObservation`, and `reducePingObservation` remain untouched.

## Architecture Decisions

| Choice | Rejected + Tradeoff | Rationale |
|--------|---------------------|-----------|
| OkHttp 5.4.0, no logging-interceptor | Older OkHttp / logging interceptor | No credential leakage to logs |
| `kotlinx-serialization-json` 1.9.0 runtime | `org.json` runtime / Gson runtime | Strict standard JSON; rejects comments, trailing commas, single quotes, unquoted keys, trailing tokens before protocol interpretation |
| Subsonic token/salt `md5(password + salt)`, per-request salt | API-key (#44) / persisted token | Password not sent over wire; salt not stored |
| Android Keystore AES/GCM/NoPadding, separate `auth_secret` DataStore | `EncryptedSharedPreferences` / combined store | Precise key lifecycle, separate store, backup exclusion |
| Crypto seams + in-memory fakes | Faking `AndroidKeyStore` on JVM via reflection | Deterministic tests, no reflection |
| Fail-closed sign-in: ping → persist → expose identity | Optimistic persist before ping | No durable state on partial success |
| `ServerAccountIdentity` = normalized endpoint + exact opaque username (case-sensitive, Unicode-preserving); `ServerMetadata` separate | Bundling metadata into identity / normalizing username | Identity stable across version changes and never collapses distinct server accounts |
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
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | Modify | OkHttp, `kotlinx-serialization-json`, mockwebserver |
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
    suspend fun save(username: String, secret: String): Result<Unit>
    suspend fun read(): Result<StoredCredentials?>
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

## Endpoint Binding Contract (WU2 / PR B implementation)

The encrypted credential MUST be cryptographically bound to the normalized `ServerEndpoint.value` and the exact opaque username, so a secret stored for server A can never decrypt or be used under server B.

The future store seam evolves to take the expected endpoint as part of its boundary (equivalent signatures, exact variant may be cleaner):

```kotlin
interface AuthSecretStore {
    suspend fun save(identity: ServerAccountIdentity, secret: String): Result<Unit>
    suspend fun read(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?>
    suspend fun clear()
}
```

Cold-start restoration: restore `ServerProfile`, then `authStore.read(profile.endpoint)`. If the ciphertext was created for another endpoint, GCM authentication fails, no credentials are returned, the invalid snapshot is cleared conditionally, and nothing derived from the other account's password is ever sent to the current server.

AAD serialization (deterministic, length-prefixed — no ambiguous concatenation):

```
UTF8("devdigi.music.auth.aad.v1")
+ uint32_be(endpointUtf8.size) + endpointUtf8
+ uint32_be(usernameUtf8.size) + usernameUtf8
```
where `endpointUtf8 = identity.endpoint.value` (UTF-8) and `usernameUtf8 = identity.username` (UTF-8, unnormalized).

`SecretCipher` evolves to `encrypt(plaintext, associatedData)` / `decrypt(encrypted, associatedData)`, calling `Cipher.updateAAD(aad)` before `doFinal` on both paths. This subsumes the earlier "authenticate the username with the ciphertext" finding with the stronger endpoint+username binding.

ServerProfile change semantics (defense-in-depth, NOT the primary guarantee): changing/deleting `ServerProfile` invalidates current authenticated state and SHOULD best-effort clear `auth_secret`. Because `server_profile` and `auth_secret` are separate DataStores, the cross-store clear is not crash-atomic; even if the clear fails, a stale ciphertext survives, or a process crashes mid-change, AAD endpoint binding still prevents A's secret from being decrypted or used under B. Concrete profile-change/session-invalidation wiring lands in WU4; the invariant is fixed here.

## Redirect Policy (WU3)

Authenticated OpenSubsonic ping requests MUST NOT automatically follow redirects. The OkHttp client (or equivalent transport) SHALL be configured with `followRedirects(false)` and `followSslRedirects(false)` if applicable. Any 3xx response SHALL be rejected locally and mapped to `AuthProtocolError` unless a future explicit taxonomy decision changes the mapping.

The signed query parameters (`u`, `t`, `s`, `v`, `c`, `f`) MUST NOT be forwarded to another origin. The client SHALL issue exactly one authenticated request per ping; it SHALL NOT retry the same signed request against a redirect target. Deterministic WU4-style tests SHALL verify that cross-origin 302/307/308 redirects result in `result != Authenticated`, the configured server receives exactly one request, the redirect target receives zero requests, and no username/salt/token reach the redirect target.

## Stale In-Flight Auth vs Profile Change (WU4)

Each authentication attempt captures the current `ServerProfile` generation/revision when it begins. When `ServerProfile` is saved, replaced, or deleted: (1) the active authentication job is cancelled AND (2) the profile/auth generation is incremented or otherwise invalidates prior attempts.

Cancellation is NOT the sole guarantee; the generation/revision check is the backstop. Before persisting credentials, exposing `ServerAccountIdentity`, or publishing `AUTHENTICATED`, the attempt MUST verify that the current profile generation matches the captured generation and that the current endpoint/profile still matches the attempt target.

If the attempt is stale: do not publish `AUTHENTICATED`, do not expose identity, do not allow stale credentials to become durable/current; the current/new profile wins; fail closed. Do NOT hold a coroutine `Mutex` across the network ping.

Deterministic planned tests SHALL cover profile change: (A) while the authenticated ping is suspended; (B) after ping success but before secure persistence; (C) after persistence but before identity/`AUTHENTICATED` exposure — using `CompletableDeferred`, controlled fakes, latches, and `kotlinx-coroutines-test`, with no `Thread.sleep` or timing races. In all cases the stale attempt loses and the current profile wins.

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
