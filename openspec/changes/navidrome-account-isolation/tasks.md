# Tasks: Navidrome Account Isolation

## WU0 — contract and gap audit

- [x] Confirm #16 is the next open First Sound implementation issue.
- [x] Confirm the exact `develop` base after #12 closeout.
- [x] Audit authentication ownership and session transition behavior.
- [x] Audit recent-albums and album-details account boundaries.
- [x] Audit First Sound navigation account reset behavior.
- [x] Audit playback and runtime queue ownership.
- [x] Audit durable queue account-fingerprint behavior.
- [x] Define the canonical server-plus-user isolation key.
- [x] Distinguish account isolation from multi-account management.
- [x] Preserve the existing #7 single-snapshot queue semantics.
- [x] Define synthetic privacy constraints.
- [x] Define implementation WU boundaries.

WU0 MUST NOT modify production Kotlin.

## WU1 — identity, session, library and navigation isolation

- [x] Add explicit regression coverage for the same username on different
      servers.
- [x] Confirm same-server, different-user identities remain distinct.
- [x] Confirm stale authentication cannot publish another account identity.
- [x] Confirm stale recent-albums results cannot cross an account change.
- [x] Confirm stale album-details results cannot cross an account change.
- [x] Confirm sign-out clears account-bound library presentation state.
- [x] Confirm navigation resets on exact account change and sign-out.
- [x] Make only the smallest production changes required by failing tests.
- [x] Run `./gradlew spotlessCheck`.
- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew lint`.
- [x] Run `./gradlew assembleDebug`.
- [x] Keep WU1 below 1000 changed lines.

## WU2 — playback and queue isolation

- [x] Confirm account mismatch rejects runtime queue mutation.
- [x] Confirm account switch invalidates previous runtime playback ownership.
- [x] Confirm stale stream resolution cannot become current after ownership
      changes.
- [x] Confirm a matching queue snapshot may restore without autoplay.
- [x] Confirm a different-account queue snapshot cannot restore or play.
- [x] Confirm sign-out removes runtime ownership while preserving the existing
      safe durable-snapshot policy.
- [x] Preserve `PlaybackService` as the sole runtime queue/player authority.
- [x] Make only the smallest production changes required by failing tests.
- [x] Run `./gradlew spotlessCheck`.
- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew lint`.
- [x] Run `./gradlew assembleDebug`.
- [x] Keep WU2 below 1000 changed lines.

## WU3 — reconciliation and closeout

- [x] Reconcile `docs/android-architecture.md` with implemented #16 behavior.
- [x] Reconcile `docs/byon-security.md` with implemented #16 behavior.
- [x] Reconcile this OpenSpec change against the final implementation.
- [x] Verify no real #17 private-server evidence is committed into #16.
- [x] Verify no #34 Android instrumented CI scope entered #16.
- [x] Verify no #104 reusable QA/BDD infrastructure scope entered #16.
- [x] Verify no Search, Discover, recommendations, queue editor, remote
      playback or external-provider scope entered #16.
- [x] Run validation appropriate to the changed files.
- [x] Keep WU3 below 1000 changed lines.

## Follow-up

After #16 is complete, #17 performs the real user-provided Navidrome
First Sound vertical-slice validation.
