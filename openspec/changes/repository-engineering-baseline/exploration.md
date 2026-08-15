## Exploration: repository-engineering-baseline

### Current State
Music is a public, single-module Android bootstrap. Gradle version-catalog dependencies, one JUnit test, `lint`, `testDebugUnitTest`, and `assembleDebug` are verified by Jenkins; no formatter, static-analysis plugin, coverage gate, GitHub Actions workflow, dependency bot, issue/PR templates, contribution guide, security policy, or repository `AGENTS.md` exists. `main` and `develop` exist and are unprotected.

Issue taxonomy was inspected before this analysis: labels separate `type:*`, `area:*`, `priority:*`, and `status:*`; milestone `v0.1.0 — First Sound` owns the initial slice. Relevant issues are #18, #19, #32–#37, #39, #40, plus closed #11 and #33. No related issue covers templates, Gradle dependency automation, or contributor instructions. #32 already covers secure trigger research and explicitly excludes public Jenkins access; no endpoint issue is warranted.

The InkScroller checkout was not found under the local projects directory, so its current public GitHub tree and metadata were inspected read-only. Its reusable evidence is: issue forms/PR template, Dependabot, explicit agent instructions, local hooks, CI/release documentation, a public-readiness checklist, and review-tool configuration. It also contains product-specific Flutter, flavor, Firebase, release, tooling-provider, and operational choices that must not be copied.

### Affected Areas
- `README.md` — truthfully documents current bootstrap, verification, privacy, license, and temporary branch-protection stance; #18 and #19 already own adjacent documentation.
- `.gitignore` — currently excludes Android/local AI state only; a future public-readiness pass must add only evidence-backed local-secret patterns.
- `Jenkinsfile` and `docs/ci.md` — define the actual quality contract and trusted-only integration boundary; do not duplicate it with speculative GitHub Actions.
- `gradle/libs.versions.toml` and Gradle build files — central dependency locations for a Gradle-aware update policy.
- `openspec/config.yaml` and `openspec/changes/` — existing hybrid SDD baseline; #39 owns portable, non-machine-specific artifacts.

