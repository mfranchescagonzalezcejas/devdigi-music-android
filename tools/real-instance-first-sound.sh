#!/usr/bin/env bash

set -uo pipefail

ROOT="$(
    cd "$(
        dirname "${BASH_SOURCE[0]}"
    )/.." &&
        pwd
)"

cd "$ROOT" || exit 1

ADB="${ADB:-adb}"

APP_ID='dev.devdigi.music'
TEST_CLASS='dev.devdigi.music.realinstance.RealInstanceAuthenticationTest'
INPUT_FILE='files/real_instance_input.json'

SERIAL=''
RUN_LOG=''

ENV_FILE="${DEVDIGI_NAVIDROME_ENV:-$HOME/.config/devdigi-music/navidrome-test.env}"

SECONDARY_ENDPOINT=''
SECONDARY_USERNAME=''
SECONDARY_PASSWORD=''
SECONDARY_STATE='ABSENT'

cleanup() {
    set +e

    if [[ -n "$SERIAL" ]]; then
        "$ADB" \
            -s "$SERIAL" \
            shell \
            "run-as $APP_ID sh -c 'rm -f $INPUT_FILE'" \
            >/dev/null 2>&1 ||
            true

        "$ADB" \
            -s "$SERIAL" \
            shell \
            pm clear "$APP_ID" \
            >/dev/null 2>&1 ||
            true
    fi

    if [[ -n "$RUN_LOG" ]]; then
        rm -f -- "$RUN_LOG"
    fi
}

trap cleanup EXIT HUP INT TERM

blocked() {
    echo "$1=BLOCKED"
    echo 'PRIVATE_METADATA_RECORDED=NO'
    exit 2
}

failed() {
    echo "$1=FAIL"
    echo 'PRIVATE_METADATA_RECORDED=NO'
    exit 1
}


echo '===== REAL FIRST SOUND / WU2 + WU3 ====='


echo
echo '--- device preflight ---'

command -v "$ADB" >/dev/null 2>&1 ||
    blocked 'ADB'

command -v python3 >/dev/null 2>&1 ||
    blocked 'PYTHON'

mapfile -t DEVICES < <(
    "$ADB" devices |
        awk '
            NR > 1 &&
            $2 == "device" {
                print $1
            }
        '
)

if [[ "${#DEVICES[@]}" -ne 1 ]]; then
    blocked 'DEVICE_PREFLIGHT'
fi

SERIAL="${DEVICES[0]}"

echo 'DEVICE_PREFLIGHT=PASS'


echo
echo '--- build instrumented artifacts ---'

BUILD_LOG="$(mktemp)"

if ! ./gradlew \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    >"$BUILD_LOG" 2>&1
then
    echo 'BUILD=FAIL'
    tail -n 80 "$BUILD_LOG"
    rm -f -- "$BUILD_LOG"
    exit 1
fi

rm -f -- "$BUILD_LOG"

echo 'BUILD=PASS'


echo
echo '--- locate APKs ---'

APP_APK="$(
    find \
        app/build/outputs/apk/debug \
        -maxdepth 1 \
        -type f \
        -name '*.apk' \
        | sort \
        | head -n 1
)"

TEST_APK="$(
    find \
        app/build/outputs/apk/androidTest/debug \
        -maxdepth 1 \
        -type f \
        -name '*.apk' \
        | sort \
        | head -n 1
)"

[[ -n "$APP_APK" ]] ||
    failed 'APP_APK'

[[ -n "$TEST_APK" ]] ||
    failed 'TEST_APK'

echo 'APK_DISCOVERY=PASS'


echo
echo '--- install ---'

"$ADB" \
    -s "$SERIAL" \
    install \
    -r \
    "$APP_APK" \
    >/dev/null 2>&1 ||
    failed 'APP_INSTALL'

"$ADB" \
    -s "$SERIAL" \
    install \
    -r \
    "$TEST_APK" \
    >/dev/null 2>&1 ||
    failed 'TEST_INSTALL'

echo 'INSTALL=PASS'


echo
echo '--- reset app data ---'

"$ADB" \
    -s "$SERIAL" \
    shell \
    pm clear "$APP_ID" \
    >/dev/null 2>&1 ||
    failed 'APP_RESET'

