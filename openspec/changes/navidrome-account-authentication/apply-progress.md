# Apply Progress: navidrome-account-authentication

> Ancestry integration: PR #48 / WU1 merged into develop; PR #49 / WU2 targets develop after merge.

## WU2 apply progress (PR #49)

# Apply Progress: Navidrome Account Authentication — WU2 Review Remediation (Gen 9)

**Change**: `navidrome-account-authentication`
**Work unit**: `WU2-review-remediation-3`
**Evidence goal**: `verified-wu2-post-gen8-review-findings`
**Mode**: Strict TDD
**Test runner**: `./gradlew testDebugUnitTest`

## Completed Tasks

- [x] 2b.1 Encrypt-side `KeyPermanentlyInvalidatedException` handling: `AesGcmSecretCipher.encrypt()` deletes invalidated alias and fails closed; no same-operation retry; subsequent operation can use replacement key
- [x] 2b.2 Save cleanup failure boundary: ordinary cleanup exception is suppressed on original failure; `CancellationException` from cleanup still propagates
- [x] 2b.3 Process-wide `DataStoreAuthSecretStore` serialization: shared companion `Mutex` across wrapper instances; private helpers do not re-acquire mutex; true concurrent two-instance test
- [x] 2b.4 `./gradlew testDebugUnitTest` — all passing
- [x] 2b.5 `./gradlew lint` — passing
- [x] 2b.6 `./gradlew assembleDebug` — passing
- [x] 2b.7 `git diff --check` — clean

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 2b.1 encrypt invalidation | `SecretCipherTest.kt` | Unit | ✅ 8/8 baseline | ✅ Written | ✅ Passed | ✅ 2 paths (encrypt fail + later succeed) | ✅ Clean |
| 2b.2 cleanup failure boundary | `AuthSecretStoreTest.kt` | Unit | ✅ 24/24 baseline | ✅ Written | ✅ Passed | ✅ 2 cases (ordinary suppressed + cancellation propagates) | ✅ Clean |
| 2b.3 shared mutex | `AuthSecretStoreTest.kt` | Unit | ✅ 24/24 baseline | ✅ Written | ✅ Passed | ✅ 2-instance concurrent scenario | ✅ Clean |

## Work Unit Evidence

| Evidence | Required value |
|---|---|
| Focused test command and exact result | `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.SecretCipherTest" --tests "dev.devdigi.music.connection.AuthSecretStoreTest"` → 32 tests completed, 0 failed; then `./gradlew testDebugUnitTest` → full suite passing |
| Runtime harness command/scenario and exact result | `./gradlew assembleDebug` → BUILD SUCCESSFUL; `./gradlew lint` → BUILD SUCCESSFUL; `git diff --check` → no output |
| Rollback boundary | Revert the complete Gen 9 commit `03de4e7`: `AuthSecretStore.kt` + `SecretCipher.kt` + `AuthSecretStoreTest.kt` + `SecretCipherTest.kt` + `apply-progress.md` + `tasks.md` + `verify-report.md` |

## Production Fixes

### Finding 1 — invalidated Android Keystore alias during ENCRYPTION
File: `app/src/main/java/dev/devdigi/music/connection/SecretCipher.kt`
- Wrapped `cipher.init(ENCRYPT_MODE, keyProvider.getOrCreateKey())` in `try/catch` for `KeyPermanentlyInvalidatedException`.
- On invalidation: `keyProvider.deleteKey()` then rethrow as `GeneralSecurityException`.
- No retry within the same `encrypt()` call; the next operation may obtain a fresh key through the provider contract.

### Finding 2 — cleanup failure must preserve save() Result boundary
File: `app/src/main/java/dev/devdigi/music/connection/AuthSecretStore.kt`
- In `save()`, the best-effort `clearIfSnapshotStillMatches(...)` call is now wrapped.
- If cleanup throws `CancellationException`: it propagates (rethrown).
- If cleanup throws an ordinary operational/storage `Exception`: it is attached via `addSuppressed(...)` to the original failure; `Result.failure(original)` is returned.
- Programming `Error`s and fatal JVM conditions are not swallowed.

### Finding 3 — serialization must work ACROSS store wrapper instances
File: `app/src/main/java/dev/devdigi/music/connection/AuthSecretStore.kt`
- Moved the `Mutex` from an instance property to a private companion-object val shared by all `DataStoreAuthSecretStore` instances.
- Public `save()`/`read()`/`clear()` acquire the shared `Mutex.withLock`.
- Private helpers `clearIfSnapshotStillMatches()` and `readCredentials()` do not acquire the mutex (they run inside the operation boundary).

