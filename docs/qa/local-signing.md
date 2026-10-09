# MUSIC-95: Local-only signed RC1 AndroidTest preparation

This is **NOT** a Jenkins signing job or a new release build. The production
`dev.devdigi.music` APK is never signed, installed, or modified by this tool.
The resulting instrumentation APK is highly privileged: it shares the release
signer and can test the installed application. **Keep it private; never publish
or distribute it, attach it to issues, or commit it into Git.**

## Prerequisites

1. Clean, committed Git checkout on `test/95-wu1-rc-smoke-runner`, `develop`, or `main`; no staged, tracked or untracked source changes. Ignored build outputs are allowed.
2. Approved original RC1 APK + `release-sha256.txt` under
   `~/Descargas/devdigi-music-rc1/` (or `DEVDIGI_RC_ARTIFACT_DIR`).
3. Original matching release keystore in private `~/.local/` storage; the keystore
   is read but never copied. In the current audit, candidate 1 `.jks` matched.
4. Android SDK build-tools with `apksigner`, `aapt`; working Gradle wrapper,
   installed JDK, and interactive terminal. No release credentials in Gradle.

## Use

Run `python3 tools/qa-sign-rc-test.py` from the checked-out repository.
The script builds only `:app:assembleDebugAndroidTest` and verifies it targets
`dev.devdigi.music`. It requests candidate selection, keystore password,
and key password privately. Enter at key-password prompt reuses store password.
Both passwords are sent to `apksigner` through stdin, not process arguments or
credential files. Nothing is installed on Android.

After verification, output files under **ignored local build tree**
`app/build/outputs/rc-smoke/`:

- `devdigi-music-rc1-test-signed.apk`
- `rc-test-sha256.txt`
- `rc-test-provenance.txt` — committed source SHA, tracked AndroidTest/Gradle build-input SHA-256, approved RC1 SHA-256 and signed test APK SHA-256 (no passwords or keystore paths).

If any output exists, the script refuses to overwrite it. Review or archive
existing artifacts privately before deliberately removing obsolete files.
This operation changes the local Gradle **build** directory only, not Git.

To execute the real test *after a separate device preflight*, configure these
existing input variables for `./tools/qa smoke --target rc1`:
`DEVDIGI_RC_APK`, `DEVDIGI_RC_SHA256_FILE`, `DEVDIGI_RC_TEST_APK`.
The runner checks the installed RC1 SHA/signature and handles only the test APK.
Provide Navidrome HTTPS input privately; never put secrets in logs or Jira.

**No n8n, AgileTest upload, Jenkins configuration or automatic Smoke execution.**

Trusted Jenkins opt-in (`main` only): only sanitized `.txt` receipts are archived.
The release-signed instrumentation APK is removed from the signing worker
workspace after the stage and must not be distributed through Jenkins archives.
Enabling this stage still requires separate trusted-worker/credential approval.
