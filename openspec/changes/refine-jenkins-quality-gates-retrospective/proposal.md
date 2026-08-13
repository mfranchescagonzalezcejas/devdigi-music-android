# Proposal: Refine Jenkins Quality Gates Retrospective

## Intent

Reconstruct and retrospectively validate the objective of closed issue #33 at immutable commit `d618641`: remove duplicate Jenkins execution of Android lint and unit tests while retaining explicit, visible gates and existing report/APK publication.

## Scope

### In Scope
- Compare RED `d618641^` (`54a1cc4`) with GREEN `d618641` as read-only evidence.
- Verify that RED aggregate `./gradlew check` includes lint and `testDebugUnitTest`, making later dedicated stages redundant.
- Verify that GREEN retains dedicated lint and unit-test stages, report publication, and APK publication without aggregate `check`.
- Record reproducible findings with a provisioned Android SDK and non-cached or Jenkins-equivalent execution for final validation.

### Out of Scope
- Any product, Jenkinsfile, CI, documentation, build, plugin, or infrastructure change.
- New ktlint, detekt, Spotless, coverage, or reporting tooling.
- Git history rewrites, commits, branch changes, GitHub mutations, or Jenkins configuration changes.

## Capabilities

### New Capabilities
None. This is a retrospective validation of existing pipeline configuration.

### Modified Capabilities
None. No existing capability requirements change; `openspec/specs/` has no capability specs.

## Approach

Treat `d618641^` as RED and `d618641` as GREEN. Inspect the committed Jenkins and CI-documentation diff, then execute only verification commands in an isolated, provisioned environment. Confirm the RED task graph contains both dedicated gates and the GREEN pipeline executes each gate once while retaining publications. Do not alter either baseline or any external system.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `Jenkinsfile` | Validated only | Compare duplicate RED gate with GREEN split gates. |
| `docs/ci.md` | Validated only | Confirm recorded decision and deferred tooling. |
| `build.gradle.kts`, `app/build.gradle.kts` | Inspected only | Establish available Gradle task/plugin surface. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Missing Android SDK produces false failures | Medium | Set/provision `ANDROID_HOME` before validation. |
| Gradle cache masks execution | Medium | Use clean/non-cached or Jenkins-equivalent verification. |
| Coverage tooling appears desirable | Low | Defer until behavior-focused tests make its signal actionable. |

## Rollback Plan

No rollback is required: this proposal performs no mutations. Discard generated local evidence if validation is invalid; leave commits and external systems unchanged.

## Dependencies

- Read access to both commits and an Android SDK/Jenkins-equivalent validation environment.

## Success Criteria

- [ ] Evidence shows RED `check` includes lint and `testDebugUnitTest`.
- [ ] Evidence shows GREEN runs dedicated gates once and preserves report/APK publication.
- [ ] Validation changes no repository, CI, infrastructure, Git history, or GitHub state.
