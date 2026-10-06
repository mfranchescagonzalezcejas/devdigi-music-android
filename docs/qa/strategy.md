# QA strategy and named suite contract

Status: Active project QA contract  
Jira: MUSIC-2  
Related GitHub issue: #104  
Scope: DevDigi Music Android

## 1. Purpose

This document defines the reusable QA contract for DevDigi Music:
- which layer owns which testing signal;
- what `smoke`, `sanity`, `regression`, and `device` mean;
- when each suite is expected;
- how PASS / FAIL / BLOCKED / NOT RUN / N/A are recorded;
- what evidence may be retained;
- how Jenkins, manual QA, Gherkin, Cucumber, and real-instance validation fit together.

The goal is repeatable evidence without duplicating useful tests or forcing every check through Android UI automation.

## 2. Ownership and principles

| Concern | Canonical source |
| --- | --- |
| Backlog, sprint, QA state, defects | Jira MUSIC |
| Code, PRs, executable tests, versioned Gherkin | GitHub |
| Primary CI result | Jenkins |
| Reusable QA process/templates | Repository docs + Notion |
| Historical feature validation | Versioned OpenSpec evidence |

Principles:
1. Test at the lowest layer that provides a reliable signal.
2. Use Gherkin for observable acceptance behavior, not as a wrapper around every low-level test.
3. Reuse the existing synthetic Navidrome integration environment.
4. Keep real-instance/device validation explicit and opt-in.
5. Keep secrets, private endpoints, usernames, device serials, listening data, signed stream URLs, and raw authenticated logs out of committed evidence.
6. A merged PR is not enough when acceptance risk requires device or real-instance evidence.
7. A Story is Done only when required verification is known and residual risk is accepted.

## 3. Testing layers

### Static and build gates
Own formatting, lint, compilation, and basic repository hygiene.

Current checks include:
```sh
./gradlew spotlessCheck
./gradlew lint
./gradlew assembleDebug
```

Jenkins owns required PR evidence for these gates.

### JVM unit and behavior tests
Use for domain behavior, parsers, reducers/state transitions, account isolation policies, cancellation/generation rules, ViewModel behavior that does not need Android runtime, and presentation helpers.

Current baseline:
```sh
./gradlew testDebugUnitTest
```

Prefer this layer when Android UI execution would add cost without increasing confidence.

### Contract and synthetic integration tests
Use for repository boundaries, authenticated OpenSubsonic behavior, malformed/error responses, transport-to-application mapping, and deterministic server-backed behavior.

The existing repository-owned Navidrome environment is authoritative:
```sh
./integration/navidrome/lifecycle.sh preflight
./integration/navidrome/run-integration.sh
```

Do not create a parallel Navidrome test environment.

### BDD acceptance specifications
Gherkin describes canonical user-observable behavior such as authentication/session restore, recent albums, album details, account isolation, playback, queue behavior, recovery, and release-critical privacy behavior.

A Gherkin scenario may be manual, automated through direct Kotlin/Compose tests, or automated through Cucumber when that adds maintainable value. Gherkin does not imply Cucumber.

### Android instrumented / Compose UI
Use only where Android runtime or UI behavior is the signal: Compose semantics/interaction, lifecycle behavior that cannot be proven lower, and Android framework surfaces. Issue #34 owns useful instrumented CI coverage.

### Real-device / real-instance
Use where synthetic/JVM coverage is insufficient: real BYON authentication, Media3 hardware playback, notification/lock-screen controls, process recreation, account switching, and privacy checks on system surfaces.

Existing privacy-safe OpenSpec evidence is historical input to reusable scenarios. New runs must not record private deployment details.

## 4. Named suite contract

Suite names are stable contracts; implementation may evolve. Until a repository-owned `tools/qa` entry point exists, suites may compose documented Gradle/integration commands plus required manual/BDD scenarios.

### Smoke
Purpose: prove the smallest critical user path is alive.

Typical scope:
- app builds/starts;
- valid server configuration/authentication;
- authenticated session available;
- recent library content loads;
- album details open;
- queue/playback can start when in scope;
- sign-out clears runtime ownership;
- inspected user/system surfaces expose no secret.

Use for release candidates and high-risk cross-cutting changes. Smoke is intentionally smaller than regression.

### Sanity
Purpose: verify a changed feature and immediate dependencies.

Composition:
- focused unit/behavior tests;
- focused integration where relevant;
- directly affected acceptance scenarios;
- adjacent critical-path checks if the feature can break them.

Use during feature development, before review on a focused change, and after a defect fix before broader regression.

### Regression
Purpose: broad repeatable confidence that established behavior remains valid.

Automated baseline:
- `spotlessCheck`;
- `testDebugUnitTest`;
- Android lint;
- `assembleDebug`;
- synthetic Navidrome integration.

Add acceptance/manual/device coverage according to risk. Use for significant vertical-slice closeout, release candidates, and changes to authentication, account isolation, persistence, playback lifecycle, or CI/build boundaries.

