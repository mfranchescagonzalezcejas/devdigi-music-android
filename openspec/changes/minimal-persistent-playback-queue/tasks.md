# Tasks: Minimal Persistent Playback Queue

## WU0 — Planning and dependency exploration

- [x] 0.1 Confirm #7 scope after completed service-backed playback #15.
- [x] 0.2 Keep `PlaybackService` as runtime player/queue authority.
- [x] 0.3 Keep queue-domain rules independent from Android and Media3.
- [x] 0.4 Define replace, append, next, previous, remove and clear semantics.
- [x] 0.5 Define album selection as ordered queue replacement.
- [x] 0.6 Define one durable v0.1 snapshot bound by opaque account fingerprint.
- [x] 0.7 Exclude endpoint, username, auth material and signed URLs from
      persistence.
- [x] 0.8 Preserve own-app-only account-bearing queue mutation.
- [x] 0.9 Allow safe system next/previous without MediaItem injection.
- [x] 0.10 Define restoration without autoplay.
- [x] 0.11 Keep #12 and #17 separate.
- [x] 0.12 Use the 1000 changed-line hard review gate for every implementation
      WU.

## WU1A — Pure queue domain

- [x] 1.1 Add RED tests for queue invariants and replacement.
- [x] 1.2 Add RED tests for append and non-wrapping navigation.
- [x] 1.3 Add RED tests for removal and clear.
- [x] 1.4 Implement pure Kotlin queue behavior.
- [x] 1.5A Run focused queue/domain tests and full local quality gates.
- [x] 1.6A Measure WU1A against the 1000 changed-line hard review budget.
- [x] 1.7A Split persistence into WU1B because the 582-line domain candidate
      leaves insufficient review budget for a properly tested DataStore
      boundary.

## WU1B — Account-scoped queue persistence

- [x] 1.5 Add account-fingerprint tests.
- [x] 1.6 Add dedicated queue persistence contract and DataStore adapter.
- [x] 1.7 Persist only schema, fingerprint, current index and safe track
      metadata.
- [x] 1.8 Add malformed, oversized and mismatched snapshot fail-closed tests.
- [x] 1.9 Verify persistence contains no raw identity, auth material or signed
      stream URL.
- [x] 1.10 Run focused tests and full local quality gates.
- [x] 1.11 Measure WU1B against the 1000 changed-line hard review budget.
- [x] 1.12 Bound required track id/title fields on save and restore in WU1C.

## WU2A — Queue session foundation

- [x] 2.1 Add RED tests for queue-session authorization and account ownership.
- [x] 2.2A Add account-bound replace/append/remove/clear request models and
      stable private session command definitions without exposing unhandled
      mutations.
- [x] 2.3A Add pure account-ownership policy for queue mutation.
- [x] 2.4A Grant own-app/trusted next/previous while continuing to block
      MediaItem set/change injection.
- [x] 2.5A Run focused tests and full local quality gates.
- [x] 2.6A Measure WU2A against the 1000 changed-line hard review budget.
- [x] 2.7A Split remaining service backend into WU2B runtime queue/Media3 sync
      and WU2C restore/reconcile/lifecycle before implementation.

## WU2B — Service runtime queue and Media3 synchronization

- [x] 2.1B Parse and handle own-app replace/append/remove/clear commands.
- [x] 2.2B Make `PlaybackService` own the runtime domain queue.
- [x] 2.3B Translate queue entries to service-local Media3 items using fresh
      transient signed stream URIs.
- [x] 2.4B Preserve safe public Media3 metadata.
- [x] 2.5B Synchronize Media3 transitions to current index and durable state.
- [x] 2.6B Run focused tests and full local quality gates.
- [x] 2.7B Measure WU2B against the 1000 changed-line hard review budget.

## WU2C — Restoration, reconciliation and lifecycle

- [x] 2.1C Restore a matching persisted queue without autoplay.
- [x] 2.2C Clear runtime queue ownership on sign-out or account mismatch.
- [x] 2.3C Preserve recoverable failure and service lifecycle behavior.
- [x] 2.4C Verify own-app/trusted next/previous operate on the runtime queue
      without MediaItem injection rights.
- [x] 2.5C Add focused restore, account-isolation, transition and error tests.
- [x] 2.6C Run focused tests and full local quality gates.
- [x] 2.7C Measure WU2C against the 1000 changed-line hard review budget.

## WU3 — Controller, ViewModel and album-selection wiring

- [x] 3.1 Extend the application playback client with queue operations.
- [x] 3.2 Preserve #15 reconnect and stale-event rejection.
- [x] 3.3 Replace queue with ordered album tracks on track selection.
- [x] 3.4 Start playback at the selected album index.
- [x] 3.5 Add app next/previous commands required before #12.
- [x] 3.6 Keep queue-editor UI out of scope.
- [x] 3.7 Add focused wiring tests.
- [x] 3.8 Run full local quality gates.
- [x] 3.9 Measure WU3 against the 1000 changed-line hard review budget.

## WU4 — Android validation and closeout

- [x] 4.1 Validate ordered album queue playback.
- [x] 4.2 Validate app next/previous boundaries.
- [x] 4.3 Validate notification/lock-screen next/previous.
- [x] 4.4 Validate queue continuity through Activity recreation/backgrounding.
- [x] 4.5 Validate process restoration without autoplay.
- [x] 4.6 Validate account-switch/sign-out isolation.
- [x] 4.7 Verify persistence/system surfaces expose no endpoint, account
      identity, credentials or signed stream URI.
- [ ] 4.8 Run Spotless, full JVM tests, lint, assemble and Jenkins.
- [x] 4.9 Refresh docs without expanding #12 or #17.
- [ ] 4.10 Record privacy-safe device evidence and close #7 after final merge.
