# Apply Progress: navidrome-account-authentication — Generation 14

**Change**: navidrome-account-authentication
**Generation**: 14
**Mode**: Strict TDD (test runner: `./gradlew testDebugUnitTest`)
**Scope**: Planning/documentation remediation only — NO production code changes
**Worktree**: `/home/merce/01_Projects/devdigi-music-android-pr48` (branch `feat/14-secure-navidrome-authentication`, HEAD `7824f8e`)

## Summary

This generation applied five fresh Codex findings (review 4997606541 on HEAD `7824f8e`) as planning/documentation updates only. No auth production Kotlin was modified, no dependencies were changed, and no new tests were added. The existing full unit-test suite (70/70) continues to pass.

Generation 13 (review 4997606540 on `0f85153`) previously applied eight planning/docs findings; that work is retained in git history and in the `Review Truthfulness` section of `tasks.md`.

## Completed Tasks

- [x] 1.27 Finding 1 (3834204845, P1): Correct exploration.md WU2 storage-model wording to the canonical layout: separate `auth_secret` Preferences DataStore persists exact username metadata + IV + ciphertext; username is non-secret binding metadata used for AAD reconstruction; password never persisted in plaintext; username stays outside ciphertext; username authenticated by AES-GCM AAD; `read(expectedEndpoint)` reads stored exact username, constructs AAD from normalized endpoint + stored username, decrypts, returns credentials only after successful GCM authentication; username remains exact/opaque/case-sensitive/Unicode-preserving/no trim/lowercase/NFC. Aligned design.md/tasks.md where stale.
- [x] 1.28 Finding 2 (3834204850, P1): Correct exploration.md WU3 integration matrix and all stale `#10` phrasing: `status = "ok"` is eligible for `Authenticated` only after metadata validation succeeds; `failed + error.code = 10` → `AuthProtocolError` (code 10 = required parameter missing; MUST NEVER produce `Authenticated`). Preserved remaining taxonomy mappings.
- [x] 1.29 Finding 3 (3834204857, P2): Correct verify-report.md and apply-progress.md to report the actual executed `./gradlew testDebugUnitTest` count as **70 executed / 70 passed (0 failures, 0 errors, 0 skipped)** derived from Gradle/JUnit XML; kept WU1-scoped requirement/scenario counts (4/4, 11/11) and `implemented_scope=WU1`, `overall_change_complete=false`.
- [x] 1.30 Finding 4 (3834204860, P2): Documented WON'T FIX — protocol compliance in design.md: official OpenSubsonic schema requires `error.code` (int) and makes `error.message` optional; a failed response with a valid envelope and integer `error.code` (e.g. `{"status":"failed","version":"1.16.1","error":{"code":40}}`) is protocol-valid and maps code 40 → `InvalidCredentials`; `error.message` is not required.
- [x] 1.31 Finding 5 (3834204864, P1): Added Phase 2 (WU2) RED/acceptance requirement for fresh IV in tasks.md: every AES-GCM encryption generates a fresh random IV; IV size = 12 bytes / 96 bits; two encryptions under the same key and same plaintext produce distinct IVs and distinct ciphertexts; IV must never be constant/reused; IV uniqueness is security-critical because AES-GCM nonce reuse is forbidden. Aligned design.md cipher contract.

## Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `openspec/changes/navidrome-account-authentication/exploration.md` | Modified | Fixed WU2 storage model to "exact username metadata + IV + ciphertext"; corrected WU3 `#10`/`ok`→`Authenticated` mis-mapping to `failed + error.code=10`→`AuthProtocolError`; updated DataStore key descriptions to include username metadata key. |
| `openspec/changes/navidrome-account-authentication/design.md` | Modified | Added fresh-random-IV contract to Endpoint Binding Contract; added OpenSubsonic Error Envelope Rationale section documenting `error.code` required/`error.message` optional and code 10 mapping. |
| `openspec/changes/navidrome-account-authentication/tasks.md` | Modified | Added Generation 14 remediation task checklist (1.27–1.31); added Phase 2 RED fresh-IV acceptance requirement (2.2b). |
| `openspec/changes/navidrome-account-authentication/verify-report.md` | Modified | Updated test execution summary to actual full count: 70 executed / 70 passed (0 failures, 0 errors, 0 skipped); retained `SubsonicResponseParserTest` focal count label. |
| `openspec/changes/navidrome-account-authentication/apply-progress.md` | Rewritten | Generation 14 apply-progress with corrected test counts and disposition of the five findings. |

