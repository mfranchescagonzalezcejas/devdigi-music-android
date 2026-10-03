# Tasks: First Sound Navigation Shell

## WU0 — Exploration and design

- [x] 0.1 Audit issue #12 and current `develop` read-only.
- [x] 0.2 Confirm `MainActivity` remains the composition root.
- [x] 0.3 Confirm current library routing and account-scoped ViewModels.
- [x] 0.4 Confirm PlaybackService remains player/queue authority.
- [x] 0.5 Confirm no Navigation Compose dependency is currently required.
- [x] 0.6 Define Home, Library, Search and Discover primary destinations.
- [x] 0.7 Define Album Details and Now Playing as secondary surfaces.
- [x] 0.8 Define mini-player ownership and safe playback metadata boundary.
- [x] 0.9 Define account reset, Back, `This device`, privacy and responsive rules.
- [x] 0.10 Keep #17, #34, #104 and external-provider work outside #12.
- [x] 0.11 Define WU1-WU4 implementation/validation boundaries.
- [x] 0.12 Apply the 1000 changed-line hard review budget to every WU.

## WU1 — Navigation foundation

- [x] 1.1 Add focused RED tests for primary/secondary navigation policy.
- [x] 1.2 Add the First Sound shell without introducing a second composition root.
- [x] 1.3 Expose Home, Library, Search and Discover.
- [x] 1.4 Reuse recent-albums and album-details state for Home/Library.
- [x] 1.5 Preserve album origin so Back returns to Home or Library correctly.
- [x] 1.6 Implement explicit Search and Discover placeholders.
- [x] 1.7 Keep playback/queue ownership unchanged.
- [x] 1.8 Run focused tests and applicable full local quality gates.
- [x] 1.9 Measure tracked + untracked changed lines and split before commit if
      over 1000.

## WU2 — Persistent mini-player and Now Playing

- [ ] 2.1 Add RED tests for mini-player visibility/action policy.
- [ ] 2.2 Render one persistent mini-player from existing `PlaybackState`.
- [ ] 2.3 Wire safe title/artist and play/pause/resume behavior.
- [ ] 2.4 Add a secondary Now Playing surface.
- [ ] 2.5 Wire existing Previous/Next/Pause/Resume/Stop/Retry commands.
- [ ] 2.6 Avoid duplicate player or queue state.
- [ ] 2.7 Avoid guessing an album route when restored playback has no album id.
- [ ] 2.8 Reconcile redundant Album Details playback controls if necessary.
- [ ] 2.9 Run focused tests and full local quality gates.
- [ ] 2.10 Measure tracked + untracked changed lines and split before commit if
      over 1000.

## WU3 — Account, target and UX hardening

- [ ] 3.1 Add RED tests for account-change navigation reset behavior.
- [ ] 3.2 Reset account-specific navigation state on exact identity change.
- [ ] 3.3 Add deterministic secondary-surface Back behavior.
- [ ] 3.4 Present `This device` as a read-only local target.
- [ ] 3.5 Add required accessibility semantics/labels.
- [ ] 3.6 Add width-responsive primary navigation behavior.
- [ ] 3.7 Verify placeholders remain honest and non-interactive.
- [ ] 3.8 Verify no endpoint/username/auth/signed URI enters shell state.
- [ ] 3.9 Run focused tests and full local quality gates.
- [ ] 3.10 Measure tracked + untracked changed lines and split before commit if
      over 1000.

## WU4 — Android validation and closeout

- [ ] 4.1 Validate Home, Library, Search and Discover navigation.
- [ ] 4.2 Validate recent albums -> album details -> Back from Home and Library.
- [ ] 4.3 Validate persistent mini-player across destinations.
- [ ] 4.4 Validate Now Playing and playback controls.
- [ ] 4.5 Validate Activity recreation and background/foreground continuity.
- [ ] 4.6 Validate queue continuity and notification/lock-screen controls.
- [ ] 4.7 Validate sign-out/account-switch isolation.
- [ ] 4.8 Validate `This device` does not imply remote playback.
- [ ] 4.9 Validate privacy using structurally scoped evidence.
- [ ] 4.10 Record PASS / FAIL / BLOCKED without private deployment data.
- [ ] 4.11 Run Spotless, full JVM tests, lint and assembleDebug.
- [ ] 4.12 Require the applicable Jenkins statuses for the exact PR head.
- [ ] 4.13 Refresh architecture/product documentation after behavior exists.
- [ ] 4.14 Close issue #12 only after final merged behavior is reconciled.
