#!/usr/bin/env bash

SCRIPT_DIR="$(
    cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &&
    pwd
)"

REPO_ROOT="$(
    cd -- "$SCRIPT_DIR/../.." &&
    pwd
)"

LIFECYCLE="$SCRIPT_DIR/lifecycle.sh"
MUSIC_DIR="$SCRIPT_DIR/fixtures"

RUNTIME="$(
    mktemp -d \
        "${TMPDIR:-/tmp}/devdigi-nav-integration-XXXXXXXX"
)" || exit 1

cleanup() {
    local status="$?"

    trap - EXIT HUP INT TERM

    if [[ -f "$RUNTIME/.devdigi-nav-runtime" ]]; then
        if ! "$LIFECYCLE" down "$RUNTIME" \
            >/dev/null 2>&1
        then
            "$LIFECYCLE" diagnostics "$RUNTIME" || true

            if [[ "$status" -eq 0 ]]; then
                status=1
            fi
        fi
    else
        rmdir -- "$RUNTIME" 2>/dev/null || true
    fi

    exit "$status"
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

DEVDIGI_NAVIDROME_MUSIC_DIR="$MUSIC_DIR" \
    "$LIFECYCLE" up "$RUNTIME" ||
    exit 10

"$LIFECYCLE" wait-ready "$RUNTIME" ||
    exit 11

PORT="$(
    cat -- "$RUNTIME/port"
)" || exit 12

PASSWORD="$(
    cat -- "$RUNTIME/admin-password"
)" || exit 13

cd -- "$REPO_ROOT" || exit 14

NAVIDROME_INTEGRATION_URL="http://127.0.0.1:$PORT" \
NAVIDROME_INTEGRATION_USERNAME="admin" \
NAVIDROME_INTEGRATION_PASSWORD="$PASSWORD" \
    ./gradlew \
        --no-daemon \
        :app:navidromeIntegrationTest ||
    exit 15

REPORT_DIR="$REPO_ROOT/app/build/test-results/navidromeIntegrationTest"

if ! find "$REPORT_DIR" \
    -type f \
    -name '*.xml' \
    -size +0c \
    -print \
    -quit |
    grep -q .
then
    echo "STOP: distinct integration JUnit XML was not produced."
    exit 16
fi

echo "NAVIDROME_INTEGRATION_TEST=PASS"
echo "INTEGRATION_JUNIT=PASS"
