# Proposal: Navidrome Recent Albums

## Intent

Implement GitHub issue #2: load a minimal recent-albums catalogue from the
currently authenticated Navidrome/OpenSubsonic account.

Authentication and server configuration are already implemented by #13 and
#14. This change is the first First Sound library feature consuming that
authenticated account boundary.

## Scope

### In scope

- Define a small account-scoped recent-album domain model.
- Define the repository contract used by presentation code.
- Parse the OpenSubsonic `getAlbumList2` response defensively.
- Add an authenticated Navidrome data boundary for `type=recent`.
- Add loading, empty, authentication, network and malformed-response states.
- Render recent albums only for the currently authenticated account.
- Preserve server order and allow selecting an album for later #8 integration.

### Out of scope

- Album details and tracks (#8).
- Media3 playback (#1).
- Persistent queue (#7).
- Full First Sound navigation shell (#12).
- Search, downloads, playlists or complete-library browsing.
- External providers.
- New DI frameworks, databases or Gradle modules.

## Architecture

The feature is introduced incrementally under `features/library`.

Domain state remains independent of OkHttp, JSON, Compose and Android.
OpenSubsonic parsing and HTTP behavior live behind the library data boundary.

All successful catalogue state carries the exact `ServerAccountIdentity`
that owns it. A result belonging to one account MUST NOT be presented after
the active account changes.

## Work units

1. WU1 — domain model, account ownership, repository contract and defensive
   OpenSubsonic recent-albums parser.
2. WU2 — authenticated OkHttp `getAlbumList2` data boundary with synthetic
   network tests.
3. WU3 — ViewModel, adaptive Compose recent-albums UI and composition-root
   wiring.

Each WU must remain independently reviewable. The issue closes only after all
three work units and the required verification are complete.

## Rollback

Each WU is additive. WU1 can be rolled back by removing the new library
feature files and its OpenSpec change without modifying the completed
authentication implementation.
