```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:b6d4e96613bd3c2600fc58ce37bb34863ccdcf293c8288b09774b36615265acd
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 5/5
scenarios: 10/10
test_command: ./gradlew --rerun-tasks testDebugUnitTest
test_exit_code: 0
test_output_hash: sha256:6b339e64bc1a38d9405b850a8196cbf9da8576275ba47641159cf9a598e48ddd
build_command: ./gradlew assembleDebug
build_exit_code: 0
build_output_hash: sha256:7da6d0c8572e6ac7309c29fc187c2ea7b773b9ef51fdc9d0b96e5bf37a249f3f
```

## Verification Report

**Change**: navidrome-server-connection
**Version**: N/A
**Mode**: Strict TDD
**Objective generation**: 6
**Attempt ordinal**: 6
**Work unit**: `final-release-binding-admission`
**Attempt revision**: `sha256:ef381bc76aee81927f1dae1eb5bceb9df9c5ea340c682848b9e11d24325a8bb8`
**Objective revision**: `sha256:5e70d26b74402f00583f1f18acfb2a86a463382218f38abc40bec4104de7c1be`
**Budget**: 300

### Completeness

| Metric | Value |
|---|---:|
| Requirements total / compliant | 5 / 5 |
| Scenarios total / compliant | 10 / 10 |
| Tasks total / complete / incomplete | 20 / 20 / 0 |
| Changed lines / budget | 282 / 300 |

### Build & Tests Execution

| Check | Exit | Output hash | Result |
|---|---:|---|---|
| `git diff --check` | 0 | `sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | ✅ Passed |
| `./gradlew --rerun-tasks testDebugUnitTest` | 0 | `sha256:6b339e64bc1a38d9405b850a8196cbf9da8576275ba47641159cf9a598e48ddd` | ✅ Passed |
| `./gradlew -PunitTestBuildType=release --rerun-tasks testReleaseUnitTest` | 0 | `sha256:3bead89263e2ebd8f0a1301a5a18c03fcab8e6848eac8216099a9f73ed09619d` | ✅ Passed |
| `./gradlew assembleDebug` | 0 | `sha256:7da6d0c8572e6ac7309c29fc187c2ea7b773b9ef51fdc9d0b96e5bf37a249f3f` | ✅ Passed |
| `./gradlew lint` | 0 | `sha256:a5483353e9ed6c0074783eee8a431d1dbdfb07e72f16c80d07dd21d19ccb06ca` | ✅ Passed |
| `./gradlew :app:assembleRelease :app:lintRelease` | 0 | `sha256:b32a27af2da48971f5235980ecec0499af7e6381d05d0f96f1cdab328b45598c` | ✅ Passed |
| Release JUnit XML validation | 0 | `sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | ✅ Passed |
| Debug and release JUnit XML summary | 0 | `sha256:3ec28d7dcd9b40bf2ac409815933cacc9bfd7190666b14a0ee92fa0aa24b306a` | ✅ Passed |
| Release endpoint source-case validation | 0 | `sha256:09e14cfa037a43e8dc2d14e4db36de48df9a4889bde6d46deec0c3775eca2f36` | ✅ Passed |
| Changed-file privacy and scope scan | 0 | `sha256:068b94d85c39549a15c18a93c9485b7e87ad7e360ab7ed5807ac620e1e4468a5` | ✅ Passed |

Debug suite: **27 tests, 0 failures, 0 errors, 0 skipped**.
Release suite: **27 tests, 0 failures, 0 errors, 0 skipped**.
Release binding: `ReleaseEndpointPolicyTest` > `rejectsHttpAndAcceptsHttps` — **1 test, 0 failures, 0 errors, 0 skipped**.

**Coverage**: ➖ Skipped — no coverage plugin configured; threshold is `0`.

### Spec Compliance Matrix

