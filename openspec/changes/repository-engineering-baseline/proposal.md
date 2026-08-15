# Proposal: Repository Engineering Baseline

## Intent

Create a truthful, technology-agnostic draft baseline that turns audited repository practices into staged guidance without importing another repository's implementation or changing Music's operational behavior.

## Scope

### In Scope

- Add `docs/devdigi-repository-baseline-draft.md` with principles, current-state caveats, reusable/adapt/do-not-copy/defer classification, and an adoption backlog.
- Preserve Jenkins as the sole primary CI, the protected interactive Jenkins boundary, and the least-privilege machine webhook exception; retain OpenSpec portability and GitFlow constraints.
- Re-audit open and closed GitHub issues immediately before creating only these narrowly scoped candidates: contributor workflow/intake templates, and Gradle dependency-update policy.

### Out of Scope

- Code, product, CI/Jenkins, infrastructure, GitHub settings, branch, or PR implementation.
- Public Jenkins endpoints, copied InkScroller tooling/configuration, hooks, AI review, coverage gates, release automation, and new security/legal policy.

## Capabilities

### New Capabilities

- `repository-engineering-baseline`: A portable, evidence-based documentation and backlog policy for staged repository-engineering adoption.

### Modified Capabilities

- None; `openspec/specs/` contains no existing capabilities.

## Capability Mapping

| Baseline area                                   | Disposition                                      |
| ----------------------------------------------- | ------------------------------------------------ |
| Privacy/readiness and license                   | Reuse #18 and #19; do not duplicate              |
| Protected Jenkins/webhook boundary              | Reuse completed #32; no duplicate endpoint issue |
| Test/release maturity                           | Defer to #34–#37; preserve closed #33 decision   |
| Portable OpenSpec and GitFlow policy            | Reuse #39 and #40                                |
| Intake/contributor workflow; dependency updates | Create only after a fresh duplicate audit        |

## Approach

Write a concise draft that leads with current facts, uses placeholders instead of identities, paths, endpoints, or secrets, and treats reference-repository practices as principles to adapt—not implementation to copy. Record adoption prerequisites and explicit deferrals. Before each candidate issue, rediscover repository policy/templates/labels and search all issue states; create it only when no equivalent issue exists.

## Affected Areas

| Area                                                | Impact           | Description                              |
| --------------------------------------------------- | ---------------- | ---------------------------------------- |
| `docs/devdigi-repository-baseline-draft.md`         | New              | Draft baseline and staged backlog map    |
| `openspec/changes/repository-engineering-baseline/` | Modified         | Proposal and future delta artifacts      |
| GitHub issues                                       | New, conditional | At most two audit-backed workflow issues |

## Risks

| Risk                            | Likelihood | Mitigation                                                   |
| ------------------------------- | ---------- | ------------------------------------------------------------ |
| Claims exceed current controls  | Med        | Mark implemented facts, owners, and deferrals explicitly     |
| Duplicate or private-data issue | Low        | Fresh all-state audit and privacy review before creation     |
| Reference-stack drift           | Med        | Principles only; prohibit copied provider/toolchain settings |

## Rollback Plan

Revert the draft document and proposal artifacts. Close only newly created, proven-duplicate issues with a link to the existing tracker; no operational state requires rollback.

## Dependencies

- Fresh GitHub policy, template, label, and duplicate audit before any issue creation.
- Existing trackers #18, #19, #34–#37, #39, and #40 remain authoritative; completed #32 remains the historical owner of the Jenkins webhook boundary.

## Success Criteria

- [x] The draft accurately distinguishes implemented controls, principles, and deferred work.
- [x] No code, CI, Jenkins, infrastructure, settings, branch, or PR changes occur.
- [x] Every new issue, if any, passes a fresh duplicate and privacy audit.
