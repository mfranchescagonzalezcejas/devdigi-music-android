# CI

Jenkins is the primary CI system. GitHub Actions CI has been removed to avoid a duplicate primary build.

## Implemented pipeline

CI-0 Basic pipeline is complete. The root `Jenkinsfile` runs visible formatting/static-analysis, unit-test, lint, and debug-assembly stages. It publishes JUnit XML and archives available debug APK, unit-test, and lint artifacts even when an earlier stage fails.

CI-2 / #33 is complete. The formatting/static-analysis stage is an intentional visible diagnostic placeholder, not a gate: it explains that formatter and static-analysis tooling is deferred for the bootstrap project. It does not run Gradle `check`, tests, or lint. Unit tests run once in their dedicated `testDebugUnitTest` stage and Android lint runs once in its dedicated `lint` stage, with the existing reports and artifacts preserved.

No formatter, ktlint, detekt, or coverage plugin is configured. Coverage is explicitly deferred until behavior-focused tests exist; the current generated application-ID test does not provide a meaningful baseline.

CI-1 Multibranch integration is complete. Jenkins is configured as a Multibranch Pipeline for branches and pull requests, uses a fine-grained GitHub credential with the least permissions needed for repository access and commit statuses, and reports build results to GitHub.

GitHub webhooks now trigger Jenkins automatically for supported push and pull-request events. Push and pull-request deliveries have been validated end to end, including Multibranch discovery, Jenkins builds, and GitHub commit statuses. The previous periodic Multibranch scan has been disabled now that webhook delivery is the primary trigger.

## Controller and agent requirements

The Jenkins controller coordinates multibranch discovery, credentials, webhook-triggered discovery, and GitHub status reporting. An agent selected by the `android` label executes the Android build.

- The Android agent runs Jenkins with JDK 21 and has a pre-provisioned Android SDK containing Android API 35 and Build Tools 35.0.0.
- The project build requires Gradle runtime JDK 25, while Android compilation targets Java 17. The controller must provision or select a compatible JDK 25 runtime for the Gradle invocation; the agent JVM version alone does not satisfy that requirement.
- Agents need a POSIX shell and permission to execute `./gradlew`.
- No SDK or JDK path is encoded in the repository. Gradle build caching is configured through `gradle.properties`, so each agent can use its own portable Gradle user home.

## Pull requests and credentials

External pull requests must not receive secrets or execute trusted-only integration steps. The optional Navidrome hook is limited to trusted builds of `main` and is skipped for all pull requests.

Signing, Play publishing, and Navidrome credentials belong only in Jenkins Credentials. They are supplied only at execution time and are never stored in repository files, documentation, logs, or artifacts.

The interactive Jenkins UI remains protected. Machine-triggered webhook access is restricted to the dedicated integration path required for GitHub delivery rather than bypassing authentication for the Jenkins interface as a whole.

## Roadmap

| Item | Status   | Scope                                                                                                                                      |
| ---- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| CI-0 | Complete | Basic Android verification, reports, and debug artifacts.                                                                                  |
| CI-1 | Complete | Multibranch branch/PR discovery, GitHub webhook triggering, and commit statuses.                                                           |
| CI-2 | Complete | #33 removed duplicated verification; the visible quality diagnostic defers tooling and coverage until behavior-focused tests justify them. |
| CI-3 | Planned  | Reproducible synthetic Navidrome integration environment.                                                                                  |
| CI-4 | Planned  | Investigate and add useful Android instrumented CI testing.                                                                                |
| CI-5 | Planned  | Secure Android release signing from trusted refs.                                                                                          |
| CI-6 | Planned  | Tagged GitHub and Google Play release automation after signing is available.                                                               |

Automatic GitHub-to-Jenkins triggering tracked by #32 has been implemented and validated. Webhooks are now the primary trigger mechanism, while interactive Jenkins access remains protected separately.
