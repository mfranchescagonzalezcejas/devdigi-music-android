# Tasks: Navidrome Account Authentication

## Review Workload Forecast

Initial forecast: ~650-750 lines; delivery was `single-pr` with `size:exception` APPROVED (maintainer), premised on staying below a then-assumed 800 hard ceiling.

### Superseded by workload audit (measured, not estimated)
Actual measured workload before WU2 GREEN: **~1244 changed lines** (WU1 commit 915 + WU2 RED ~329). The 800 figure was an orchestration/SDD estimate, NOT a repository hard policy. The real repo rule (docs/devdigi-repository-baseline-draft.md) is the **400-line review-budget decision threshold**: record `single PR`, a chain strategy, or an explicit size exception before implementation.

**Decision (supersedes single-pr): delivery_strategy = chained-prs**
- PR A: planning + WU1 auth-core — base `develop`. Reconfirms `size:exception = APPROVED` (~915 lines, mostly planning + tests).
- PR B: WU2 secure-secret-storage — base PR A branch. Evaluated individually against the 400 threshold.
- PR C: WU3 authenticated-network-boundary — base PR B branch.
- PR D: WU4 session-ui — base PR C branch.
- WU5: real-Navidrome validation gate (final).

Each subsequent PR is assessed individually against the 400 threshold; a small focused size:exception per PR is acceptable if it lands ~450-600. Tests are NOT reduced to satisfy the threshold. The OpenSpec change `navidrome-account-authentication` stays single across all chained PRs.

400-line budget risk: High (per-PR)
Chain strategy: feature-branch-chain (PR N targets PR N-1 branch)
WU3 prerequisite: OkHttp 5.4.0 → compileSdk 36; Jenkins agent currently documented with Android Platform 35 — WU3 must not start until platforms;android-36 is available locally and on the Jenkins agent.

### PR B / WU2 size:exception (APPROVED, specific — separate from PR A's historical decision)
Prospective PR-B workload ≈ **540 changed lines** (production 173, tests 313, backup/config 27, OpenSpec/tasks 27) vs the **400 review threshold**. `size:exception = APPROVED` explicitly for PR B.

Justification: WU2 is a cohesive secure-secret-storage security boundary; splitting AuthSecretStore, AES-GCM/Android Keystore integration, encrypted persistence, backup exclusion, and fail-closed behavior would create an artificial security boundary. Most of the excess comes from security/fail-closed tests, which must NOT be reduced to satisfy the review threshold.

| Unit | Goal | Test command | Rollback boundary |
|------|------|--------------|-------------------|
| 1 | Types + signer + storage | `testDebugUnitTest --tests "*.connection.*Auth*"` | Remove new files + deps |
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

- [x] 2.1 Create `AuthSecretCipherTest.kt` / `SecretCipherTest.kt` — round-trip; wrong key->exception; tampered->fail; missing key fail-closed
- [x] 2.2 Create `AuthSecretStoreTest.kt` — round-trip; empty->null; clear; failure->no durable state; plaintext never persisted; malformed->cleared; server_profile independence

### GREEN

- [x] 2.3 Create `AuthKeyProvider.kt` — interface + AndroidKeystore impl (AES/GCM, alias devdigi.music.auth.v1)
- [x] 2.4 Create `SecretCipher.kt` — interface + EncryptedSecret + Android impl (AES/GCM/NoPadding, fresh IV)
- [x] 2.5 Create `AuthSecretStore.kt` — interface + DataStore impl, separate auth_secret
- [x] 2.6 Create `res/xml/backup_rules.xml` — exclude auth_secret.preferences_pb
- [x] 2.7 Create `res/xml/data_extraction_rules.xml` — exclude auth_secret.preferences_pb (cloud-backup + device-transfer)
- [x] 2.8 Update `AndroidManifest.xml` — fullBackupContent, dataExtractionRules

### Verify

- [x] 2.9 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.SecretCipherTest"`
- [x] 2.10 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.AuthSecretStoreTest"`

## Phase 3: Authenticated Network Boundary (WU3)

### RED

- [ ] 3.1 Create `OkHttpAuthenticatedPingClientTest.kt` — MockWebServer: success->Authenticated; #40->InvalidCredentials; #41/#42->Unsupported; #43->AuthProtocolError; #20/#30->IncompatibleServer; malformed/timeout->NetworkError; #44 unmapped

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
