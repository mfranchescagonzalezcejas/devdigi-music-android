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

The username is persisted OUTSIDE the ciphertext as non-secret binding metadata. The username is NOT a secret; the password remains encrypted. The stored username participates in AES-GCM AAD authentication: changing or tampering with the stored username causes GCM authentication failure, preventing a ciphertext from being decrypted under a different username binding. The stored username remains exact, opaque, case-sensitive, Unicode-preserving, with no trim, no lowercase, and no NFC normalization.

The future store seam evolves to take the expected endpoint as part of its boundary (equivalent signatures, exact variant may be cleaner):

```kotlin
interface AuthSecretStore {
    suspend fun save(identity: ServerAccountIdentity, secret: String): Result<Unit>
    suspend fun read(expectedEndpoint: ServerEndpoint): Result<StoredCredentials?>
    suspend fun clear()
}

interface StoredCredentials {
    val username: String
    val secret: String
}
```

Cold-start restoration: restore `ServerProfile`, then `authStore.read(profile.endpoint)`. `read(expectedEndpoint)` reads the stored username first, builds the AAD from the normalized `expectedEndpoint` and the stored exact username, and only then decrypts. On success it returns `StoredCredentials(username, secret)`. If the ciphertext was created for another endpoint, or if the username was tampered with, GCM authentication fails, no credentials are returned, the invalid snapshot is cleared conditionally, and nothing derived from the other account's password is ever sent to the current server.

AAD serialization (deterministic, length-prefixed — no ambiguous concatenation):

```
UTF8("devdigi.music.auth.aad.v1")
+ uint32_be(endpointUtf8.size) + endpointUtf8
+ uint32_be(usernameUtf8.size) + usernameUtf8
```
where `endpointUtf8 = identity.endpoint.value` (UTF-8) and `usernameUtf8 = identity.username` (UTF-8, unnormalized).

`SecretCipher` evolves to `encrypt(plaintext, associatedData)` / `decrypt(encrypted, associatedData)`, calling `Cipher.updateAAD(aad)` before `doFinal` on both paths. Every encryption MUST use a fresh cryptographically random IV; the IV size is 12 bytes (96 bits), the standard GCM nonce length. Two encryptions under the same key and same plaintext MUST produce distinct IVs and therefore distinct ciphertexts; a constant or reused IV is forbidden because AES-GCM nonce reuse destroys confidentiality and authenticity. This subsumes the earlier "authenticate the username with the ciphertext" finding with the stronger endpoint+username binding.

ServerProfile change semantics (defense-in-depth, NOT the primary guarantee): changing/deleting `ServerProfile` invalidates current authenticated state and SHOULD best-effort clear `auth_secret`. Because `server_profile` and `auth_secret` are separate DataStores, the cross-store clear is not crash-atomic; even if the clear fails, a stale ciphertext survives, or a process crashes mid-change, AAD endpoint binding still prevents A's secret from being decrypted or used under B. Concrete profile-change/session-invalidation wiring lands in WU4; the invariant is fixed here.

## Redirect Policy (WU3)

Authenticated OpenSubsonic ping requests MUST NOT automatically follow redirects. The OkHttp client (or equivalent transport) SHALL be configured with `followRedirects(false)` and `followSslRedirects(false)` if applicable. Any 3xx response SHALL be rejected locally and mapped to `AuthProtocolError` unless a future explicit taxonomy decision changes the mapping.

The signed query parameters (`u`, `t`, `s`, `v`, `c`, `f`) MUST NOT be forwarded to another origin. The client SHALL issue exactly one authenticated request per ping; it SHALL NOT retry the same signed request against a redirect target. Deterministic WU4-style tests SHALL verify that cross-origin 302/307/308 redirects result in `result != Authenticated`, the configured server receives exactly one request, the redirect target receives zero requests, and no username/salt/token reach the redirect target.

## Endpoint Base Path Preservation (WU3)

The authenticated ping URL MUST preserve the configured endpoint path prefix. `ServerEndpoint.parse` already normalizes reverse-proxy/base paths, so the ping client MUST construct the request path by appending `/rest/ping.view` to the normalized endpoint value without stripping or duplicating separators.

- Endpoint root case: `https://music.example.com` → `https://music.example.com/rest/ping.view`
- Endpoint with base path: `https://music.example.com/navidrome` → `https://music.example.com/navidrome/rest/ping.view`
- Trailing-slash normalization must not create `//rest/...` (e.g. `https://music.example.com/` → `/rest/ping.view`, NOT `//rest/ping.view`)
- Encoded path segments must not be decoded/re-encoded incorrectly
- Query and auth parameter construction must never discard the existing endpoint path

Deterministic MockWebServer acceptance cases SHALL cover: endpoint root `/rest/ping.view`; endpoint with base path `/navidrome/rest/ping.view`; trailing-slash normalization avoiding `//rest/...`; encoded path segments preserved; query/auth construction preserving endpoint path.

## Authenticated Ping Protocol / Query-Parameter Rationale (WU3)

