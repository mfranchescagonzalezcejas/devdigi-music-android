#!/usr/bin/env bash

set +x
set -uo pipefail

blocked() {
    echo "$1=BLOCKED"
    exit 2
}

failed() {
    echo "$1=FAIL"
    exit 1
}

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" ||
    exit 2

cd "$ROOT" || exit 2

# This tool must never run with production credentials in a PR.
[[ -n "${JENKINS_URL:-}" ]] ||
    blocked 'TRUSTED_JENKINS_CONTEXT'

[[ "${BRANCH_NAME:-}" == 'main' ]] ||
    blocked 'TRUSTED_BRANCH'

[[ -z "${CHANGE_ID:-}" ]] ||
    blocked 'PULL_REQUEST_FORBIDDEN'

[[ "${DEVDIGI_RC_SMOKE_SIGNING_ENABLED:-}" == 'true' ]] ||
    blocked 'SIGNING_NOT_ENABLED'

[[ -n "${GIT_COMMIT:-}" ]] ||
    blocked 'GIT_PROVENANCE'

SOURCE_COMMIT="$(git rev-parse HEAD)" ||
    blocked 'SOURCE_COMMIT'

[[ "$SOURCE_COMMIT" == "$GIT_COMMIT" ]] ||
    blocked 'SOURCE_COMMIT_MISMATCH'

for name in \
    DEVDIGI_RELEASE_STORE_FILE \
    DEVDIGI_RELEASE_STORE_PASSWORD \
    DEVDIGI_RELEASE_KEY_ALIAS \
    DEVDIGI_RELEASE_KEY_PASSWORD \
    DEVDIGI_RC_REFERENCE_APK \
    DEVDIGI_RC_APPROVED_SHA256
do
    [[ -n "${!name:-}" ]] ||
        blocked 'SIGNING_INPUT_MISSING'
done

[[ -f "$DEVDIGI_RELEASE_STORE_FILE" ]] ||
    blocked 'RELEASE_KEYSTORE_MISSING'

[[ -f "$DEVDIGI_RC_REFERENCE_APK" ]] ||
    blocked 'APPROVED_RC_MISSING'

EXPECTED_SHA="${DEVDIGI_RC_APPROVED_SHA256,,}"

[[ "$EXPECTED_SHA" =~ ^[0-9a-f]{64}$ ]] ||
    blocked 'APPROVED_SHA_FORMAT'

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"

APKSIGNER="$(
    find "$SDK_ROOT/build-tools" \
        -type f -name apksigner 2>/dev/null |
        sort -V | tail -n 1
)"

[[ -n "$APKSIGNER" && -x "$APKSIGNER" ]] ||
    blocked 'APKSIGNER_MISSING'

AAPT="${APKSIGNER%/*}/aapt"

[[ -x "$AAPT" ]] ||
    blocked 'AAPT_MISSING'

command -v python3 >/dev/null 2>&1 ||
    blocked 'PYTHON_MISSING'

command -v sha256sum >/dev/null 2>&1 ||
    blocked 'SHA256SUM_MISSING'

APK_DIR='app/build/outputs/apk/androidTest/debug'

mapfile -t TEST_APKS < <(
    find "$APK_DIR" -maxdepth 1 -type f \
        -name '*.apk' 2>/dev/null | sort
)

[[ "${#TEST_APKS[@]}" -eq 1 ]] ||
    blocked 'TEST_APK_DISCOVERY'

TEST_APK="${TEST_APKS[0]}"
RC_APK="$DEVDIGI_RC_REFERENCE_APK"

ACTUAL_RC_SHA="$(sha256sum "$RC_APK" | awk '{print $1}')"

[[ "$ACTUAL_RC_SHA" == "$EXPECTED_SHA" ]] ||
    blocked 'APPROVED_RC_SHA'

echo 'APPROVED_RC_SHA=PASS'

# Check binary manifests without printing private paths or metadata.
python3 - "$AAPT" "$RC_APK" "$TEST_APK" <<'PY'
import re
import subprocess
import sys

aapt, rc_apk, test_apk = sys.argv[1:]

def dump(*args):
    result = subprocess.run(
        [aapt, "dump", *args],
        capture_output=True,
        text=True,
    )
    if result.returncode:
        raise SystemExit("APK_MANIFEST_READ=BLOCKED")
    return result.stdout

def package(path):
    output = dump("badging", path)
    match = re.search(
        r"(?m)^package:\s+name='([^']+)'",
        output,
    )
    return match.group(1) if match else ""

if package(rc_apk) != "dev.devdigi.music":
    raise SystemExit("APPROVED_RC_PACKAGE=BLOCKED")

if package(test_apk) != "dev.devdigi.music.test":
    raise SystemExit("TEST_PACKAGE=BLOCKED")

tree = dump(
    "xmltree",
    test_apk,
    "AndroidManifest.xml",
)