| Requirement | Scenario | Passing evidence | Result |
|---|---|---|---|
| Endpoint Validation and Normalization | Normalize a reverse-proxy endpoint | Debug and release shared parser tests | ✅ COMPLIANT |
| Endpoint Validation and Normalization | Reject malformed input | Debug and release shared parser tests | ✅ COMPLIANT |
| Endpoint-only Server Profile Repository | Restore a valid stored endpoint | Debug and release temporary-file DataStore tests | ✅ COMPLIANT |
| Endpoint-only Server Profile Repository | Replace, delete, or reject storage | Debug and release repository/ViewModel tests | ✅ COMPLIANT |
| Variant Transport Admission | Admit permitted local debug HTTP | Default debug `EndpointPolicyTest` and shared allowlist tests | ✅ COMPLIANT |
| Variant Transport Admission | Reject HTTP outside the allowance | Release `ReleaseEndpointPolicyTest` plus debug remote rejection tests | ✅ COMPLIANT |
| Ping Client Verification Facts | Report reachability without compatibility | Debug and release synthetic protocol response tests | ✅ COMPLIANT |
| Ping Client Verification Facts | Preserve authenticated-ping boundary | Debug and release synthetic error/unauthenticated tests | ✅ COMPLIANT |
| Local Synthetic Verification | Run safe verification | Unit execution and privacy/scope scan; no network | ✅ COMPLIANT |
| Local Synthetic Verification | Exclude infrastructure bootstrap | Verification completed without Docker or private systems | ✅ COMPLIANT |

**Compliance summary**: 10/10 scenarios and 5/5 requirements compliant.

### Correctness

| Boundary | Status | Evidence |
|---|---|---|
| Release policy binding executes on host | ✅ | Release JUnit XML names `ReleaseEndpointPolicyTest` 1/1 passing |
| Release rejects all HTTP cases | ✅ | Four source cases execute against `BuildVariantEndpointPolicy` |
| Release accepts HTTPS case | ✅ | `https://music.example.com` accepted |
| Default debug unit task functional | ✅ | `testDebugUnitTest` 27/27 green |
| Budget respected | ✅ | 282 / 300 changed lines |
| No scope expansion | ✅ | Only Gradle test selection, release test, and task text changed |

### Changed-Line Accounting

| Path | Additions | Deletions | Changed lines |
|---|---:|---:|---:|
| `app/build.gradle.kts` | 1 | 0 | 1 |
| `openspec/changes/navidrome-server-connection/tasks.md` | 1 | 1 | 2 |
| `app/src/testRelease/.../ReleaseEndpointPolicyTest.kt` | 14 | 0 | 14 |
| `openspec/changes/navidrome-server-connection/verify-report.md` | 147 | 118 | 265 |
| **Total** | **282** | **119** | **282** |

Untracked release test included. Report bytes are evidence output included in budget.

### Privacy and Scope

| Check | Result |
|---|---|
| Authorized paths only | ✅ Exactly three implementation paths plus report |
| Synthetic/local fixtures only | ✅ Four local HTTP + one HTTPS example case |
| No unapproved secrets or credentials | ✅ 0 occurrences |
| No network or private-system access | ✅ No requests, Docker, or homelab |
| Read-only lifecycle | ✅ No finish, commit, push, or remote operation |

### Coherence (Design)

| Decision | Followed? | Notes |
|---|---|---|
| Mutually exclusive variant policies | ✅ Yes | Release test compiles against release source-set |
| Release HTTPS-only admission | ✅ Yes | Four HTTP rejections, one HTTPS acceptance |
| Default debug workflow preserved | ✅ Yes | Property defaults to `debug` |
| Synthetic verification only | ✅ Yes | No sockets, services, or credentials |
| Minimal change | ✅ Yes | 282 lines within 300 budget |

### TDD Compliance

| Check | Result | Details |
|---|---|---|
| Test file exists | ✅ | Release test in release unit-test source set |
| GREEN confirmed | ✅ | Release and debug suites pass |
| Triangulation adequate | ✅ | Four rejection + one acceptance case |
| Safety net | ✅ | Debug suite 27/27 green |

### Test Layer Distribution

| Layer | Debug | Release | Tools |
|---|---:|---:|---|
| Unit | 27 | 27 | JUnit 4 / Gradle |
| Integration | 0 | 0 | N/A |
| E2E | 0 | 0 | N/A |

### Issues Found

**CRITICAL**: None.

**WARNING**

1. Preserved failing RED transcript unavailable for the Gradle/test change; GREEN and regression safety fully proven.

**SUGGESTION**: None.

### Canonical Verification Evidence

