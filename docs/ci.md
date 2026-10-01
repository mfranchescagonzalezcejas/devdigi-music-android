# CI

Jenkins is the primary CI system. GitHub Actions CI has been removed to avoid a duplicate primary build.

## Implemented pipeline

CI-0 Basic pipeline is complete. The root `Jenkinsfile` runs visible formatting/static-analysis, unit-test, lint, debug-assembly, and synthetic Navidrome integration stages. It publishes ordinary unit-test and Navidrome integration JUnit results separately and archives available debug APK, test, and lint artifacts even when an earlier stage fails.

The Navidrome integration path is repository-owned and deterministic. It starts the pinned ephemeral Navidrome image, mounts only committed synthetic fixtures, creates a fresh synthetic account secret for the run, waits for authenticated library readiness, exercises the real JVM application boundaries, and cleans up its temporary Docker/runtime state.

Jenkins Declarative performs the initial SCM checkout automatically.
The pipeline does not need a second explicit `checkout scm` stage.
The formatting stage separately fetches `origin/develop` to establish
the Spotless Ratchet baseline. Removing the redundant checkout
does not eliminate all network operations or prevent transient
connection failures.

CI-2 / #33 removed duplicate verification. Issue #62 adds a Spotless + ktlint formatting gate to the existing formatting/static-analysis stage. Its Gradle command is only `spotlessCheck`: unit tests and Android lint still run once in their separate stages, and report/artifact handling is unchanged.

Spotless 8.10.3 and ktlint 1.8.0 are pinned. `.editorconfig` establishes the 140-character limit and permits PascalCase `@Composable` function names. Ratchet compares against `origin/develop`: legacy Kotlin files remain untouched until changed, at which point the *entire changed file* must meet formatting rules. Some line-length violations need manual fixes. Run `./gradlew spotlessApply` manually when appropriate, then review `git diff` before staging. Jenkins never applies formatting. Detekt and coverage are not configured and remain deferred.

CI-1 Multibranch integration is complete. Jenkins is configured as a Multibranch Pipeline for branches and pull requests, uses a fine-grained GitHub credential with the least permissions needed for repository access and commit statuses, and reports build results to GitHub.

GitHub webhooks now trigger Jenkins automatically for supported push and pull-request events. Push and pull-request deliveries have been validated end to end, including Multibranch discovery, Jenkins builds, and GitHub commit statuses. The previous periodic Multibranch scan has been disabled now that webhook delivery is the primary trigger.

## Controller and agent requirements

The Jenkins controller coordinates multibranch discovery, credentials, webhook-triggered discovery, and GitHub status reporting. An agent selected by the `android` label executes the Android build.

- The Android agent runs Jenkins with JDK 21 and has Android SDK platform 36 available, as demonstrated by #68 and subsequent `develop` builds. Build Tools are resolved from the provisioned SDK/AGP toolchain rather than documented as a fixed API-35 baseline.
- Gradle 9.5.0 and AGP 9.3.1 were verified locally using JDK 17; Android compilation targets Java 17. Jenkins may use another compatible Gradle runtime JDK. Verify its actual launcher and daemon versions using the pipeline diagnostics rather than assuming they match the agent JVM.
- Agents need a POSIX shell, Git access to the repository's `develop` branch, and permission to execute `./gradlew`. The formatting stage fetches `origin/develop` explicitly before `spotlessCheck` and fails if the Git baseline/merge-base cannot be resolved. The multibranch PR checkout must retain enough Git history to find that common ancestor; the required Jenkins PR check must verify this.
- The Android agent also needs access to a Docker daemon and the integrated `docker compose` command for synthetic Navidrome integration. The repository lifecycle preflight additionally checks the small POSIX/JVM-host toolset it uses, including Python 3, curl, core text utilities, and temporary-file support.
- Navidrome runtime state uses a unique temporary directory, random Compose project identity, dynamic loopback-only host port, temporary container data, and a fresh generated synthetic password. Concurrent trusted builds therefore do not rely on fixed container names, persistent shared volumes, or fixed host ports.
- No SDK, JDK, Docker socket path, server endpoint, or machine-specific runtime path is encoded in the repository. Gradle build caching is configured through `gradle.properties`, so each agent can use its own portable Gradle user home.

## Pull requests and credentials

Automated Navidrome integration uses no personal or persistent Navidrome credentials. The server, media, account and password are synthetic and ephemeral, and the test server is reachable only through a dynamically allocated loopback port.

Repository-owned branches and same-repository pull requests may run the Docker-backed synthetic integration. Pull requests originating from forks skip the Docker-backed stages because access to the Jenkins agent Docker daemon is a privileged capability. They still run the ordinary Android formatting, unit-test, lint and assembly gates.

The previous automated trusted-server Navidrome credential hook has been removed. Real user-provided Navidrome validation remains the separate manual #17 workflow and is not replaced by synthetic CI.

Signing and Play publishing credentials, when implemented, belong only in Jenkins Credentials and must be supplied only to explicitly trusted release jobs. They must never be stored in repository files, documentation, logs, or artifacts.

The interactive Jenkins UI remains protected. Machine-triggered webhook access is restricted to the dedicated integration path required for GitHub delivery rather than bypassing authentication for the Jenkins interface as a whole.

## Roadmap

| Item | Status   | Scope                                                                                                                                      |
| ---- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| CI-0 | Complete | Basic Android verification, reports, and debug artifacts.                                                                                  |
| CI-1 | Complete | Multibranch branch/PR discovery, GitHub webhook triggering, and commit statuses.                                                           |
| CI-2 | Implemented | #33 eliminated duplicate tests/lint; #62 introduces ratcheted Spotless as a separate formatting gate. |
| CI-3 | Implemented | #35 provides pinned, ephemeral synthetic Navidrome integration with isolated runtime state and separate JUnit evidence. |
| CI-4 | Planned  | Investigate and add useful Android instrumented CI testing.                                                                                |
| CI-5 | Planned  | Secure Android release signing from trusted refs.                                                                                          |
| CI-6 | Planned  | Tagged GitHub and Google Play release automation after signing is available.                                                               |

Automatic GitHub-to-Jenkins triggering tracked by #32 has been implemented and validated. Webhooks are now the primary trigger mechanism, while interactive Jenkins access remains protected separately.
