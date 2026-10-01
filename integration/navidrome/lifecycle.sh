#!/usr/bin/env bash

SCRIPT_DIR="$(
    cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &&
    pwd
)"

COMPOSE_FILE="$SCRIPT_DIR/compose.yaml"
SERVICE="navidrome"

die() {
    echo "STOP: $*" >&2
    return 1
}

need_command() {
    command -v "$1" >/dev/null 2>&1 ||
        die "missing command: $1"
}

preflight() {
    local command

    for command in \
        docker \
        curl \
        md5sum \
        od \
        tr \
        awk \
        mktemp \
        python3 \
        grep \
        find
    do
        need_command "$command" || return 1
    done

    docker info >/dev/null 2>&1 ||
        die "Docker daemon is unavailable" ||
        return 1

    docker compose version >/dev/null 2>&1 ||
        die "integrated docker compose command is unavailable" ||
        return 1

    echo "PREFLIGHT=PASS"
}

random_hex() {
    local bytes="$1"
    local value
    local expected

    value="$(
        od \
            -An \
            -N"$bytes" \
            -tx1 \
            /dev/urandom |
        tr -d ' \n'
    )" || return 1

    expected="$(( bytes * 2 ))"

    if [[ "${#value}" -ne "$expected" ]]; then
        die "random value generation failed"
        return 1
    fi

    printf '%s' "$value"
}

new_runtime() {
    local runtime

    runtime="$(
        mktemp -d \
            "${TMPDIR:-/tmp}/devdigi-nav-XXXXXXXX"
    )" || return 1

    chmod 700 "$runtime" || return 1

    printf '%s\n' "$runtime"
}

state_path() {
    printf '%s/%s\n' "$1" "$2"
}

read_state() {
    local file

    file="$(state_path "$1" "$2")"

    if [[ ! -f "$file" ]]; then
        die "missing lifecycle state: $2"
        return 1
    fi

    cat -- "$file"
}

prepare_state() {
    local runtime="$1"
    local music_dir
    local password
    local project

    if [[ -z "${DEVDIGI_NAVIDROME_MUSIC_DIR:-}" ]]; then
        die "DEVDIGI_NAVIDROME_MUSIC_DIR is required"
        return 1
    fi

    music_dir="$(
        cd -- "$DEVDIGI_NAVIDROME_MUSIC_DIR" 2>/dev/null &&
        pwd -P
    )" || {
        die "music fixture directory is unavailable"
        return 1
    }

    if ! find "$music_dir" \
        -type f \
        -print \
        -quit |
        grep -q .
    then
        die "music fixture directory is empty"
        return 1
    fi

    if [[ -e "$runtime" ]]; then
        if [[ ! -d "$runtime" ]]; then
            die "runtime path is not a directory"
            return 1
        fi

        if find "$runtime" \
            -mindepth 1 \
            -print \
            -quit |
            grep -q .
        then
            die "runtime directory must be empty"
            return 1
        fi
    else
        mkdir -- "$runtime" || return 1
    fi

    chmod 700 "$runtime" || return 1

    password="$(random_hex 24)" || return 1
    project="devdigi-nav-$(random_hex 6)" || return 1

    umask 077

    printf '%s\n' "devdigi-nav-runtime-v1" \
        >"$runtime/.devdigi-nav-runtime" ||
        return 1

    printf '%s\n' "$project" \
        >"$runtime/project" || return 1

    printf '%s\n' "$password" \
        >"$runtime/admin-password" || return 1

    printf '%s\n' "$music_dir" \
        >"$runtime/music-dir" || return 1

    chmod 600 \
        "$runtime/.devdigi-nav-runtime" \
        "$runtime/project" \
        "$runtime/admin-password" \
        "$runtime/music-dir" ||
        return 1
}

compose_cmd() {
    local runtime="$1"
    shift

    local project
    local password
    local music_dir

    project="$(read_state "$runtime" project)" ||
        return 1

    password="$(read_state "$runtime" admin-password)" ||
        return 1

    music_dir="$(read_state "$runtime" music-dir)" ||
        return 1

    DEVDIGI_NAVIDROME_ADMIN_PASSWORD="$password" \
    DEVDIGI_NAVIDROME_MUSIC_DIR="$music_dir" \
        docker compose \
            --project-name "$project" \
            --file "$COMPOSE_FILE" \
            "$@"
}

