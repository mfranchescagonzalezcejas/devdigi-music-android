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

python3 - "$RUNTIME" <<'PY_SEED' || exit 14
import hashlib
import json
from pathlib import Path
import secrets
import sys
import time
from urllib.parse import urlencode
from urllib.request import urlopen

EXPECTED_ALBUM = "DevDigi Synthetic Album"

runtime = Path(sys.argv[1])
password = (runtime / "admin-password").read_text().strip()
port = int((runtime / "port").read_text().strip())
base = f"http://127.0.0.1:{port}/rest/"


def api(endpoint: str, **extra):
    salt = secrets.token_hex(8)
    token = hashlib.md5(
        (password + salt).encode("utf-8")
    ).hexdigest()

    params = {
        "u": "admin",
        "t": token,
        "s": salt,
        "v": "1.13.0",
        "c": "devdigi-integration",
        "f": "json",
        **extra,
    }

    url = (
        base
        + endpoint
        + ".view?"
        + urlencode(params)
    )

    with urlopen(url, timeout=5) as response:
        if response.status != 200:
            raise RuntimeError("unexpected HTTP status")

        payload = json.load(response)

    root = payload.get("subsonic-response", {})

    if root.get("status") != "ok":
        raise RuntimeError("OpenSubsonic request failed")

    return root


def as_list(value):
    if isinstance(value, list):
        return value

    if isinstance(value, dict):
        return [value]

    return []


try:
    root = api(
        "getAlbumList2",
        type="alphabeticalByName",
        size="50",
    )

    albums = as_list(
        root
        .get("albumList2", {})
        .get("album", [])
    )

    matching = [
        album
        for album in albums
        if isinstance(album, dict)
        and album.get("name") == EXPECTED_ALBUM
        and isinstance(album.get("id"), str)
        and album["id"]
    ]

    if len(matching) != 1:
        raise RuntimeError("synthetic album not uniquely discovered")

    album_id = matching[0]["id"]

    details = api(
        "getAlbum",
        id=album_id,
    ).get("album", {})

    tracks = as_list(
        details.get("song", [])
        if isinstance(details, dict)
        else []
    )

    track_ids = [
        track["id"]
        for track in tracks
        if isinstance(track, dict)
        and isinstance(track.get("id"), str)
        and track["id"]
    ]

    if len(track_ids) < 2:
        raise RuntimeError("synthetic tracks not discovered")

    api(
        "scrobble",
        id=track_ids[0],
        submission="true",
    )

    for _ in range(20):
        recent = api(
            "getAlbumList2",
            type="recent",
            size="50",
        )

        recent_albums = as_list(
            recent
            .get("albumList2", {})
            .get("album", [])
        )

        if any(
            isinstance(album, dict)
            and album.get("name") == EXPECTED_ALBUM
            and isinstance(album.get("id"), str)
            and album["id"]
            for album in recent_albums
        ):
            print("RECENT_STATE_SEEDED=PASS")
            raise SystemExit(0)

        time.sleep(1)

except SystemExit:
    raise
except Exception:
    print(
        "STOP: deterministic synthetic recent-state setup failed.",
        file=sys.stderr,
    )
    raise SystemExit(1)

print(
    "STOP: synthetic album did not become recent in time.",
    file=sys.stderr,
)
raise SystemExit(1)
PY_SEED

cd -- "$REPO_ROOT" || exit 15

NAVIDROME_INTEGRATION_URL="http://127.0.0.1:$PORT" \
NAVIDROME_INTEGRATION_USERNAME="admin" \
NAVIDROME_INTEGRATION_PASSWORD="$PASSWORD" \
    ./gradlew \
        --no-daemon \
        :app:navidromeIntegrationTest ||
    exit 16

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
    exit 17
fi

echo "NAVIDROME_INTEGRATION_TEST=PASS"
echo "INTEGRATION_JUNIT=PASS"
