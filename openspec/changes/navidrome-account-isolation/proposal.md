# Proposal: Navidrome Account Isolation

## Issue

GitHub #16 — isolate local state by Navidrome account.

## Outcome

Guarantee that First Sound state owned by one authenticated account cannot
become visible, restorable or controllable by another account.

The canonical account identity remains:

`ServerAccountIdentity(normalized endpoint, exact username)`.

## Why

Account-aware behavior already exists across authentication, library,
navigation and playback, but those guarantees were introduced by separate
issues.

Issue #16 establishes one cross-cutting isolation contract and closes any
remaining gaps before the real First Sound validation in #17.

## Isolation, not multi-account management

This issue does not add a multi-account selector or account manager.

The app may continue to persist one selected server profile, one active
encrypted credential snapshot and the existing minimal queue snapshot.

The required invariant is:

State owned by account A must never be accepted as current state for account B.

Independent retained profiles, credentials or queues for every historical
account are out of scope.

## Existing authorities remain unchanged

- Navidrome/OpenSubsonic remains library and streaming authority.
- `ServerAccountIdentity` remains the account identity.
- Library ViewModels remain presentation-state owners.
- `PlaybackService` remains runtime player/session/queue authority.
- `PlaybackViewModel` remains a presentation client.
- First Sound navigation remains presentation-only.

## In scope

- Exact server-plus-user account identity.
- Authentication and session-transition isolation.
- Recent-albums and album-details stale-result isolation.
- Navigation reset on account change or sign-out.
- Playback and runtime queue ownership isolation.
- Safe matching queue restoration.
- Synthetic regression coverage for:
  - two users on one server;
  - one username on two servers;
  - sign-out;
  - account switching;
  - stale asynchronous results.

## Out of scope

- Multi-account selector UX.
- Multiple simultaneously retained credentials.
- Account migration or cross-device sync.
- Search or Discover implementation.
- Recommendations.
- Queue-editor UI.
- Cast, Music Connect or remote playback.
- External providers.
- Android instrumented CI (#34).
- Reusable QA/BDD infrastructure (#104).
- Real private-server First Sound validation (#17).

## Privacy

Committed examples must be synthetic.

No real endpoint, IP, DNS name, username, password, token, salt, signed stream
URI, device serial, private library metadata or listening history belongs in
this change.
