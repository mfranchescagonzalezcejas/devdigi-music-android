# Exploration: Minimal Persistent Playback Queue

Related issue: #7

## Baseline

Issue #15 completed the service-backed Media3 playback boundary.

The current playback path provides:

- one `PlaybackService`-owned ExoPlayer;
- one `MediaLibrarySession`;
- an application `MediaController`;
- service-only authenticated stream resolution;
- safe Media3 metadata;
- account reconciliation across Activity recreation;
- notification, lock-screen and media-button controls.

Playback remains one-track oriented.

`PlaybackService` currently clears the existing player item before resolving
and playing a selected track.

The controller policy intentionally removes Media3 next/previous and media-item
mutation commands because queue behavior was outside #15.

## Existing boundaries to preserve

`PlaybackTrack` already contains the minimum safe queue metadata:

- opaque track id;
- title;
- optional artist.

`ServerAccountIdentity` remains the canonical runtime identity:

- normalized endpoint;
- exact opaque username.

Signed stream URLs remain transient service/player-local configuration.

The playback service remains the only runtime owner of the player/session.

## Album selection

Album details already expose an ordered track list.

Selecting one track can therefore replace the queue with the album's ordered
tracks and make the selected track current.

This gives First Sound useful next/previous behavior without adding a queue
editor.

## Persistence baseline

The application already uses Preferences DataStore and
`kotlinx-serialization-json`.

The minimal queue does not require:

- Room;
- a new Gradle module;
- a DI framework;
- a serialization compiler plugin.

## Security observations

Durable queue state must not contain:

- server endpoints;
- usernames;
- credentials;
- tokens;
- salts;
- signed stream URLs;
- private playback URIs.

A versioned deterministic fingerprint can bind a durable queue snapshot to a
server-plus-user identity without persisting the raw endpoint or username.

## WU0 decisions

1. `PlaybackService` remains runtime queue/player/session authority.
2. Queue mutation rules remain pure Kotlin domain behavior.
3. Queue persistence uses a dedicated DataStore boundary.
4. v0.1 stores one durable account-scoped queue snapshot.
5. Identity mismatch behaves as no restorable queue.
6. A later account queue may replace the previous durable snapshot.
7. Album-track selection replaces the queue with ordered album tracks.
8. Next and previous do not wrap.
9. Trusted system controllers may receive next/previous once queue support
   exists.
10. External/system controllers remain unable to inject arbitrary MediaItems.
11. Account-bearing queue mutation remains own-application-only.
12. Queue restoration never auto-plays.
13. Signed stream URLs are never persisted.
14. #12 navigation and mini-player remain separate.
15. #17 full First Sound validation remains separate.
16. Every implementation WU uses the 1000 changed-line hard review gate.
