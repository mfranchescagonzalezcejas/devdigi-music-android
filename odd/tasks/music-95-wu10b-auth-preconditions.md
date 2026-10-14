# MUSIC-95 WU10B Authentication Preconditions

## Objective
Make the WU10B real-instance authentication instrumentation safely distinguish fresh, reused, equivalent, and mismatched saved server profiles without changing production code or the existing RC1 APK.

## Problem and rationale
The current test infers persisted configuration from asynchronously rendered Compose text and compares raw endpoint strings. A profile can be present before its UI appears, and semantically equivalent URLs can compare unequal. Either condition can make the test take the wrong path and replace a saved profile, potentially clearing credentials. Follow-up review also found that post-save Flow reads can still observe null, late-profile recovery edits the draft, and UI synchronization errors share the real-mismatch reporting marker.

## Scope and constraints
- Preserve the existing WU10B work on `test/95-wu10b-reused-server-evidence`.
- Prefer test-only Kotlin changes; do not modify production source or add dependencies.
- Read persisted state through `serverProfileRepository(context).profile` with a bounded fail-closed wait before any URL-field mutation; wait for matching UI state when a profile exists.
- Recheck persisted state immediately before a fresh Save action.
- Wait boundedly for a non-null saved profile after Save; never infer persistence from an initial null emission.
- Treat a profile appearing after the initial fresh-path decision as a distinct non-persisting blocked precondition; do not edit the draft or claim MUSIC-64 PASS.
- Emit the runner's different-server outcome only after a canonical endpoint mismatch is proven; UI synchronization failures use a separate failure stage.
- Compare endpoints through production `ServerEndpoint.parse()` canonical values.
- Preserve MUSIC-64 PASS/BLOCKED and reporting semantics.
- No ADB, physical device, signing, credential disclosure, or merge. Conditional commit, push, and Draft PR are authorized only after all gates pass and remote base is verified.
- Keep the complete WU under 1,000 changed lines.

## Authorized files
- `app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationTest.kt`
- `app/src/androidTest/java/dev/devdigi/music/realinstance/RealInstanceAuthenticationPreconditionTest.kt`
- `app/src/test/java/dev/devdigi/music/connection/ServerConnectionTest.kt`
- `tools/test-qa-report.py`
- `tools/rc-smoke.sh`

## Acceptance criteria
- [x] T1: A delayed persisted-profile emission is treated as an existing profile; matching UI state is awaited before proceeding.
- [x] T2: A genuinely different saved server is rejected before URL-field mutation and no Save is invoked.
- [x] T3: Endpoint equivalence uses production canonicalization (HTTPS scheme/host case, default port, trailing slash).
- [x] T4: A fresh save is allowed only when the persisted profile remains absent at the final pre-save check; MUSIC-64 PASS is emitted only after a genuine fresh save.
- [x] T5: Existing QA reporter and setup contracts, including all 13 case semantics and exact stdout marker, remain green.
- [x] T6: Required offline checks pass; AndroidTest compiles without device operations; full WU remains under 1,000 changed lines.
- [x] F1: Post-save profile read skips initial null emissions and waits boundedly for persisted matching profile/UI.
- [x] F2: A late-arriving profile blocks without Save, draft mutation, or MUSIC-64 PASS.
- [x] F3: Only confirmed canonical mismatch emits `DIFFERENT_SAVED_SERVER`; UI wait failure has distinct attribution.

## Work route and verification
- Route: delegated direct, one bounded writer; trigger: two non-trivial Kotlin test-source files plus focused regression contracts, with source reading required before edits.
- Test-first policy: add executable regression contracts, observe RED, implement GREEN, then minimal refactor.
- Checks: `git diff --check`, `bash -n tools/qa`, `bash -n tools/rc-smoke.sh`, reporter/setup Python tests, `./tools/qa sanity`, Gradle unit tests, AndroidTest compilation, Spotless, and Android lint where environment permits.
- Delivery is conditionally authorized after independent review and local validation; never merge or alter protected branches.

## Progress
- [x] RED: three new reviewer-v2 reporter contracts failed before the F1–F3 behavior changes.
- [x] GREEN: post-save Flow skips initial null; late profile is blocked without draft mutation; UI synchronization and actual endpoint mismatch have distinct stages.
- [x] Refactor: applied scoped Kotlin formatting and confirmed `spotlessCheck`.
- [x] Validation: reporter 31, setup 8, QA sanity, JVM unit tests, AndroidTest assembly, Spotless, lint, shell syntax, and `git diff --check` passed.
- **Budget**: 814 changed lines including the 162-line untracked AndroidTest class and this 54-line task document; below 1,000.
- **Review**: independent Jira evidence #10211 is READY_FOR_DRAFT_PR_ONLY. AndroidTests were compiled but not executed.
- **Base and delivery**: `origin/develop` was freshly fetched and matches expected base `eebb60b98e5c32aac5b15d454ba2d32bfbeeaf78`. Exactly the 12 approved paths are staged; no commit has been created.
- Next: complete staged review, commit, push, verify remote SHA, create Draft PR and inspect Jenkins; never merge. Physical RC1 Smoke with an updated signed AndroidTest APK remains separate authorized validation.