resolve_port() {
    local runtime="$1"
    local binding=""
    local port=""
    local attempt

    for attempt in $(seq 1 20); do
        binding="$(
            compose_cmd \
                "$runtime" \
                port \
                "$SERVICE" \
                4533 \
                2>/dev/null ||
            true
        )"

        case "$binding" in
            127.0.0.1:*)
                port="${binding##*:}"
                ;;
            *)
                port=""
                ;;
        esac

        if [[ "$port" =~ ^[0-9]+$ ]] &&
           (( port >= 1 && port <= 65535 )); then
            printf '%s\n' "$port" \
                >"$runtime/port" ||
                return 1

            chmod 600 "$runtime/port" ||
                return 1

            return 0
        fi

        sleep 1
    done

    die "loopback dynamic port could not be resolved"
}

api_get() {
    local runtime="$1"
    local endpoint="$2"
    local output="$3"
    shift 3

    local password
    local port
    local salt
    local token
    local item

    password="$(
        read_state \
            "$runtime" \
            admin-password
    )" || return 1

    port="$(
        read_state \
            "$runtime" \
            port
    )" || return 1

    salt="$(random_hex 8)" || return 1

    token="$(
        printf '%s%s' \
            "$password" \
            "$salt" |
        md5sum |
        awk '{print $1}'
    )" || return 1

    local args=(
        --silent
        --connect-timeout 1
        --max-time 3
        --output "$output"
        --write-out '%{http_code}'
        --get
        --data-urlencode "u=admin"
        --data-urlencode "t=$token"
        --data-urlencode "s=$salt"
        --data-urlencode "v=1.13.0"
        --data-urlencode "c=devdigi-integration"
        --data-urlencode "f=json"
    )

    for item in "$@"; do
        args+=(
            --data-urlencode "$item"
        )
    done

    curl \
        "${args[@]}" \
        "http://127.0.0.1:$port/rest/$endpoint.view"
}

json_ping_ok() {
    python3 - "$1" <<'PY_JSON'
import json
from pathlib import Path
import sys

try:
    payload = json.loads(
        Path(sys.argv[1]).read_text()
    )
    response = payload["subsonic-response"]
except Exception:
    raise SystemExit(1)

raise SystemExit(
    0
    if response.get("status") == "ok"
    else 1
)
PY_JSON
}

json_expected_library_visible() {
    python3 - "$1" <<'PY_JSON'
import json
from pathlib import Path
import sys

EXPECTED_ALBUM = "DevDigi Synthetic Album"

try:
    payload = json.loads(
        Path(sys.argv[1]).read_text()
    )

    response = payload["subsonic-response"]

    if response.get("status") != "ok":
        raise SystemExit(1)

    albums = (
        response
        .get("albumList2", {})
        .get("album", [])
    )
except Exception:
    raise SystemExit(1)

if isinstance(albums, dict):
    albums = [albums]

if not isinstance(albums, list):
    raise SystemExit(1)

visible = any(
    isinstance(album, dict)
    and album.get("name") == EXPECTED_ALBUM
    for album in albums
)

raise SystemExit(
    0
    if visible
    else 1
)
PY_JSON
}

redact_stream() {
    local runtime="$1"

    python3 -c '
from pathlib import Path
import sys

password = Path(sys.argv[1]).read_text().strip()
music_dir = Path(sys.argv[2]).read_text().strip()
runtime = sys.argv[3]

text = sys.stdin.read()

for secret in (
    password,
    music_dir,
    runtime,
):
    if secret:
        text = text.replace(
            secret,
            "<redacted>",
        )

sys.stdout.write(text)
' \
        "$runtime/admin-password" \
        "$runtime/music-dir" \
        "$runtime"
}

diagnostics() {
    local runtime="$1"

    echo "NAVIDROME_DIAGNOSTICS_BEGIN"

    if [[ ! -d "$runtime" ||
          ! -f "$runtime/.devdigi-nav-runtime" ||
          ! -f "$runtime/project" ||
          ! -f "$runtime/admin-password" ||
          ! -f "$runtime/music-dir" ]]; then
        echo "STATE=INCOMPLETE"
        echo "NAVIDROME_DIAGNOSTICS_END"
        return 0
    fi

    {
        compose_cmd \
            "$runtime" \
            ps ||
            true

        compose_cmd \
            "$runtime" \
            logs \
            --no-color \
            --tail 80 \
            "$SERVICE" ||
            true
    } 2>&1 |
        redact_stream "$runtime" ||
        true

    echo "NAVIDROME_DIAGNOSTICS_END"
}

