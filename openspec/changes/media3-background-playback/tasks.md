# Tasks: Media3 Background Playback

## WU0 — Planning and dependency exploration

- [x] 0.1 Confirm #15 scope against completed issue #1 playback.
- [x] 0.2 Confirm queue behavior remains #7 scope.
- [x] 0.3 Confirm navigation/mini-player behavior remains #12 scope.
- [x] 0.4 Confirm existing pinned Media3 1.11.1 baseline.
- [x] 0.5 Select `MediaLibraryService` + `MediaLibrarySession` as the
      service/session boundary.
- [x] 0.6 Select a Media3 `MediaController` as the application playback client.
- [x] 0.7 Define service-only authenticated stream resolution and safe
      now-playing metadata.
- [x] 0.8 Define own-app-only account-bearing session commands.
- [x] 0.9 Define account reconciliation for Activity recreation/return.
- [x] 0.10 Define Android foreground-service manifest requirements.
- [x] 0.11 Define one-player lifecycle and release ownership.
- [x] 0.12 Define large cohesive WUs with the 1000 changed-line hard review
      budget as the split gate.

## WU1A — Service/session backend

- [x] 1A.1 Add the Media3 session dependency matching the existing pin.
- [x] 1A.2 Add RED tests for own-app controller authorization and
      account-reconciliation cleanup policy.
- [x] 1A.3 Create one service-owned ExoPlayer.
- [x] 1A.4 Create one MediaLibrarySession around that same player.
- [x] 1A.5 Implement per-controller command grants: own-app-only authenticated
      commands, supported notification/system transport controls, and no
      external media-item mutation.
- [x] 1A.6 Preserve exact-account secret lookup, fresh signing and
      redirect-disabled media transfer inside the service.
- [x] 1A.7 Publish only safe title/artist/opaque-id Media3 metadata.
- [x] 1A.8 Clear the sensitive local media item on stop, terminal failure and
      account mismatch.
- [x] 1A.9 Add foreground-service permissions, exported media-service
      declaration and Media3/platform service actions.
- [x] 1A.10 Run focused tests, Spotless, full JVM tests, lint and assemble.
- [x] 1A.11 Split the measured 1552-line WU1 candidate at the service/client
      boundary because it exceeded the 1000 changed-line hard review budget.

## WU1B — Controller/UI migration and reconnect

- [x] 1B.1 Add the controller-backed PlaybackEngine client.
- [x] 1B.2 Extend the PlaybackEngine account-reconciliation contract without
      transferring service-owned player lifetime to the ViewModel.
- [x] 1B.3 Reconnect and reconstruct safe active playback state when the UI
      returns with the matching authenticated account.
- [x] 1B.4 Reconcile sign-out and account changes with service-owned playback.
- [x] 1B.5 Wire MainActivity to the controller client instead of constructing a
      foreground ExoPlayer.
- [x] 1B.6 Preserve stale-event rejection and safe retry/play/pause/stop
      presentation behavior.
- [x] 1B.7 Add focused ViewModel/controller reconciliation coverage.
- [x] 1B.8 Run focused tests plus full local quality gates.
- [x] 1B.9 Measure the complete WU1B candidate against the 1000 changed-line
      hard review budget.

## WU2 — Android validation and closeout

- [x] 2.1 Validate real playback continues when the Activity is backgrounded.
- [x] 2.2 Validate returning to the app controls the same active player.
- [x] 2.3 Validate notification play/pause controls.
- [x] 2.4 Validate lock-screen/system media controls.
- [x] 2.5 Validate available headset/media-button controls.
- [x] 2.6 Validate no duplicate player/session behavior across Activity
      recreation.
- [x] 2.7 Validate sign-out/account switch stops and clears stale playback.
- [x] 2.8 Validate terminal playback failure remains recoverable.
- [x] 2.9 Verify playback/system media surfaces, application playback logs and
      public evidence disclose no endpoint, account identity, credentials or
      signed stream URI.
- [ ] 2.10 Run required Jenkins pull-request checks after the closeout PR
      exists.
- [x] 2.11 Refresh README/architecture/security status without expanding #7,
      #12 or #17.
- [x] 2.12 Record privacy-safe device evidence and prepare #15 final closeout.
