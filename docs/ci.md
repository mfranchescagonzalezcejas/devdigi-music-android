# CI

Jenkins is the primary CI system. GitHub Actions CI has been removed to avoid a duplicate primary build.

## Implemented pipeline

CI-0 Basic pipeline is complete. The root `Jenkinsfile` runs visible formatting/static-analysis, unit-test, lint, and debug-assembly stages. It publishes JUnit XML and archives available debug APK, unit-test, and lint artifacts even when an earlier stage fails.

The formatting/static-analysis stage currently runs Gradle `check`. This intentionally overlaps with the later unit-test and lint stages so the existing verification remains visible. No formatter, ktlint, detekt, or coverage plugin is configured yet.

CI-1 Multibranch integration is complete. Jenkins is configured as a Multibranch Pipeline for branches and pull requests, uses a fine-grained GitHub credential with the least permissions needed for repository access and commit statuses, and reports build results to GitHub. A periodic multibranch scan is enabled; automatic GitHub-to-Jenkins triggering remains future work.

## Controller and agent requirements

The Jenkins controller coordinates multibranch discovery, credentials, and GitHub status reporting. An agent selected by the `android` label executes the Android build.

- The Android agent runs Jenkins with JDK 21 and has a pre-provisioned Android SDK containing Android API 35 and Build Tools 35.0.0.
- The project build requires Gradle runtime JDK 25, while Android compilation targets Java 17. The controller must provision or select a compatible JDK 25 runtime for the Gradle invocation; the agent JVM version alone does not satisfy that requirement.
- Agents need a POSIX shell and permission to execute `./gradlew`.
- No SDK or JDK path is encoded in the repository. Gradle build caching is configured through `gradle.properties`, so each agent can use its own portable Gradle user home.

## Pull requests and credentials

External pull requests must not receive secrets or execute trusted-only integration steps. The optional Navidrome hook is limited to trusted builds of `main` and is skipped for all pull requests.

Signing, Play publishing, and Navidrome credentials belong only in Jenkins Credentials. They are supplied only at execution time and are never stored in repository files, documentation, logs, or artifacts.

## Roadmap

| Item | Status | Scope |
| --- | --- | --- |
| CI-0 | Complete | Basic Android verification, reports, and debug artifacts. |
| CI-1 | Complete | Multibranch branch/PR discovery, GitHub commit statuses, and periodic scans. |
| CI-2 | Planned | Evaluate static-analysis tooling; remove duplicated verification while retaining visible stages and reports. |
| CI-3 | Planned | Secure automatic GitHub-to-Jenkins triggering that works without assuming a public Jenkins endpoint. |
| CI-4 | Planned | Reproducible synthetic Navidrome integration environment and useful Android instrumented CI testing. |
| CI-5 | Planned | Secure Android release signing from trusted refs. |
| CI-6 | Planned | Tagged GitHub and Google Play release automation after signing is available. |
