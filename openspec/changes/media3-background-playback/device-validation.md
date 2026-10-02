# Device validation: Media3 background playback

Status: WU2 real-device validation complete
Related issue: #15

## Validation boundary

Validation was performed on a physical Android device running API 36 against
a real authenticated BYON Navidrome/OpenSubsonic library.

Device model, serial, server endpoint, usernames, credentials, authentication
material and listening history are intentionally omitted.

Raw authenticated `dumpsys` and logcat captures were kept only in temporary
local storage during the QA run and were deleted afterwards.

## Results

| Requirement | Result |
| --- | --- |
| Playback continues after the Activity is backgrounded | PASS |
| Returning to the app controls the same active playback | PASS |
| Notification/system media controls pause and resume playback | PASS |
| Lock-screen media controls operate the same playback | PASS |
| Standard Android media-button pause/resume commands operate playback | PASS |
| Activity recreation preserves the same playback and a single service lifecycle | PASS |
| Sign-out stops and clears active playback | PASS |
| Switching between two distinct valid accounts does not retain stale playback state | PASS |
| A terminal network/source failure remains recoverable | PASS |
| MediaSession/system surfaces omit server/account/authenticated stream data | PASS |
| App-process log privacy scan omits authenticated playback material | PASS |

## Account-switch clarification

The first account-switch QA prompt was rejected as a false negative.

`Recent albums` is explicitly account-scoped and is loaded through
OpenSubsonic `getAlbumList2` with `type=recent`. A newly created account may
therefore have an empty recent catalogue even when another account on the
same server has recent listening history.

The targeted retest verified that:

- sign-out stopped the first account's playback;
- the second authenticated account started without stale playback state;
- recent-album state remained specific to the active account;
- playback could then start normally under the second identity.

No account names or listening-history values are retained in this evidence.

## Privacy recheck clarification

The first automated privacy scan was also rejected as a false positive because
its notification scan covered unrelated applications on the Android device.

The targeted recheck restricted inspection to:

- DevDigi Music MediaSession output;
- DevDigi Music notification/system-media output;
- the DevDigi Music application process log.

Exact private server/account values were held only in shell memory for leak
matching and were never persisted.

The targeted automated scan and the manual notification/lock-screen/system
media review both passed.

The ordinary authenticated application UI may identify the active account.
Issue #15's privacy boundary applies to playback/system media surfaces and
authenticated playback material, not to removing the account identity from
the connection UI.

## Local verification

The closeout QA run passed:

- `./gradlew spotlessCheck`
- `./gradlew testDebugUnitTest`
- `./gradlew lint`
- `./gradlew assembleDebug`

Required Jenkins pull-request checks are intentionally recorded only after the
closeout pull request exists.

## Scope

This validation does not add or validate queue-domain behavior tracked by #7,
navigation/mini-player work tracked by #12, broader real-instance validation
tracked by #17, or Android Auto/Wear OS/Cast/assistant integrations.
