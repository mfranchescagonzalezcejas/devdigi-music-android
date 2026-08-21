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

- [x] 1.1 Add `org.json:json` (testImplementation) to `libs.versions.toml` + `build.gradle.kts` for WU1; OkHttp 5.4.0 + MockWebServer deferred to WU3 (boundary decision)
- [x] 1.2 Create `SubsonicAuthSignerTest.kt` — Subsonic vector (sesame+c19b2d->26719a...); salt >=6 hex
- [x] 1.3 Create `AuthCredentialsTest.kt` — toString redacts password
- [x] 1.4 Create `ReduceAuthResultTest.kt` — all AuthResult variants map to ConnectionFacts
- [x] 1.5 Create `ServerAccountIdentityTest.kt` — identity stable across metadata changes
- [x] 1.6 Create `SubsonicResponseParserTest.kt` — JSON `subsonic-response` → AuthResult mapping; validates org.json:json JVM test dep (fails "Method not mocked" without it)

### GREEN

- [x] 1.7 Add to `ServerConnection.kt`: ServerAccountIdentity, ServerMetadata, AuthCredentials, AuthResult, AuthenticatedPingClient, reduceAuthResult, SubsonicResponseParser
- [x] 1.8 Create `SubsonicAuthSigner.kt` — interface+AuthSignature+impl: md5(password+salt) lowercase hex, SecureRandom salt >=6 hex

### Verify

- [x] 1.9 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.*Auth*"`
- [x] 1.10 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.ReduceAuthResultTest"`
- [x] 1.11 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.ServerAccountIdentityTest"`
- [x] 1.12 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.SubsonicResponseParserTest"`

## Phase 2: Secure Secret Storage (WU2)

### RED

- [ ] 2.1 Create `AuthSecretCipherTest.kt` — round-trip; wrong key->exception; tampered->fail
- [ ] 2.2 Create `AuthSecretStoreTest.kt` — round-trip; empty->null; clear; failure->no durable state

### GREEN

- [ ] 2.3 Create `AuthKeyProvider.kt` — interface + AndroidKeystore impl (AES/GCM)
- [ ] 2.4 Create `AuthSecretCipher.kt` — interface + EncryptedSecret + Android impl
- [ ] 2.5 Create `AuthSecretStore.kt` — interface + DataStore impl, separate auth_secret
- [ ] 2.6 Create `res/xml/backup_rules.xml` — exclude auth_secret.preferences_pb
- [ ] 2.7 Create `res/xml/data_extraction_rules.xml` — exclude auth_secret.preferences_pb
- [ ] 2.8 Update `AndroidManifest.xml` — fullBackupContent, dataExtractionRules, disableIfNoEncryption

### Verify

- [ ] 2.9 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.AuthSecretCipherTest"`
- [ ] 2.10 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.AuthSecretStoreTest"`

## Phase 3: Authenticated Network Boundary (WU3)

### RED

- [ ] 3.1 Create `OkHttpAuthenticatedPingClientTest.kt` — MockWebServer: success->Authenticated; #40->InvalidCredentials; #41/#42->Unsupported; #43->AuthProtocolError; #20/#30->IncompatibleServer; malformed/protocol-invalid response->AuthProtocolError; timeout/IOException->NetworkError; #44 unmapped

### GREEN

- [ ] 3.2 Create `OkHttpAuthenticatedPingClient.kt` — OkHttp impl, SubsonicAuthSigner, org.json parsing
- [ ] 3.3 OkHttp client factory — no logging-interceptor

### Verify

- [ ] 3.4 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.OkHttpAuthenticatedPingClientTest"`

## Phase 4: Session + ViewModel + UI (WU4)

### RED

- [ ] 4.1 Create `SessionRestorerTest.kt` — valid->success; missing profile/secret/ping->fail closed
- [ ] 4.2 Update `ServerConnectionViewModelTest.kt` — sign-in Restoring->AUTHENTICATED; failure->no durable; sign-out clears auth; restore re-authenticates

### GREEN

- [ ] 4.3 Create `SessionRestorer.kt` — read profile + recover secret + fresh ping; fail-closed
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