echo 'APP_RESET=PASS'


echo
echo '--- runtime-only credentials ---'

if [[ -f "$ENV_FILE" ]]; then
    ENV_MODE="$(
        stat             -c '%a'             "$ENV_FILE"             2>/dev/null             || true
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
    source "$ENV_FILE"
    SOURCE_RC=$?

    set -u

    [[ "$SOURCE_RC" -eq 0 ]] ||
        failed 'RUNTIME_ENV_LOAD'

    LOCAL_ENDPOINT="${DEVDIGI_NAVIDROME_LOCAL_URL:-}"
    TAILSCALE_ENDPOINT="${DEVDIGI_NAVIDROME_TAILSCALE_URL:-}"

    if [[ "$LOCAL_ENDPOINT" == https://* ]]; then
        ENDPOINT="$LOCAL_ENDPOINT"
        echo 'RUNTIME_ENDPOINT=LOCAL'
    elif [[ "$TAILSCALE_ENDPOINT" == https://* ]]; then
        ENDPOINT="$TAILSCALE_ENDPOINT"
        echo 'RUNTIME_ENDPOINT=TAILSCALE'
    else
        ENDPOINT=''
    fi

    USERNAME="${DEVDIGI_NAVIDROME_USER:-}"
    PASSWORD="${DEVDIGI_NAVIDROME_PASSWORD:-}"

    SECONDARY_LOCAL_ENDPOINT="${DEVDIGI_NAVIDROME_SECONDARY_LOCAL_URL:-}"
    SECONDARY_TAILSCALE_ENDPOINT="${DEVDIGI_NAVIDROME_SECONDARY_TAILSCALE_URL:-}"
    SECONDARY_USERNAME="${DEVDIGI_NAVIDROME_SECONDARY_USER:-}"
    SECONDARY_PASSWORD="${DEVDIGI_NAVIDROME_SECONDARY_PASSWORD:-}"

    if [[ -n "$SECONDARY_LOCAL_ENDPOINT$SECONDARY_TAILSCALE_ENDPOINT$SECONDARY_USERNAME$SECONDARY_PASSWORD" ]]
    then
        if [[ "$SECONDARY_LOCAL_ENDPOINT" == https://* ]]; then
            SECONDARY_ENDPOINT="$SECONDARY_LOCAL_ENDPOINT"
        elif [[ "$SECONDARY_TAILSCALE_ENDPOINT" == https://* ]]; then
            SECONDARY_ENDPOINT="$SECONDARY_TAILSCALE_ENDPOINT"
        elif [[
            -z "$SECONDARY_LOCAL_ENDPOINT" &&
            -z "$SECONDARY_TAILSCALE_ENDPOINT" &&
            -n "$SECONDARY_USERNAME" &&
            -n "$SECONDARY_PASSWORD"
        ]]; then
            SECONDARY_ENDPOINT="$ENDPOINT"
        fi

        if [[
            "$SECONDARY_ENDPOINT" == https://* &&
            -n "$SECONDARY_USERNAME" &&
            -n "$SECONDARY_PASSWORD"
        ]]; then
            if [[
                "$SECONDARY_ENDPOINT" == "$ENDPOINT" &&
                "$SECONDARY_USERNAME" == "$USERNAME"
            ]]; then
                SECONDARY_STATE='SAME_AS_PRIMARY'
            else
                SECONDARY_STATE='COMPLETE'
            fi
        else
            SECONDARY_STATE='PARTIAL'
        fi
    fi

    unset \
        LOCAL_ENDPOINT \
        TAILSCALE_ENDPOINT \
        DEVDIGI_NAVIDROME_URL \
        DEVDIGI_NAVIDROME_LOCAL_URL \
        DEVDIGI_NAVIDROME_TAILSCALE_URL \
        DEVDIGI_NAVIDROME_USER \
        DEVDIGI_NAVIDROME_USERNAME \
        DEVDIGI_NAVIDROME_PASSWORD \
        DEVDIGI_NAVIDROME_API_KEY \
        DEVDIGI_NAVIDROME_SECONDARY_LOCAL_URL \
        DEVDIGI_NAVIDROME_SECONDARY_TAILSCALE_URL \
        DEVDIGI_NAVIDROME_SECONDARY_USER \
        DEVDIGI_NAVIDROME_SECONDARY_PASSWORD \
        SECONDARY_LOCAL_ENDPOINT \
        SECONDARY_TAILSCALE_ENDPOINT

    echo 'RUNTIME_ENV=PASS'
else
    printf 'Navidrome HTTPS URL (hidden): '
    IFS= read -r -s ENDPOINT
    printf '\n'

    printf 'Navidrome username (hidden): '
    IFS= read -r -s USERNAME
    printf '\n'

    printf 'Navidrome password (hidden): '
    IFS= read -r -s PASSWORD
    printf '\n'

    echo 'RUNTIME_ENV=FALLBACK_PROMPT'
fi

[[ -n "$ENDPOINT" ]] ||
    blocked 'RUNTIME_INPUT'

[[ "$ENDPOINT" == https://* ]] ||
    blocked 'HTTPS_ENDPOINT'

[[ -n "$USERNAME" ]] ||
    blocked 'RUNTIME_INPUT'

[[ -n "$PASSWORD" ]] ||
    blocked 'RUNTIME_INPUT'


echo
echo '--- stage ephemeral app-private input ---'

if ! (
    REAL_ENDPOINT="$ENDPOINT" \
    REAL_USERNAME="$USERNAME" \
    REAL_PASSWORD="$PASSWORD" \
    REAL_SECONDARY_STATE="$SECONDARY_STATE" \
    REAL_SECONDARY_ENDPOINT="$SECONDARY_ENDPOINT" \
    REAL_SECONDARY_USERNAME="$SECONDARY_USERNAME" \
    REAL_SECONDARY_PASSWORD="$SECONDARY_PASSWORD" \
        python3 - <<'PY'
import json
import os
import sys

payload = {
    "endpoint": os.environ["REAL_ENDPOINT"],
    "username": os.environ["REAL_USERNAME"],
    "password": os.environ["REAL_PASSWORD"],
}

if os.environ["REAL_SECONDARY_STATE"] == "COMPLETE":
    payload["secondary"] = {
        "endpoint": os.environ["REAL_SECONDARY_ENDPOINT"],
        "username": os.environ["REAL_SECONDARY_USERNAME"],
        "password": os.environ["REAL_SECONDARY_PASSWORD"],
    }

json.dump(
    payload,
    sys.stdout,
    ensure_ascii=False,
)
PY
) |
    "$ADB" \
        -s "$SERIAL" \
        shell \
        "run-as $APP_ID sh -c 'umask 077; mkdir -p files; cat > files/real_instance_input.json'"
then
    unset \
    ENDPOINT \
    USERNAME \
    PASSWORD \
    SECONDARY_ENDPOINT \
    SECONDARY_USERNAME \
    SECONDARY_PASSWORD
    failed 'RUNTIME_INPUT_STAGE'
fi

unset \
    ENDPOINT \
    USERNAME \
    PASSWORD \
    SECONDARY_ENDPOINT \
    SECONDARY_USERNAME \
    SECONDARY_PASSWORD

echo 'RUNTIME_INPUT_STAGE=PASS'


echo
echo '--- discover instrumentation ---'

INSTRUMENTATION="$(
    "$ADB" \
        -s "$SERIAL" \
        shell \
        pm list instrumentation |
        sed -n \
            's/^instrumentation:\([^ ]*\) (target=dev\.devdigi\.music)$/\1/p' |
        head -n 1 |
        tr -d '\r'
)"

[[ -n "$INSTRUMENTATION" ]] ||
    failed 'INSTRUMENTATION_DISCOVERY'

echo 'INSTRUMENTATION_DISCOVERY=PASS'


echo
echo '--- execute media, system controls and isolation slice ---'

RUN_LOG="$(mktemp)"

"$ADB" \
    -s "$SERIAL" \
    shell \
    am instrument \
    -w \
    -r \
    -e class "$TEST_CLASS" \
    "$INSTRUMENTATION" \
    >"$RUN_LOG" 2>&1

RUN_RC=$?

SUCCESS=0

if [[ "$RUN_RC" -eq 0 ]] &&
    grep -Fq \
        'OK (1 test)' \
        "$RUN_LOG" &&
    ! grep -Eq \
        'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' \
        "$RUN_LOG"
then
    SUCCESS=1
fi

if [[ "$SUCCESS" -ne 1 ]]; then
    if grep -Fq \
        'REAL_MEDIA_CANDIDATE_BLOCKED' \
        "$RUN_LOG"
    then
        echo 'MEDIA_CANDIDATE=BLOCKED'
        echo 'PRIVATE_METADATA_RECORDED=NO'
        echo 'WU2_REAL_INSTANCE_MEDIA_QUEUE=BLOCKED'
        exit 2
    fi

    if grep -Fq \
        'REAL_MEDIA_CANDIDATE_PROBE_FAILED' \
        "$RUN_LOG"
    then
        echo 'MEDIA_CANDIDATE=FAIL'
        echo 'PRIVATE_METADATA_RECORDED=NO'
        echo 'WU2_REAL_INSTANCE_MEDIA_QUEUE=FAIL'
        exit 1
    fi

    STAGE_MARKER="$(
        grep -Eo \
            'REAL_STAGE_[A-Z0-9_]+' \
            "$RUN_LOG" |
            head -n 1
    )"

    if [[ -n "$STAGE_MARKER" ]]; then
        echo "FAIL_STAGE=${STAGE_MARKER#REAL_STAGE_}"
    else
        echo 'FAIL_STAGE=UNCLASSIFIED'
    fi

    echo 'WU2_REAL_INSTANCE_MEDIA_QUEUE=FAIL'
    echo 'PRIVATE_METADATA_RECORDED=NO'
    exit 1
fi


echo
echo 'REAL_INSTANCE_CONNECTION=PASS'
echo 'AUTHENTICATION=PASS'
echo 'RECENT_ALBUMS_DESTINATION=PASS'
echo 'MEDIA_CANDIDATE=PASS'
echo 'DYNAMIC_ALBUM_OPEN=PASS'
echo 'FLAC_PLAYBACK=PASS'
echo 'QUEUE_NEXT=PASS'
echo 'QUEUE_PREVIOUS=PASS'
echo 'RUNTIME_INPUT_DELETION=PASS'
echo 'PRIVATE_METADATA_RECORDED=NO'
echo 'WU2_REAL_INSTANCE_MEDIA_QUEUE=PASS'

echo 'BACKGROUND_PLAYBACK=PASS'
echo 'SYSTEM_MEDIA_CONTROLS=PASS'
echo 'SIGN_OUT_ISOLATION=PASS'
echo 'ACCOUNT_PRESENTATION_CLEAR=PASS'
echo 'RUNTIME_PLAYBACK_OWNERSHIP_CLEAR=PASS'
echo 'RUNTIME_QUEUE_OWNERSHIP_CLEAR=PASS'
echo 'WU3_REQUIRED_SCENARIOS=PASS'

case "$SECONDARY_STATE" in
    COMPLETE)
        echo 'SECOND_IDENTITY=SUPPLIED'
        echo 'ACCOUNT_SWITCH_ISOLATION=PASS'
        ;;
    ABSENT)
        echo 'SECOND_IDENTITY=NOT_SUPPLIED'
        echo 'ACCOUNT_SWITCH_ISOLATION=BLOCKED'
        ;;
    PARTIAL)
        echo 'SECOND_IDENTITY=INVALID_CONFIGURATION'
        echo 'ACCOUNT_SWITCH_ISOLATION=BLOCKED'
        ;;
    SAME_AS_PRIMARY)
        echo 'SECOND_IDENTITY=NOT_DISTINCT'
        echo 'ACCOUNT_SWITCH_ISOLATION=BLOCKED'
        ;;
    *)
        echo 'SECOND_IDENTITY=UNKNOWN'
        echo 'ACCOUNT_SWITCH_ISOLATION=BLOCKED'
        ;;
esac

echo 'PRIVATE_METADATA_RECORDED=NO'
echo 'WU3_REAL_INSTANCE_BACKGROUND_ISOLATION=PASS'
