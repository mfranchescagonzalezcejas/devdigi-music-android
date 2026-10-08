#!/usr/bin/env bash

set +x
set -uo pipefail

ROOT="$(
    cd "$(
        dirname "${BASH_SOURCE[0]}"
    )/.." &&
        pwd
)"

cd "$ROOT" || exit 1

ADB="${ADB:-adb}"
readonly ADB

APP_ID='dev.devdigi.music'
TEST_APP_ID='dev.devdigi.music.test'
readonly APP_ID TEST_APP_ID

TEST_CLASS='dev.devdigi.music.realinstance.RealInstanceAuthenticationTest'
INPUT_SOURCE_ARGUMENT='devdigiInputSource'
readonly TEST_CLASS INPUT_SOURCE_ARGUMENT

ENV_FILE="${DEVDIGI_NAVIDROME_ENV:-$HOME/.config/devdigi-music/navidrome-test.env}"

RC_APK="${DEVDIGI_RC_APK:-}"
SHA_FILE="${DEVDIGI_RC_SHA256_FILE:-}"
TEST_APK="${DEVDIGI_RC_TEST_APK:-}"

REPORT="${DEVDIGI_RC_SMOKE_JUNIT:-$ROOT/build/test-results/rcSmoke/TEST-rc-smoke.xml}"
readonly REPORT

EXPECTED_VERSION_NAME="${DEVDIGI_RC_VERSION_NAME:-0.1.0}"

EXPECTED_VERSION_CODE="${DEVDIGI_RC_VERSION_CODE:-1}"

SERIAL=''
TEST_APP_MUTATED='NO'

TMP_DIR="$(mktemp -d)"
[[ -n "$TMP_DIR" && -d "$TMP_DIR" ]] || exit 2
RUN_LOG="$TMP_DIR/instrumentation.log"
readonly TMP_DIR RUN_LOG

