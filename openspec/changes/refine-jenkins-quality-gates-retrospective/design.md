# Design: Refine Jenkins Quality Gates Retrospective

## Technical Approach

Validate, without changing production code or CI, the immutable RED/GREEN pair `d618641^` (`54a1cc4`) and `d618641`. Use separate detached worktrees pinned exactly to RED and GREEN—never the `main` checkout—restrict the commit diff to `Jenkinsfile` and `docs/ci.md`, collect the specification's exact Gradle evidence with an explicit Android SDK, verify report/APK paths, and inspect—but never execute—the credentialed Navidrome stage.

## Architecture Decisions

| Option | Tradeoff | Decision |
|---|---|---|
| Immutable commit pair versus reconstructing history | Fixed commits cannot model later Jenkins changes, but make evidence reproducible | Resolve and assert `RED=54a1cc4` and `GREEN=d618641`; reject drift |
| Detached RED/GREEN worktrees versus the primary checkout | Uses two sibling directories but isolates both immutable baselines from branch drift | Use `issue-33-red` at `54a1cc4` and `issue-33-green` detached exactly at `d618641`; never validate GREEN from `main` |
| Specification commands versus a new test harness | Console/report evidence needs categorization, but adds no repository tooling | Run the exact RED dry-run and GREEN acceptance commands; do not substitute `--rerun-tasks` or extra Gradle flags |
| Source inspection versus Navidrome execution | Does not prove the external service, but avoids credentials and is sufficient for unchanged stage gating | Inspect the committed Jenkins predicate, credential wrapper, and command only |

## Data Flow

    immutable refs -> detached worktrees -> RED task graph -> GREEN acceptance run
          |                                                   |
          +-> Jenkins source inspection -> evidence index <- reports/APK paths

Evidence categories are `baseline`, `scope`, `red-task-graph`, `green-execution`, `publications`, `navidrome-source`, and `integrity`. Command output must contain no environment dump or credential value.

## File Changes

| File | Action | Description |
|---|---|---|
| `openspec/changes/refine-jenkins-quality-gates-retrospective/design.md` | Modify | Correct the isolated GREEN baseline and acceptance-evidence contract |
| `Jenkinsfile`, `docs/ci.md` | Inspect | Diff and committed-source evidence; no edits |
| `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml` | Inspect | Confirm task/plugin surface; no edits |

## Interfaces / Contracts

Run from canonical, quoted paths and fail closed on any assertion:

```sh
ROOT="$(git rev-parse --show-toplevel)"; RED="$(git rev-parse d618641^)"; GREEN="$(git rev-parse d618641)"
test "$RED" = 54a1cc4d537b333aba5af69202b7b9c0961c696c
test "$GREEN" = d6186418f4ccb70e4dcc704199996f232abd73cb
git diff --name-only "$RED" "$GREEN" | diff -u <(printf 'Jenkinsfile\ndocs/ci.md\n') -
RED_WT="/home/merce/01_Projects/devdigi-music-android-worktrees/issue-33-red"
GREEN_WT="/home/merce/01_Projects/devdigi-music-android-worktrees/issue-33-green"
git worktree add --detach "$GREEN_WT" "$GREEN" # only when the path is absent
test "$(git -C "$RED_WT" rev-parse HEAD)" = "$RED"; test -z "$(git -C "$RED_WT" symbolic-ref -q HEAD || true)"
test "$(git -C "$GREEN_WT" rev-parse HEAD)" = "$GREEN"; test -z "$(git -C "$GREEN_WT" symbolic-ref -q HEAD || true)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
test -d "$ANDROID_HOME"; test -z "$(git -C "$GREEN_WT" status --porcelain --untracked-files=no)"
(cd "$RED_WT" && ./gradlew check --dry-run --offline)
(cd "$GREEN_WT" && ./gradlew --no-build-cache clean testDebugUnitTest lint assembleDebug)
test -z "$(git -C "$GREEN_WT" status --porcelain --untracked-files=no)"
test -z "$(git -C "$GREEN_WT" ls-files -- .gradle build app/build)"
git show "${GREEN}:Jenkinsfile"
```

RED passes only if the graph lists `:app:testDebugUnitTest`, `:app:lint`, and `:app:check`. GREEN passes only if the exact acceptance command exits `0`, stage commands are singular, and generated JUnit/lint/APK files match Jenkins archive globs. Gradle outputs (`.gradle/`, `build/`, and `app/build/`) are exclusively non-tracked disposable files: they MUST remain outside the index, and tracked status MUST be empty before and after validation. Missing SDK/dependencies are environment failures, not gate failures.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Static | Ref identity, two-file scope, GREEN stage/publication/Navidrome source | Assertions plus committed `git diff`/`git show` |
| Integration | RED aggregate graph and exact non-cached GREEN acceptance execution | Gradle commands above; retain exit codes and sanitized logs |
| Artifact | JUnit XML, lint XML/HTML/SARIF, debug APK | Enumerate exact Jenkins globs; record presence/absence and hashes |

## Threat Matrix

| Boundary | Applicability | Expected safe / failure behavior | Planned RED test or evidence |
|---|---|---|---|
| Documentation-like paths | Applicable: diff allowlist | Accept only `Jenkinsfile`, `docs/ci.md`; fail on `requirements.txt`, `CMakeLists.txt`, executable Markdown/MDX, or `README.sh` | Two-file diff assertion; adversarial fixture names rejected by allowlist logic |
| Git repository selection | Applicable | Fixed canonical `ROOT`, `RED_WT`, and `GREEN_WT` with quoted `git -C`; fail on relative, mismatched, non-detached, or absent paths | Assert both worktree roots, detached state, and exact commit IDs |
| Commit state | N/A: no staging or commit operation | No index semantics involved | None |
| Push state | N/A: no push operation | No destination resolution involved | None |
| PR commands | N/A: no PR operation | No command composition involved | None |
| Gradle/process environment | Applicable | Require valid `ANDROID_HOME`; execute the exact spec commands; permit only non-tracked Gradle outputs; fail on any tracked-file mutation, environment dump, Navidrome execution, or secret-bearing log | Missing-SDK negative control, exact GREEN command, before/after tracked-status checks, tracked-output-path assertion, source-only Navidrome evidence |

## Migration / Rollout

No migration or rollout required. Validation writes only disposable, non-tracked Gradle outputs in detached worktrees and MUST alter no tracked file. Commits, branches, Jenkins, GitHub, and credentials remain untouched.

## Open Questions

None.
