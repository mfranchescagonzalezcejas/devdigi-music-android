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
- [x] Finding 7 (3834346852, P2): Build explicit WU1 scenario-to-evidence matrix and correct verify-report.md + apply-progress.md machine-readable counts: requirements 2/4, scenarios 9/11 (Req 1 2/2, Req 2 5/6 pending "Network failure" for WU3, Req 6 1/2 pending "No secret in persisted/logged artifacts" for WU4, Req 7 1/1). Preserved real Gradle evidence: testDebugUnitTest = 81 executed / 81 passed after Finding-4 tests.

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
| `openspec/changes/navidrome-account-authentication/verify-report.md` | Rewritten | Corrected machine-readable counts to 2/4 requirements and 9/11 scenarios; added full scenario-to-evidence matrix; updated test counts to 81/81. |
| `openspec/changes/navidrome-account-authentication/apply-progress.md` | Rewritten | Generation 15 apply-progress with TDD evidence, validation results, and disposition of all seven findings. |

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| Finding 4 parser guard | `app/src/test/java/dev/devdigi/music/connection/SubsonicResponseParserTest.kt` | Unit | ✅ 30/30 focal pass | ✅ Written (`okEnvelopeWithErrorMapsToAuthProtocolError`, `okEnvelopeWithAnyErrorMemberMapsToAuthProtocolError`) | ✅ 32/32 focal pass | ✅ Two error-code cases (40 and 70) | ➖ None needed (single guard line) |

### Test Summary

- **Total tests written**: 2
- **Total tests passing**: 81/81 (full `./gradlew testDebugUnitTest` suite)
- **Focal `SubsonicResponseParserTest` count**: 41/41
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
| Unit tests | `./gradlew testDebugUnitTest` | ✅ PASS (81 executed / 81 passed; 0 failures, 0 errors, 0 skipped) |
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
