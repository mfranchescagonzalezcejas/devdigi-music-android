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
- [ ] 1.12 Bound required track id/title fields on save and restore in WU1C.

## WU2 — Service/session Media3 queue backend

- [ ] 2.1 Add RED tests for queue-session authorization and account ownership.
- [ ] 2.2 Add own-app replace/append/remove/clear session commands.
- [ ] 2.3 Make `PlaybackService` own the runtime domain queue.
- [ ] 2.4 Translate queue entries to service-local Media3 items with transient
      signed stream URIs.
- [ ] 2.5 Preserve safe public Media3 metadata.
- [ ] 2.6 Enable trusted next/previous without MediaItem injection rights.
- [ ] 2.7 Synchronize Media3 transitions to current index and durable state.
- [ ] 2.8 Restore matching persisted queue without autoplay.
- [ ] 2.9 Clear runtime queue on sign-out/account mismatch.
- [ ] 2.10 Preserve recoverable failure and service lifecycle behavior.
- [ ] 2.11 Run focused tests and full local quality gates.
- [ ] 2.12 Measure WU2 against the 1000 changed-line hard review budget.

## WU3 — Controller, ViewModel and album-selection wiring

- [ ] 3.1 Extend the application playback client with queue operations.
- [ ] 3.2 Preserve #15 reconnect and stale-event rejection.
- [ ] 3.3 Replace queue with ordered album tracks on track selection.
- [ ] 3.4 Start playback at the selected album index.
- [ ] 3.5 Add app next/previous commands required before #12.
- [ ] 3.6 Keep queue-editor UI out of scope.
- [ ] 3.7 Add focused wiring tests.
- [ ] 3.8 Run full local quality gates.
- [ ] 3.9 Measure WU3 against the 1000 changed-line hard review budget.

## WU4 — Android validation and closeout

- [ ] 4.1 Validate ordered album queue playback.
- [ ] 4.2 Validate app next/previous boundaries.
- [ ] 4.3 Validate notification/lock-screen next/previous.
- [ ] 4.4 Validate queue continuity through Activity recreation/backgrounding.
- [ ] 4.5 Validate process restoration without autoplay.
- [ ] 4.6 Validate account-switch/sign-out isolation.
- [ ] 4.7 Verify persistence/system surfaces expose no endpoint, account
      identity, credentials or signed stream URI.
- [ ] 4.8 Run Spotless, full JVM tests, lint, assemble and Jenkins.
- [ ] 4.9 Refresh docs without expanding #12 or #17.
- [ ] 4.10 Record privacy-safe device evidence and close #7 after final merge.
