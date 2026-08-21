# Tasks: Navidrome Server Connection

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 260–360 remaining; 560–720 total |
| 400-line budget risk | Medium (remaining slice) |
| 800-line budget risk | Medium (session total) |
| Chained PRs recommended | No |
| Suggested split | Single PR; one persistence/policy slice |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium
800-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 3 | Persistence and variant admission | PR 1 | `./gradlew testDebugUnitTest assembleRelease` | N/A: temp-file DataStore/synthetic URLs; no Docker, Compose, or server | Revert Phases 5–7 only; preserve staged history |

## Phase 1: RED Tests / Contract Boundaries

- [x] 1.1 Create `ServerConnectionTest.kt` RED cases for normalization, paths, rejection, profile shape, facts, and reducer invariants.
- [x] 1.2 Create `ServerConnectionViewModelTest.kt` RED cases for edit, invalid/valid confirm, and unchanged `NotChecked` facts.

## Phase 2: Pure Core / GREEN

- [x] 2.1 Create `ServerConnection.kt` with pure validation, immutable profile, independent facts, credential-free `PingClient`, reducer, and no I/O.
- [x] 2.2 Make tests pass for malformed/unsafe hosts, control/ambiguous paths, port 443, and reverse-proxy paths.

## Phase 3: UI State and Wiring

- [x] 3.1 Add only `lifecycle-viewmodel-compose`; add no HTTP, JSON, navigation, persistence, or `INTERNET` permission.
- [x] 3.2 Create `ServerConnectionViewModel.kt` with immutable state, input/confirm, pure parser, and sign-in copy; no I/O.
- [x] 3.3 Create stateless `ServerConnectionScreen.kt`; wire `MainActivity.kt` via `viewModel()`.

## Phase 4: Verification

- [x] 4.1 Run unit tests, debug assemble, and lint; use synthetic data only.
- [x] 4.2 Confirm no ping, credentials, identity, persistence, or false facts; threat rows N/A.

## Phase 5: Remaining RED Tests (TDD)

- [x] 5.1 RED: extend `app/src/test/.../ServerConnectionTest.kt` for release HTTP rejection, debug local allowlist (`localhost`, `127.0.0.1`, `10.0.2.2`), HTTPS, and no request.
- [x] 5.2 RED: create `app/src/test/.../ServerProfileRepositoryTest.kt` for temp-file DataStore restore/reparse, replace, delete, malformed discard, and one-key endpoint-only payload.
- [x] 5.3 RED: extend `app/src/test/.../ServerConnectionViewModelTest.kt` with fake-Flow restore/replace/delete and write-failure cases.
- [x] 5.4 RED: create `app/src/testDebug/.../EndpointPolicyTest.kt` and `app/src/testRelease/.../EndpointPolicyTest.kt`; synthetic only, no sockets.

## Phase 6: Remaining GREEN Implementation

- [x] 6.1 GREEN: modify `app/src/main/.../ServerConnection.kt` for `EndpointPolicy` admission; retain pure normalization/ping boundary.
- [x] 6.2 GREEN: create `app/src/main/.../ServerProfileRepository.kt` with one `server_endpoint` Preferences key, `Flow`, save/delete, app-context DataStore, and reparse.
- [x] 6.3 GREEN: modify `ServerConnectionViewModel.kt` for injected repository, scoped restore/save/delete, Flow state, and failure safety.
- [x] 6.4 GREEN: modify `ServerConnectionScreen.kt`, `MainActivity.kt`, `gradle/libs.versions.toml`, and `app/build.gradle.kts` for delete, restore, factory, and aliases.
- [x] 6.5 GREEN: create debug/release `BuildVariantEndpointPolicy.kt`, plus debug manifest and `res/xml/network_security_config.xml` with local cleartext allowlist.

## Phase 7: Local Verification / Settle

- [x] 7.1 Run local synthetic verification: `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, and `lint`; `./gradlew -PunitTestBuildType=release testReleaseUnitTest` executes the real release `BuildVariantEndpointPolicy` behavior.
- [x] 7.2 Preserve staged modifications; do not reset or recreate them. Continue the supplied active attempt token and return control without acquiring or settling another attempt.
