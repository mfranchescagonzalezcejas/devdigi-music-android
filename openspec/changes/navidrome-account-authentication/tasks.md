# Tasks: Navidrome Account Authentication

## Review Workload Forecast

Estimated changed lines: ~650-750. 400-line budget risk: High (soft threshold intentionally exceeded).

Hard planning ceiling: 800 — respected; forecast is within ceiling.
Delivery strategy: single-pr
size:exception: APPROVED (maintainer) — #14 is one cohesive security/authentication capability; splitting WU1+WU2 from WU3+WU4 would create artificial intermediate PR boundaries between authentication core, secure credential persistence, authenticated network behavior, and session state. Forecast remains below the 800 hard ceiling.

Decision needed before apply: No (size:exception approved)
Chained PRs recommended: No (single-pr approved)
Chain strategy: N/A
400-line budget risk: High (accepted under size:exception)

| Unit | Goal | Test command | Rollback boundary |
|------|------|--------------|-------------------|
| 1 | Auth types + signer + response parser + result/facts mapping + identity/metadata | `testDebugUnitTest --tests "*.connection.*Auth*"` | Remove new files + deps |
| 2 | Ping client + session + UI | `testDebugUnitTest --tests "*.connection.*"` | Remove OkHttp/SessionRestorer, revert VM/Screen |

## Phase 1: Domain Types + Signer (WU1)

### RED

- [x] 1.1 Add `kotlinx-serialization-json` 1.9.0 runtime dependency to `libs.versions.toml` + `implementation` in `app/build.gradle.kts`; remove `org.json`. OkHttp 5.4.0 + MockWebServer deferred to WU3 (boundary decision)
- [x] 1.2 Create `SubsonicAuthSignerTest.kt` — Subsonic vector (sesame+c19b2d->26719a...); salt >=6 hex
- [x] 1.3 Create `AuthCredentialsTest.kt` — toString redacts password
- [x] 1.4 Create `ReduceAuthResultTest.kt` — all AuthResult variants map to ConnectionFacts
- [x] 1.5 Create `ServerAccountIdentityTest.kt` — identity stable across metadata changes
- [x] 1.6 Create `SubsonicResponseParserTest.kt` — JSON `subsonic-response` → AuthResult mapping using strict `kotlinx-serialization-json`; rejects single quotes, unquoted keys, trailing commas, comments, trailing tokens

### GREEN

- [x] 1.7 Add to `ServerConnection.kt`: ServerAccountIdentity, ServerMetadata, AuthCredentials, AuthResult, AuthenticatedPingClient, reduceAuthResult, SubsonicResponseParser
- [x] 1.8 Create `SubsonicAuthSigner.kt` — interface+AuthSignature+impl: md5(password+salt) lowercase hex, SecureRandom salt >=6 hex

### Verify

- [x] 1.9 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.*Auth*"`
- [x] 1.10 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.ReduceAuthResultTest"`
- [x] 1.11 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.ServerAccountIdentityTest"`
- [x] 1.12 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.SubsonicResponseParserTest"`

### Remediation (PR #48 late review — WU1)

- [x] 1.13 RED tests for strict standard JSON: single quotes, unquoted keys, trailing commas, comments, trailing tokens all map to `AuthProtocolError`
- [x] 1.14 GREEN: rewrite `SubsonicResponseParser.parse` with `Json { isLenient = false }`, safe `JsonPrimitive` extraction, no `org.json`

### Remediation (PR #48 late review — planning/docs)

- [x] 1.15 Backup-path documentation corrected across openspec: `auth_secret.preferences_pb` → `datastore/auth_secret.preferences_pb`
- [x] 1.16 WU2 endpoint/username binding acceptance expanded in Phase 2 (AAD binds normalized endpoint + exact opaque username; no trim/lowercase/NFC)
- [x] 1.17 WU3 redirect policy + request-inspection acceptance expanded in Phase 3 (`followRedirects(false)`, no signed-param forwarding, 3xx → `AuthProtocolError`, exact query-param assertions)
- [x] 1.18 WU4 stale in-flight auth vs profile-change contract added to Phase 4 (generation/revision backstop, cancellation, no Mutex across network ping)

## Phase 2: Secure Secret Storage (WU2)

### RED

- [ ] 2.1 Create `AuthSecretCipherTest.kt` — round-trip; wrong key->exception; tampered->fail
- [ ] 2.2 Create `AuthSecretStoreTest.kt` — round-trip; empty->null; clear; failure->no durable state
- [ ] 2.2a Endpoint/username binding acceptance: secret saved for endpoint A cannot be read under endpoint B; same snapshot with changed exact username fails authentication; endpoint mismatch returns no credentials; username/AAD mismatch returns no credentials; invalid rejected snapshot is conditionally cleared; stale cleanup must not erase a newer valid replacement snapshot

### GREEN

