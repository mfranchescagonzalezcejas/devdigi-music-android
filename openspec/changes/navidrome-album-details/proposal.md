# Proposal: Navidrome Album Details

## Intent

Implement GitHub issue #8: selecting a recent album loads its
Navidrome/OpenSubsonic metadata and ordered track listing for the currently
authenticated account.

Issue #2 already provides the recent-albums catalogue and authenticated
account boundary. This change extends that vertical slice from album
selection into album details.

## Scope

### In scope

- Define account-scoped album-details and track domain models.
- Define the repository/result contract consumed by presentation code.
- Parse the OpenSubsonic album response defensively.
- Load album metadata and songs through the authenticated album endpoint.
- Preserve server track order.
- Render loading, empty, authentication, network, malformed-response and
  server-error states.
- Display album title, artist and safe artwork fallback.
- Allow selecting a track for later playback integration.
- Keep album details scoped to the exact authenticated server-plus-user
  identity.

### Out of scope

- Playback implementation (#1).
- Background playback (#15).
- Persistent queue (#7).
- Playlist management.
- Metadata editing.
- Search and full-library browsing.
- Persisting resolved stream URLs or authentication material.

## Architecture

The feature continues the existing dependency direction:

`presentation -> domain <- data`

inside `features/library`.

The domain exposes opaque album and track identifiers only. It does not carry
resolved stream URLs, tokens, salts, passwords or endpoint-specific request
objects.

`AlbumTrack.id` is the opaque track reference that playback issue #1 may later
use to resolve an authenticated stream request at execution time.

## Work units

1. WU1A — domain models, repository contract and OpenSpec.
2. WU1B — strict bounded OpenSubsonic album parser.
3. WU2 — authenticated `getAlbum` HTTP and credential boundary.
4. WU3 — account-aware ViewModel, album-detail Compose surface and
   composition-root navigation/wiring.

Each work unit remains independently reviewable.

## Rollback

WU1A is additive and can be removed without changing the completed
recent-albums flow.
