# Exploration: First Sound Navigation Shell

## Audit baseline

GitHub issue #12 is implemented from `develop` baseline:

`b71b8042caf981739f1137a943a0215dce10efe8`

The audit is read-only and covers the current navigation, authenticated
library, playback presentation, account ownership, architecture rules and
OpenSpec baseline.

## Current application composition

`MainActivity` remains the explicit application composition root.

It currently wires:

- server profile persistence;
- encrypted authentication secrets;
- authenticated OpenSubsonic clients;
- recent-albums and album-details repositories;
- connection, library and playback ViewModels;
- the controller-backed playback engine.

No dependency-injection framework or additional application module is needed
for issue #12.

## Current navigation

The authenticated UI currently has one small navigation state:

- `selectedAlbumId == null` shows recent albums;
- an opaque selected album id shows album details;
- Back from album details clears the selected id.

`LibraryDestination` represents only:

- recent albums;
- album details.

There is no AndroidX Navigation Compose dependency in the project.

The First Sound shell therefore does not require a navigation framework merely
to satisfy issue #12. A small explicit presentation-state model is preferred
unless implementation demonstrates a concrete need for additional machinery.

## Current library flow

Recent albums and album details are already authenticated and account-aware.

Both presentation ViewModels reject stale results through generation and
account/target checks.

Issue #12 must reuse these flows rather than create parallel library loading
state.

For First Sound, both Home and Library may expose the existing recent-albums
to album-details vertical slice. They must not imply additional library or
recommendation functionality that is not implemented.

## Current playback ownership

`PlaybackService` remains the runtime authority for:

- ExoPlayer;
- MediaLibrarySession;
- queue contents;
- current queue index;
- durable queue synchronization.

`PlaybackViewModel` is an application client of that service.

Issue #12 must not introduce another player, queue, queue snapshot or playback
ownership layer.

Presentation-visible playback state contains only safe track metadata:

- opaque track id;
- title;
- optional artist;
- playback phase/failure.

It does not contain a source album id.

## Player-surface consequence

Because restored or service-transitioned playback does not reliably carry an
album id, a persistent mini-player cannot safely assume that the originating
album-details screen is available.

Issue #12 therefore uses a secondary `Now Playing` surface for mini-player
navigation.

`Now Playing` is not a fifth primary destination.

It consumes the existing `PlaybackViewModel` state and commands only.

## Current playback controls

Album details currently embeds `PlaybackControls`.

The navigation-shell work must avoid treating those controls as a second
playback authority.

When the persistent mini-player and `Now Playing` surface are introduced,
presentation may be reorganized so controls are not needlessly duplicated,
while preserving the underlying playback contracts.

## Account ownership

`ServerAccountIdentity` is the canonical exact server-plus-user identity.

Existing library and playback state already reconcile against that identity.

Navigation state that can reference account-specific content, especially an
opaque album id, must reset when the authenticated identity changes.

The shell must not persist or expose raw endpoint or username information.

## Primary destinations

Issue #12 requires exactly four primary destinations:

- Home;
- Library;
- Search;
- Discover.

Search and Discover are explicit placeholders in First Sound.

They must not expose fake search results, fake recommendations or external
provider behavior.

## Local playback target

`This device` is a conceptual read-only playback target.

It must communicate that playback is local to the current Android device
without implying that another device can be selected or controlled.

Issue #12 does not add:

- Cast;
- Music Connect;
- remote-device discovery;
- remote playback;
- companion-device control.

## Existing responsive strategy

Recent albums and album details already derive layout decisions from available
Compose width instead of device-model checks.

The First Sound shell should preserve that pattern.

No full design system is required.

## Existing testing strategy

Presentation tests currently favor JVM-testable policies and state helpers.

The repository does not currently depend on a Compose instrumented UI-test
stack.

Issue #12 should keep deterministic navigation, mini-player and layout
decisions testable as ordinary Kotlin/JVM behavior where practical.

Android/device validation remains appropriate for Activity recreation,
background playback, system controls and real interaction behavior.

## Privacy

No issue #12 source, test, OpenSpec, PR text, log or QA evidence may contain:

- a real server endpoint or private DNS name;
- a real account identifier;
- credentials, tokens or salts;
- signed stream URLs;
- private network topology;
- identifying device serials;
- personal listening/library evidence.

## Explicit non-goals

Issue #12 does not implement:

- issue #17 real-instance vertical-slice closeout;
- issue #34 instrumented CI;
- issue #104 reusable QA framework;
- external music providers;
- recommendations;
- real search;
- remote playback;
- queue editing;
- a new DI framework;
- Room;
- multi-module architecture.

## Review-budget forecast

The planned slices are expected to remain below the hard 1000 changed-line
limit:

- WU0: OpenSpec planning only;
- WU1: navigation foundation;
- WU2: persistent mini-player and Now Playing;
- WU3: account/target/back/accessibility/responsive hardening;
- WU4: Android validation and closeout.

Implementation WUs should stop and split before commit if the complete
tracked-plus-untracked candidate would exceed 1000 changed lines.
