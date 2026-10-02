# WU3 — Real-device playback validation

Validation date: 2026-10-02

Source baseline: develop at 71c13ab14496bd4dc719ab98f10279edea0b7489

## Targets

- Phone target: Android 16, SDK 36.
- Tablet target: Android 16, SDK 36.
- At least one validation target is API 27 or newer, where Android platform FLAC decoding is guaranteed by the project compatibility assumptions.
- Device serials, personal server details, account identifiers and track metadata are intentionally not recorded.

## Results

- FLAC playback: PASS on phone and tablet.
- Selected track reaches the app's Media3 foreground playback path and produces audible playback: PASS.
- Playing state reflects actual playback: PASS.
- Pause: PASS.
- Resume: PASS.
- Stop: PASS.
- Terminal/network-source failure was validated by temporarily making the user-provided server unreachable while leaving ordinary device connectivity available; the UI became recoverable and did not remain falsely Playing: PASS.
- Retry after restoring server reachability: PASS.
- Real-device sign-out stops and clears playback: PASS.
- Account-change stale-state/cleanup behavior remains covered by the PlaybackViewModel JVM suite: PASS.
- Width-responsive playback controls remain usable on phone and tablet, including an orientation/width change: PASS.
- Playback UI does not expose password, authentication token, salt or signed stream URL: PASS.
- Runtime app-process log scan found no signed stream request query or account-secret marker: PASS.
- Raw runtime logs were temporary and were deleted after the privacy scan: PASS.

## Scope boundaries

This validates the foreground one-track playback scope of issue #1.

Background playback, Android MediaSession/service integration and system playback controls remain #15 scope. Queue behavior remains #7 scope. Instrumented CI remains #34 scope.

No personal server URL, username, password, token, salt, device serial or track title is included in this evidence.
