# Proposal: Minimal Persistent Playback Queue

## Intent

Implement GitHub issue #7 by extending the completed service-backed Media3
playback path with a small persistent playback queue.

The queue preserves the player ownership, account isolation and privacy
boundaries established by #15.

## In scope

- Pure Kotlin queue state and mutation rules.
- Replace, append, next, previous, remove and clear.
- Deterministic current-index behavior.
- Album selection replacing the queue with ordered album tracks.
- Dedicated queue persistence.
- Account-scoped restoration.
- Media3 playlist translation inside `PlaybackService`.
- Own-application queue mutation commands.
- Android system next/previous controls.
- Restoration without autoplay.
- Focused JVM tests.
- Final real-device validation.

## Out of scope

- Queue editor UI.
- Drag-and-drop reordering.
- Smart shuffle.
- Collaborative queues.
- Cloud synchronization.
- Kotlin Multiplatform.
- Room or another database.
- Navigation/mini-player work from #12.
- Full First Sound E2E closeout from #17.
- External-provider queue semantics.

## Architecture

The ownership chain remains:

    Compose / PlaybackViewModel
              |
              v
       MediaController
              |
              v
    MediaLibrarySession
              |
              v
       PlaybackService
         queue + player
              |
              v
         ExoPlayer

`PlaybackService` remains the single runtime queue authority.

The queue domain contains no Android, Media3, DataStore or networking types.

## Persistence

The durable snapshot contains only:

- schema version;
- opaque account fingerprint;
- current index;
- safe track metadata.

It must not contain:

- raw endpoint;
- username;
- credentials;
- tokens;
- salts;
- signed stream URLs.

## Controller boundary

Own-app session commands may carry transient account identity required for
authenticated queue mutation.

Trusted Android media controllers may navigate next/previous but remain unable
to inject arbitrary MediaItems or authenticated sources.

## Restoration

A matching queue may be restored after process recreation.

Restoration must not start audible playback automatically.

A different account must never observe or control the stored or runtime queue
of another account.

## Work units

1. WU1 — pure queue domain and persistence.
2. WU2 — service/session Media3 queue backend.
3. WU3 — controller, ViewModel and album-selection wiring.
4. WU4 — Android validation and closeout.

Each implementation candidate is measured against the 1000 changed-line hard
review budget before commit.