cleanup() {
    set +e

    unset \
        ENDPOINT \
        USERNAME \
        PASSWORD

    if [[ -n "$SERIAL" && "$TEST_APP_MUTATED" == YES ]]; then
        "$ADB" \
            -s "$SERIAL" \
            shell \
            run-as "$TEST_APP_ID" \
            sh -c \
            'rm -f files/real_instance_input.json' \
            </dev/null \
            >/dev/null 2>&1 ||
            true

        "$ADB" \
            -s "$SERIAL" \
            uninstall "$TEST_APP_ID" \
            >/dev/null 2>&1 ||
            true
    fi

    rm -rf -- "$TMP_DIR"
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

blocked() {
    write_junit 'BLOCKED' "$1" >/dev/null 2>&1 || true
    echo "$1=BLOCKED"
    echo 'PRIVATE_METADATA_RECORDED=NO'
    exit 2
}

failed() {
    write_junit 'FAIL' "$1" >/dev/null 2>&1 || true
    echo "$1=FAIL"
    echo 'PRIVATE_METADATA_RECORDED=NO'
    exit 1
}

require_file() {
    [[ -n "$2" && -f "$2" ]] ||
        blocked "$1"
}

cert_digest() {
    "$APKSIGNER" \
        verify \
        --print-certs \
        "$1" \
        2>&1 |
        grep -i \
            'certificate SHA-256 digest:' |
        head -n 1 |
        sed -E \
            's/^.*:[[:space:]]*//' |
        tr -d '\r'
}

write_junit() {
    local status="$1"
    local stage="${2:-}"

    [[ ! -L "$REPORT" ]] || return 1

    mkdir -p \
        "$(dirname "$REPORT")" ||
        return 1

    RC_SMOKE_STATUS="$status" \
    RC_SMOKE_STAGE="$stage" \
    RC_SMOKE_REPORT="$REPORT" \
        python3 - <<'PY'
import os
import xml.etree.ElementTree as ET

cases = [
    (
        "MUSIC-64",
        "Saving a compatible server does not authenticate the user",
    ),
    (
        "MUSIC-65",
        "Sign in successfully with valid credentials",
    ),
    (
        "MUSIC-72",
        "Recent albums load for the authenticated account",
    ),
    (
        "MUSIC-75",
        "Open album details from Recent Albums",
    ),
    (
        "MUSIC-80",
        "Start FLAC playback from an album",
    ),
    (
        "MUSIC-81",
        "Selecting an album track creates an ordered queue",
    ),
    (
        "MUSIC-82",
        "Next advances to the next queue item",
    ),
    (
        "MUSIC-83",
        "Previous returns to the previous queue item",
    ),
    (
        "MUSIC-85",
        "Playback continues when the app moves to background",
    ),
    (
        "MUSIC-86",
        "Android system Play and Pause controls",
    ),
    (
        "MUSIC-87",
        "Android system Next and Previous controls",
    ),
    (
        "MUSIC-89",
        "Sign out clears playback and queue ownership",
    ),
    (
        "MUSIC-69",
        "Sign out clears authenticated presentation state",
    ),
]

status = os.environ["RC_SMOKE_STATUS"]
stage = os.environ.get(
    "RC_SMOKE_STAGE",
    "",
)
report = os.environ["RC_SMOKE_REPORT"]

if status == "PASS":
    suite = ET.Element(
        "testsuite",
        name="DevDigi Music RC Smoke",
        tests=str(len(cases)),
        failures="0",
        skipped="0",
    )

    for key, name in cases:
        ET.SubElement(
            suite,
            "testcase",
            classname="devdigi.music.rc.smoke",
            name=f"{key} — {name}",
        )
elif status in {"FAIL", "BLOCKED"}:
    is_blocked = status == "BLOCKED"
    suite = ET.Element(
        "testsuite",
        name="DevDigi Music RC Smoke",
        tests=str(len(cases) + 1),
        failures="0" if is_blocked else "1",
        skipped=str(len(cases) + (1 if is_blocked else 0)),
    )

    for key, name in cases:
        case = ET.SubElement(
            suite,
            "testcase",
            classname="devdigi.music.rc.smoke",
            name=f"{key} — {name}",
        )

        ET.SubElement(
            case,
            "skipped",
            message="Not promoted after RC Smoke failure",
        )

    harness = ET.SubElement(
        suite,
        "testcase",
        classname="devdigi.music.rc.smoke",
        name="RC smoke harness",
    )

    result = ET.SubElement(
        harness,
        "skipped" if is_blocked else "failure",
        message="RC Smoke blocked" if is_blocked else "RC Smoke failed",
    )

    result.text = stage or "UNCLASSIFIED"
else:
    raise ValueError("Unknown RC Smoke report status")

ET.ElementTree(
    suite,
).write(
    report,
    encoding="utf-8",
    xml_declaration=True,
)
PY
}


echo '===== RC SMOKE / SIGNED ARTIFACT ====='

# Prevent a stale PASS report being mistaken for this execution's evidence.
# Only a regular report file at the configured output path is removable.
if [[ -L "$REPORT" || ( -e "$REPORT" && ! -f "$REPORT" ) ]]; then
    echo 'JUNIT_REPORT_PATH=BLOCKED'
    exit 2
fi

if [[ -f "$REPORT" ]]; then
    rm -f -- "$REPORT" || {
        echo 'JUNIT_REPORT_RESET=BLOCKED'
        exit 2
    }
fi


echo
echo '--- tool preflight ---'

command -v "$ADB" \
    >/dev/null 2>&1 ||
    blocked 'ADB'

command -v python3 \
    >/dev/null 2>&1 ||
    blocked 'PYTHON'

command -v sha256sum \
    >/dev/null 2>&1 ||
    blocked 'SHA256SUM'

require_file \
    'RC_APK' \
    "$RC_APK"

require_file \
    'RC_SHA256_FILE' \
    "$SHA_FILE"

require_file \
    'RC_TEST_APK' \
    "$TEST_APK"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"

APKSIGNER="$(
    find \
        "$SDK_ROOT/build-tools" \
        -type f \
        -name apksigner \
        2>/dev/null |
        sort -V |
        tail -n 1
)"

[[ -n "$APKSIGNER" ]] ||
    blocked 'APKSIGNER'

AAPT="${APKSIGNER%/*}/aapt"
[[ -x "$AAPT" ]] || blocked 'AAPT'

# Read package IDs from APK manifests; a certificate match alone does not
# distinguish the application APK from the instrumentation APK.
package_name() {
    "$AAPT" dump badging "$1" 2>/dev/null |
        sed -n "s/^package: name='\([^']*\)'.*/\1/p" |
        head -n 1
}

[[ "$(package_name "$RC_APK")" == "$APP_ID" ]] ||
    blocked 'RC_APK_PACKAGE'
[[ "$(package_name "$TEST_APK")" == "$TEST_APP_ID" ]] ||
    blocked 'RC_TEST_APK_PACKAGE'

echo 'APK_PACKAGE_IDENTITIES=PASS'
echo 'TOOL_PREFLIGHT=PASS'


echo
echo '--- device preflight ---'

