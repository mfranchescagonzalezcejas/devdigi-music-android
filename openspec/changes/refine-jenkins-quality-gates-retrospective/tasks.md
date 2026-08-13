# Tasks: Refine Jenkins Quality Gates Retrospective

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 0 product lines; SDD artifact only (~50 lines) |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single SDD artifact update |
| Delivery strategy | ask-always |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | Complete the retrospective evidence record without product edits | SDD-only | `./gradlew --no-build-cache clean testDebugUnitTest lint assembleDebug` in GREEN | Detached RED/GREEN worktrees; Navidrome source inspection only | Revert `tasks.md` and Engram task artifact |

## Phase 1: Baseline and RED Evidence

- [x] 1.1 Confirm detached RED `d618641^` resolves to `54a1cc4` and detached worktree `/home/merce/01_Projects/devdigi-music-android-worktrees/issue-33-red`.
- [x] 1.2 Run `ANDROID_HOME=$HOME/Android/Sdk ./gradlew check --dry-run --offline` in RED; record exit `0` and `:app:lint`, `:app:testDebugUnitTest`, `:app:check`.
- [x] 1.3 Record RED JUnit/lint report paths and the missing-`ANDROID_HOME` diagnostic negative control from exploration evidence.

## Phase 2: Static Scope and Source Audit

- [x] 2.1 Confirm `git show d618641` changes only `Jenkinsfile` and `docs/ci.md`; no product, CI, or history mutation is planned.
- [x] 2.2 Confirm the inspected build surface is limited to `build.gradle.kts`, `app/build.gradle.kts`, and `gradle/libs.versions.toml`, with no new quality-tool dependency.

## Phase 3: GREEN Acceptance and Publication Evidence

- [x] 3.1 In detached GREEN `/home/merce/01_Projects/devdigi-music-android-worktrees/issue-33-green` at `d618641`, run the exact command: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew --no-build-cache clean testDebugUnitTest lint assembleDebug`; require exit `0`.
- [x] 3.2 Verify GREEN JUnit XML, lint XML/HTML/SARIF, and debug APK against Jenkins archive globs; inspect `Jenkinsfile` stages, single gate commands, always-run publication, and credential-wrapped Navidrome source without executing Navidrome.
- [x] 3.3 Complete `git diff --name-only 54a1cc4d537b333aba5af69202b7b9c0961c696c d6186418f4ccb70e4dcc704199996f232abd73cb` allowlist audit and GREEN integrity checks: detached HEAD, clean tracked status before/after, and no tracked `.gradle/`, `build/`, or `app/build/` outputs.

## Phase 4: Artifact Closure

- [x] 4.1 Preserve this retrospective task artifact only; do not modify `Jenkinsfile`, `docs/ci.md`, Gradle files, product code, Git history, Jenkins, GitHub, or credentials.
