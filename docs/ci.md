# CI

Jenkins is the primary CI system. Configure this repository as a Multibranch Pipeline so Jenkins reads the root `Jenkinsfile` for branches and pull requests.

## Agent requirements

Agents must provide:

- JDK 17 or later that is supported by the Gradle wrapper (JDK 17 is the Android toolchain target).
- A pre-provisioned Android SDK containing Android API 35 and Build Tools 35.0.0.
- A POSIX shell and permission to execute `./gradlew`.

No SDK or JDK path is encoded in the repository. Gradle build caching is enabled through `gradle.properties`, so each Jenkins agent can use its own portable Gradle user home.

## Pipeline

The pipeline runs `./gradlew check`, `testDebugUnitTest`, `lint`, and `assembleDebug`. There is no formatting plugin in this project; the Formatting/static analysis stage therefore runs the existing `check` verification task rather than adding a formatter solely for CI.

The `post { always }` block publishes JUnit XML and archives any debug APK, unit-test result, and lint report that exists. Empty results are allowed so an early failure does not hide the original build failure.

GitHub Actions CI was removed to avoid a duplicate primary build. Jenkins should be configured to report branch and pull-request checks through its GitHub integration.

## Optional Navidrome integration

Set `RUN_NAVIDROME_INTEGRATION` only for a trusted build of `main`. The stage is skipped for every pull request, including same-repository pull requests, and never exposes credentials to untrusted PR code.

The Jenkins controller must provide these credentials by ID:

- `navidrome-url` as a Secret text credential.
- `navidrome-test-account` as a Username with password credential.

The pipeline passes those values only to Gradle at runtime. It stores no values in the repository and does not inspect local credential files. The current application has no Navidrome integration test yet, so this stage is a gated execution hook; add the actual trusted integration test before enabling it in routine builds.

Future release signing, Play publishing, and Navidrome credentials belong only in Jenkins Credentials.
