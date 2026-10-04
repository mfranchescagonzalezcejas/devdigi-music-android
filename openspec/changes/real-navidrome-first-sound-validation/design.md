# Design: Automated Real Navidrome First Sound Validation

## 1. Current-state audit

At the #17 base:

- `develop` is
  `a780e4dfb5f7a2b7bb052d3b4e8671cb44f7f333`.
- `AndroidJUnitRunner` is already declared by the app.
- `app/src/androidTest` does not yet exist.
- Compose UI test dependencies are not configured.
- UIAutomator is not configured.
- Jenkins runs JVM/unit, lint, build and synthetic Navidrome integration gates.
- #35 provides the reproducible synthetic Navidrome environment.
- #34 owns future instrumented CI.
- #104 owns the future reusable QA/BDD harness.

The app therefore has the product behavior required by #17 but does not yet
have a local device-level automation entry point.

## 2. Execution boundary

The intended command is a repository-owned local runner.

The runner will:

1. verify a usable Android device is connected and authorized;
2. build/install the debug application and test APK;
3. reset app state for deterministic execution;
4. receive real-instance inputs without printing them;
5. stage those inputs only in temporary app-private storage;
6. launch one targeted Android instrumentation suite;
7. emit sanitized result labels;
8. delete temporary runtime material on success and failure;
9. leave Jenkins untouched.

The exact command name is selected in WU1.

## 3. Runtime input security

Secrets must not be supplied as committed configuration.

The preferred input path is:

- the local shell prompts for endpoint and account data;
- passwords use non-echoing input;
- runtime data is sent over ADB stdin rather than embedded in a command line;
- the debug app receives it in a temporary app-private file;
- the instrumentation process reads it once;
- the file is deleted immediately after reading and again from a cleanup trap.

No raw runtime value may be printed by the runner or test.

A throwable whose message may contain endpoint or media information must not be
used directly as public test evidence.

Failures are converted to fixed sanitized failure codes.

## 4. Device identity privacy

The runner may internally select an ADB device.

It must not print or persist the device serial.

If exactly one authorized device is available, it is selected automatically.

If device selection is ambiguous, execution is BLOCKED unless the caller
privately selects a device through a local-only mechanism.

The selected serial never becomes committed evidence.

## 5. UI automation strategy

Compose semantics are the primary UI-driving mechanism.

Existing user-facing labels may be used where stable and unambiguous.

Where stable selection requires a dedicated hook, production UI may add the
smallest semantic test tag necessary.

Test tags MUST describe role or list position, not private data.

Allowed examples:

- `recent-album-0`
- `recent-album-1`
- `album-track-0`
- `album-track-1`
- `mini-player`
- `now-playing`

Disallowed examples include embedding:

- server URL;
- username;
- album id;
- album title;
- artist;
- track id;
- track title.

The tags are selectors, not evidence.

## 6. Why candidate discovery needs a test-only real-server probe

The current `AlbumTrack` product model contains playback-safe track metadata
such as id, title, artist, track/disc numbers, duration and cover-art id.

It intentionally does not expose codec/suffix information.

Therefore #17 should not expand the product domain model solely to make a QA
assertion about FLAC.

Instead, the instrumented validation may use a narrowly scoped test-only
OpenSubsonic probe.

That probe should reuse production authentication/signing and defensive
transport components where practical.

It may inspect only the minimum response fields needed to determine:

- the Recent Albums ordering;
- album track count;
- whether a candidate contains a FLAC track.

Raw responses and private metadata are never logged.

## 7. Dynamic candidate algorithm

Candidate selection is deterministic by properties, not by name.

For each Recent Albums entry in order:

1. obtain its track list through the privacy-safe test probe;
2. require at least three tracks;
3. require at least one track whose real server metadata identifies it as FLAC;
4. stop at the first matching album.

The validator keeps only ephemeral in-memory information required to drive the
test.

Public evidence records:

- candidate found or not found;
- track-count condition passed or blocked;
- FLAC condition passed or blocked.

It does not record which album or track satisfied the condition.

## 8. Mapping the candidate to UI

The app and test probe must use matching Recent Albums request semantics.

The chosen candidate is mapped to the corresponding UI list position.

The UI test selects the positional semantic node rather than searching for the
private title.

After opening it, the test verifies the album screen exposes at least three
track selectors.

The FLAC track position determined by the probe is selected through its
positional track selector.

If list ordering cannot be proven equivalent, implementation must stop and
introduce a privacy-safe deterministic mapping rather than guessing.

## 9. Queue validation

Opening a track from Album Details already supplies the album track list to the
service-owned queue.

For a qualifying album, the automated scenario validates at least:

- selected track enters active playback;
- queue ownership belongs to the authenticated account;
- Next changes the selected queue entry;
- a second Next reaches another queue entry where available;
- Previous returns to the prior queue entry;
- the queue does not wrap unexpectedly.

Assertions use position/selection state or fixed playback state.

Private track names are not emitted.

## 10. FLAC playback proof

The real-server probe establishes that the selected track is FLAC.

The UI then selects that exact positional track.

The test proves the real app reaches active Media3 playback for that selection.

The validator does not publish:

- codec response payload;
- stream URL;
- media id;
- media title.

The evidence is only `FLAC_PLAYBACK=PASS` or a sanitized failure/block reason.

## 11. Background and system media controls

UIAutomator or narrowly scoped shell commands may move the app to background
and issue standard Android media key events.

The validator must not parse or publish `dumpsys media_session` metadata.

Expected flow:

- establish active playback;
- background the app;
- wait for playback to remain active;
- send Pause through Android system media transport;
- return to the app and verify paused state;
- background again;
- send Play;
- verify playing state;
- exercise Next/Previous where supported;
- return to the app and verify the service-owned queue reflects the command.

This proves system integration without collecting notification metadata.

Visual styling of notifications is not part of #17.

## 12. Sign-out and account isolation

With one supplied identity, the automated suite validates:

- sign-out succeeds;
- account-bound navigation disappears;
- recent library content is no longer visible;
- mini-player/Now Playing no longer exposes prior runtime playback;
- runtime queue ownership is cleared.

If a second real identity is supplied, the suite additionally validates:

- authenticate identity A;
- establish library/playback/queue state;
- sign out;
- authenticate identity B;
- previous account library state is not inherited;
- previous queue does not become B's runtime queue;
- previous playback does not resume as B.

No private account names are used in assertions or evidence.

If the second identity is absent, that optional stronger scenario is reported
as BLOCKED/NOT RUN according to the final runner contract rather than faked.

## 13. Evidence contract

Successful public output should contain only stable labels such as:

- `REAL_INSTANCE_CONNECTION=PASS`
- `AUTHENTICATION=PASS`
- `RECENT_ALBUMS=PASS`
- `CANDIDATE_ALBUM=PASS`
- `CANDIDATE_TRACK_COUNT_GE_3=PASS`
- `FLAC_CANDIDATE=PASS`
- `FLAC_PLAYBACK=PASS`
- `QUEUE_NEXT=PASS`
- `QUEUE_PREVIOUS=PASS`
- `BACKGROUND_PLAYBACK=PASS`
- `SYSTEM_MEDIA_CONTROLS=PASS`
- `SIGN_OUT_ISOLATION=PASS`
- `ACCOUNT_SWITCH_ISOLATION=PASS`
- `PRIVATE_METADATA_RECORDED=NO`
- `REAL_INSTANCE_VALIDATION=PASS`

No selected media metadata accompanies these labels.

## 14. Failure policy

A deterministic product defect found against the real server is not patched
only inside the real-instance test.

Instead:

1. sanitize the observation;
2. reproduce the behavior with synthetic/unit coverage where possible;
3. add the smallest product fix;
4. pass normal Android gates;
5. retry the real scenario.

Environmental limitations produce BLOCKED rather than false FAIL where
appropriate.

Examples include:

- no connected device;
- device unauthorized;
- no qualifying recent album;
- real server temporarily unreachable;
- missing second account for the optional two-identity scenario.

## 15. WU boundaries

### WU0 — contract and automation design

Documentation/OpenSpec only.

No Android test source, Gradle dependency, runner script, Jenkins change or
production Kotlin change.

### WU1 — local runner and instrumentation foundation

Add the smallest local Android instrumentation foundation:

- required test dependencies;
- privacy-safe runtime-input transport;
- device preflight;
- basic launch/authentication smoke path;
- sanitized output.

No Jenkins integration.

### WU2 — dynamic media, FLAC and queue validation

Add:

- privacy-safe real-server candidate probe;
- positional semantic selectors where required;
- dynamic >=3-track + FLAC candidate selection;
- album navigation;
- FLAC playback;
- queue Next/Previous validation.

### WU3 — background, system controls and isolation

Add:

- background/foreground orchestration;
- Android media transport control validation;
- sign-out validation;
- optional second-identity account-switch validation.

### WU4 — real execution evidence and closeout

Run the complete suite against the user-provided instance.

Commit only sanitized validation evidence and documentation.

Any product bug discovered first receives deterministic regression coverage.

## 16. Changed-line governance

Each WU must remain below 1000 changed lines.

Prefer approximately 800 to 850 lines or less so implementation remains
reviewable.

Large testing-framework work must be deferred to #104 rather than expanding
#17.
