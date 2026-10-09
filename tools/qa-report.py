#!/usr/bin/env python3
"""Privacy-safe DevDigi Music RC Smoke evidence reducer; never saves raw logs."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import uuid
import xml.etree.ElementTree as ET

CASES = (
    ("MUSIC-64", "Saving a compatible server does not authenticate the user"),
    ("MUSIC-65", "Sign in successfully with valid credentials"),
    ("MUSIC-72", "Recent albums load for the authenticated account"),
    ("MUSIC-75", "Open album details from Recent Albums"),
    ("MUSIC-80", "Start FLAC playback from an album"),
    ("MUSIC-81", "Selecting an album track creates an ordered queue"),
    ("MUSIC-82", "Next advances to the next queue item"),
    ("MUSIC-83", "Previous returns to the previous queue item"),
    ("MUSIC-85", "Playback continues when the app moves to background"),
    ("MUSIC-86", "Android system Play and Pause controls"),
    ("MUSIC-87", "Android system Next and Previous controls"),
    ("MUSIC-89", "Sign out clears playback and queue ownership"),
    ("MUSIC-69", "Sign out clears authenticated presentation state"),
)
EXPECTED = [case[0] for case in CASES]
MARKER = re.compile(r"INSTRUMENTATION_STATUS: devdigi\.rc\.case=(MUSIC-[A-Za-z0-9_-]+)")
SUCCESS = re.compile(r"^\s*OK \(1 test\)\s*$", re.M)
FAILURE = re.compile(r"FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed")
SHA = re.compile(r"[0-9a-f]{40}\Z")
STAGE = re.compile(r"[A-Z][A-Z0-9_]{0,100}\Z")

# Attribute only failures with a direct, known link to an AgileTest case.
STAGE_TO_CASE = {
    "AUTH_SERVER_INPUT_FAILED": "MUSIC-64",
    "AUTH_SERVER_ENABLE_FAILED": "MUSIC-64",
    "AUTH_SERVER_SAVE_FAILED": "MUSIC-64",
    "AUTH_SERVER_PERSIST_FAILED": "MUSIC-64",
    "AUTH_SERVER_NOT_AUTHENTICATED_FAILED": "MUSIC-64",
    "AUTH_CREDENTIALS_FAILED": "MUSIC-65",
    "AUTH_SUBMIT_FAILED": "MUSIC-65",
    "AUTH_DESTINATION_FAILED": "MUSIC-65",
    "RECENT_ALBUMS_FAILED": "MUSIC-72",
    "ALBUM_OPEN_FAILED": "MUSIC-75",
    "FLAC_SELECTION_FAILED": "MUSIC-80",
    "QUEUE_SEED_FAILED": "MUSIC-81",
    "QUEUE_NEXT_FAILED": "MUSIC-82",
    "QUEUE_PREVIOUS_FAILED": "MUSIC-83",
    "BACKGROUND_PLAYBACK_FAILED": "MUSIC-85",
    "SYSTEM_PAUSE_FAILED": "MUSIC-86",
    "SYSTEM_PLAY_FAILED": "MUSIC-86",
    "SYSTEM_NEXT_FAILED": "MUSIC-87",
    "SYSTEM_PREVIOUS_FAILED": "MUSIC-87",
    "SIGN_OUT_FAILED": "MUSIC-89",
    "SIGN_OUT_PRESENTATION_CLEAR_FAILED": "MUSIC-69",
    "SIGN_OUT_RUNTIME_CLEAR_FAILED": "MUSIC-89",
}


def evidence(log_path):
    if not log_path or not Path(log_path).is_file():
        return [], False, False
    # Parsing stays local. The original log is NEVER written into either output.
    with open(log_path, encoding="utf-8", errors="replace") as handle:
        log = handle.read(2_000_001)
    if len(log) > 2_000_000:
        raise ValueError("EVIDENCE_TOO_LARGE")
    markers = []
    for line in log.splitlines():
        if "INSTRUMENTATION_STATUS: devdigi.rc.case=" in line:
            match = MARKER.fullmatch(line.strip())
            if not match:
                raise ValueError("EVIDENCE_MARKER_MALFORMED")
            markers.append(match.group(1))
    if markers != EXPECTED[:len(markers)]:
        raise ValueError("EVIDENCE_ORDER_INVALID")
    ok = bool(SUCCESS.search(log)) and not FAILURE.search(log)
    return markers, ok, True


def atomic_write(path, data):
    path = Path(path)
    if path.is_symlink() or (path.exists() and not path.is_file()):
        raise ValueError("OUTPUT_PATH_UNSAFE")
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(prefix=".qa-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            os.fchmod(stream.fileno(), 0o600)
            stream.write(data)
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


def reduce_result(status, stage, log_path, source_sha):
    if status not in {"PASS", "FAIL", "BLOCKED"}:
        raise ValueError("STATUS_INVALID")
    if not SHA.fullmatch(source_sha):
        raise ValueError("SOURCE_SHA_INVALID")
    # Only allow known failure names into output; never include arbitrary CLI text.
    stage = stage if STAGE.fullmatch(stage) else "UNCLASSIFIED"
    stage = stage if stage in STAGE_TO_CASE else "UNCLASSIFIED"
    try:
        markers, ok, present = evidence(log_path)
    except ValueError:
        markers, ok, present = [], False, False
        status = "FAIL"
        stage = "UNCLASSIFIED"
    if status == "PASS" and (markers != EXPECTED or not ok):
        status = "FAIL"
        stage = "UNCLASSIFIED"
        markers = []  # Invalid claimed overall PASS must not promote case PASS.
    case_statuses = {key: "NOT RUN" for key in EXPECTED}
    for key in markers:
        case_statuses[key] = "PASS"
    if status == "BLOCKED":
        for key in EXPECTED[len(markers):]:
            case_statuses[key] = "BLOCKED"
    if status == "FAIL" and stage != "UNCLASSIFIED":
        failing = STAGE_TO_CASE[stage]
        next_case = EXPECTED[len(markers)] if len(markers) < len(EXPECTED) else None
        # Never claim a distant, unexecuted case failed based on a stray marker.
        attributable = failing == next_case
        # Presentation state is verified after sign-out, before MUSIC-89 emits.
        if stage == "SIGN_OUT_PRESENTATION_CLEAR_FAILED" and next_case == "MUSIC-89":
            attributable = True
        if attributable and case_statuses[failing] != "PASS":
            case_statuses[failing] = "FAIL"
    counts = Counter(case_statuses.values())
    supplied_id = os.environ.get("DEVDIGI_QA_RUN_ID", "")
    if supplied_id:
        try:
            run_id = str(uuid.UUID(supplied_id, version=4))
        except ValueError as exc:
            raise ValueError("RUN_ID_INVALID") from exc
        if run_id != supplied_id:
            raise ValueError("RUN_ID_INVALID")
    else:
        run_id = str(uuid.uuid4())
    report = {
        "schema": "devdigi.qa.result.v1",
        "run_id": run_id,
        "recorded_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "project": "MUSIC",
        "test_execution": "MUSIC-92",
        "suite": "smoke",
        "target": "rc1",
        "instrumentation_source_commit": source_sha,
        "candidate_version": "0.1.0",
        "candidate_version_code": 1,
        "status": status,
        "failure_stage": stage if status == "FAIL" else None,
        "counts": {key: counts[key] for key in ("PASS", "FAIL", "BLOCKED", "NOT RUN", "N/A")},
        "cases": [{"key": key, "status": case_statuses[key]} for key in EXPECTED],
        "publication": "DRY_RUN_ONLY",
        "requires_manual_approval": True,
        "raw_logs_included": False,
    }
    return report


def xml_report(report):
    root = ET.Element("testsuite", name="DevDigi Music RC Smoke")
    failures = 0
    skipped = 0
    names = dict(CASES)
    for case in report["cases"]:
        key = case["key"]
        node = ET.SubElement(root, "testcase", classname="devdigi.music.rc.smoke", name=f"{key} — {names[key]}")
        if case["status"] == "FAIL":
            ET.SubElement(node, "failure", message="RC Smoke case failed")
            failures += 1
        elif case["status"] != "PASS":
            ET.SubElement(node, "skipped", message=case["status"])
            skipped += 1
    if report["status"] != "PASS":
        harness = ET.SubElement(root, "testcase", classname="devdigi.music.rc.smoke", name="RC smoke harness")
        if report["status"] == "FAIL":
            ET.SubElement(harness, "failure", message="RC Smoke execution failed")
            failures += 1
        else:
            ET.SubElement(harness, "skipped", message="RC Smoke prerequisite blocked")
            skipped += 1
    root.set("tests", str(len(root)))
    root.set("failures", str(failures))
    root.set("skipped", str(skipped))
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def print_summary(report):
    print("LOCAL_QA_SUITE=" + report["suite"])
    print("LOCAL_QA_STATUS=" + report["status"])
    for case in report["cases"]:
        print(f"QA_{case['key']}={case['status'].replace(' ', '_')}")
    print("N8N_PAYLOAD_STATUS=LOCAL_ONLY")
    print("AGILETEST_PUBLISHED=NO")


def main():
    parser = argparse.ArgumentParser(description="Sanitized Smoke reporter")
    sub = parser.add_subparsers(dest="action", required=True)
    generate = sub.add_parser("generate")
    generate.add_argument("--status", required=True, choices=("PASS", "FAIL", "BLOCKED"))
    generate.add_argument("--stage", default="UNCLASSIFIED")
    generate.add_argument("--log", default="")
    generate.add_argument("--junit", required=True)
    generate.add_argument("--json", required=True)
    generate.add_argument("--source-sha", required=True)
    show = sub.add_parser("show")
    show.add_argument("--json", required=True)
    show.add_argument("--expected-run-id", default="")
    args = parser.parse_args()
    if args.action == "show":
        report = json.loads(Path(args.json).read_text())
        if report.get("schema") != "devdigi.qa.result.v1":
            raise ValueError("UNKNOWN_REPORT_SCHEMA")
        if args.expected_run_id and report.get("run_id") != args.expected_run_id:
            raise ValueError("STALE_REPORT")
    else:
        report = reduce_result(args.status, args.stage, args.log, args.source_sha)
        if Path(args.json).resolve() == Path(args.junit).resolve():
            raise ValueError("OUTPUT_PATHS_IDENTICAL")
        atomic_write(args.junit, xml_report(report))
        atomic_write(args.json, (json.dumps(report, indent=2) + "\n").encode())
    print_summary(report)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as exc:
        # Never print raw exception text, paths, or contents.
        print("QA_REPORT=FAIL", file=sys.stderr)
        sys.exit(1)
