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

## WU1 — Service-backed playback vertical slice

- [ ] 1.1 Add the Media3 session dependency matching the existing pin.
- [ ] 1.2 Add RED tests for new framework-independent authorization,
      reconciliation and playback-state behavior.
- [ ] 1.3 Extract only the construction seams needed for service-owned
      authenticated playback.
- [ ] 1.4 Create one service-owned ExoPlayer.
- [ ] 1.5 Create one MediaLibrarySession around that same player.
- [ ] 1.6 Implement per-controller command grants: own-app-only authenticated
      commands, supported notification/system transport controls, and no
      external media-item mutation.
- [ ] 1.7 Preserve exact-account secret lookup, fresh signing and
      redirect-disabled media transfer.
- [ ] 1.8 Publish only safe title/artist/opaque-id Media3 metadata.
- [ ] 1.9 Add the controller-backed application PlaybackEngine/client.
- [ ] 1.10 Reconnect/reconstruct safe active playback state when the UI returns.
- [ ] 1.11 Reconcile sign-out/account changes with service-owned playback.
- [ ] 1.12 Wire MainActivity to the controller client instead of constructing a
      foreground ExoPlayer.
- [ ] 1.13 Add required foreground-service permissions, exported media-service
      declaration and Media3/platform service actions.
- [ ] 1.14 Verify stop/error/account cleanup clears sensitive local media items.
- [ ] 1.15 Run focused tests plus full local quality gates.
- [ ] 1.16 Measure the complete WU1 candidate and split only if the 1000-line
      hard review budget is exceeded.

## WU2 — Android validation and closeout

- [ ] 2.1 Validate real playback continues when the Activity is backgrounded.
- [ ] 2.2 Validate returning to the app controls the same active player.
- [ ] 2.3 Validate notification play/pause controls.
- [ ] 2.4 Validate lock-screen/system media controls.
- [ ] 2.5 Validate available headset/media-button controls.
- [ ] 2.6 Validate no duplicate player/session across Activity recreation.
- [ ] 2.7 Validate sign-out/account switch stops and clears playback.
- [ ] 2.8 Validate terminal playback failure remains recoverable.
- [ ] 2.9 Verify UI, system metadata, logs and artifacts disclose no endpoint,
      account identity, credentials or signed stream URI.
- [ ] 2.10 Run Spotless, full JVM tests, lint, assemble and required Jenkins
      checks.
- [ ] 2.11 Refresh README/architecture/security status without expanding #7,
      #12 or #17.
- [ ] 2.12 Record privacy-safe device evidence and complete #15 after final
      merge.
