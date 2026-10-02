# Design: Media3 Background Playback

## 1. Existing baseline

Issue #1 already provides:

- `PlaybackTrack` and safe `PlaybackState`;
- `PlaybackEngine`;
- exact-account authenticated stream resolution;
- fresh OpenSubsonic signing;
- redirect-disabled OkHttp media transport;
- ExoPlayer one-track playback;
- safe playback error mapping;
- account-aware playback cleanup;
- stale-event rejection;
- Compose playback controls.

The current foreground adapter owns its ExoPlayer instance inside the
Activity/ViewModel lifetime.

Issue #15 changes that ownership boundary.

## 2. Service ownership

Exactly one playback `Player` SHALL be created by the Media3 playback service.

The service SHALL own:

- ExoPlayer;
- MediaLibrarySession;
- service-side authenticated playback coordination;
- the lifetime of any active signed media source.

The service SHALL release its session and player exactly once when its
lifecycle ends.

An Activity, Compose screen or ViewModel MUST NOT create a second ExoPlayer.

## 3. MediaLibraryService

The service SHALL extend Media3 `MediaLibraryService`.

A `MediaLibrarySession` SHALL expose the service-owned player to Android media
infrastructure.

The library aspect is used as the Media3 service/session contract required by
the issue. A browsable Navidrome hierarchy is not introduced by #15.

Library browsing requests MAY remain unsupported until a dedicated feature
requires them.

## 4. Application controller

Application presentation code SHALL communicate with the service through a
Media3 `MediaController`.

The controller is a client handle only. Releasing an Activity/ViewModel
controller MUST NOT release the service-owned player.

Recreating the Activity SHALL reconnect to the existing session rather than
construct another player.

The controller-backed adapter SHALL continue exposing framework-independent
`PlaybackState` to presentation.

## 5. Authenticated play command

A normal external media controller MUST NOT receive the information required
to resolve a private Navidrome stream.

Starting a selected Navidrome track therefore requires an app-internal session
command carrying only the information necessary for the service to perform
the already-defined issue #1 operation:

- exact account identity;
- opaque track id;
- title;
- optional artist.

This command SHALL be accepted only from this application's own controller.

The service SHALL NOT echo account identity into player metadata, session
extras, notifications or logs.

The service SHALL resolve credentials from the existing secure store and
create a fresh OpenSubsonic signature immediately before playback.

## 6. MediaItem privacy

The playable MediaItem inside the service may contain a local playback URI
because ExoPlayer requires a playable source.

That URI is sensitive because it contains short-lived authentication
material.

Therefore:

- the URI MUST remain local playback configuration;
- it MUST NOT be copied into `MediaMetadata`;
- it MUST NOT be copied into session extras;
- it MUST NOT be copied into controller-visible application state;
- it MUST NOT be logged;
- it MUST be cleared when playback stops, fails or changes account.

Published `MediaMetadata` SHALL contain only the minimum now-playing fields:

- title;
- optional artist.

The opaque track id MAY be used as Media3 `mediaId`.

## 7. Controller authorization

The service SHALL grant commands per controller through the MediaLibrarySession
connection callback.

The application's own MediaController is the only controller privileged to
issue authenticated selected-track or account-reconciliation commands.

Own-app authorization MUST NOT rely on `ControllerInfo.isTrusted` alone.
Media3 trust may also include Android system components, media-control
permission holders and notification listeners.

The privileged own-app decision SHALL require same-application identity. The
implementation SHALL compare the controller UID with the application UID and,
where Media3 reports a verified package identity, SHALL require the package to
match this application.

The Media3 media-notification controller MUST NOT be rejected. It SHALL receive
only the minimum standard player/read commands required for the supported
notification controls.

Other appropriate Android system controllers MAY receive the minimum standard
transport/read commands needed for lock-screen, headset and system playback
control.

No controller other than this application's own controller SHALL receive the
authenticated selected-track or account-reconciliation custom commands.

External and system controllers MUST NOT receive player commands that permit
arbitrary replacement or addition of media items for this one-track #15
boundary.

