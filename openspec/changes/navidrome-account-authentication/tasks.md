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

### WU2 review remediation (gen 6 → gen 7 re-budget)
- Gen 6 measured the implementation delta at **428** changed lines (native). It exceeded the original **400**-line authorization by **28** lines.
- Maintainer explicitly **re-budgeted the same candidate to 500** (`re-budget = APPROVED`). The gen 7 attempt exists only to satisfy the governance gate; its delta is ~0 because its baseline IS the remediated candidate. The 428-line measurement remains gen 6's historical evidence.
- Reason: the 28-line overage belongs to the same cohesive WU2 security-remediation boundary (AAD endpoint+username binding, fail-closed recovery, cancellation handling, DataStore corruption recovery, Keystore lifecycle/concurrency). Tests were not removed and the security boundary was not split.
- Two distinct metrics: **native SDD attempt budget = 500**; **PR #49 review workload ≈ 622** (548 tracked + ~74 untracked). They are not the same number and are not made to match.
- PR exception reaffirmed: review threshold 400 · current PR workload ~622 · `size:exception: APPROVED` · additional review guard ~800.

### Gen 8 audit (reaffirmed, measured)
Final measured workload for PR #49 @ d85027e (approved audit, maintainer):

| Category | Changed lines |
|----------|---------------|
| production | 291 |
| tests | 552 |
| backup/config | 29 |
| OpenSpec/docs | 55 |
| **total** | **927** |

- `size:exception = REAFFIRMED / APPROVED`
- `split = NO`
- The ~800 figure was advisory (an orchestration estimate), NOT repository policy.

### Workload snapshots (supersede the stale 927 audit for PR #49)
- **pre-Gen8 @ d85027e** (previous approved audit): production 291 · tests 552 · backup/config 29 · OpenSpec/docs 55 · **total 927**
- **post-Gen8 @ 586227f**: GitHub approx additions 1163 · deletions 20 · **total 1183** (matches the earlier GitHub report).
- **post-Gen9 @ 03de4e7 (committed three-dot @ 98c4801...03de4e7, measured from git): additions 1407 · deletions 22 · total 1429** — the 1183 snapshot corresponded to Gen 8 HEAD; after the Gen 9 commit the committed PR workload grew to 1429.
- **post-Gen10 (final, three-dot @ 98c4801...Gen10 HEAD, measured from git):** see the recalculated value at the bottom of this section once Gen 10 is committed.
- PR #49 remains governed by the already-REAFFIRMED `size:exception = APPROVED` (`split = NO`); the ~800 and earlier figures were advisory, NOT a repository hard ceiling. A 1200 session/review guard is advisory and not a repo policy.
- Native change budgets are the SDD attempt budgets, distinct from the PR/session review-budget: Gen 9 measured **197** native changed lines (max 300); Gen 10 measured **164** native changed lines (max 200).

### Generation 10 governance re-budget (maintainer decision APPROVED)
- original native budget = **200** · observed changed_lines = **208** · maintainer re-budget = **250** · decision = **APPROVED**
- The 8-line overage was security regression coverage + OpenSpec/evidence bookkeeping within the same WU2 secure-secret-storage boundary; all validation was already GREEN. No tests/evidence were reduced.
- Execution followed the native governance reset → begin (max 250) → finish (passed, complete=true, decision_required=false), preserving the same candidate in the working tree (re-budget generation delta 0, mirroring gen 6→7).

### PR #49 workload
- **pre-Gen10 committed workload** @ `98c4801...03de4e7`: additions 1407 · deletions 22 · **total 1429**
- **projected final** after Gen 10 commit ≈ **~1593** changed lines
- `size:exception = REAFFIRMED / APPROVED` · `split = NO`
- The historical ~800 and 1200 values are advisory/review guards, NOT repository hard policies.

### Final PR three-dot workload after Gen 10 commit
Recalculated exactly from git after the Gen 10 commit (base `98c4801`, head = Gen 10 HEAD):
- additions: **see git diff --numstat 98c4801...HEAD**
- deletions: **see git diff --numstat 98c4801...HEAD**
- total changed: **see git diff --numstat 98c4801...HEAD**

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

## Phase 2b: WU2 Review Remediation — Gen 9 (WU2-review-remediation-3)

### RED → GREEN

- [x] 2b.1 Encrypt-side `KeyPermanentlyInvalidatedException` handling: `AesGcmSecretCipher.encrypt()` deletes invalidated alias and fails closed; no same-operation retry; subsequent operation can use replacement key
- [x] 2b.2 Save cleanup failure boundary: ordinary cleanup exception is suppressed on original failure; `CancellationException` from cleanup still propagates
- [x] 2b.3 Process-wide `DataStoreAuthSecretStore` serialization: shared companion `Mutex` across wrapper instances; private helpers do not re-acquire mutex; true concurrent two-instance test

### Verify

- [x] 2b.4 `./gradlew testDebugUnitTest` — all passing
- [x] 2b.5 `./gradlew lint` — passing
- [x] 2b.6 `./gradlew assembleDebug` — passing
- [x] 2b.7 `git diff --check` — clean

## Phase 2c: WU2 Review Remediation — Gen 10 (WU2-review-remediation-4)

### RED → GREEN

- [x] 2c.1 Distinguish known snapshot vs unknown snapshot in `DataStoreAuthSecretStore.save()`; fail-closed best-effort unconditional clear when the initial DataStore read fails
- [x] 2c.2 Preserve original initial-read failure as `Result.failure`; attach ordinary cleanup exception as suppressed; propagate `CancellationException` from cleanup
- [x] 2c.3 Extract private `clearCredentialsNoLock()` used by both public `clear()` and the unknown-snapshot save path; no second lock, no reentrant mutex violation

### Verify

- [x] 2c.4 `./gradlew testDebugUnitTest` — all passing
- [x] 2c.5 `./gradlew lint` — passing
- [x] 2c.6 `./gradlew assembleDebug` — passing
- [x] 2c.7 `git diff --check` — clean

## Phase 5: Real Navidrome Validation (WU5 gated)

- [ ] 5.1 Gated, separate. Injection TBD. After WU1-WU4 merged.
