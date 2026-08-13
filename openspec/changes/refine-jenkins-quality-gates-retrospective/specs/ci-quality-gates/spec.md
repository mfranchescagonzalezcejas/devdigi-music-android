# CI Quality Gates Specification

## Purpose

Define the CI-2 quality-gate behavior reconstructed from issue #33, comparing RED `d618641^` with GREEN `d618641`.

## Requirements

### Requirement: Explicit Android verification gates

The pipeline MUST run `testDebugUnitTest`, `lint`, and `assembleDebug` in separate visible stages. It MUST publish JUnit XML and archive debug APK and lint XML, HTML, and SARIF reports in an always-run post-build action.

#### Scenario: Successful standard pipeline

- GIVEN an Android-compatible agent and a buildable revision
- WHEN the standard pipeline runs
- THEN each named verification command SHALL run in its own stage
- AND its JUnit and APK/lint outputs SHALL be published or archived.

#### Scenario: Earlier gate fails

- GIVEN a unit-test or lint failure produces available reports
- WHEN the pipeline reaches its post-build action
- THEN it MUST attempt publication and archival with empty results permitted.

### Requirement: Single execution and diagnosable pipeline

The standard quality path MUST NOT execute unit tests or Android lint more than once. A diagnostic stage MAY explain deferred tooling, but MUST NOT invoke aggregate `check`, tests, or lint. The pipeline SHOULD keep distinct stage names so a failed gate is diagnosable and SHOULD avoid unnecessary aggregate work.

#### Scenario: Deferred diagnostic stage

- GIVEN no formatter or static-analysis tool is configured
- WHEN the diagnostic stage runs
- THEN it SHALL emit only the deferral diagnosis
- AND no relevant quality gate SHALL be duplicated.

### Requirement: Safe Android-agent operation

The pipeline MUST run on an `android`-labelled agent with its required SDK and compatible Gradle runtime. It MUST NOT print, archive, or persist private credentials. New quality tools MUST NOT be introduced without actionable value, bounded maintenance cost, and demonstrated Android/AGP compatibility.

#### Scenario: Missing SDK

- GIVEN the Android SDK is unavailable to Gradle
- WHEN a verification command runs
- THEN the build MUST fail diagnostically rather than claim a quality result.

### Requirement: Tooling decision

| Tool | Current value / overlap | Maintenance and compatibility | Decision |
| --- | --- | --- | --- |
| ktlint | No configured Kotlin style policy; overlaps lint review | Adds rules/plugin compatibility work | Defer |
| detekt | No actionable static-analysis rules; overlaps Android lint | Adds configuration and AGP/Kotlin upkeep | Defer |
| Spotless | No formatting convention; overlaps ktlint-style enforcement | Adds formatter/plugin upkeep | Reject now |
| Kover | One generated test gives no meaningful coverage signal | Adds coverage/plugin compatibility upkeep | Defer |

The project MUST retain these decisions until concrete conventions or behavior-focused tests justify reevaluation.

#### Scenario: Tooling reconsideration

- GIVEN a proposed new quality tool
- WHEN its adoption is evaluated
- THEN the proposal MUST demonstrate unique actionable value and Android-agent compatibility
- AND it SHALL state its maintenance owner and overlap with existing gates.

### Requirement: Retrospective acceptance evidence

Acceptance MUST distinguish RED `d618641^` from GREEN `d618641` using a provisioned Android SDK and non-cached or Jenkins-equivalent execution.

#### Scenario: RED duplicate evidence

- GIVEN a detached checkout at `d618641^` and `ANDROID_HOME` is set
- WHEN `./gradlew check --dry-run --offline` runs
- THEN it MUST exit `0` and list `:app:lint` and `:app:testDebugUnitTest`
- AND `git show d618641^:Jenkinsfile` SHALL show `./gradlew check` plus dedicated test and lint stages.

#### Scenario: GREEN single-gate evidence

- GIVEN a detached checkout at `d618641` and `ANDROID_HOME` is set
- WHEN `./gradlew --no-build-cache clean testDebugUnitTest lint assembleDebug` runs
- THEN it MUST exit `0` and produce JUnit XML, lint reports, and a debug APK
- AND `git show d618641:Jenkinsfile` SHALL contain no `./gradlew check` and one dedicated command for each gate.