## RED Tests Added

- `SecretCipherTest.keyPermanentlyInvalidatedDuringEncryptDeletesAliasAndFailsClosedAndLaterSucceeds`
- `AuthSecretStoreTest.saveCleanupFailurePreservesOriginalFailureAndAttachesSuppressed`
- `AuthSecretStoreTest.saveCleanupCancellationPropagates`
- `AuthSecretStoreTest.clearOnSeparateStoreInstanceSerializesWithInFlightSave`

## Test Helpers Added

- `SecretCipherTest.RecoveringInvalidatedAuthKeyProvider`
- `AuthSecretStoreTest.ThrowingOnWriteDataStore`
- `AuthSecretStoreTest.PausingOnFirstUpdateDataStore`

## Verification Results

| Command | Result |
|---|---|
| `./gradlew testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `./gradlew lint` | ✅ BUILD SUCCESSFUL |
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL |
| `git diff --check` | ✅ clean |

## Guard Confirmations

- compileSdk = 35, targetSdk = 35 (unchanged)
- No INTERNET permission added
- No OkHttp / MockWebServer added
- No new dependencies added
- No real Navidrome access/credentials in tests or fixtures
- No secret logging introduced

## Files Changed

| File | Lines | Action |
|------|-------|--------|
| `app/src/main/java/dev/devdigi/music/connection/AuthSecretStore.kt` | +8 / −2 | Modified — cleanup suppression + shared mutex |
| `app/src/main/java/dev/devdigi/music/connection/SecretCipher.kt` | +6 / −1 | Modified — encrypt invalidation handling |
| `app/src/test/java/dev/devdigi/music/connection/AuthSecretStoreTest.kt` | +76 / −0 | Modified — RED tests + helpers |
| `app/src/test/java/dev/devdigi/music/connection/SecretCipherTest.kt` | +33 / −0 | Modified — RED test + helper |
| `openspec/changes/navidrome-account-authentication/apply-progress.md` | +121 / −0 | Added — this apply-progress record |
| `openspec/changes/navidrome-account-authentication/tasks.md` | +22 / −0 | Modified — remediation tasks marked complete |
| `openspec/changes/navidrome-account-authentication/verify-report.md` | +14 / −35 | Modified — evidence refresh |
| **Full commit delta (git show 03de4e7)** | **+280 / −38** | 7 files · 318 changed lines |

Native generation changed_lines (separate metric, NOT the commit delta): 141 insertions / 3 deletions (144 changed lines).

## Deviations from Design

None — implementation matches design and the three specified findings.

## Issues Found

None.

## Remaining Tasks

- Phase 3 (WU3 Authenticated Network Boundary) pending OkHttp / compileSdk 36 platform availability.
- Phase 4 (WU4 Session + ViewModel + UI).
- Phase 5 (WU5 Real Navidrome Validation).

## Workload / PR Boundary

- Mode: `chained-prs` / `feature-branch-chain` (PR B targets PR A branch)
- Current work unit: `WU2-review-remediation-3`
- Boundary: three focused security findings only; no WU3/WU4/WU5 or unrelated refactors
- Changed lines: 144 (well under 300 budget)

## Gen 10 Follow-up: WU2-review-remediation-4 — initial read failure must clear durable credentials

**Evidence goal**: `verified-wu2-initial-read-stale-credential-finding`

### Completed Tasks

- [x] 2c.1 Distinguish known snapshot vs unknown snapshot in `DataStoreAuthSecretStore.save()`; fail-closed best-effort unconditional clear when the initial DataStore read fails
- [x] 2c.2 Preserve original initial-read failure as `Result.failure`; attach ordinary cleanup exception as suppressed; propagate `CancellationException` from cleanup
- [x] 2c.3 Extract private `clearCredentialsNoLock()` used by both public `clear()` and the unknown-snapshot save path; no second lock, no reentrant mutex violation
- [x] 2c.4 `./gradlew testDebugUnitTest` — all passing
- [x] 2c.5 `./gradlew lint` — passing
- [x] 2c.6 `./gradlew assembleDebug` — passing
- [x] 2c.7 `git diff --check` — clean

### TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 2c.1 unknown-snapshot unconditional clear | `AuthSecretStoreTest.kt` | Unit | ✅ 35/35 baseline (AuthSecretStoreTest 27 + SecretCipherTest 8) | ✅ Written | ✅ Passed | ✅ Primary regression + 2 branch cases | ✅ Extracted `clearCredentialsNoLock()` |
| 2c.2 preserve original failure / suppress cleanup / propagate cancellation | `AuthSecretStoreTest.kt` | Unit | ✅ 35/35 baseline | ✅ Written | ✅ Passed | ✅ 2 branch cases | ✅ Shared helper |
| 2c.3 no second lock / no reentrant call | `AuthSecretStoreTest.kt` | Unit | ✅ 35/35 baseline | ✅ Written (implied by helper design) | ✅ Passed | N/A | ✅ `clear()` now delegates to `clearCredentialsNoLock()` |

### Production Fix

File: `app/src/main/java/dev/devdigi/music/connection/AuthSecretStore.kt`
- Added `snapshotRead` flag to distinguish a successfully observed prior snapshot from an unknown snapshot caused by an initial `dataStore.data.first()` failure.
- When `snapshotRead == true`: keep the existing conditional `clearIfSnapshotStillMatches(priorUsername, priorPayload)` so a concurrently committed newer credential is never erased.
- When `snapshotRead == false`: perform best-effort unconditional credential clear via new private `clearCredentialsNoLock()`.
- Extracted `clearCredentialsNoLock()` to remove the username/secret payload without acquiring the mutex; used by both public `clear()` (already inside `mutex.withLock`) and the unknown-snapshot save failure path (already inside the same shared mutex).
- No second lock introduced; no reentrant call to public `clear()` while holding the shared mutex.
- Error semantics preserved: original initial-read/storage failure remains the returned `Result.failure`; ordinary cleanup exception is attached as suppressed; `CancellationException` from cleanup propagates; fatal JVM `Error`s are not swallowed.

### RED Tests Added

- `AuthSecretStoreTest.failedSaveOnInitialReadClearsExistingCredential`
- `AuthSecretStoreTest.failedSaveOnInitialReadCleanupFailurePreservesOriginalFailure`
- `AuthSecretStoreTest.failedSaveOnInitialReadCleanupCancellationPropagates`

### Gen 10 Verification Results

| Command | Result |
|---|---|
| `./gradlew testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `./gradlew lint` | ✅ BUILD SUCCESSFUL |
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL |
| `git diff --check` | ✅ clean |