wait_ready() {
    local runtime="$1"
    local attempts="${DEVDIGI_NAVIDROME_READY_ATTEMPTS:-90}"
    local ping_file="$runtime/ping.json"
    local library_file="$runtime/library.json"
    local ping_code
    local library_code
    local attempt

    if [[ ! "$attempts" =~ ^[0-9]+$ ]] ||
       (( attempts < 1 || attempts > 300 )); then
        die "invalid readiness attempt count"
        return 1
    fi

    for attempt in $(seq 1 "$attempts"); do
        ping_code="$(
            api_get \
                "$runtime" \
                ping \
                "$ping_file" \
                2>/dev/null ||
            true
        )"

        if [[ "$ping_code" == "200" ]] &&
           json_ping_ok "$ping_file"
        then
            library_code="$(
                api_get \
                    "$runtime" \
                    getAlbumList2 \
                    "$library_file" \
                    "type=alphabeticalByName" \
                    "size=50" \
                    2>/dev/null ||
                true
            )"

            if [[ "$library_code" == "200" ]] &&
               json_expected_library_visible "$library_file"
            then
                rm -f \
                    "$ping_file" \
                    "$library_file"

                echo "READY_HTTP=PASS"
                echo "READY_AUTHENTICATED=PASS"
                echo "READY_LIBRARY=PASS"
                echo "READY=PASS"
                return 0
            fi
        fi

        sleep 1
    done

    echo "READY=FAIL" >&2
    diagnostics "$runtime" || true
    return 1
}

up() {
    local runtime="$1"

    preflight || return 1
    prepare_state "$runtime" || return 1

    if ! compose_cmd \
        "$runtime" \
        up \
        --detach \
        --remove-orphans
    then
        diagnostics "$runtime" || true
        return 1
    fi

    if ! resolve_port "$runtime"; then
        diagnostics "$runtime" || true
        return 1
    fi

    echo "UP=PASS"
}

down() {
    local runtime="$1"
    local marker="$runtime/.devdigi-nav-runtime"

    if [[ ! -e "$runtime" ]]; then
        echo "DOWN=PASS"
        return 0
    fi

    if [[ ! -d "$runtime" ]]; then
        die "refusing cleanup of non-directory runtime path"
        return 1
    fi

    if [[ ! -f "$marker" ]] ||
       [[ "$(cat -- "$marker")" != "devdigi-nav-runtime-v1" ]]; then
        die "refusing cleanup of unowned runtime directory"
        return 1
    fi

    if [[ -f "$runtime/project" &&
          -f "$runtime/admin-password" &&
          -f "$runtime/music-dir" ]]; then
        compose_cmd \
            "$runtime" \
            down \
            --volumes \
            --remove-orphans \
            --timeout 10 \
            >/dev/null ||
            return 1
    fi

    rm -rf -- "$runtime" || return 1

    echo "DOWN=PASS"
}

cleanup_run_runtime() {
    local runtime="$1"
    local marker="$runtime/.devdigi-nav-runtime"

    if [[ ! -e "$runtime" ]]; then
        return 0
    fi

    if [[ -f "$marker" ]] &&
       [[ "$(cat -- "$marker")" == "devdigi-nav-runtime-v1" ]]; then
        if down "$runtime" >/dev/null 2>&1; then
            return 0
        fi

        diagnostics "$runtime" || true
        return 1
    fi

    if [[ -d "$runtime" ]]; then
        rmdir -- "$runtime" 2>/dev/null &&
            return 0
    fi

    die "owned run runtime could not be cleaned"
}

assert_project_gone() {
    local project="$1"
    local remaining

    remaining="$(
        docker ps \
            --all \
            --quiet \
            --filter \
            "label=com.docker.compose.project=$project"
    )" || return 1

    if [[ -n "$remaining" ]]; then
        die "Compose project still has containers"
        return 1
    fi
}

