# Exploration: Navidrome Media3 Playback

## Baseline

Planning baseline is the `develop` merge that completed issue #35.

Implemented prerequisites already available:

- authenticated server-plus-user identity;
- encrypted credential storage;
- fresh OpenSubsonic token/salt signing;
- recent albums;
- album details;
- ordered album tracks;
- opaque `AlbumTrack.id`;
- synthetic Navidrome integration environment;
- Jenkins Docker-backed integration execution.

No Media3 dependency or playback implementation exists at this baseline.

## Media3 selection

AndroidX Media3 1.11.1 is the stable release selected for implementation
planning.

The minimum dependency set for #1 is:

- Media3 ExoPlayer;
- Media3 OkHttp data source.

MediaSession is intentionally deferred to #15.

Media3 UI modules are not required because the application already uses
Jetpack Compose and #1 needs only a small control surface.

## Android compatibility

The application minSdk is API 23, which is compatible with core ExoPlayer
audio playback.

FLAC is a supported progressive container.

The Android platform guarantees FLAC decoding from API 27. Earlier compatible
devices may still provide FLAC decoding, but #1 will not introduce a bundled
native decoder extension solely to guarantee all API 23-26 devices.

Decoder failure must therefore be represented as safe recoverable playback
state.

## Network decision

Playback will reuse OkHttp rather than Media3's default HTTP implementation.

Reasons:

- the application already depends on OkHttp;
- the authentication security model already requires redirect control;
- the Media3 OkHttp data source accepts an application-provided
  `Call.Factory`;
- a playback-specific client can disable both ordinary and SSL redirects.

No HTTP logging interceptor is added.

## Authentication decision

OpenSubsonic stream authentication remains query-signed using the existing
signer.

Credentials are looked up at execution time for the exact current account.

The signed request exists only in the playback data/Media3 boundary and
memory.

No stream request is added to:

- `AlbumTrack`;
- navigation state;
- `rememberSaveable`;
- ViewModel saved state;
- DataStore;
- logs;
- verification evidence.

## Player ownership decision

#1 needs one foreground application player only.

A playback port keeps presentation independent from Media3.

Concrete ExoPlayer ownership must avoid eager duplicate construction across
Activity recreation.

#15 will later replace/extend that owner with MediaSession and service
lifecycle behavior.

The design deliberately avoids implementing #15 early.

## Queue decision

Selecting a track in #1 means one active selected item only.

No persistent or multi-item queue model is introduced.

Issue #7 remains responsible for replacement, append, next, previous,
persistence and account-scoped queue semantics.

## Validation decision

Pure state and security behavior belongs in JVM tests.

Actual ExoPlayer behavior requires Android runtime evidence.

The existing #35 synthetic Navidrome environment may provide synthetic FLAC
content, but automated emulator/device CI remains outside #1 and belongs to
#34 if justified.

Manual/device evidence must be sanitized and must not disclose a personal
server or personal library.
