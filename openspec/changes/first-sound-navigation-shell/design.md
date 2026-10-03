# Design: First Sound Navigation Shell

## 1. Existing authorities

`MainActivity` remains the explicit application composition root.

Existing library ViewModels remain responsible for their library state.

`PlaybackService` remains the runtime playback and queue authority.

`PlaybackViewModel` remains a presentation client.

The shell owns navigation presentation only.

## 2. Primary destinations

The shell exposes exactly:

- Home;
- Library;
- Search;
- Discover.

A primary destination selection is presentation state, not library or
playback domain state.

## 3. Home and Library

First Sound does not yet have separate data products for Home and Library.

Both destinations therefore reuse the implemented authenticated
recent-albums to album-details flow.

Their visible labels may distinguish Home from Library, but neither may imply
recommendations, favorites, playlists, artists, full library browsing or
other functionality not yet implemented.

They reuse the same existing repository/ViewModel boundaries rather than
performing duplicate requests through new state owners.

## 4. Search and Discover

Search and Discover render explicit placeholder content.

The placeholders MUST communicate that the capability is not implemented.

They MUST NOT:

- return synthetic results;
- present disabled controls as if a search were running;
- display fake recommendations;
- invoke external-provider behavior.

## 5. Secondary navigation

Album Details remains a secondary route carrying only an opaque album id.

Now Playing is a second secondary route.

Now Playing is deliberately not a primary bottom/rail destination.

Opening a primary destination closes the currently presented secondary
surface.

## 6. Why Now Playing exists

`PlaybackState` contains safe current-track metadata but no reliable source
album id.

A queue restored after process recreation may therefore have a valid current
track without an album-details route that can be reconstructed safely.

The persistent mini-player opens Now Playing rather than guessing an album.

## 7. Account-scoped navigation state

Navigation state that can reference account content MUST reset whenever the
exact authenticated `ServerAccountIdentity` changes or the session becomes
unauthenticated.

The shell may retain safe navigation tokens and opaque content ids across
ordinary Activity recreation.

It MUST NOT serialize:

- server endpoint;
- username;
- credentials;
- authentication token/salt;
- signed stream URI.

Library ViewModels continue performing their own stale-result and
account-ownership checks.

## 8. Persistent mini-player

The mini-player consumes the same `PlaybackState` exposed by
`PlaybackViewModel`.

It MUST NOT mirror the queue into independent state.

Visibility follows the existing playback context:

- no track / IDLE -> hidden;
- track with an existing visible playback-control context -> shown.

This allows recoverable STOPPED or ERROR state to remain reachable through
Now Playing without inventing another playback lifecycle.

The compact surface shows only safe metadata:

- title;
- optional artist;
- playback status when useful.

The compact direct action is play/pause/resume according to current phase.

Selecting the mini-player opens Now Playing.

## 9. Now Playing

Now Playing consumes existing playback state and commands.

It may expose the already-supported:

- Previous;
- Next;
- Pause;
- Resume;
- Stop;
- Retry.

It does not expose a queue editor.

It does not resolve streams.

It does not persist player state.

It does not construct Media3 objects.

## 10. This device

Now Playing communicates the playback target as a read-only value:

`This device`

The presentation MUST NOT use a dropdown, remote-device icon, scanning state
or other affordance that implies implemented remote playback.

## 11. Existing Album Details controls

Album Details currently embeds the full `PlaybackControls` component.

During WU2 the presentation should avoid redundant large playback controls
when the shell mini-player and Now Playing surface provide the same
application-level control path.

Any adjustment is presentation-only and must preserve track selection,
queue replacement and service-owned playback behavior.

## 12. Back behavior

Back behavior is deterministic:

1. Now Playing returns to the previous shell/library context.
2. Album Details returns to its originating Home or Library root.
3. A primary root does not invent a deeper history entry; normal Android Back
   behavior remains available.

Selecting another primary destination exits any current secondary surface.

## 13. Responsive behavior

Layout decisions are derived from available Compose width.

The shell may use a bottom navigation surface for compact widths and a rail or
equivalent width-appropriate primary navigation surface when doing so remains
small and clear.

No device model checks are permitted.

Existing recent-albums and album-details width policies remain authoritative
inside those screens.

## 14. Accessibility

Primary destinations and mini-player controls must have unambiguous visible
labels or semantics.

A placeholder must be announced as unavailable/not implemented rather than as
an interactive feature.

Playback controls must preserve their existing enabled/phase behavior.

The `This device` target must be presented as informational rather than as an
actionable remote selector.

## 15. Testing

Prefer pure JVM tests for deterministic shell behavior:

- primary destination selection;
- secondary-route behavior;
- Back policy;
- account-change reset policy;
- placeholder policy;
- mini-player visibility/action policy;
- responsive layout selection;
- safe fallback labels.

Existing library and playback ViewModel regression tests remain in force.

Do not introduce issue #34 or #104 infrastructure solely for #12.

## 16. Android validation

WU4 validates on a real Android device:

- all four primary destinations;
- Home/Library album flow;
- honest placeholders;
- mini-player persistence across destinations;
- Now Playing controls;
- Activity recreation;
- background/foreground transitions;
- service/queue continuity;
- notification and lock-screen controls;
- sign-out/account switching;
- privacy.

Temporary interactive runners stay outside the repository unless there is a
separate reviewed reason to version them.

## 17. Privacy

System and application UI may expose only safe playback metadata required by
the existing playback contracts.

Issue #12 must not surface endpoint, username, authentication material or
signed stream configuration.

QA evidence records PASS / FAIL / BLOCKED using sanitized information only.

## 18. Work-unit boundaries

WU0 — planning and OpenSpec.

WU1 — navigation foundation:
- shell;
- four primary destinations;
- existing Home/Library flow;
- honest placeholders;
- focused tests.

WU2 — playback shell:
- persistent mini-player;
- secondary Now Playing surface;
- existing playback command wiring;
- focused tests;
- no second player/queue state.

WU3 — UX hardening:
- exact account reset semantics;
- `This device`;
- Back policy;
- accessibility;
- width-responsive navigation;
- integration hardening.

WU4 — Android validation and closeout:
- real-device acceptance checks;
- lifecycle/queue/system controls;
- account isolation;
- privacy;
- full local gates;
- Jenkins;
- final documentation.

Every candidate WU is measured using additions + deletions across tracked and
untracked files before commit.

The hard maximum is 1000 changed lines per WU/PR.
