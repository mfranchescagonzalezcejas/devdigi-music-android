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
| 2b.1 encrypt invalidation | `SecretCipherTest.kt` | Unit | ✅ 9/9 baseline | ✅ Written | ✅ Passed | ✅ 2 paths (encrypt fail + later succeed) | ✅ Clean |
| 2b.2 cleanup failure boundary | `AuthSecretStoreTest.kt` | Unit | ✅ 24/24 baseline | ✅ Written | ✅ Passed | ✅ 2 cases (ordinary suppressed + cancellation propagates) | ✅ Clean |
| 2b.3 shared mutex | `AuthSecretStoreTest.kt` | Unit | ✅ 24/24 baseline | ✅ Written | ✅ Passed | ✅ 2-instance concurrent scenario | ✅ Clean |

## Work Unit Evidence

| Evidence | Required value |
|---|---|
| Focused test command and exact result | `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.SecretCipherTest" --tests "dev.devdigi.music.connection.AuthSecretStoreTest"` → 32 tests completed, 0 failed; then `./gradlew testDebugUnitTest` → full suite passing |
| Runtime harness command/scenario and exact result | `./gradlew assembleDebug` → BUILD SUCCESSFUL; `./gradlew lint` → BUILD SUCCESSFUL; `git diff --check` → no output |
| Rollback boundary | Revert `AuthSecretStore.kt` + `SecretCipher.kt` + `AuthSecretStoreTest.kt` + `SecretCipherTest.kt`; no other files touched |

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
| `app/src/main/java/dev/devdigi/music/connection/SecretCipher.kt` | +7 / −1 | Modified — encrypt invalidation handling |
| `app/src/main/java/dev/devdigi/music/connection/AuthSecretStore.kt` | +10 / −2 | Modified — cleanup suppression + shared mutex |
| `app/src/test/java/dev/devdigi/music/connection/SecretCipherTest.kt` | +33 / −0 | Modified — RED test + helper |
| `app/src/test/java/dev/devdigi/music/connection/AuthSecretStoreTest.kt` | +76 / −0 | Modified — RED tests + helpers |
| `openspec/changes/navidrome-account-authentication/tasks.md` | +15 / −0 | Modified — remediation tasks marked complete |
| **Total** | **141 / −3** | 144 changed lines |

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

## Status

7/7 remediation tasks complete. Ready for verify.
