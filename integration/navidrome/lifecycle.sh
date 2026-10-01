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
        od \
        tr \
        mktemp \
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

up() {
    local runtime="$1"

    preflight || return 1
    prepare_state "$runtime" || return 1

    compose_cmd \
        "$runtime" \
        up \
        --detach \
        --remove-orphans ||
        return 1

    resolve_port "$runtime" || return 1

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

usage() {
    echo "Usage:"
    echo "  lifecycle.sh preflight"
    echo "  lifecycle.sh up RUNTIME_DIR"
    echo "  lifecycle.sh down RUNTIME_DIR"
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

    down)
        [[ -n "${2:-}" ]] || {
            usage
            exit 2
        }

        down "$2"
        ;;

    *)
        usage
        exit 2
        ;;
esac
