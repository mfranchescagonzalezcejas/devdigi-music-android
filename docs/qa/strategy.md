# QA strategy and named suite contract

Status: Active project QA contract  
Jira: MUSIC-2  
Related GitHub issue: #104  
Scope: DevDigi Music Android

## 1. Purpose

This document defines the reusable QA contract for DevDigi Music.

It explains:

- which testing layer owns which kind of signal;
- what the named `smoke`, `sanity`, `regression`, and `device` suites mean;
- when each suite is expected;
- how PASS / FAIL / BLOCKED / NOT RUN are recorded;
- what evidence may be retained;
- how manual QA, Jenkins, Gherkin, Cucumber, and real-instance validation fit together.

The goal is repeatable evidence without duplicating existing useful tests or
turning every check into an Android UI test.

## 2. Sources of truth

Use the following ownership model:

| Concern | Canonical source |
| --- | --- |
| Operational backlog, sprint, QA state, defects | Jira MUSIC |
| Code, PRs, executable tests, versioned Gherkin | GitHub repository |
| Primary CI result | Jenkins |
| Reusable QA process and templates | Repository docs + Notion playbooks |
| Historical feature-specific validation evidence | Versioned OpenSpec validation files |

Jenkins remains the primary CI system. This QA contract composes its existing
gates; it does not create a second CI authority.

## 3. Core principles

1. Test at the lowest layer that provides a reliable signal.
2. Use Gherkin for observable acceptance behavior, not as a wrapper around every
   low-level test.
3. Reuse the existing synthetic Navidrome integration environment.
4. Keep real-instance and physical-device validation explicit and opt-in.
5. Keep secrets, private endpoints, usernames, device serials, listening data,
   signed stream URLs, and raw authenticated logs out of committed evidence.
6. A merged PR is not sufficient evidence when the acceptance risk requires
   device or real-instance validation.
7. A Story is Done only when its required verification is known and residual
   risk is accepted.

## 4. Testing layers

### 4.1 Static and build gates

Purpose:

- formatting;
- Android lint;
- compilation/assembly;
- basic repository hygiene.

Current authoritative checks include:

```sh
./gradlew spotlessCheck
./gradlew lint
./gradlew assembleDebug
```

Jenkins owns the required PR evidence for these gates.

### 4.2 JVM unit and behavior tests

Use for:

- domain behavior;
- parsers;
- reducers and state transitions;
- cancellation/generation rules;
- account isolation policies;
- ViewModel behavior that does not need Android runtime;
- presentation helpers.

Current baseline:

```sh
./gradlew testDebugUnitTest
```

Prefer this layer when Android UI execution would add cost without increasing
confidence.

### 4.3 Contract and synthetic integration tests

Use for:

- repository boundaries;
- authenticated OpenSubsonic behavior;
- malformed/error responses;
- mapping between transport and application outcomes;
- deterministic server-backed behavior.

The existing synthetic Navidrome environment is the single repository-owned
integration environment:

```sh
./integration/navidrome/lifecycle.sh preflight
./integration/navidrome/run-integration.sh
```

Do not create a parallel Navidrome test stack for QA suites.

### 4.4 BDD acceptance specifications

Gherkin describes canonical user-observable behavior.

Use it for scenarios such as:

- authentication and session restoration;
- recent albums;
- album details;
- account switching/isolation;
- playback;
- queue behavior;
- errors and recovery;
- release-critical security/privacy behavior.

A Gherkin scenario may be:

- manual;
- automated directly through Kotlin/Compose tests;
- automated through Cucumber when that adds maintainable value.

The presence of Gherkin does not require Cucumber.

### 4.5 Android instrumented / Compose UI automation

Use only where Android runtime or UI behavior is the signal being tested.

Examples:

- Compose semantics and interaction;
- lifecycle behavior that cannot be proven lower;
- device/runtime integration;
- Android framework surfaces.

Issue #34 owns the decision about useful instrumented CI coverage.

### 4.6 Real-device / real-instance validation

Use for behavior where synthetic or JVM coverage is insufficient, including:

