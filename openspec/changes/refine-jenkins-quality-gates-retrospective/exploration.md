## Exploration: Refine Jenkins Quality Gates Retrospective

### Current State
Issue #33 is closed and was implemented by `d618641`. The RED parent (`54a1cc4`) runs `./gradlew check` in the Formatting/static analysis stage, then separately runs `testDebugUnitTest` and `lint`. Reproducible RED evidence in detached worktree `/home/merce/01_Projects/devdigi-music-android-worktrees/issue-33-red` shows `check --dry-run` contains `:app:lint` and `:app:testDebugUnitTest`; the later Jenkins stages therefore repeat those gates.

The GREEN Jenkinsfile replaces `check` with a diagnostic placeholder and retains dedicated test and lint stages plus the existing post-build report/APK publication. The root build has only Android and Kotlin Compose plugins; the single `:app` module has one generated-application-ID JUnit test. No ktlint, detekt, Spotless, Kover, JaCoCo, formatter, coverage, or custom lint configuration is present.

### Affected Areas
- `Jenkinsfile` — RED duplicate `check` gate and GREEN diagnostic replacement; report publication remains in the post block.
- `docs/ci.md` — records the quality-gate decision and deferred coverage/tooling.
- `build.gradle.kts` and `gradle/libs.versions.toml` — establish the available plugin surface: AGP 9.3.1 and Kotlin 2.2.10 only.
- `app/build.gradle.kts` — exposes Android lint and unit-test tasks for the single Android module.
- `app/src/test/java/dev/devdigi/music/BootstrapTest.kt` — only test; insufficient for actionable coverage.

### Approaches
1. **Keep dedicated Android lint and unit-test gates; retain a diagnostic quality stage** — do not invoke aggregate `check` in Jenkins.
   - Pros: removes demonstrated duplicate work; preserves visible stages, existing reports, and artifacts; no new dependency or configuration.
   - Cons: the named quality stage is informational rather than an enforcing gate.
   - Effort: Low.

2. **Adopt a formatter/static-analysis or coverage plugin now** — add ktlint, detekt, Spotless, Kover, or equivalent.
   - Pros: could add new policy checks or coverage reports.
   - Cons: no current rule set, behavior-focused tests, or coverage signal justifies maintenance and compatibility cost; overlaps Android lint and existing unit-test work.
   - Effort: Medium.

3. **Use only `check` and remove dedicated stages** — make aggregate Gradle verification the sole Jenkins quality gate.
   - Pros: one Gradle invocation.
   - Cons: loses separately visible test/lint stages requested by #33 and makes Jenkins diagnostics/report failures less explicit.
   - Effort: Low.

### Recommendation
Keep approach 1. It is the smallest evidence-backed resolution: in RED, aggregate `check` includes both dedicated gates; in GREEN, the dedicated stages run once and preserve their existing report paths. Defer ktlint, detekt, Spotless, and coverage until the project has concrete conventions or behavior-focused tests that make their output actionable.

### Risks
- The first RED commands failed until `ANDROID_HOME=/home/merce/Android/Sdk` was supplied; future local verification must declare or provision the Android SDK rather than treating an SDK-less failure as a gate result.
- Local task execution used Gradle cache (`testDebugUnitTest` reported FROM-CACHE), so the next verification phase must force a clean/non-cached or Jenkins-equivalent run before claiming final GREEN validation.
- The current test only checks generated metadata, so any coverage percentage would be misleading.

### Ready for Proposal
Yes — scope the retrospective proposal around preserving the current minimal GREEN split, documenting the reproducible RED task-graph evidence, and deferring tool adoption. The following verification phase must confirm the actual GREEN stage commands, report publication, and non-duplicated execution under a provisioned Android SDK/Jenkins-equivalent environment; it must not reuse this exploration as final validation.

### Evidence Commands
- RED baseline: `git worktree add --detach /home/merce/01_Projects/devdigi-music-android-worktrees/issue-33-red d618641^` resolved to `54a1cc4`.
- RED: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew check --dry-run --offline` exited `0` and listed `:app:lint` and `:app:testDebugUnitTest` before `:app:check`.
- RED: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew testDebugUnitTest --offline` exited `0`; JUnit XML was written under `app/build/test-results/testDebugUnitTest/`.
- RED: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew lint --offline` exited `0`; HTML, XML, TXT, and SARIF reports were written under `app/build/reports/`.
- Environment control: the same RED commands without `ANDROID_HOME` exited `1` with `SDK location not found`.
- GREEN inspection: `git show d618641` changes only `Jenkinsfile` and `docs/ci.md`; no related PRs or subsequent commits were found.