### InkScroller Practice Classification
| Classification | Evidence inspected | Baseline disposition |
| --- | --- | --- |
| REUSE AS PRINCIPLE | Structured issue forms, PR evidence checklist, dependency updates, public-readiness checklist, release preflight, and documented quality commands | Reuse the principles: actionable intake, documented verification, dependency hygiene, privacy-before-publicity, and truthful release evidence. |
| ADAPT | `AGENTS.md`, Dependabot, PR template, hook configuration, CI quality stages, `.gitignore`, and security-readiness documentation | Adapt to Android/Gradle and the real Jenkins contract. Use placeholders such as `<repo>`, `<command>`, `<secret-name>`, and `<supported-branch>`; do not carry values or identities across repositories. |
| PROJECT-SPECIFIC DO NOT COPY | Flutter/Dart/FVM commands, Clean/Riverpod/get_it rules, flavors, Firebase configuration, app-store workflow, service integrations, package IDs, release secrets, URLs, and identities | Music needs Android/Kotlin/Compose guidance and Jenkins-owned release work (#36, #37), not this implementation. |
| OBSOLETE NOT RECOMMENDED | Reference workflow setup that assumes its own CI provider, monorepo/toolchain, or release topology | Do not introduce a second primary CI just to mirror the reference; Music intentionally uses Jenkins. |
| DEFER | AI review provider/GGA integration, mandatory AI hook, CodeRabbit automation (disabled in reference), coverage threshold, instrumented tests, synthetic integration environment, release automation | Defer until behavior-focused tests, contributor volume, and release readiness justify cost. Existing #34–#37 track the test/release prerequisites. |

### Gap Matrix
| Gap | InkScroller evidence | Music current state | Recommendation / action | Priority | Candidate issue |
| --- | --- | --- | --- | --- | --- |
| Issue and PR intake templates | Required bug/feature forms and a PR evidence checklist | Blank issues enabled; no templates or PR template | **Create** one repository-workflow issue for Android-neutral issue forms and a minimal PR template; include privacy placeholders and no assignee identity. | Medium | New: `repo: establish contributor workflow and intake templates` |
| Gradle dependency automation | Scheduled dependency updates for runtime and CI ecosystems | Version catalog exists; no update automation | **Create** a Gradle/GitHub Actions update-policy issue, limited to supported Gradle and workflow ecosystems with tested PRs. | Medium | New: `ci: automate dependency update proposals for Gradle` |
| Repository instructions (`AGENTS.md`) | Explicit language, architecture, test, commit, and command conventions | No agent/contributor instruction file | **Create** in the contributor-workflow issue: concise Android-specific instructions, existing verification commands, privacy rules, and 400-line review budget. | Medium | New contributor-workflow issue |
| Local quality gates | Hooks run analysis/tests/build and validate commit messages | Jenkins is authoritative; local checks are documented only as Gradle commands | **Document** the current portable commands first; **defer** hooks/tool additions until a repeated failure or contributor need demonstrates value. Do not reopen #33. | Low | New contributor-workflow issue (docs only) |
| AI review | Reference has an opt-in/manual review script and disabled automatic CodeRabbit review | No AI review policy/tooling | **Defer**. Define a human-owned, opt-in review policy only after repository instructions and PR flow exist; no bot integration now. | Low | None |
| Public readiness | Public-readiness/security checklist and secret-safe examples | Public repo has a privacy warning, but no repository-wide checklist; no license selected | **Existing covers / document**: #18 owns safe README/security documentation; #19 blocks reuse/contribution clarity. Extend their acceptance evidence only if needed; do not create a duplicate generic audit issue. | High | #18, #19 |
| Contribution documentation | README quality/verification onboarding and PR workflow | No `CONTRIBUTING.md`; no license | **Create**, but sequence after #19; describe contribution path, DCO/commit policy only if explicitly selected, and no unapproved legal policy. | Medium | New contributor-workflow issue, blocked by #19 |
| README metadata | README badges, license, setup, quality and architecture links | Minimal truthful README; verification/privacy/license/branch note exist | **Existing covers / edit** #18 for public-facing navigation and truthful status; #19 for license metadata. Badges only after a stable public signal exists. | Medium | #18, #19 |
| Branch protection | Reference branch protection is not reusable as a preset; its branch protection is also absent | Both integration branches unprotected | **Existing covers** #40. Choose sole-maintainer-compatible rules and require the Jenkins context only once stability is confirmed. | High | #40 |
| OpenSpec hygiene | Versioned artifacts and portable-documentation discipline | Hybrid OpenSpec exists; prior artifacts may carry machine paths | **Existing covers** #39. Keep all new baseline artifacts repository-relative and use placeholders. | High | #39 |
| GitHub-to-Jenkins triggering | Reference CI is a different provider | Jenkins scans periodically; automatic trigger absent | **Existing covers** #32. Research private-compatible trigger paths; explicitly do not create a public Jenkins endpoint issue. | Medium | #32 |
| Quality, integration, and release maturity | Mature CI/release practices | Bootstrap test signal only; Jenkins correctly defers added gates | **Existing covers / defer**: preserve #33 decision; wait on #34 instrumented testing, #35 synthetic Navidrome environment, #36 signing, and #37 releases. | Per existing issues | #33–#37 |

### Approaches
1. **Minimum baseline, staged adoption** — Establish contribution metadata, intake, instructions, dependency policy, and portable documentation; keep Jenkins as the only primary CI.
   - Pros: Fits the bootstrap, reuses existing issues, avoids duplicate automation and premature tools.
   - Cons: AI review, hooks, coverage, and release polish remain intentionally deferred.
   - Effort: Medium.

2. **Reference-stack replication** — Port hooks, AI tooling, extensive CI/release automation, and public-readiness structure wholesale.
   - Pros: Faster apparent parity.
   - Cons: Imports Flutter/provider/release assumptions, duplicates Jenkins, risks leaked configuration, and exceeds current test signal.
   - Effort: High.

### Recommendation
Use the minimum staged baseline. Treat the following technology-agnostic outline as the next proposal's scope, not a draft to implement now:

1. Purpose, scope, and non-goals; source-of-truth CI and privacy boundary.
2. Repository entry points: README metadata, supported commands, architecture/status links, and honest implementation status.
3. Contribution contract: license prerequisite, behavior expectations, small reviewable work units, branch/PR lifecycle, and issue/PR intake templates.
4. Quality contract: one documented local command set matching CI, evidence expectations, and explicit deferral criteria for hooks, analysis, coverage, and AI review.
5. Dependency hygiene: supported ecosystems, update cadence, PR verification, ownership, and rollback.
6. Security/public readiness: placeholder-only examples, ignored local material, history/assets review, secret handling, and release evidence.
7. SDD hygiene: portable artifact paths, decisions, evidence retention, and no machine-specific values.
8. Governance: branch-protection rollout and trusted automation boundaries.

Reusable prompt requirements: state target repository/stack/CI and artifact-store mode; require read-only taxonomy and duplicate review before recommendations; enumerate exact existing issues to reuse; require evidence-backed classification (`reuse`, `adapt`, `do not copy`, `obsolete`, `defer`); prohibit copied paths, endpoints, identities, credentials, secrets, and provider-specific values; demand a gap matrix with `existing covers|edit|create|document|defer`; preserve a single CI source of truth; and require the 400-line review-budget decision before implementation.

### Risks
- The baseline must not claim quality, branch protection, release, license, or public-readiness controls that are not implemented.
- Adding CI, hooks, AI review, or coverage before meaningful behavior tests would create maintenance cost without actionable signal.
- Public repository work must use placeholders and avoid infrastructure, identity, endpoint, credential, and secret leakage.
- New workflow issues must be created only after the named candidates are duplicate-checked again at creation time.

### Ready for Proposal
Yes — propose the staged, technology-agnostic baseline. Reuse #18, #19, #32, #34–#37, #39, and #40 as recorded; create only the two workflow candidates after a fresh duplicate check, and do not add a public-Jenkins-endpoint issue.
