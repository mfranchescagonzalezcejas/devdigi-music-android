# DevDigi Music — local QA and n8n result contract

This WU preserves the existing Android RC Smoke checks and its **signed-release boundary**.
Only the report writer is replaced. Jenkins remains the required PR CI, unchanged.

## WARNING — required device/app state before RC1 Smoke

> **WARNING: RC1 Smoke currently requires a signed-out app with NO saved server URL/profile.**
> Open DevDigi Music on the test device first. A previously registered URL
> must not be present when this test starts. This is a *manual preflight*, not
> an automatic detector, and it applies **every time**, including reruns
> after a previous PASS.

1. Connect and unlock exactly one authorized Android device with the approved
   signed RC1 `dev.devdigi.music` v0.1.0 / versionCode 1 already installed.
   The runner verifies the RC artifact SHA, version, and certificate.
2. Open the app. Confirm it is **signed out**, on the connection screen,
   and has **no saved server URL/profile**. The connection form must be ready
   to save a new server. Do not share the private URL in issue comments/logs.
3. If a server is already saved, **do not run the current Smoke**. If the
   owner explicitly agrees, first sign out (when needed) and use **Delete
   server** inside the app to remove its saved configuration. This may also
   remove credentials; it is a user-visible, potentially destructive action.
   **Do not do this automatically.**
4. Never use **Clear storage / Clear data**, `adb shell pm clear`, production
   RC1 uninstall/reinstall, or an automatic reset to make the test pass.
   If the saved profile must be retained, stop and report this prerequisite
   as a limitation; do not falsify PASS or erase user state.
5. Provide the exact approved `DEVDIGI_RC_APK`, `DEVDIGI_RC_SHA256_FILE`,
   and pre-signed `DEVDIGI_RC_TEST_APK`, plus private HTTPS Navidrome
   credentials. Only then execute `./tools/qa smoke --target rc1`.

**Why this matters:** The current `RealInstanceAuthenticationTest.authenticate()`
replaces the server URL and waits for `connection-save-server` to become
enabled. The UI deliberately disables **Save server** when the URL is equal
to an already persisted profile. In that starting state the test can report
`AUTH_SERVER_ENABLE_FAILED` even when the RC1 and signing boundary are valid.
The warning printed by `tools/qa` is advisory: **there is not yet an
automated saved-profile preflight or safe idempotent test flow**. A future
WU10 will address repeatability without discarding genuine `MUSIC-64`
acceptance coverage.

### Observed execution history (9 October 2026)

- **First valid device run:** RC1 provenance, installed SHA, signatures,
  driver installation, instrumentation discovery and private input all
  passed; `FAIL_STAGE=AUTH_SERVER_ENABLE_FAILED`, with zero case markers.
  The local report correctly recorded `MUSIC-64=FAIL` and the remaining
  12 cases as `NOT RUN`. The user confirmed a URL had already been saved.
- **Subsequent run with the app prepared:** `RC_SMOKE_CASE_EVIDENCE=PASS`,
  `RC_SMOKE_VERIFIED_CASES=13`, `RC_SMOKE=PASS`,
  `LOCAL_QA_STATUS=PASS` and all 13 `MUSIC-*` cases PASS.
- Neither run replaced the production RC1 or published results to n8n or
  AgileTest. Evidence stays in local sanitized JUnit/JSON until case-ID
  reconciliation and explicit publication approval under `MUSIC-92`.
  The prior failure is retained as diagnostic history, not overwritten
  in Jira as if it had never happened.

## Single-command local QA workflow (WU10A)

After cloning on a **new computer**, provision the Android SDK/JDK, Docker
(for regression integration), the authorized immutable release RC1 APK + SHA
manifest, and the **matching pre-signed private instrumentation APK** through
approved private transfer. This repository NEVER contains either APK, passwords,
keystores or private local configuration. There is no public download or
on-demand automatic signing with the production release key.

Run **once per machine**:

```bash
./tools/qa setup
```

The wizard discovers the approved RC1 manifest, validates the signed test APK
against `rc-test-sha256.txt`, and can copy it **with explicit consent** to a
private persistent cache at `~/.local/share/devdigi-music/qa/` (file 0600,
directory 0700). It writes `~/.config/devdigi-music/qa-local.env` as a local
**non-secret path map** (0600). If no `navidrome-test.env` exists, it asks once
for an HTTPS endpoint, test account and password and stores them in
`~/.config/devdigi-music/navidrome-test.env` (0600), only after explicit
acceptance. This is **plaintext at rest protected by OS file permissions**, not
encrypted storage or an OS keyring. Use a low-privilege dedicated test account,
protect workstation backups, and rotate its password if the workstation is
compromised. Never put release keystore passwords in either file.

On subsequent runs:

```bash
./tools/qa smoke
./tools/qa sanity
./tools/qa regression
./tools/qa report
```

`smoke` reuses the approved RC1 + private signed driver and cached Navidrome
input without prompting. The old `./tools/qa smoke --target rc1` form remains
supported. It still performs every existing identity/signer/device check, and
**does not delete any saved server or modify the production APK**. The warning
above remains a manual preflight: on an already configured phone, stop rather
than reset the user's data. Safe repeatability is WU10B, not part of WU10A.

`sanity` runs offline contracts, Spotless, JVM unit tests and AndroidTest
compilation (no device, credentials, Docker or release signing).
`regression` adds Android Lint, debug APK build and a **synthetic** Navidrome
integration run under Docker. It is a repeatable automated regression baseline,
**not a claim of full manual/device regression** and does not replace the
physical RC Smoke. Docker must be available locally. All these commands have
nonzero exit status on failures and do not publish to AgileTest/n8n.

If the signed instrumentation driver is missing after `gradle clean`, rerun
setup with a separately provisioned private APK or restore your authorized
private cache. `qa-local.env` is generated per machine: clone/pull onto
another laptop does not bring it along. Do not commit local `.env` files or
copy them over chat. If rotation is necessary, securely replace your local
credential file yourself after confirming the change; setup refuses to
silently overwrite existing sensitive files.

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