```yaml
schema: gentle-ai.verification-evidence/v1
change: navidrome-server-connection
attempt_revision: sha256:ef381bc76aee81927f1dae1eb5bceb9df9c5ea340c682848b9e11d24325a8bb8
objective_revision: sha256:5e70d26b74402f00583f1f18acfb2a86a463382218f38abc40bec4104de7c1be
objective_generation: 6
attempt_ordinal: 6
work_unit: final-release-binding-admission
evidence_goal: verified-release-binding-final-evidence
requirements: 5/5
scenarios: 10/10
tasks: 20/20
changed_lines:
  actual: 282
  budget: 300
  exceeded: false
commands:
  - command: git diff --check
    exit_code: 0
    output_hash: sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
  - command: ./gradlew --rerun-tasks testDebugUnitTest
    exit_code: 0
    output_hash: sha256:6b339e64bc1a38d9405b850a8196cbf9da8576275ba47641159cf9a598e48ddd
  - command: ./gradlew -PunitTestBuildType=release --rerun-tasks testReleaseUnitTest
    exit_code: 0
    output_hash: sha256:3bead89263e2ebd8f0a1301a5a18c03fcab8e6848eac8216099a9f73ed09619d
  - command: ./gradlew assembleDebug
    exit_code: 0
    output_hash: sha256:7da6d0c8572e6ac7309c29fc187c2ea7b773b9ef51fdc9d0b96e5bf37a249f3f
  - command: ./gradlew lint
    exit_code: 0
    output_hash: sha256:a5483353e9ed6c0074783eee8a431d1dbdfb07e72f16c80d07dd21d19ccb06ca
  - command: ./gradlew :app:assembleRelease :app:lintRelease
    exit_code: 0
    output_hash: sha256:b32a27af2da48971f5235980ecec0499af7e6381d05d0f96f1cdab328b45598c
  - command: release JUnit XML validation
    exit_code: 0
    output_hash: sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
  - command: debug and release JUnit XML summary
    exit_code: 0
    output_hash: sha256:3ec28d7dcd9b40bf2ac409815933cacc9bfd7190666b14a0ee92fa0aa24b306a
  - command: release endpoint source-case validation
    exit_code: 0
    output_hash: sha256:09e14cfa037a43e8dc2d14e4db36de48df9a4889bde6d46deec0c3775eca2f36
  - command: changed-file privacy and scope scan
    exit_code: 0
    output_hash: sha256:068b94d85c39549a15c18a93c9485b7e87ad7e360ab7ed5807ac620e1e4468a5
test_results:
  debug:
    passed: 27
    failed: 0
    errors: 0
    skipped: 0
  release:
    passed: 27
    failed: 0
    errors: 0
    skipped: 0
  release_binding:
    class: ReleaseEndpointPolicyTest
    testcase: rejectsHttpAndAcceptsHttps
    passed: 1
    failed: 0
    errors: 0
    skipped: 0
release_endpoint_cases:
  - endpoint: http://localhost
    expected: reject
  - endpoint: http://127.0.0.1
    expected: reject
  - endpoint: http://10.0.2.2
    expected: reject
  - endpoint: http://music.example.com
    expected: reject
  - endpoint: https://music.example.com
    expected: accept
changed_line_accounting:
  - path: app/build.gradle.kts
    additions: 1
    deletions: 0
  - path: openspec/changes/navidrome-server-connection/tasks.md
    additions: 1
    deletions: 1
  - path: app/src/testRelease/java/dev/devdigi/music/connection/ReleaseEndpointPolicyTest.kt
    additions: 14
    deletions: 0
privacy_scan:
  changed_or_untracked_paths: 3
  expected_paths_only: true
  approved_synthetic_or_local_fixtures_only: true
  unapproved_occurrences: 0
scope:
  issue: 13
  network_requests_executed: 0
  docker_invoked: false
  private_systems_accessed: false
  authentication_or_credentials_added: false
lifecycle:
  attempt_finished: false
  runtime_or_review_state_mutated: false
```

### Verdict

**PASS WITH WARNINGS**

All 5 requirements, 10 scenarios, 20 tasks, both 27-test suites, and the real release binding test pass for active generation 6. Budget 282/300. The attempt remains running for the parent to finish.