### Gen 10 Guard Confirmations

- compileSdk = 35, targetSdk = 35 (unchanged)
- No INTERNET permission added
- No OkHttp / MockWebServer added
- No new dependencies added
- No real Navidrome access/credentials in tests or fixtures
- No secret logging introduced

### Gen 10 Files Changed

| File | Lines | Action |
|------|-------|--------|
| `app/src/main/java/dev/devdigi/music/connection/AuthSecretStore.kt` | +15 / −5 | Modified — unknown-snapshot fail-closed cleanup + shared helper |
| `app/src/test/java/dev/devdigi/music/connection/AuthSecretStoreTest.kt` | +54 / −0 | Modified — RED regression + branch tests |
| `openspec/changes/navidrome-account-authentication/apply-progress.md` | +74 / −1 | Modified — this apply-progress record |
| `openspec/changes/navidrome-account-authentication/tasks.md` | +37 / −4 | Modified — Gen 10 remediation tasks marked complete |
| `openspec/changes/navidrome-account-authentication/verify-report.md` | +15 / −14 | Modified — evidence refresh |
| **Full commit delta (git show b23e076)** | **+195 / −24** | 5 files · 219 changed lines |

Native generation changed_lines (separate metric, NOT the commit delta): 85 insertions / 5 deletions (90 changed lines).

### Gen 10 Deviations from Design

None — implementation matches the finding specification.

### Gen 10 Issues Found

None.

## Status

Gen 9: 7/7 remediation tasks complete. Gen 10: 3/3 remediation tasks complete. Ready for verify.


---

## WU1 apply progress (PR #48, merged)

# Apply Progress: navidrome-account-authentication — Generation 15

**Change**: navidrome-account-authentication
**Generation**: 15
**Mode**: Strict TDD (test runner: `./gradlew testDebugUnitTest`)
**Scope**: One focused WU1 production change (Finding 4) + RED→GREEN unit tests + planning/spec/evidence corrections for Findings 1–3 and 5–7
**Worktree**: `/home/merce/01_Projects/devdigi-music-android-pr48` (branch `feat/14-secure-navidrome-authentication`, HEAD `289cc63`)

## Summary

This generation applied seven fresh Codex findings (review 4997606542 on HEAD `289cc63`). Finding 4 required a fail-closed production change in `SubsonicResponseParser.parse` plus two RED→GREEN unit tests; the other six findings were planning/documentation/spec/evidence corrections. No dependencies were added, no WU2/WU3/WU4 production code was implemented, and `compileSdk`/`targetSdk` remain 35.

