# DevDigi Music — local QA and n8n result contract

This WU preserves the existing Android RC Smoke checks and its **signed-release boundary**.
Only the report writer is replaced. Jenkins remains the required PR CI, unchanged.

## Commands

- `./tools/qa report` — view latest sanitized per-case report; does not use ADB.
- `python3 tools/test-qa-report.py` — run synthetic evidence contract tests; does not use ADB.
- `./tools/qa smoke --target rc1` — **real Android Smoke**, only after supplying
  approved `DEVDIGI_RC_APK`, `DEVDIGI_RC_SHA256_FILE`, `DEVDIGI_RC_TEST_APK`
  and required Navidrome input. Never executes automatically.

The RC1 runner will refuse to start unless the installed signed application
matches the reference APK by checksum, version, and signing identity. It never
installs/replaces the production app. It may install/uninstall only the test APK.

## Outputs

- `build/test-results/rcSmoke/TEST-rc-smoke.xml`: JUnit-compatible per-case results.
- `build/test-results/rcSmoke/report.json`: small JSON schema
  `devdigi.qa.result.v1` ready for **future** n8n intake.

Only anchored `INSTRUMENTATION_STATUS: devdigi.rc.case=MUSIC-NN` markers are
accepted, in strict prefix order. On failure, already observed cases remain
`PASS`; an allowlisted failure stage may mark its mapped case `FAIL`; remaining
cases are `NOT RUN`. Preflight `BLOCKED` is never marked PASS. Invalid/missing
markers cannot produce a full PASS report. A harness test in JUnit represents
overall execution failure separately.

JSON records run ID, execution time, source SHA of **instrumentation runner**
(not proof of RC1 artifact SHA or test APK bytes), 13 case statuses, counts, and suite status.
The signed-test provenance receipt separately binds the APK SHA, approved RC1 SHA,
committed source SHA and deterministic AndroidTest/Gradle input fingerprint.
It deliberately excludes raw instrumentation logs, private URLs, usernames,
Android serials, media metadata, tokens, and signing secrets.

**No webhook calls or AgileTest mutations are performed.** n8n can later ingest
this payload via authenticated private webhook; validate schema/run ID, reject
duplicates, notify and stage explicit approval. AgileTest import against
existing `MUSIC-92` needs separate case-ID reconciliation and human approval.
Do not promote debug/synthetic reports into signed-RC evidence.

## AgileTest import safety gate

The JUnit `classname` and `name` pair is preserved from the earlier RC Smoke
report, but this does not prove it matches AgileTest's already registered Test
Definitions. **Do not import automatically** until those definitions are reconciled.
Standard JUnit uses `<skipped>` for both BLOCKED and NOT RUN in this local
report; the JSON retains the exact states. The eventual n8n/AgileTest adapter
must explicitly map BLOCKED to AgileTest `<blocked>`, NOT RUN to `<todo>` (or
leave the case unmodified) and prevent the harness case from creating a new
AgileTest test. Keep this report dry-run only until that verification passes.