assert_run_cleanup() {
    local runtime="$1"
    local project_capture="$2"
    local project

    if [[ -e "$runtime" ]]; then
        die "runtime directory survived cleanup"
        return 1
    fi

    if [[ ! -s "$project_capture" ]]; then
        die "run did not record its Compose project"
        return 1
    fi

    project="$(
        cat -- "$project_capture"
    )" || return 1

    assert_project_gone "$project" || return 1
}

execute_owned_run() (
    local runtime="$1"
    local project_capture="$2"
    local mode="${3:-success}"

    cleanup() {
        local status="$?"

        trap - EXIT HUP INT TERM

        if ! cleanup_run_runtime "$runtime"; then
            if [[ "$status" -eq 0 ]]; then
                status=1
            fi
        fi

        exit "$status"
    }

    trap cleanup EXIT
    trap 'exit 129' HUP
    trap 'exit 130' INT
    trap 'exit 143' TERM

    up "$runtime" || exit 10

    read_state \
        "$runtime" \
        project \
        >"$project_capture" ||
        exit 11

    wait_ready "$runtime" || exit 12

    case "$mode" in
        success)
            echo "RUN_BODY=PASS"
            ;;

        forced-failure)
            echo "FORCED_FAILURE_TRIGGERED=YES"
            exit 23
            ;;

        *)
            die "unsupported run mode"
            exit 24
            ;;
    esac
)

run_once() {
    local runtime
    local project_capture
    local status=0

    runtime="$(new_runtime)" || return 1
    project_capture="$(mktemp)" || {
        rmdir -- "$runtime" 2>/dev/null || true
        return 1
    }

    execute_owned_run \
        "$runtime" \
        "$project_capture" \
        success ||
        status="$?"

    if ! assert_run_cleanup \
        "$runtime" \
        "$project_capture"
    then
        status=1
    fi

    rm -f -- "$project_capture"

    if [[ "$status" -ne 0 ]]; then
        die "lifecycle run failed with status $status"
        return "$status"
    fi

    echo "SUCCESS_CLEANUP=PASS"
    echo "RUN=PASS"
}

forced_failure_test() {
    local runtime
    local project_capture
    local status=0

    runtime="$(new_runtime)" || return 1
    project_capture="$(mktemp)" || {
        rmdir -- "$runtime" 2>/dev/null || true
        return 1
    }

    execute_owned_run \
        "$runtime" \
        "$project_capture" \
        forced-failure ||
        status="$?"

    if [[ "$status" -ne 23 ]]; then
        rm -f -- "$project_capture"

        die "forced failure returned unexpected status $status"
        return 1
    fi

    if ! assert_run_cleanup \
        "$runtime" \
        "$project_capture"
    then
        rm -f -- "$project_capture"
        return 1
    fi

    rm -f -- "$project_capture"

    echo "FAILURE_CLEANUP=PASS"
    echo "FORCED_FAILURE_TEST=PASS"
}

self_test() {
    echo "SELF_TEST_RUN_1"

    run_once || return 1

    echo "SELF_TEST_RUN_2"

    run_once || return 1

    echo "SELF_TEST_FORCED_FAILURE"

    forced_failure_test || return 1

    echo "REPEATED_RUNS=PASS"
    echo "SUCCESS_AND_FAILURE_CLEANUP=PASS"
    echo "SELF_TEST=PASS"
}

usage() {
    echo "Usage:"
    echo "  lifecycle.sh preflight"
    echo "  lifecycle.sh up RUNTIME_DIR"
    echo "  lifecycle.sh wait-ready RUNTIME_DIR"
    echo "  lifecycle.sh diagnostics RUNTIME_DIR"
    echo "  lifecycle.sh down RUNTIME_DIR"
    echo "  lifecycle.sh run"
    echo "  lifecycle.sh self-test"
}

case "${1:-}" in
    preflight)
        preflight
        ;;

    up)
        [[ -n "${2:-}" ]] || {
            usage
            exit 2
        }

        up "$2"
        ;;

    wait-ready)
        [[ -n "${2:-}" ]] || {
            usage
            exit 2
        }

        wait_ready "$2"
        ;;

    diagnostics)
        [[ -n "${2:-}" ]] || {
            usage
            exit 2
        }

        diagnostics "$2"
        ;;

    down)
        [[ -n "${2:-}" ]] || {
            usage
            exit 2
        }

        down "$2"
        ;;

    run)
        run_once
        ;;

    self-test)
        self_test
        ;;

    *)
        usage
        exit 2
        ;;
esac