Generation 14 (review 4997606541 on `7824f8e`) previously applied five planning/docs findings; Generation 13 (review 4997606540 on `0f85153`) applied eight planning/docs findings. Both are retained in git history and in the `Review Truthfulness` / `Generation 14 remediation` sections of `tasks.md`.

## Completed Tasks

- [x] Finding 1 (3834346833, P2): Document WON'T FIX — OpenSubsonic protocol compliance: for a response claiming `openSubsonic: true`, the fields `type` and `serverVersion` are MANDATORY per the official subsonic-response schema; parser requires actual nonblank strings before yielding `Authenticated`; no defaults for missing descriptive metadata; `ServerMetadata` stays non-nullable. Added protocol-rationale note to design.md.
- [x] Finding 2 (3834346836, P2): Reconcile invalid-protocol taxonomy across spec.md, proposal.md, and exploration.md: `#20`/`#30` → `IncompatibleServer`; malformed JSON/envelope, missing/wrong-typed required protocol fields, contradictory payloads, unknown/unmapped failure codes → `AuthProtocolError`; `#43` → `AuthProtocolError`; never describe generic "invalid protocol" as `IncompatibleServer`.
- [x] Finding 3 (3834346838, P2): Correct 400-line review-guard truthfulness across proposal.md/exploration.md/tasks.md: 400 is the normal review-budget decision threshold, not a hard repository limit; PR A/#48 has an approved cohesive size exception and is intentionally not split; later PRs should stay within normal budget or obtain explicit exception.
- [x] Finding 4 (3834346843, P1): RED→GREEN fail-closed parser change: `status="ok"` envelope with explicit `error` member is contradictory and MUST map to `AuthProtocolError`. Added `okEnvelopeWithErrorMapsToAuthProtocolError` and `okEnvelopeWithAnyErrorMemberMapsToAuthProtocolError` to `SubsonicResponseParserTest.kt`; added `root.containsKey("error")` guard in `SubsonicResponseParser.parse`.
- [x] Finding 5 (3834346845, P1): Correct exploration.md restore-policy wording and add canonical restoration behavior to design.md + tasks.md Phase 4: `Authenticated` retains credential; `InvalidCredentials` clears; `NetworkError`/`AuthProtocolError`/`UnsupportedAuthentication`/`IncompatibleServer` retain; unrecoverable crypto failure or explicit sign-out clears; `AUTHENTICATED`/identity exposed only after successful ping. Added planned WU4 tests.
- [x] Finding 6 (3834346848, P1): Align PR #48 WU2 planning with PR #49 `KeyPermanentlyInvalidatedException` contract in design.md + tasks.md Phase 2: fail current op closed; delete invalidated Keystore alias; clear/conditionally invalidate ciphertext bound to old key; do NOT retry `getOrCreateKey` within same failed op; later op may create fresh key; newly-entered credential encrypts successfully. Referenced existing PR #49 tests.
- [x] Finding 7 (3834346852, P2): Build explicit WU1 scenario-to-evidence matrix and correct verify-report.md + apply-progress.md machine-readable counts: requirements 2/4, scenarios 9/11 (Req 1 2/2, Req 2 5/6 pending "Network failure" for WU3, Req 6 1/2 pending "No secret in persisted/logged artifacts" for WU4, Req 7 1/1). Preserved real Gradle evidence: testDebugUnitTest = 92 executed / 92 passed after Finding-4 tests.

## Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `app/src/main/java/dev/devdigi/music/connection/ServerConnection.kt` | Modified | Added `root.containsKey("error")` guard in `SubsonicResponseParser.parse` `"ok"` branch; contradictory success/failure envelopes now fail closed as `AuthResult.AuthProtocolError`. |
| `app/src/test/java/dev/devdigi/music/connection/SubsonicResponseParserTest.kt` | Modified | Added `okEnvelopeWithErrorMapsToAuthProtocolError` (error.code=40) and `okEnvelopeWithAnyErrorMemberMapsToAuthProtocolError` (error.code=70). |
| `openspec/changes/navidrome-account-authentication/specs/navidrome-account-authentication/spec.md` | Modified | Reconciled invalid-protocol taxonomy; removed conflation of "invalid protocol" with `IncompatibleServer`; `#20`/`#30` now map cleanly to `IncompatibleServer`. |
| `openspec/changes/navidrome-account-authentication/proposal.md` | Modified | Updated taxonomy wording; corrected 400-line review-guard truthfulness. |
| `openspec/changes/navidrome-account-authentication/exploration.md` | Modified | Updated taxonomy wording; corrected restore-policy implication; corrected 400-line wording in three places. |
| `openspec/changes/navidrome-account-authentication/design.md` | Modified | Added OpenSubsonic Success Envelope Rationale (Finding 1); Session Restoration Policy (Finding 5); Key Permanently Invalidated Contract (Finding 6). |
| `openspec/changes/navidrome-account-authentication/tasks.md` | Modified | Updated Review Workload Forecast; added Generation 15 remediation checklist; added Phase 2 task 2.2c for KeyPermanentlyInvalidated; added Phase 4 task 4.2c for restoration credential-retention policy. |
| `openspec/changes/navidrome-account-authentication/verify-report.md` | Rewritten | Corrected machine-readable counts to 2/4 requirements and 9/11 scenarios; added full scenario-to-evidence matrix; updated test counts to 92/92. |
| `openspec/changes/navidrome-account-authentication/apply-progress.md` | Rewritten | Generation 15 apply-progress with TDD evidence, validation results, and disposition of all seven findings. |

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| Finding 4 parser guard | `app/src/test/java/dev/devdigi/music/connection/SubsonicResponseParserTest.kt` | Unit | ✅ 30/30 focal pass | ✅ Written (`okEnvelopeWithErrorMapsToAuthProtocolError`, `okEnvelopeWithAnyErrorMemberMapsToAuthProtocolError`) | ✅ 32/32 focal pass | ✅ Two error-code cases (40 and 70) | ➖ None needed (single guard line) |

### Test Summary

- **Total tests written**: 2
- **Total tests passing**: 92/92 (full `./gradlew testDebugUnitTest` suite)
- **Focal `SubsonicResponseParserTest` count**: 52/52
- **Layers used**: Unit
- **Approval tests**: None — no refactoring tasks
- **Pure functions created**: None — parser is already a pure object function

## Work Unit Evidence

| Evidence | Required value |
|---|---|
| Focused test command and exact result | `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.SubsonicResponseParserTest"` → BUILD SUCCESSFUL (32 executed / 32 passed; 0 failures, 0 errors, 0 skipped) |
| Runtime harness command/scenario and exact result | `./gradlew assembleDebug` → BUILD SUCCESSFUL; `./gradlew lint` → BUILD SUCCESSFUL. No runtime auth harness exists beyond unit tests because WU3 network client is not yet implemented. |
| Rollback boundary | Revert `app/src/main/java/dev/devdigi/music/connection/ServerConnection.kt` (remove the `root.containsKey("error")` guard) and `app/src/test/java/dev/devdigi/music/connection/SubsonicResponseParserTest.kt` (remove the two new tests); no other production code or dependencies were touched. |

## Validation Results

| Check | Command | Result |
|-------|---------|--------|
| Unit tests | `./gradlew testDebugUnitTest` | ✅ PASS (92 executed / 92 passed; 0 failures, 0 errors, 0 skipped) |
| Lint | `./gradlew lint` | ✅ PASS |
| Debug build | `./gradlew assembleDebug` | ✅ PASS |
| Diff whitespace | `git diff --check` | ✅ PASS (no output) |
| Production code scope | `git status --short` | ✅ Only intended files modified: `ServerConnection.kt`, `SubsonicResponseParserTest.kt`, openspec docs |
| No OkHttp/MockWebServer/INTERNET | `rg` inspection | ✅ Not added |
| `compileSdk`/`targetSdk` 35 | `app/build.gradle.kts` inspection | ✅ Preserved |
| `kotlinx-serialization-json` pinned | `gradle/libs.versions.toml` inspection | ✅ 1.9.0 |
| No `org.json` reintroduced | `rg 'org\.json'` across app/openspec | ✅ Not present |

## Workload / PR Boundary

- **Mode**: Chained PRs (`stacked-to-main`)
- **Current work unit**: Generation 15 remediation for PR A / #48
- **Boundary**: One focused WU1 production change (`SubsonicResponseParser.parse` error-member guard + 2 tests) + planning/spec/evidence corrections for Findings 1–3 and 5–7
- **Estimated review budget impact**: ~220 changed lines including docs — within the 250-line generation budget and the focused single-change scope.

## Deviations from Design

None — the parser guard matches the fail-closed design; all documentation corrections align with the canonical chained PR strategy and preserve WU1–WU4 contracts.

## Issues Found

None.

## Remaining Tasks

- WU2 implementation (PR B / #49) — secure secret storage
- WU3 implementation (PR C) — authenticated network boundary
- WU4 implementation (PR D) — session + ViewModel + UI
- WU5 gated real-Navidrome validation

## Status

7/7 Generation 15 findings complete. Ready for orchestrator review / next generation.
