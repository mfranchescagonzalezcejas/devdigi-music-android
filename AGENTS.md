# DevDigi Music Android — Repository Instructions

These are maintainable repository rules for people and developer assistants,
not an instruction to install or run a specific AI service.

## Current project and sources of truth

- Native **Kotlin + Jetpack Compose** Android application, currently one Gradle
  app module. Navidrome/OpenSubsonic is the initial, user-provided server.
- Read `README.md`, `docs/android-architecture.md`, `docs/byon-security.md`,
  `docs/ci.md` and `docs/gitflow-governance.md` before changing their domains.
- The target Screaming/Clean Architecture is **proposed** and adopted gradually;
  the current `connection` package has mixed responsibilities. Do not claim
  proposed packages, DI, databases, Media3 or HTTP behavior already exist.

## Changes and safety

- Keep one issue/specification and one reviewable scope per change. Prefer
  changes within 400 changed lines; explain any cohesive exception.
- Preserve existing contracts and behavior-focused tests, especially ongoing
  authentication work. Avoid unrelated package moves in security-sensitive PRs.
- Keep account/server isolation and BYON behavior. Do not hardcode deployment
  topology, private endpoints, accounts, credentials, salts or real user data.
- Treat logs, generated artifacts, fixtures, screenshots, OpenSpec and PR text
  as potentially public. Use placeholders; never publish secrets or personal
  machine paths. Do not introduce secret-bearing backups or unsafe logging.
- Do not edit InkScroller as part of this repository's work.

## Kotlin, Compose and verification

- Prefer clear, testable Kotlin, explicit dependency boundaries, structured
  concurrency and lifecycle-aware collection. Keep networking and crypto out
  of Compose screens; business logic belongs in appropriate non-UI boundaries.
- Follow `.editorconfig` and pinned Gradle Spotless/ktlint. `spotlessCheck`
  checks formatting without rewriting. Run `spotlessApply` **manually only**,
  inspect its entire diff and expect Ratchet to check whole changed files.
- Use focused JVM tests for changed behavior. Add network, instrumented or UI
  tests only when the change has a meaningful signal at that layer.
- Local commands: `./gradlew spotlessCheck`, `./gradlew testDebugUnitTest`,
  `./gradlew lint`, and `./gradlew assembleDebug`, as applicable.
- Jenkins is the **single primary CI**. Local Lefthook checks are opt-in,
  fail-fast helpers; they are not substitutes for the required Jenkins PR run.

## Git and collaboration

- Follow documented GitFlow: ordinary changes branch from `develop`, target
  `develop` by PR, and use **Create a merge commit** only. Never force-push,
  rewrite or directly push protected branches.
- Use Conventional Commits: e.g. `feat(auth): ...`, `fix: ...`, `docs(ci): ...`.
- Verify tests, scope, privacy, rollback and the required Jenkins PR check
  before merging. A skipped automated review is not an actual code review.
- Never install hooks, dependencies, CI tools or apply formatting on another
  developer's behalf without checking their local state and explicit intent.