nodes = re.findall(
    r"E:\s*instrumentation\b(.*?)(?=\n\s*E:|\Z)",
    tree,
    re.S,
)

if len(nodes) != 1:
    raise SystemExit("INSTRUMENTATION_COUNT=BLOCKED")

target = re.search(
    r'targetPackage[^=]*="([^"]+)"',
    nodes[0],
)

runner = re.search(
    r'android:name[^=]*="([^"]+)"',
    nodes[0],
)

if not target or target.group(1) != "dev.devdigi.music":
    raise SystemExit("INSTRUMENTATION_TARGET=BLOCKED")

if not runner or runner.group(1) != (
    "androidx.test.runner.AndroidJUnitRunner"
):
    raise SystemExit("INSTRUMENTATION_RUNNER=BLOCKED")

print("APPROVED_RC_PACKAGE=PASS")
print("TEST_PACKAGE=PASS")
print("INSTRUMENTATION_TARGET=PASS")
print("INSTRUMENTATION_RUNNER=PASS")
PY

[[ "$?" -eq 0 ]] ||
    blocked 'APK_MANIFEST_GATE'

"$APKSIGNER" verify "$RC_APK" >/dev/null 2>&1 ||
    blocked 'APPROVED_RC_SIGNATURE'

"$APKSIGNER" verify "$TEST_APK" >/dev/null 2>&1 ||
    blocked 'TEST_INPUT_SIGNATURE'

cert_digest() {
    "$APKSIGNER" verify --print-certs "$1" 2>/dev/null |
        grep -i 'certificate SHA-256 digest:' |
        head -n 1 |
        sed -E 's/^.*:[[:space:]]*//' |
        tr -d '\r'
}

RC_CERT="$(cert_digest "$RC_APK")"

[[ -n "$RC_CERT" ]] ||
    blocked 'REFERENCE_CERTIFICATE'

OUT_DIR="$ROOT/app/build/outputs/rc-smoke"
OUT_APK="$OUT_DIR/devdigi-music-rc1-test-signed.apk"
OUT_SHA="$OUT_DIR/rc-test-sha256.txt"
OUT_PROVENANCE="$OUT_DIR/rc-test-provenance.txt"

mkdir -p "$OUT_DIR" ||
    blocked 'OUTPUT_DIRECTORY'

[[ ! -e "$OUT_APK" && ! -L "$OUT_APK" ]] ||
    blocked 'OUTPUT_ALREADY_EXISTS'

TMP_OUT="$(mktemp "$OUT_DIR/.signing.XXXXXXXX.apk")" ||
    blocked 'TEMP_SIGNING_OUTPUT'

cleanup() {
    rm -f -- "$TMP_OUT"
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

# Only this command executes within the trusted credential scope.
# Password values never appear in command arguments or logs.
if ! "$APKSIGNER" sign \
    --ks "$DEVDIGI_RELEASE_STORE_FILE" \
    --ks-key-alias "$DEVDIGI_RELEASE_KEY_ALIAS" \
    --ks-pass env:DEVDIGI_RELEASE_STORE_PASSWORD \
    --key-pass env:DEVDIGI_RELEASE_KEY_PASSWORD \
    --out "$TMP_OUT" \
    "$TEST_APK" \
    >/dev/null 2>&1
then
    failed 'RC_TEST_SIGNING'
fi

"$APKSIGNER" verify "$TMP_OUT" >/dev/null 2>&1 ||
    failed 'SIGNED_TEST_VERIFICATION'

SIGNED_CERT="$(cert_digest "$TMP_OUT")"

[[ -n "$SIGNED_CERT" && "$SIGNED_CERT" == "$RC_CERT" ]] ||
    failed 'SIGNED_TEST_CERTIFICATE_MISMATCH'

SIGNED_SHA="$(sha256sum "$TMP_OUT" | awk '{print $1}')"

mv -- "$TMP_OUT" "$OUT_APK" ||
    failed 'SIGNED_OUTPUT_PROMOTION'

printf '%s  %s\n' \
    "$SIGNED_SHA" \
    "$(basename "$OUT_APK")" > "$OUT_SHA" ||
    failed 'SHA_REPORT'

{
    printf 'source_commit=%s\n' "$SOURCE_COMMIT"
    printf 'approved_rc_sha256=%s\n' "$EXPECTED_SHA"
    printf 'signed_test_sha256=%s\n' "$SIGNED_SHA"
    printf 'test_package=dev.devdigi.music.test\n'
    printf 'target_package=dev.devdigi.music\n'
} > "$OUT_PROVENANCE" ||
    failed 'PROVENANCE_REPORT'

echo 'RC_TEST_APK_SIGNING=PASS'
echo 'RC_TEST_CERT_MATCH=PASS'
echo 'RC_TEST_PROVENANCE=PASS'
echo 'PRODUCTION_RC_REBUILT=NO'
echo 'DEVICE_OPERATIONS=NONE'