Baseline authenticated ping uses the Subsonic/OpenSubsonic query-parameter authentication mechanism (`u`, `t`, `s`, `v`, `c`, `f`). This is the protocol-compatible mechanism required before extension discovery: the first authenticated ping occurs before the client knows which OpenSubsonic extensions the server advertises, so requiring the optional `formPost` extension would break compatible servers that do not advertise or support it.

Security properties:
- HTTPS endpoints only; HTTP endpoints SHALL be rejected by `ServerEndpoint` policy (release) or limited to permitted local hosts (debug).
- Redirects disabled: `followRedirects(false)` (and `followSslRedirects(false)` if applicable).
- No logging interceptor; application code never logs full authenticated request URLs.
- `AuthSignature.toString` remains redacted (`salt=***`, `token=***`).
- Fresh cryptographically random salt on every request; no persistence of token or salt.
- `token = md5(password + salt)` UTF-8 lowercase hex, computed per-request.

If future server capability discovery confirms `formPost` support, switching later authenticated calls away from query parameters MAY be considered separately. The authenticated ping itself SHALL remain query-parameter based.

## Non-Success HTTP Response Rejection (WU3)

Only HTTP 2xx responses are eligible for OpenSubsonic JSON parsing. A non-2xx response with an otherwise perfectly valid `status: ok` / `openSubsonic: true` body MUST NOT yield `Authenticated`.

Approved mapping for this scope:
- Non-2xx HTTP response → `AuthProtocolError` BEFORE body interpretation.
- HTTP 3xx → `AuthProtocolError` (redirects are disabled; see Redirect Policy).
- Timeout / `IOException` → `NetworkError`.

Deterministic cases SHALL cover 400/401/404/500/502/503 responses that carry a valid success envelope and assert `AuthProtocolError`, not `Authenticated`.

## OpenSubsonic Error Envelope Rationale

Per the official OpenSubsonic schema, the `error` object inside a `subsonic-response` envelope requires `error.code` (integer) and makes `error.message` optional ("The optional error message" / "A human readable error message"). Therefore a failed response that carries only a valid integer `error.code` — for example `{"status":"failed","version":"1.16.1","error":{"code":40}}` — is protocol-valid. The parser maps code 40 to `InvalidCredentials` without requiring `error.message` to be present. Code 10 ("Required parameter is missing.") is a protocol-level failure and MUST map to `AuthProtocolError`; it MUST NEVER produce `Authenticated`.

## Stale In-Flight Auth vs Profile Change (WU4)

Each authentication attempt captures the current `ServerProfile` generation/revision when it begins. When `ServerProfile` is saved, replaced, or deleted: (1) the active authentication job is cancelled AND (2) the profile/auth generation is incremented or otherwise invalidates prior attempts.

Cancellation is NOT the sole guarantee; the generation/revision check is the backstop. The final generation/profile validation MUST be atomic with each security-relevant commit of authentication state (persist credentials, expose `ServerAccountIdentity`, publish `AUTHENTICATED`). This atomicity is achieved with a SHORT shared critical section / orchestration mutex covering the sequence: (a) check captured profile generation, (b) check captured endpoint/profile still matches the current profile, (c) commit the state transition. The network request MUST remain OUTSIDE this mutex; never hold the mutex while waiting for the network ping or any long unrelated I/O. Profile save/delete uses the SAME coordination boundary when invalidating auth.

Before persisting credentials, exposing `ServerAccountIdentity`, or publishing `AUTHENTICATED`, the attempt MUST verify that the current profile generation matches the captured generation and that the current endpoint/profile still matches the attempt target.

If the attempt is stale: do not publish `AUTHENTICATED`, do not expose identity, do not allow stale credentials to become durable/current; the current/new profile wins; fail closed.

Deterministic planned tests SHALL cover profile change: (A) immediately after ping completion; (B) immediately after the final pre-persist check; (C) while persistence is suspended; (D) immediately after the final pre-publish check; (E) stale identity cannot become visible; (F) stale credentials cannot become the current durable snapshot — using `CompletableDeferred`, controlled fakes, latches, and `kotlinx-coroutines-test`, with no `Thread.sleep` or timing races. In all cases the stale attempt loses and the current profile wins.

## Fail-Closed Sign-Out (WU4)

A user MUST NOT be told sign-out succeeded if a recoverable durable credential still exists for the active profile. Successful sign-out requires EITHER (A) secret store `clear()` succeeds, OR (B) an explicitly-designed durable cryptographic invalidation mechanism succeeds such that restoration cannot recover the credential.

For the current scope, prefer option (A): `clear()` MUST succeed before sign-out is committed as successful. If `clear()` fails:
- Do NOT report successful sign-out.
- Do NOT silently transition to a state that can restore as authenticated later.
- Surface a non-secret error/retry state.
- Keep fail-closed semantics.
- Never include secret material in errors or logs.

Planned deterministic tests SHALL cover: successful `clear()` leads to signed-out state; `clear()` failure means sign-out is NOT reported successful; subsequent restoration cannot be incorrectly treated as a successful prior logout; cancellation propagates correctly; retry can eventually complete logout.

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