- [ ] 2.3 Create `AuthKeyProvider.kt` — interface + AndroidKeystore impl (AES/GCM)
- [ ] 2.4 Create `AuthSecretCipher.kt` — interface + EncryptedSecret + Android impl; AAD binds normalized `ServerEndpoint.value` + exact opaque username (case-sensitive, Unicode-preserving, no trim/lowercase/NFC)
- [ ] 2.5 Create `AuthSecretStore.kt` — interface + DataStore impl, separate auth_secret
- [ ] 2.6 Create `res/xml/backup_rules.xml` — exclude `datastore/auth_secret.preferences_pb`
- [ ] 2.7 Create `res/xml/data_extraction_rules.xml` — exclude `datastore/auth_secret.preferences_pb`
- [ ] 2.8 Update `AndroidManifest.xml` — fullBackupContent, dataExtractionRules, disableIfNoEncryption

### Verify

- [ ] 2.9 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.AuthSecretCipherTest"`
- [ ] 2.10 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.AuthSecretStoreTest"`

## Phase 3: Authenticated Network Boundary (WU3)

### RED

- [ ] 3.1 Create `OkHttpAuthenticatedPingClientTest.kt` — MockWebServer: success->Authenticated; #40->InvalidCredentials; #41/#42->Unsupported; #43->AuthProtocolError; #20/#30->IncompatibleServer; malformed/protocol-invalid response->AuthProtocolError; timeout/IOException->NetworkError; #44 unmapped. Request inspection: path `/rest/ping.view`; decoded query params exactly `u`=opaque username, `t`=MD5(password+captured salt) lowercase 32 hex, `s`=fresh salt, `v`=1.13.0, `c`=devdigi-music, `f`=json; `p` absent; plaintext password absent; no duplicate auth params; username NOT trimmed/lowercased/NFC-normalized; salt satisfies format/length; successive requests use different salts. Redirect tests: cross-origin 302/307/308 — configured server receives exactly one request, redirect target receives zero, result != Authenticated, username/salt/token never reach redirect target.

### GREEN

- [ ] 3.2 Create `OkHttpAuthenticatedPingClient.kt` — OkHttp impl, SubsonicAuthSigner, strict `kotlinx-serialization-json` parsing
- [ ] 3.3 OkHttp client factory — no logging-interceptor; `followRedirects(false)`; `followSslRedirects(false)` if applicable; any 3xx rejected locally as `AuthProtocolError`

### Verify

- [ ] 3.4 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.OkHttpAuthenticatedPingClientTest"`

## Phase 4: Session + ViewModel + UI (WU4)

### RED

- [ ] 4.1 Create `SessionRestorerTest.kt` — valid->success; missing profile/secret/ping->fail closed
- [ ] 4.2 Update `ServerConnectionViewModelTest.kt` — sign-in Restoring->AUTHENTICATED; failure->no durable; sign-out clears auth; restore re-authenticates
- [ ] 4.2a Stale in-flight auth vs profile change: each attempt captures current `ServerProfile` generation/revision; profile save/replace/delete cancels the active job AND increments/invalidates prior attempts; before exposing `AUTHENTICATED`/identity, verify generation still matches and target profile still current; stale attempt loses, current profile wins. Deterministic tests for (A) during suspended ping, (B) after ping success before persistence, (C) after persistence before identity/AUTHENTICATED exposure — using CompletableDeferred/controlled fakes/coroutines-test, no Thread.sleep.

### GREEN

- [ ] 4.3 Create `SessionRestorer.kt` — read profile + recover secret + fresh ping; fail-closed; generation/revision check backstop (not Mutex across network ping)
- [ ] 4.4 Update `ServerConnectionViewModel.kt` — sign-in/sign-out/restore flows; wire deps
- [ ] 4.5 Update `ServerConnectionScreen.kt` — masked password, sign-in/out buttons, status
- [ ] 4.6 Update `MainActivity.kt` — DI wiring
- [ ] 4.7 Update `AndroidManifest.xml` — INTERNET permission

### Verify

- [ ] 4.8 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.SessionRestorerTest"`
- [ ] 4.9 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.ServerConnectionViewModelTest"`
- [ ] 4.10 `./gradlew testDebugUnitTest` + `./gradlew assembleDebug`

## Phase 5: Real Navidrome Validation (WU5 gated)

- [ ] 5.1 Gated, separate. Injection TBD. After WU1-WU4 merged.

## Review Truthfulness (PR #48 late review)

Codex reviewed exact HEAD `98c4801` and reported six fresh findings for work unit `WU1-late-review-remediation`:

- [x] Finding 1 (3833373946, P1): strict standard JSON parser — production + tests switched from `org.json` to `kotlinx-serialization-json` 1.9.0 runtime with `isLenient = false`.
- [x] Finding 2 (3833373954, P1): WU3 redirect policy added to design/tasks — no automatic redirects, signed query params must not be forwarded to another origin, 3xx → `AuthProtocolError`.
- [x] Finding 3 (3833373957, P1): WU3 request-inspection acceptance expanded in tasks — exact path, decoded query params, absence of plaintext password, fresh salts, no normalization.
- [x] Finding 4 (3833373964, P2): backup-path documentation corrected from `auth_secret.preferences_pb` to `datastore/auth_secret.preferences_pb`.
- [x] Finding 5 (3833373969, P1): WU2 endpoint/username binding acceptance expanded in tasks to match the AES-GCM AAD contract already implemented in PR #49.
- [x] Finding 6 (3833373974, P1): WU4 stale in-flight auth vs profile-change contract added to design/tasks — generation/revision check backstop, cancellation, no Mutex across network ping.
