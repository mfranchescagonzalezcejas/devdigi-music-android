# Tasks: Repository Engineering Baseline

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~700 |
| 400-line budget risk | High — explicit size exception |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | explicit-size-exception |
| Chain strategy | N/A — single PR |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: N/A — single PR
400-line budget risk: High — explicit size exception

**Size exception rationale:** this change is documentation/OpenSpec-only and forms one
coherent review unit. Splitting the portable baseline from its proposal, design,
specification, tasks, exploration, and verification evidence would separate the
implementation from the evidence used to review it.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Publish the portable baseline and conditionally audit two workflow gaps | PR 1 | `git diff --check` plus document checklist | N/A — inert Markdown and read-only issue preflight | Revert `docs/devdigi-repository-baseline-draft.md`; close only newly created proven duplicates |

## Phase 1: Evidence and Safety Gates

- [x] 1.1 RED: verify repository identity and cwd agree; reject wrong or conflicting targets before any issue write.
- [x] 1.2 Build the evidence ledger from `README.md`, `docs/ci.md`, `Jenkinsfile`, Gradle files, OpenSpec artifacts, and audited trackers.

## Phase 2: Baseline Draft

- [x] 2.1 Create `docs/devdigi-repository-baseline-draft.md` with Universal, Technology-dependent, and Project-dependent guidance.
- [x] 2.2 Record each practice’s evidence, classification, gap action, owner/prerequisite, deferral trigger, and Jenkins/OpenSpec/GitFlow constraints.
- [x] 2.3 Add the reusable placeholder-only prompt, 400-line review-budget decision, staged backlog, explicit deferrals, and no-copy boundaries.

## Phase 3: Conditional Backlog Audit

- [x] 3.1 Freshly inspect repository policy, templates, labels, milestones, and open/closed duplicates before each candidate.
- [x] 3.2 Create at most one contributor-workflow/intake issue and one Gradle dependency-update policy issue only when unique, supported, and privacy-safe; reuse #18, #19, #32, #34–#37, #39, #40 and preserve closed #33.

## Phase 4: Verification

- [x] 4.1 Verify all spec scenarios: classifications, placeholders, tracker reuse, deferrals, candidate limits, rollback guidance, and forbidden operational changes.
- [x] 4.2 Run `git diff --check`; confirm only the draft and explicitly approved candidate issue records changed, with no code, CI, infrastructure, settings, branch, or PR changes.