## TDD Cycle Evidence

This generation modified planning/documentation only. The orchestrator explicitly prohibited production code changes and new test dependencies. The existing full unit-test suite served as the safety net.

| Task | Production Code Changed | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|------------------------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.27 Finding 1 | None | N/A | N/A | ✅ 70/70 pass | N/A (planning/docs only) | N/A | N/A | N/A |
| 1.28 Finding 2 | None | N/A | N/A | ✅ 70/70 pass | N/A (planning/docs only) | N/A | N/A | N/A |
| 1.29 Finding 3 | None | N/A | N/A | ✅ 70/70 pass | N/A (planning/docs only) | N/A | N/A | N/A |
| 1.30 Finding 4 | None | N/A | N/A | ✅ 70/70 pass | N/A (planning/docs only) | N/A | N/A | N/A |
| 1.31 Finding 5 | None | N/A | N/A | ✅ 70/70 pass | N/A (planning/docs only) | N/A | N/A | N/A |

### Test Summary

- **Total tests written**: 0 (scope prohibited new tests)
- **Total tests passing**: 70/70 (full `./gradlew testDebugUnitTest` suite)
- **Focal `SubsonicResponseParserTest` count**: 30/30
- **Layers used**: N/A
- **Approval tests**: None — no refactoring tasks
- **Pure functions created**: None — no production code changes

## Work Unit Evidence

| Evidence | Required value |
|---|---|
| Focused test command and exact result | `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL (70 executed / 70 passed; 0 failures, 0 errors, 0 skipped) |
| Runtime harness command/scenario and exact result | `./gradlew assembleDebug` → BUILD SUCCESSFUL; `./gradlew lint` → BUILD SUCCESSFUL. N/A for runtime auth harness because no production auth code was modified. |
| Rollback boundary | Revert the five modified openspec files; no production code or dependencies were touched. |

## Validation Results

| Check | Command | Result |
|-------|---------|--------|
| Unit tests | `./gradlew testDebugUnitTest` | ✅ PASS (70/70) |
| Lint | `./gradlew lint` | ✅ PASS |
| Debug build | `./gradlew assembleDebug` | ✅ PASS |
| Diff whitespace | `git diff --check` | ✅ PASS (no output) |
| Production code untouched | `git status --short` | ✅ Only `openspec/...` files modified |
| No OkHttp/MockWebServer/INTERNET | `rg` inspection | ✅ Not added |
| `compileSdk`/`targetSdk` 35 | `app/build.gradle.kts` inspection | ✅ Preserved |
| `kotlinx-serialization-json` pinned | `gradle/libs.versions.toml` inspection | ✅ 1.9.0 |
| No `org.json` reintroduced | `rg 'org\.json'` across app/openspec | ✅ Not present |

## Workload / PR Boundary

- **Mode**: Chained PRs (`stacked-to-main`)
- **Current work unit**: Generation 14 planning/docs remediation for PR A / #48
- **Boundary**: Planning artifacts only (tasks.md, design.md, exploration.md, verify-report.md, apply-progress.md)
- **Estimated review budget impact**: ~120 changed lines — well under the 200-line generation budget and the 400-line PR guard.

## Deviations from Design

None — all changes are planning/documentation clarifications that align with the canonical chained PR strategy and preserve the WU1–WU4 contracts already established.

## Issues Found

None.

## Remaining Tasks

- WU2 implementation (PR B / #49) — secure secret storage
- WU3 implementation (PR C) — authenticated network boundary
- WU4 implementation (PR D) — session + ViewModel + UI
- WU5 gated real-Navidrome validation

## Status

5/5 Generation 14 planning/docs findings complete. Ready for orchestrator review / next generation.