mapfile -t DEVICES < <(
    "$ADB" devices |
        awk '
            NR > 1 &&
            $2 == "device" {
                print $1
            }
        '
)

[[ "${#DEVICES[@]}" -eq 1 ]] ||
    blocked 'DEVICE_PREFLIGHT'

SERIAL="${DEVICES[0]}"
readonly SERIAL

echo 'DEVICE_PREFLIGHT=PASS'


echo
echo '--- RC provenance ---'

RC_NAME="$(basename "$RC_APK")"

EXPECTED_SHA="$(
    awk \
        -v name="$RC_NAME" \
        '$2 == name {
            print $1
            exit
        }' \
        "$SHA_FILE"
)"

ACTUAL_SHA="$(
    sha256sum "$RC_APK" |
        awk '{ print $1 }'
)"

[[ -n "$EXPECTED_SHA" ]] ||
    failed 'RC_PROVENANCE'

[[ "$EXPECTED_SHA" == "$ACTUAL_SHA" ]] ||
    failed 'RC_PROVENANCE'

"$APKSIGNER" \
    verify \
    "$RC_APK" \
    >/dev/null 2>&1 ||
    failed 'RC_SIGNATURE'

"$APKSIGNER" verify "$TEST_APK" >/dev/null 2>&1 ||
    blocked 'RC_TEST_SIGNATURE'

echo 'RC_PROVENANCE=PASS'
echo 'RC_SIGNATURE=PASS'
echo 'RC_TEST_SIGNATURE=PASS'


echo
echo '--- installed RC identity ---'

PACKAGE_INFO="$(
    "$ADB" \
        -s "$SERIAL" \
        shell \
        dumpsys package \
        "$APP_ID" \
        </dev/null \
        2>/dev/null
)"

[[ -n "$PACKAGE_INFO" ]] ||
    blocked 'RC_INSTALLED_PACKAGE'

INSTALLED_VERSION_NAME="$(
    printf '%s\n' \
        "$PACKAGE_INFO" |
        sed -n \
            's/.*versionName=\([^ ]*\).*/\1/p' |
        head -n 1 |
        tr -d '\r'
)"

INSTALLED_VERSION_CODE="$(
    printf '%s\n' \
        "$PACKAGE_INFO" |
        sed -n \
            's/.*versionCode=\([0-9]*\).*/\1/p' |
        head -n 1 |
        tr -d '\r'
)"

[[ "$INSTALLED_VERSION_NAME" == "$EXPECTED_VERSION_NAME" ]] ||
    blocked 'RC_VERSION_NAME'

[[ "$INSTALLED_VERSION_CODE" == "$EXPECTED_VERSION_CODE" ]] ||
    blocked 'RC_VERSION_CODE'

echo 'RC_VERSION_IDENTITY=PASS'


echo
echo '--- signer boundary ---'

INSTALLED_PATH="$(
    "$ADB" \
        -s "$SERIAL" \
        shell \
        pm path \
        --user 0 \
        "$APP_ID" \
        </dev/null \
        2>/dev/null |
        head -n 1 |
        sed 's/^package://' |
        tr -d '\r'
)"

[[ -n "$INSTALLED_PATH" ]] ||
    blocked 'RC_INSTALLED_PATH'

"$ADB" \
    -s "$SERIAL" \
    pull \
    "$INSTALLED_PATH" \
    "$TMP_DIR/installed.apk" \
    >/dev/null 2>&1 ||
    blocked 'RC_INSTALLED_PULL'

# Exact installed artifact identity, not just same signer/version.
INSTALLED_SHA="$(sha256sum "$TMP_DIR/installed.apk" | awk '{print $1}')"
[[ "$INSTALLED_SHA" == "$ACTUAL_SHA" ]] ||
    blocked 'RC_INSTALLED_ARTIFACT'
echo 'RC_INSTALLED_ARTIFACT=PASS'

RC_CERT="$(
    cert_digest \
        "$RC_APK"
)"

INSTALLED_CERT="$(
    cert_digest \
        "$TMP_DIR/installed.apk"
)"

TEST_CERT="$(
    cert_digest \
        "$TEST_APK"
)"

[[ \
    -n "$RC_CERT" &&
    -n "$INSTALLED_CERT" &&
    -n "$TEST_CERT" \
]] ||
    failed 'SIGNER_READ'

[[ "$RC_CERT" == "$INSTALLED_CERT" ]] ||
    blocked 'RC_INSTALLED_SIGNER'

[[ "$RC_CERT" == "$TEST_CERT" ]] ||
    blocked 'RC_TEST_SIGNER'

