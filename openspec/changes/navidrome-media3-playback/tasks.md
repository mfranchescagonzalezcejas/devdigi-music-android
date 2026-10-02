# Tasks: Navidrome Media3 Playback

## WU0 — Planning and dependency exploration

- [x] 0.1 Confirm #1 scope against completed recent-album and album-details
      boundaries.
- [x] 0.2 Confirm `AlbumTrack.id` remains the opaque playback reference.
- [x] 0.3 Confirm Media3 dependency direction and current stable version.
- [x] 0.4 Define exact-account authenticated stream-resolution boundary.
- [x] 0.5 Define redirect, secret and presentation-state protections.
- [x] 0.6 Define player ownership and #15/#7 boundaries.
- [x] 0.7 Define coarse implementation WUs with review-budget splitting only
      when measured.

## WU1 — Playback core

- [x] 1.1 Add the minimum pinned Media3 ExoPlayer and OkHttp data-source
      dependencies.
- [x] 1.2 Add RED framework-independent playback state/contract tests.
- [x] 1.3 Define safe selected-track and playback-state models.
- [x] 1.4 Add RED exact-account stream-resolution tests.
- [x] 1.5 Implement secure credential lookup and fresh stream signing.
- [x] 1.6 Keep resolved stream requests internal and redacted.
- [x] 1.7 Configure Media3 data loading through redirect-disabled OkHttp.
- [x] 1.8 Implement one-track ExoPlayer play/pause/resume/stop/release.
- [x] 1.9 Map Media3 callbacks and terminal errors to safe playback outcomes.
- [x] 1.10 Run focused and complete JVM/build quality gates.
- [x] 1.11 Measure the whole WU1 candidate and split only if the hard review
      budget requires it.

## WU2 — Presentation and wiring

- [x] 2.1 Add RED playback ViewModel/state transition tests with a fake engine.
- [x] 2.2 Add account-aware playback ViewModel/controller behavior.
- [x] 2.3 Reject stale playback events after track or account changes.
- [x] 2.4 Wire album track selection into one-track playback.
- [x] 2.5 Add play, pause/resume and stop controls with safe error/retry UI.
- [x] 2.6 Stop and clear playback on sign-out/account change.
- [x] 2.7 Preserve responsive width-based Compose behavior.
- [x] 2.8 Verify normal Activity/Compose lifecycle does not duplicate players.
- [x] 2.9 Run full local quality gates and hard review-budget check.

## WU3 — Final validation

- [x] 3.1 Validate a selected synthetic or privacy-safe FLAC track reaches
      Media3 on a compatible Android target.
- [x] 3.2 Validate unsupported/terminal playback failure becomes recoverable
      UI state and never remains falsely playing.
- [x] 3.3 Validate play, pause, resume and stop behavior.
- [x] 3.4 Validate sign-out/account switch stops and clears playback.
- [x] 3.5 Verify logs, UI state and artifacts contain no signed stream request
      or account secrets.
- [ ] 3.6 Run Spotless, unit tests, lint, assemble and applicable Jenkins
      checks.
- [x] 3.7 Record privacy-safe verification evidence.
- [x] 3.8 Refresh relevant architecture/README status without expanding #15 or
      #7 scope.
- [ ] 3.9 Complete #1 after the final merge.