In particular, #15 SHALL NOT expose external queue/media-source mutation merely
because a controller is considered trusted.

#15 does not grant queue-editing behavior.

## 8. Account reconciliation

The service SHALL retain the account owning active playback only in memory.

It SHALL NOT persist that ownership as playback state.

When application presentation connects or the authenticated identity changes,
the application and service SHALL reconcile the current account.

If there is no authenticated account, active playback SHALL stop and clear.

If the authenticated account differs from the account owning playback, active
playback SHALL stop and clear before the new account can control media.

If the account matches, the reconnected UI MAY reconstruct safe playback state
from the session/controller.

This covers returning to the application while service-owned playback remains
active.

## 9. State mapping

Presentation-facing state remains the issue #1 domain model.

The controller adapter maps Media3 player/session callbacks to:

- idle;
- preparing;
- playing;
- paused;
- stopped;
- recoverable error.

Raw player exceptions and media URIs MUST NOT enter presentation state.

A terminal player failure MUST never leave presentation reporting `PLAYING`.

## 10. Foreground-service model

The manifest SHALL declare the permissions required by Android for media
playback foreground services:

- `android.permission.FOREGROUND_SERVICE`;
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

The MediaLibraryService SHALL declare the `mediaPlayback` foreground service
type.

The playback service SHALL be declared with `android:exported="true"` so
supported Media3, platform and system media controllers can discover and
connect to the declared media-service interfaces.

Exporting the Android service does NOT grant authenticated playback authority.
Per-controller session authorization remains the security boundary for
commands.

Its manifest intent filter SHALL expose the Media3 library-service action:

- `androidx.media3.session.MediaLibraryService`.

The platform compatibility action:

- `android.media.browse.MediaBrowserService`

SHOULD also be declared for supported platform/legacy media-controller
interoperability. Declaring that action does not add a browsable Navidrome
hierarchy to #15.

Controller authorization remains responsible for limiting which commands each
connected controller may issue.

Media3 owns the normal media notification lifecycle from the session/player
state.

No custom notification implementation is required unless device validation
demonstrates a concrete need.

## 11. Notification metadata

Android system media surfaces SHALL derive their display information from
safe Media3 metadata.

No notification title, subtitle, artwork URI, extras or action SHALL expose:

- server host;
- server path;
- username;
- credentials;
- authentication material;
- signed stream URL.

Album artwork is not required by #15.

## 12. Queue boundary

#15 continues to represent one selected track.

No persistent queue, next/previous domain behavior, queue persistence or
account-scoped queue restoration is introduced.

Those remain issue #7.

## 13. Playback resumption boundary

Playback may continue while the app UI is backgrounded because the active
player/session live in the media foreground service.

Resuming playback after the service has been destroyed or after device reboot
would require a separate persisted playback-resumption contract.

That behavior is not part of #15.

## 14. Composition

`MainActivity` remains the manual composition root for UI-facing dependencies.

The playback service is a second Android lifecycle entry point and therefore
constructs only the minimum playback-side dependencies needed to own the
session and resolve authenticated streams.

No general-purpose application DI container or framework is introduced.

Shared construction helpers MAY be extracted where doing so prevents
duplicating security-sensitive credential/signing configuration.

## 15. Verification

Automated tests SHOULD cover framework-independent behavior including:

- own-app versus external controller authorization;
- account mismatch cleanup decisions;
- safe playback state reconstruction;
- terminal errors not remaining playing;
- no queue behavior introduced;
- existing issue #1 security/state regressions.

Android/device validation SHALL confirm:

- one player/session lifecycle;
- playback continues after backgrounding;
- returning to the Activity controls the same playback;
- notification play/pause controls work;
- lock-screen/system controls work;
- supported headset/media-button controls act on the same player;
- stopping clears the notification/player item appropriately;
- sign-out/account change clears playback;
- no sensitive playback URI or account data appears in visible system
metadata or collected logs.

Verification artifacts MUST use only privacy-safe evidence.
