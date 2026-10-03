# Device validation: First Sound navigation shell

Status: WU4 real-device validation and closeout complete
Related issue: #12
Validation date: 2026-10-03
Source baseline: develop at fc468856a9922271a6c768b32c9bea2e3487c0da
Final merged baseline: develop at 4f1f3a22c64b3d2295b43c059eea395f14ad6c54

## Validation boundary

Validation was performed on one physical Android device running API 36
against a real authenticated BYON Navidrome/OpenSubsonic library.

Device model, serial, server endpoint, account identifiers, credentials,
authentication material, signed stream URIs, track titles and listening
history are intentionally omitted.

Only privacy-safe PASS / FAIL / BLOCKED outcomes are retained.

## Results

| Requirement | Result |
| --- | --- |
| Home, Library, Search and Discover navigation | PASS |
| Search and Discover remain explicit non-interactive placeholders | PASS |
| Home album details returns to Home | PASS |
| Library album details returns to Library | PASS |
| Secondary-surface Android Back behavior | PASS |
| Persistent mini-player across primary destinations | PASS |
| Mini-player Pause/Resume uses existing playback authority | PASS |
| Now Playing navigation and playback controls | PASS |
| Activity recreation presentation continuity | PASS |
| Background/foreground playback continuity | PASS |
| Queue continuity through app/system controls | PASS |
| Notification media controls operate the same service-owned playback | PASS |
| Lock-screen controls operate the same service-owned playback | PASS |
| Sign-out clears prior account playback presentation | PASS |
| Distinct account receives no stale prior shell/playback state | PASS |
| `This device` is presented as a read-only local target | PASS |
| No remote selector, Cast or Music Connect affordance is implied | PASS |
| First Sound navigation state contains no endpoint, username or auth material | PASS |
| First Sound UI exposes no authenticated stream material | PASS |

## Structural evidence

The closeout rechecked that:

- `FirstSoundNavigationState` contains only navigation presentation data;
- mini-player and Now Playing consume existing playback presentation state;
- playback commands remain wired through the existing `PlaybackViewModel`;
- no second player or queue authority is introduced;
- `This device` remains informational only.

The bottom-bar versus navigation-rail width policy remains covered by the
focused WU3 JVM policy tests, while the real-device QA validates the shell
variant applicable to the physical test target.

## Local verification

The WU4A candidate passed:

- `./gradlew spotlessCheck`
- `./gradlew testDebugUnitTest`
- `./gradlew lint`
- `./gradlew assembleDebug`
- `git diff --check`

## Jenkins and merge closeout

The exact WU4A pull-request head was:

`84774d5b82c2307cb1943ee9f7c932a4fec3d7d5`

For that exact head, both required Jenkins contexts passed:

- `continuous-integration/jenkins/branch`: SUCCESS
- `continuous-integration/jenkins/pr-merge`: SUCCESS

PR #151 was merged into `develop` with merge commit:

`4f1f3a22c64b3d2295b43c059eea395f14ad6c54`

Issue #12 was then reconciled against the merged behavior and closed as
completed on 2026-10-03.

## Scope

This validates issue #12's First Sound navigation shell.

It does not implement search results, recommendations, queue editing,
remote playback, Cast, Music Connect, external-provider behavior or
instrumented CI.
