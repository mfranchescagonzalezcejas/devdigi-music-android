# Verification Report

**Change**: real-navidrome-first-sound-validation
**Issue**: #17
**Work unit**: WU4 — real execution and closeout
**Validated base revision**: `decacdcd6c279b64ca9eea5e731a1c1169f1beee`
**Verdict**: PASS, with optional second-identity scenario BLOCKED / NOT RUN

## Real-instance result

The repository-owned local runner completed the complete First Sound slice
against the user-provided real Navidrome/OpenSubsonic instance.

Final canonical execution: **PASS**.

The required path validated:

- BYON connection and authentication;
- Recent Albums destination;
- dynamic album selection with at least three tracks;
- FLAC selection and playback;
- queue Next/Previous;
- background playback;
- Android system Play/Pause/Next/Previous;
- foreground service/UI reconciliation;
- sign-out;
- account-bound presentation clearing;
- runtime playback and queue ownership clearing.

## Sanitized evidence

Only the following allowlisted result labels are retained:

~~~text
REAL_INSTANCE_CONNECTION=PASS
AUTHENTICATION=PASS
RECENT_ALBUMS_DESTINATION=PASS
MEDIA_CANDIDATE=PASS
DYNAMIC_ALBUM_OPEN=PASS
FLAC_PLAYBACK=PASS
QUEUE_NEXT=PASS
QUEUE_PREVIOUS=PASS
RUNTIME_INPUT_DELETION=PASS
WU2_REAL_INSTANCE_MEDIA_QUEUE=PASS
BACKGROUND_PLAYBACK=PASS
SYSTEM_MEDIA_CONTROLS=PASS
SIGN_OUT_ISOLATION=PASS
ACCOUNT_PRESENTATION_CLEAR=PASS
RUNTIME_PLAYBACK_OWNERSHIP_CLEAR=PASS
RUNTIME_QUEUE_OWNERSHIP_CLEAR=PASS
WU3_REQUIRED_SCENARIOS=PASS
SECOND_IDENTITY=NOT_SUPPLIED
ACCOUNT_SWITCH_ISOLATION=BLOCKED
WU3_REAL_INSTANCE_BACKGROUND_ISOLATION=PASS
PRIVATE_METADATA_RECORDED=NO
~~~

Sanitized evidence SHA-256:

~~~text
5ae483c6ff0c28044e851e524ba348539ce685711ea1523968c19c57b26a89b3
~~~

## Deterministic harness defect and regression

WU4 exposed a deterministic test-harness defect while investigating
intermittent `FAIL_STAGE=UNCLASSIFIED` results.

The raw instrumentation failure was safely classified as an Android Compose
timeout. The existing stage wrapper handled `AssertionError` and `Exception`,
but did not classify `ComposeTimeoutException`.

A synthetic instrumentation regression was added first and proved that Compose
timeouts are mapped to their privacy-safe `REAL_STAGE_*` marker.

Authentication was then split into privacy-safe sub-stages. This isolated the
intermittent path to server-setup synchronization.

The harness now waits for the Save server control to return to its persisted,
disabled state after the profile is stored before continuing with credentials.

No production application code was changed.

After the harness fix:

- synthetic Compose-timeout regression: **PASS**;
- five consecutive complete real-instance executions: **5 / 5 PASS**;
- final canonical complete real-instance execution: **PASS**.

No production defect was found.

## Android validation

Final applicable validation:

~~~text
./gradlew spotlessCheck testDebugUnitTest lint assembleDebug assembleDebugAndroidTest
~~~

Result: **PASS**.

Android gate output SHA-256:

~~~text
fe363dbd32bc62ce708bb67bd86f3854001c297372ca35a070a414122ed7f014
~~~

`git diff --check` also passed.

## Privacy

Privacy audit: **PASS**.

Persisted evidence contains no:

- endpoint, hostname or private IP;
- username or account identifier;
- password, token, salt or signed stream URL;
- Android device serial;
- artist, album or track names;
- private library identifiers;
- screenshots or UI hierarchy dumps;
- private media-session metadata.

Runtime credential reflection check: **NO**.
Device serial reflection check: **NO**.
Runner-reported private metadata recording: **NO**.

Raw runtime and Gradle logs remain temporary local artifacts and are not part
of the repository evidence.

## Optional second identity

No second real identity was supplied.

Therefore:

~~~text
SECOND_IDENTITY=NOT_SUPPLIED
ACCOUNT_SWITCH_ISOLATION=BLOCKED
~~~

This remains explicitly **BLOCKED / NOT RUN** and is not reported as PASS.

## Scope

Issue #17 remains within its approved product/testing scope.

- #34 Android instrumented CI/device-farm infrastructure did not enter #17.
- #104 reusable general QA/BDD infrastructure did not enter #17.
- Jenkins was not changed.
- Production application code was not changed.
- The real-instance workflow remains local and opt-in.
- No public hosted test server or telemetry was introduced.

## Documentation reconciliation

No architecture, security or broader QA documentation change is required.

WU4 changes are restricted to the issue-specific real-instance Android test
harness, its synthetic regression, OpenSpec closeout state and this sanitized
verification report.