- real BYON authentication;
- Media3 playback on Android hardware;
- notification / lock-screen / media controls;
- process recreation;
- account switching;
- privacy checks on system playback surfaces;
- release-candidate confidence.

Existing privacy-safe evidence under OpenSpec is historical input to reusable QA
scenarios. New runs should not record private deployment details.

## 5. Named suite contract

The suite names are stable contracts. Their implementation may evolve.

Until a repository-owned `tools/qa` entry point exists, a suite may be
executed by composing the documented Gradle/integration commands plus the
required manual/BDD scenarios.

### 5.1 Smoke

Purpose: prove the smallest critical user path is alive.

Target scope:

- app builds and starts;
- valid server configuration/authentication;
- authenticated session available;
- recent library content can load;
- album details can open;
- a queue/playback path can start when playback is in scope;
- sign-out clears active runtime ownership;
- no secret is exposed in inspected user/system surfaces.

Expected use:

- release-candidate validation;
- after high-risk cross-cutting changes;
- targeted device validation when the critical path depends on Android runtime.

Smoke is intentionally small. It is not full regression.

### 5.2 Sanity

Purpose: verify a changed feature and its immediate dependencies.

Composition:

- focused unit/behavior tests for the change;
- focused integration tests where relevant;
- directly affected acceptance scenarios;
- adjacent critical-path checks if the feature can break them.

Expected use:

- during feature development;
- before requesting review on a focused change;
- after a defect fix before broader regression.

Sanity should be proportional to the change.

### 5.3 Regression

Purpose: provide broad repeatable confidence that established behavior remains
valid.

Automated baseline:

- `spotlessCheck`;
- `testDebugUnitTest`;
- Android lint;
- `assembleDebug`;
- synthetic Navidrome integration.

Add acceptance/manual/device coverage according to risk.

Expected use:

- significant vertical-slice closeout;
- release-candidate validation;
- after changes to authentication, account isolation, persistence, playback
  lifecycle, CI/build infrastructure, or other cross-cutting boundaries.

Regression is not automatically "run every manual scenario". The selected scope
must be recorded.

### 5.4 Device

Purpose: validate behavior that specifically requires Android hardware/runtime
or real system surfaces.

Possible coverage:

- Media3 playback;
- background playback;
- notification and lock-screen controls;
- Activity/process recreation;
- orientation/width behavior;
- real-instance authentication/recovery;
- account switching;
- privacy-safe runtime inspection.

The device suite is opt-in and must never depend on committed private server
configuration.

## 6. When suites are expected

| Work stage | Required QA expectation |
| --- | --- |
| Feature development | Focused tests + Sanity |
| PR closeout | Jenkins required; Sanity evidence; Smoke/Regression if risk requires |
| Defect retest | Reproduce → targeted retest → proportional regression |
| Significant vertical-slice closeout | Jenkins + Regression + required device/real-instance evidence |
| Release candidate | Jenkins + Smoke + selected Regression + required Device evidence |
| Low-risk docs-only change | Documentation review; automated code suites may be N/A with reason |

A required suite may be marked N/A only with an explicit reason.

## 7. Result semantics

Use the same result vocabulary in Jira Test Executions, manual notes, and
future automated reporting.

### PASS

The expected result was observed for the build/commit and environment under
test.

### FAIL

The expected result was not observed and the scenario executed far enough to
make that conclusion.

Action:

- create/link a Jira Bug when actionable;
- preserve privacy-safe evidence;
- do not mark the parent acceptance complete.

### BLOCKED

The scenario could not produce a valid result because a prerequisite,
environment, dependency, or external condition prevented execution.

A blocker is not a product PASS or FAIL.

Record:

- what blocked execution;
- whether the blocker is product, environment, infrastructure, or dependency;
- the next action.

### NOT RUN

The scenario was in scope but was not executed.

Use for incomplete test runs, not as a substitute for BLOCKED.

### N/A

The scenario or suite does not apply to this change/run.

Record the reason.

## 8. Exit-code contract for future QA entry points

