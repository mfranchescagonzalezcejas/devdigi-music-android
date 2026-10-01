# Proposal: Navidrome Media3 Playback

## Intent

Implement GitHub issue #1: play one selected Navidrome/OpenSubsonic track,
including FLAC where the Android device supports decoding, through AndroidX
Media3.

The completed library flow already exposes `AlbumTrack.id` as an opaque track
reference. Playback must use that reference to resolve an authenticated stream
only when playback is requested.

## Scope

### In scope

- Add the minimum AndroidX Media3 dependencies required for local ExoPlayer
  playback.
- Define playback models and state that contain no stream URLs, credentials,
  authentication tokens, salts or private endpoints.
- Resolve the authenticated OpenSubsonic `stream` request for the exact
  server-plus-user account at playback time.
- Reuse the existing secure credential store and `SubsonicAuthSigner`.
- Use a fresh authentication signature for each selected-track playback
  request.
- Use the existing OkHttp networking stack for Media3 data loading.
- Disable HTTP and HTTPS redirects for authenticated media transfer.
- Play one selected track through ExoPlayer.
- Support play, pause, resume, stop and release.
- Map Media3 lifecycle and terminal failures to safe presentation state.
- Stop and clear active playback when the authenticated account changes or
  signs out.
- Add focused JVM coverage for transport separation and playback state
  mapping.
- Validate FLAC playback on a compatible Android target or produce a
  recoverable unsupported-playback state.

### Out of scope

- MediaSession, MediaLibraryService and background playback (#15).
- Persistent queue behavior (#7).
- Android Auto, Wear OS, Cast or assistant integrations.
- Downloads or offline playback.
- Crossfade, equalizer and advanced audio controls.
- Persistent stream URLs or signed playback requests.
- Persisting playback state.
- Adding a DI framework or additional Gradle module.
- Guaranteeing a bundled FLAC software decoder on every API 23-26 device.

## Architecture

The change continues the feature-first lightweight Clean Architecture
direction:

    presentation -> domain <- data / Android playback adapter

Playback code will live under `features/playback`.

The domain and presentation layers see only:

- the selected opaque track id;
- safe display metadata such as title and artist;
- safe playback phases and recoverable error categories.

Resolved URLs and authentication query parameters remain internal to the
playback data/adapter boundary.

`ServerAccountIdentity` remains the canonical ownership key. Playback is
always initiated for an exact authenticated account and must be stopped when
that account is no longer current.

## Work units

The implementation will be attempted as cohesive work units. A work unit is
split only if its measured candidate exceeds the repository review budget or
an independently reviewable boundary becomes necessary.

1. WU1 — playback core:
   Media3 dependencies, pure playback contract/state, authenticated stream
   resolver, Media3/OkHttp playback adapter and focused tests.
2. WU2 — presentation and application wiring:
   account-aware playback ViewModel/state, album-track selection, controls,
   lifecycle handling and sign-out/account-change cleanup.
3. WU3 — final validation:
   complete test/build gates, Android playback evidence, privacy verification,
   documentation refresh and issue completion.

## Rollback

Playback is additive to the completed library browsing flow.

Removing `features/playback` and its composition-root wiring must leave recent
albums and album details behavior unchanged.