echo 'RC_SIGNER_MATCH=PASS'
echo 'RC_TEST_SIGNER_MATCH=PASS'


echo
echo '--- install test driver only ---'

TEST_APP_MUTATED='YES'
readonly TEST_APP_MUTATED

"$ADB" \
    -s "$SERIAL" \
    uninstall \
    "$TEST_APP_ID" \
    >/dev/null 2>&1 ||
    true

TEST_INSTALL="$(
    "$ADB" \
        -s "$SERIAL" \
        install \
        -t \
        "$TEST_APK" \
        2>&1
)"

TEST_INSTALL_RC=$?

if [[ "$TEST_INSTALL_RC" -ne 0 ]] ||
    ! grep -q \
        'Success' \
        <<<"$TEST_INSTALL"
then
    failed 'RC_TEST_INSTALL'
fi

echo 'RC_TEST_INSTALL=PASS'
echo 'RC_APP_REPLACED=NO'


echo
echo '--- discover instrumentation ---'

INSTRUMENTATION="$(
    "$ADB" \
        -s "$SERIAL" \
        shell \
        pm list instrumentation \
        </dev/null |
        sed -n \
            "s/^instrumentation:\([^ ]*\) (target=${APP_ID//./\\.})$/\1/p" |
        head -n 1 |
        tr -d '\r'
)"

[[ -n "$INSTRUMENTATION" ]] ||
    failed 'INSTRUMENTATION_DISCOVERY'

[[ "$INSTRUMENTATION" == "$TEST_APP_ID/"* ]] ||
    failed 'INSTRUMENTATION_PACKAGE'

echo 'INSTRUMENTATION_DISCOVERY=PASS'


echo
echo '--- runtime-only credentials ---'