If a repository-owned QA runner is introduced, use predictable process exits:

- `0` — requested automated suite PASS;
- non-zero — automated suite FAIL or execution error.

Human states such as BLOCKED, NOT RUN, and N/A must remain explicit in the
execution record; they must not be silently converted into PASS.

Do not implement a new runner merely to satisfy this document. The runner is
separate scoped work under #104.

## 9. Test execution record

For any meaningful manual/device/release execution, record:

- Jira Test Execution or equivalent work item;
- build/version;
- commit SHA;
- branch/PR when relevant;
- date;
- tester;
- environment category;
- device model/Android version only when safe and useful;
- scenarios included;
- PASS / FAIL / BLOCKED / NOT RUN / N/A per scenario;
- sanitized evidence;
- linked Bug for each actionable failure;
- retest relationship;
- regression decision;
- residual risk;
- confirmation that no private server/account/authentication data was captured.

Do not store real passwords, tokens, salts, private endpoints, device serials,
signed stream URLs, or listening/library metadata as evidence.

## 10. Defect workflow

For a failed acceptance scenario:

1. Confirm the failure is reproducible enough to act on.
2. Create/link a Jira Bug.
3. Record expected vs actual behavior.
4. Record build/environment and sanitized evidence.
5. Fix through the normal Jira → branch → PR → Jenkins workflow.
6. Run targeted retest.
7. Run proportional regression.
8. Update the Test Execution.
9. Close the Bug only when the required retest is PASS.

Priority and severity are separate concepts.

## 11. Gherkin tags

Canonical initial tags:

- `@smoke`
- `@sanity`
- `@regression`
- `@manual`
- `@automated`
- `@automation-candidate`
- `@real-instance`
- `@android`
- `@auth`
- `@library`
- `@playback`
- `@security`
- `@account-isolation`

Tags describe selection and ownership; they must not encode private environment
details.

## 12. Cucumber decision boundary

Use Cucumber only where it improves shared acceptance readability and
maintainability.

Prefer direct Kotlin/JUnit/Compose tests when:

- the behavior is lower-level;
- the scenario does not benefit from business-readable steps;
- Cucumber would only add indirection.

A later automation spike must decide:

- Cucumber JVM / Android compatibility;
- the first maintainable automated Gherkin scenario;
- tag-based execution;
- Compose integration;
- Jenkins/JUnit reporting.

## 13. Jenkins relationship

Jenkins remains mandatory for normal PR integration.

The QA strategy reuses the current Jenkins baseline:

```text
spotlessCheck
→ testDebugUnitTest
→ Android lint
→ assembleDebug
→ synthetic Navidrome integration
```

Future instrumented/Cucumber reporting may add evidence to Jenkins, but must not
create a second competing primary CI.

## 14. Privacy and evidence policy

Treat QA artifacts as potentially public.

Never commit or publish:

- credentials or authentication material;
- private server URLs/DNS/IP addresses;
- usernames/account identifiers;
- device serials;
- signed authenticated stream URLs;
- listening history or personal library data;
- raw authenticated logs;
- machine-specific private paths.

Use placeholders and sanitized summaries.

Temporary local inspection may use sensitive values when needed for validation,
but they must not be persisted in repository evidence.

## 15. Current known implementation gaps

This strategy defines the contract; it does not claim the following already
exist:

- central `./tools/qa smoke|sanity|regression|device` runner;
- canonical versioned Gherkin feature files;
- Android Cucumber integration;
- instrumented CI;
- automatic Jira Test Execution import from Jenkins.

Those belong to separate scoped work under #104 and related Jira items.

## 16. Definition of done for this strategy

MUSIC-2 is complete when:

- the testing layers are defined;
- smoke/sanity/regression/device have stable semantics;
- suite expectations by work stage are documented;
- result semantics are consistent;
- Jenkins and the synthetic integration environment are reused;
- manual/device evidence requirements are defined;
- Gherkin/Cucumber boundaries are explicit;
- privacy rules are explicit;
- future harness work is clearly separated from this documentation change.
