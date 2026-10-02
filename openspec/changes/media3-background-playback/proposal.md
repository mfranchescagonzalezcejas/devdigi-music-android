# Proposal: Media3 Background Playback

## Intent

Implement GitHub issue #15 by moving active Android playback ownership from
the foreground Activity/ViewModel lifetime into a Media3
`MediaLibraryService`.

The completed issue #1 playback boundary already provides authenticated
Navidrome/OpenSubsonic stream resolution, fresh signing, redirect-disabled
media transport, safe playback state and one-track Media3 playback.

This change extends that working playback path rather than replacing its
security model.

## Scope

### In scope

- Add the Media3 session dependency matching the existing pinned Media3
  version.
- Host one ExoPlayer instance in a `MediaLibraryService`.
- Expose that player through one `MediaLibrarySession`.
- Connect the application UI to the service through a Media3
  `MediaController`.
- Keep application controls, notification controls, lock-screen controls and
  trusted system media controls operating the same player.
- Continue playback while the Activity is backgrounded when Android's media
  foreground-service model permits it.
- Reconnect a recreated UI to the active service/session without creating a
  second player.
- Resolve authenticated OpenSubsonic stream requests inside the service-side
  playback boundary.
- Preserve exact-account credential lookup, fresh authentication signing and
  redirect-disabled media transport from issue #1.
- Publish only minimum safe now-playing metadata.
- Stop and clear playback when the authenticated account is removed or no
  longer matches the account that owns active playback.
- Add the Android foreground-service declarations required for media playback.
- Add focused automated coverage for new framework-independent lifecycle,
  authorization and state behavior.
- Validate notification, lock-screen, headset/system and background/return
  behavior on a real Android device.

### Out of scope

- Persistent or multi-item queue behavior (#7).
- First Sound navigation shell and persistent mini-player (#12).
- Broad real-instance validation (#17).
- Android Auto.
- Wear OS.
- Cast.
- Assistant integrations.
- Playback resumption after service destruction or device reboot.
- Browsable MediaLibrary content hierarchy.
- Downloads or offline playback.
- Introducing Hilt, Dagger, Koin or another DI framework.
- Persisting signed stream URLs, playback credentials or account playback
  state.

## Architecture

The existing feature-first direction remains:

    presentation -> domain <- data / Android Media3 adapters

For #15 the lifetime boundary changes:

    Compose / PlaybackViewModel
              |
              v
      MediaController client
              |
              v
      MediaLibrarySession
              |
              v
        service-owned
          ExoPlayer
              |
              v
    authenticated Navidrome stream

`MainActivity` remains the explicit composition root for application UI
dependencies.

The `MediaLibraryService` becomes the Android lifecycle owner for the playback
player and session because their lifetime must no longer depend on an Activity
or ViewModel.

No second player is allowed in the UI process boundary.

## Privacy boundary

Published now-playing data may contain only:

- opaque track id;
- track title;
- optional artist;
- playback state required by Media3.

It MUST NOT publish:

- server endpoint;
- account username;
- password;
- authentication token;
- authentication salt;
- signed stream URI;
- raw network or player exception details.

The signed media URI remains service/player-local configuration.

## Controller boundary

Controller permissions are granted per controller.

The application's own MediaController is privileged for #15-specific
authenticated commands. Own-app authorization MUST NOT rely on Media3
`isTrusted` alone because trusted controllers can include system and other
media-control components.

The service MUST use same-application identity as the privileged boundary,
including the application UID and, where Media3 can verify it, the matching
application package identity.

The Media3 notification controller and appropriate Android system controllers
MUST remain able to connect for normal media integration, but they receive
only the minimum standard transport/read commands required for the supported
one-track controls.

Commands carrying account context or requesting authenticated track
resolution MUST be exposed only to the application's own controller.

External controllers MUST NOT receive authenticated custom commands and MUST
NOT be able to inject an account identity, endpoint, stream URI, arbitrary
media source or replacement queue.

## Work units

The implementation will be attempted in large cohesive work units and split
only if the measured candidate exceeds the 1000 changed-line hard review
budget or an independently reviewable safety boundary becomes necessary.

1. WU1 — service-backed playback vertical slice:
   session dependency, service/player ownership, secure session command
   boundary, MediaController client, application wiring, account
   reconciliation and focused tests.
2. WU2 — Android validation and closeout:
   background/foreground lifecycle, notification, lock screen, system/headset
   controls, account cleanup, privacy checks, documentation and final CI.

## Rollback

The change remains isolated under the playback feature plus manifest,
dependency and composition wiring.

Rolling #15 back must restore the foreground-only issue #1 playback path
without affecting authenticated library browsing or credential storage.