if [[ -f "$ENV_FILE" ]]; then
    [[ ! -L "$ENV_FILE" ]] || blocked 'RUNTIME_ENV_SYMLINK'
    [[ "$(stat -c '%u' "$ENV_FILE" 2>/dev/null)" == "$(id -u)" ]] ||
        blocked 'RUNTIME_ENV_OWNER'

    ENV_MODE="$(
        stat \
            -c '%a' \
            "$ENV_FILE" \
            2>/dev/null ||
            true
    )"

    case "$ENV_MODE" in
        400|600)
            ;;
        *)
            blocked 'RUNTIME_ENV_PERMISSIONS'
            ;;
    esac

    set +u

    # Local-only file containing export statements.
    # shellcheck disable=SC1090
    source "$ENV_FILE" >/dev/null 2>&1

    SOURCE_RC=$?

    set +x
    set -u

    [[ "$SOURCE_RC" -eq 0 ]] ||
        failed 'RUNTIME_ENV_LOAD'

    LOCAL_ENDPOINT="${DEVDIGI_NAVIDROME_LOCAL_URL:-}"

    TAILSCALE_ENDPOINT="${DEVDIGI_NAVIDROME_TAILSCALE_URL:-}"

    if [[ "$LOCAL_ENDPOINT" == https://* ]]; then
        ENDPOINT="$LOCAL_ENDPOINT"
    elif [[ "$TAILSCALE_ENDPOINT" == https://* ]]; then
        ENDPOINT="$TAILSCALE_ENDPOINT"
    else
        ENDPOINT=''
    fi

    USERNAME="${DEVDIGI_NAVIDROME_USER:-}"

    PASSWORD="${DEVDIGI_NAVIDROME_PASSWORD:-}"

    unset \
        LOCAL_ENDPOINT \
        TAILSCALE_ENDPOINT \
        DEVDIGI_NAVIDROME_LOCAL_URL \
        DEVDIGI_NAVIDROME_TAILSCALE_URL \
        DEVDIGI_NAVIDROME_USER \
        DEVDIGI_NAVIDROME_PASSWORD
else
    printf \
        'Navidrome HTTPS URL (hidden): '

    IFS= read \
        -r \
        -s \
        ENDPOINT

    printf '\n'

    printf \
        'Navidrome username (hidden): '

    IFS= read \
        -r \
        -s \
        USERNAME

    printf '\n'

    printf \
        'Navidrome password (hidden): '

    IFS= read \
        -r \
        -s \
        PASSWORD

    printf '\n'
fi

[[ "$ENDPOINT" == https://* ]] ||
    blocked 'HTTPS_ENDPOINT'

[[ \
    -n "$USERNAME" &&
    -n "$PASSWORD" \
]] ||
    blocked 'RUNTIME_INPUT'

if ! (
    REAL_ENDPOINT="$ENDPOINT" \
    REAL_USERNAME="$USERNAME" \
    REAL_PASSWORD="$PASSWORD" \
        python3 - <<'PYJSON'
import json
import os
import sys

json.dump(
    {
        "endpoint":
            os.environ["REAL_ENDPOINT"],
        "username":
            os.environ["REAL_USERNAME"],
        "password":
            os.environ["REAL_PASSWORD"],
    },
    sys.stdout,
    separators=(",", ":"),
)
PYJSON
) |
    "$ADB" \
        -s "$SERIAL" \
        shell \
        run-as "$TEST_APP_ID" \
        sh -c \
        'umask 077; mkdir -p files; cat > files/real_instance_input.json'
then
    unset \
        ENDPOINT \
        USERNAME \
        PASSWORD

    failed 'RUNTIME_INPUT_STAGE'
fi

unset \
    ENDPOINT \
    USERNAME \
    PASSWORD

echo 'RUNTIME_INPUT_STAGE=PASS'


echo
echo '--- execute signed RC Smoke ---'

"$ADB" \
    -s "$SERIAL" \
    shell \
    am instrument \
    -w \
    -r \
    -e \
    "$INPUT_SOURCE_ARGUMENT" \
    instrumentation \
    -e \
    class \
    "$TEST_CLASS" \
    "$INSTRUMENTATION" \
    >"$RUN_LOG" \
    2>&1

RUN_RC=$?

"$ADB"     -s "$SERIAL"     shell     run-as "$TEST_APP_ID"     sh -c     'rm -f files/real_instance_input.json'     </dev/null     >/dev/null 2>&1 ||
    failed 'RUNTIME_INPUT_DELETION'

echo 'RUNTIME_INPUT_DELETION=PASS'


if [[ "$RUN_RC" -ne 0 ]] ||
    ! grep -Eq \
        '^[[:space:]]*OK \(1 test\)[[:space:]]*$' \
        "$RUN_LOG" ||
    grep -Eq \
        'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' \
        "$RUN_LOG"
then
    STAGE_MARKER="$(
        grep -Eo \
            'REAL_STAGE_[A-Z0-9_]+' \
            "$RUN_LOG" |
            head -n 1
    )"

    FAIL_STAGE="${STAGE_MARKER#REAL_STAGE_}"

    [[ -n "$FAIL_STAGE" ]] ||
        FAIL_STAGE='UNCLASSIFIED'

    write_junit \
        'FAIL' \
        "$FAIL_STAGE" ||
        true

    echo "FAIL_STAGE=$FAIL_STAGE"
    echo 'RC_SMOKE=FAIL'
    echo 'PRIVATE_METADATA_RECORDED=NO'

    exit 1
fi



echo
echo '--- validate actual Smoke case evidence ---'

if ! python3 - "$RUN_LOG" <<'PY_CASES'
import pathlib
import re
import sys

expected = [
    "MUSIC-64", "MUSIC-65", "MUSIC-72",
    "MUSIC-75", "MUSIC-80", "MUSIC-81",
    "MUSIC-82", "MUSIC-83", "MUSIC-85",
    "MUSIC-86", "MUSIC-87", "MUSIC-89",
    "MUSIC-69",
]

log = pathlib.Path(sys.argv[1]).read_text(
    encoding="utf-8",
    errors="replace",
)

observed = []

for line in log.splitlines():
    match = re.fullmatch(
        r"INSTRUMENTATION_STATUS: devdigi\.rc\.case=(MUSIC-[0-9]+)",
        line.strip(),
    )
    if match:
        observed.append(match.group(1))

if observed != expected:
    raise SystemExit("RC_SMOKE_CASE_EVIDENCE=FAIL")

print("RC_SMOKE_CASE_EVIDENCE=PASS")
print("RC_SMOKE_VERIFIED_CASES=13")
PY_CASES
then
    failed 'RC_SMOKE_CASE_EVIDENCE'
fi

echo
echo '--- result mapping ---'

write_junit \
    'PASS' \
    '' ||
    failed 'JUNIT_REPORT'

for key in \
    64 \
    65 \
    72 \
    75 \
    80 \
    81 \
    82 \
    83 \
    85 \
    86 \
    87 \
    89 \
    69
do
    echo \
        "AGILETEST_MUSIC_${key}=PASS"
done

echo \
    "JUNIT_REPORT=${REPORT#"$ROOT"/}"

echo 'PRIVATE_METADATA_RECORDED=NO'
echo 'RC_APP_REPLACED=NO'
echo 'RC_SMOKE=PASS'
