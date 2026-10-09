#!/usr/bin/env python3
"""Allowlist-only diagnosis of ephemeral instrumentation output. Never prints logs."""
from pathlib import Path
import re
import sys

# Only fixed literal markers; never echo the matching line, Java class names,
# stack traces, endpoint, username, password or device serial.
RULES = (
    (r'\bREAL_INSTANCE_INPUT_SOURCE_INVALID\b', 'INPUT_SOURCE_INVALID'),
    (r'\bREAL_INSTANCE_INPUT_MISSING\b', 'INPUT_FILE_MISSING'),
    (r'\bREAL_INSTANCE_INPUT_UNREADABLE\b', 'INPUT_FILE_UNREADABLE'),
    (r'\bREAL_INSTANCE_INPUT_INVALID\b', 'INPUT_JSON_INVALID'),
    (r'\bREAL_INSTANCE_ENDPOINT_INVALID\b', 'INPUT_ENDPOINT_INVALID'),
    (r'\bREAL_STAGE_[A-Z0-9_]+\b', 'REPORTED_FUNCTIONAL_STAGE'),
    (r'\bINSTRUMENTATION_FAILED\b', 'INSTRUMENTATION_START_FAILED'),
    (r'\bClassNotFoundException\b|\bNoClassDefFoundError\b', 'CLASS_LOAD_FAILURE'),
    (r'\bNoSuchMethodError\b|\bNoSuchFieldError\b|\bIncompatibleClassChangeError\b', 'BINARY_INCOMPATIBILITY'),
    (r'\bSecurityException\b', 'ANDROID_SECURITY_EXCEPTION'),
    (r'\bREAL_MEDIA_CANDIDATE_BLOCKED\b', 'MEDIA_CANDIDATE_BLOCKED'),
    (r'\bREAL_MEDIA_CANDIDATE_PROBE_FAILED\b', 'MEDIA_PROBE_FAILED'),
    (r'\bAssertionError\b', 'UNATTRIBUTED_ASSERTION'),
    (r'\bProcess crashed\b|\bFATAL EXCEPTION\b', 'PROCESS_CRASH'),
    (r'\bNo tests found\b', 'NO_TESTS_DISCOVERED'),
    (r'\bFAILURES!!!\b', 'TEST_FAILURE_OTHER'),
)
RULES = [(re.compile(pattern), name) for pattern, name in RULES]
MARKER = re.compile(r'^INSTRUMENTATION_STATUS: devdigi\.rc\.case=MUSIC-[0-9]+$', re.M)


def classify(content: str):
    # The first marker is best-supported and intentionally not inferred from
    # untrusted arbitrary exception text. Missing markers remain NOT_RUN.
    reason = next((label for pattern, label in RULES if pattern.search(content)), 'UNCLASSIFIED')
    return reason, len(MARKER.findall(content))


def main():
    if len(sys.argv) != 2:
        raise SystemExit(2)
    path = Path(sys.argv[1])
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 2_000_000:
        print('INSTRUMENTATION_DIAGNOSTIC=UNAVAILABLE')
        raise SystemExit(2)
    # Read only while the runner owns its private ephemeral file; no persistence.
    reason, observed = classify(path.read_text(encoding='utf-8', errors='replace'))
    print('INSTRUMENTATION_DIAGNOSTIC=' + reason)
    print('INSTRUMENTATION_CASE_MARKERS=' + str(observed))
    print('INSTRUMENTATION_RAW_LOG_PRINTED=NO')


if __name__ == '__main__':
    main()
