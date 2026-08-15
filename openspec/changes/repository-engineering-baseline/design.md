# Design: Repository Engineering Baseline

## Technical Approach

Create `docs/devdigi-repository-baseline-draft.md` as a portable draft. Keep Jenkins as sole primary CI and private boundary. Reuse #18, #19, #32, #34–#37, #39, and #40; preserve closed #33. Gate at most two candidate issues with read-only policy, taxonomy, duplicate, and privacy checks. No operational behavior changes.

## Guidance Structure

### Universal Guidance

Document portable principles: truthful evidence, reviewable changes, privacy-safe examples, ownership, verification, rollback, and classification as `reuse`, `adapt`, `do not copy`, `obsolete`, or `defer`.

### Technology-dependent Guidance

Describe adaptation inputs—not vendors or copied configuration—including stack/maturity, dependency ecosystems, commands, test signal, release topology, and the single CI source of truth. Unimplemented controls remain proposed or deferred.

### Project-dependent Guidance

Record Music-specific evidence, constraints, tracker ownership, prerequisites, and current-state notes. Jenkins remains authoritative; OpenSpec stays portable; GitFlow constraints remain. Public endpoints, copied reference tooling, hooks, AI review, coverage gates, release automation, and new security/legal policy remain out of scope.

## Source Inputs

| Source | Authority |
|---|---|
| `README.md`, `docs/ci.md`, `Jenkinsfile` | Implemented verification, privacy, and automation facts |
| `gradle/libs.versions.toml`, Gradle files | Current dependency entry points only |
| `openspec/config.yaml`, proposal, spec, exploration | Scope, requirements, and prior evidence |
| Policy, templates, taxonomy, milestones, all issue states | Rediscover read-only before each candidate |
| Reference evidence | Principles only; never copy stack, providers, identities, paths, or values |

## Architecture Decisions

| Decision | Choice | Tradeoff and rationale |
|---|---|---|
| Document shape | Three guidance sections plus evidence/gap matrices | More structured than narrative; reviewers can verify applicability and disposition directly |
| Classification | Practice disposition, then `existing covers|edit|create|document|defer` action | Two fields avoid conflating reuse evidence with backlog action |
| Candidate gating | Refresh and gate each candidate independently | More reads, but prevents stale taxonomy and duplicate writes |
| Privacy | Semantic placeholders preserve structure | Less concrete, but portable and non-identifying |

## Data Flow

    target facts + prior evidence + fresh repository state
                         ↓
                  evidence ledger
                         ↓
       guidance section + classification + gap action
                         ↓
              draft + independent issue gate

## File Changes

| File | Action | Description |
|---|---|---|
| `docs/devdigi-repository-baseline-draft.md` | Create | Guidance sections, evidence ledger, reusable prompt, gap matrix, and staged backlog |

No other file, CI, code, infrastructure, branch, PR, setting, or provider configuration changes.

## Interfaces / Contracts

Every evidence row records practice, source, current fact, guidance section, classification, action, owner/prerequisite, and deferral trigger.

Reusable prompt contract:

> Audit `<repo>` using `<stack-and-maturity>`. Treat `<primary-ci-source>` as the single CI source of truth and `<artifact-store-mode>` as the artifact contract. Read-only inspect repository policy, taxonomy, templates, milestones, and open/closed duplicates. Reuse `<existing-trackers>`. Classify every practice as `reuse|adapt|do not copy|obsolete|defer`; produce an `existing covers|edit|create|document|defer` gap matrix with evidence, owner, prerequisite, and rollback. Decide whether the forecast exceeds the 400-line review budget and whether work must be chained before implementation. Replace sensitive data with semantic placeholders.

Placeholder-only examples: `<repo>`, `<command>`, `<secret-name>`, `<supported-branch>`, `<identity>`, `<endpoint>`, and `<machine-path>`. No example may contain provider-specific values or tool names.

Every candidate issue body MUST contain scope, evidence, prerequisites, existing-tracker/duplicate result, verification, and rollback guidance:

- Contributor workflow/intake candidate: rollback by reverting documentation/templates and restoring the prior intake path; blocked by #19; excludes hooks, AI review, and legal policy.
- Dependency-update policy candidate: rollback by reverting policy-only changes and stopping proposed update activity; it cannot configure automation or replace primary CI.

Abort creation on ambiguous repository identity, failed discovery, duplicates, unsupported taxonomy, or private data; never invent labels.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Document | Three sections, truth, classifications, prompt contract | Source-to-row and placeholder checklist |
| Process | Duplicate, taxonomy, privacy, rollback-body gates | Read-only preflight immediately before each candidate; failure produces no write |
| Acceptance | Scope and backlog result | Confirm only the draft changed and every candidate body satisfies the contract |

## Threat Matrix

| Boundary | Cases | Applicability | Response / RED test |
|---|---|---|---|
| Documentation-like paths | executable-looking docs/files | N/A — inert Markdown only | No execution or classification |
| Git repository selection | relative/absolute/conflicting repository | Applicable | Safe: resolved repository and cwd agree; failure aborts. RED: reject wrong cwd or conflicting target |
| Commit state | staged/empty index | N/A — no commit | No index automation |
| Push state | tracking/refspec | N/A — no push | No ref resolution |
| PR commands | explicit head/composed commands | N/A — issues only | No PR automation |

## Migration / Rollout

No migration required. Publish the draft first, then gate each candidate independently. Revert the draft to roll back; close only a newly created proven duplicate and link its authoritative tracker.

## Open Questions

None.
