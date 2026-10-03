# Proposal: First Sound Navigation Shell

## Intent

Implement GitHub issue #12 by adding the small native Compose navigation shell
required to turn the existing authenticated library and playback features into
a coherent First Sound application surface.

## In scope

- Primary destinations:
  - Home;
  - Library;
  - Search;
  - Discover.
- Existing recent-albums to album-details browsing from Home and Library.
- Honest non-functional Search and Discover placeholders.
- A persistent mini-player backed by the existing playback state.
- A secondary Now Playing surface.
- Accessible local playback controls.
- A read-only `This device` local target.
- Account-scoped navigation/content state.
- Correct Back behavior.
- Width-responsive Compose behavior.
- Focused JVM tests for deterministic presentation policies.
- Privacy-safe Android/device validation.

## Out of scope

- Search implementation.
- Recommendations.
- External providers.
- Cast.
- Music Connect.
- Remote playback.
- Remote device discovery.
- Queue editing.
- A full design system.
- New dependency-injection framework.
- Room.
- Multi-module architecture.
- Issue #17 full real-instance closeout.
- Issue #34 instrumented CI.
- Issue #104 reusable QA framework.

## Ownership

The ownership chain remains:

    First Sound Compose shell
              |
              v
      presentation ViewModels
              |
              v
    existing domain/data boundaries

Playback remains:

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

`PlaybackService` remains the only runtime player/queue authority.

## Navigation approach

The First Sound topology is deliberately small.

Issue #12 will start with explicit Compose/presentation navigation state
instead of adding Navigation Compose solely for framework conformity.

A navigation framework may be reconsidered later only if actual routing
complexity demonstrates a benefit.

## Secondary surfaces

Album Details and Now Playing are secondary surfaces rather than primary
navigation destinations.

The Now Playing surface is required because current safe playback state does
not guarantee an originating album id after queue restoration or service
transitions.

## Account isolation

Any shell state referencing account-specific library content must reset when
the exact authenticated `ServerAccountIdentity` changes.

The shell must not store raw endpoint, username, credential or authenticated
stream material in its own persistent state.

## Rollback

Issue #12 is presentation-layer work around already implemented library and
playback boundaries.

Each WU remains independently reviewable.

If a shell slice is reverted, the existing authenticated connection, library,
service-backed playback and queue contracts remain intact.