Regression does not mean "run every manual scenario"; record the selected scope.

### Device
Purpose: validate behavior requiring Android hardware/runtime or real system surfaces.

Possible coverage:
- Media3/background playback;
- notification/lock-screen/media controls;
- Activity/process recreation;
- orientation/width behavior;
- real-instance authentication/recovery;
- account switching;
- privacy-safe runtime inspection.

The suite is opt-in and never depends on committed private server configuration.

## 5. Expected suites by work stage

| Stage | QA expectation |
| --- | --- |
| Feature development | Focused tests + Sanity |
| PR closeout | Jenkins + Sanity; Smoke/Regression if risk requires |
| Defect retest | Reproduce → targeted retest → proportional regression |
| Significant vertical-slice closeout | Jenkins + Regression + required device/real-instance evidence |
| Release candidate | Jenkins + Smoke + selected Regression + required Device evidence |
| Low-risk docs-only change | Documentation review; code suites may be N/A with reason |

A required suite may be N/A only with an explicit reason.

## 6. Result semantics

**PASS** — expected result observed for the build/commit and environment under test.

**FAIL** — expected result not observed and execution was sufficient to conclude failure. Create/link a Jira Bug when actionable and retain only privacy-safe evidence.

**BLOCKED** — no valid result because a prerequisite, environment, dependency, or external condition prevented execution. Record the blocker type and next action.

**NOT RUN** — scenario was in scope but was not executed. Do not use this instead of BLOCKED.

**N/A** — scenario/suite does not apply; record why.

Future automated QA entry points should return `0` for PASS and non-zero for FAIL/execution error. Human states BLOCKED/NOT RUN/N/A remain explicit in the Test Execution and must never be silently converted to PASS.

## 7. Test Execution record

For meaningful manual/device/release execution, record:
- Jira Test Execution or equivalent item;
- build/version and commit SHA;
- branch/PR when relevant;
- date/tester;
- environment category;
- safe device/Android context when useful;
- scenarios included;
- result per scenario;
- sanitized evidence;
- linked Bug for actionable failures;
- retest relationship;
- regression decision;
- residual risk;
- confirmation that no private server/account/authentication data was captured.

Never retain real passwords, tokens, salts, private endpoints, device serials, signed stream URLs, listening/library metadata, or raw authenticated logs.

## 8. Defect workflow

For a failed acceptance scenario:
1. Confirm it is reproducible enough to act on.
2. Create/link a Jira Bug.
3. Record expected vs actual, build/environment, and sanitized evidence.
4. Fix through Jira → branch → PR → Jenkins.
5. Run targeted retest.
6. Run proportional regression.
7. Update the Test Execution.
8. Close the Bug only when required retest is PASS.

Priority and severity are separate concepts.

## 9. Gherkin tags

Canonical initial tags:
- `@smoke`, `@sanity`, `@regression`;
- `@manual`, `@automated`, `@automation-candidate`;
- `@real-instance`, `@android`;
- `@auth`, `@library`, `@playback`;
- `@security`, `@account-isolation`.

Tags describe selection/ownership and never encode private environment details.

## 10. Cucumber boundary

Use Cucumber only where it improves acceptance readability and maintainability. Prefer direct Kotlin/JUnit/Compose tests when the behavior is lower-level or Cucumber would add indirection without better confidence.

The later automation spike must decide:
- Cucumber JVM / Android compatibility;
- the first maintainable automated Gherkin scenario;
- tag-based execution;
- Compose integration;
- Jenkins/JUnit reporting.

## 11. Jenkins relationship

Jenkins remains mandatory for normal PR integration. This strategy reuses the current baseline:
```text
spotlessCheck
→ testDebugUnitTest
→ Android lint
→ assembleDebug
→ synthetic Navidrome integration
```

Future instrumented/Cucumber reporting may add evidence to Jenkins but must not create another primary CI.

## 12. Privacy and evidence

Treat QA artifacts as potentially public. Never commit or publish credentials/authentication material, private server URLs/DNS/IPs, usernames/account identifiers, device serials, signed authenticated stream URLs, listening history/personal library data, raw authenticated logs, or private machine paths.

Use placeholders and sanitized summaries. Temporary local inspection may use sensitive values when necessary, but they must not be persisted in repository evidence.

## 13. Known implementation gaps

This contract does not claim these already exist:
- central `./tools/qa smoke|sanity|regression|device` runner;
- canonical versioned Gherkin feature files;
- Android Cucumber integration;
- instrumented CI;
- automatic Jenkins → Jira Test Execution import.

Those are separate scoped work under #104 and related Jira items.

## 14. Definition of done

MUSIC-2 is complete when:
- testing layers are defined;
- smoke/sanity/regression/device have stable semantics;
- suite expectations by work stage are documented;
- result semantics are consistent;
- Jenkins and the synthetic environment are reused;
- manual/device evidence requirements are defined;
- Gherkin/Cucumber boundaries are explicit;
- privacy rules are explicit;
- future harness work is clearly separated from this documentation change.
