# GitFlow and Branch Governance

Status: Active
Related issue: #40
Scope: DevDigi Music Android

## 1. Branch responsibilities

This repository follows GitFlow with merge commits.

`main` represents the published or release-ready history.
The project has not yet established its first production
release, so its current bootstrap state is not evidence of
a published application.

`develop` is the integration branch for work intended for
the next release.

Feature and maintenance branches remain separate until their
changes pass review and the required CI checks.

The historical repository state predating this policy is
preserved. Do not rewrite commits to make old merges conform
retroactively.

## 2. Branch routing

| Branch | Created from | Integrated into |
| --- | --- | --- |
| `feature/*` | `develop` | `develop` |
| `fix/*` | `develop` | `develop` |
| `docs/*` | `develop` | `develop` |
| `test/*` | `develop` | `develop` |
| `ci/*` | `develop` | `develop` |
| `chore/*` | `develop` | `develop` |
| `refactor/*` | `develop` | `develop` |
| `release/*` | `develop` | `main` and `develop` |
| `hotfix/*` | `main` | `main` and `develop` |

Only release and emergency hotfix work follows the
normal integration path into `main`.

PR #58 was an exceptional, historical repository-maintenance
change made before this policy. It does not establish an
additional standard integration route.

## 3. Pull request policy

All integrations into `main` and `develop` require a PR.

The repository permits merge commits only. Use GitHub's
"Create a merge commit" option.

Do not squash, rebase, force-push or directly push changes
into protected integration branches.

Each substantial PR should reference its issue or
specification, explain its scope, document relevant tests
and provide rollback information.

The detailed Jira state, QA handoff, defect/retest, and PR evidence contract is defined in [Jira QA and pull-request workflow](qa/jira-pr-workflow.md).

Resolve required review conversations before merging.

A sole maintainer cannot provide an independent approval
of their own PR. Both protected branches currently require
zero formal approving reviews, while keeping the PR,
conversation-resolution and CI requirements.

Do not misrepresent automated review status as human or
AI code review when the reviewer has skipped the review.

## 4. Active GitHub protections

Both `main` and `develop` have active GitHub rulesets.

Their current protection requirements include:

- Pull requests for normal integration.
- Resolution of review conversations.
- Prevention of branch deletion.
- Prevention of non-fast-forward updates.
- Successful `continuous-integration/jenkins/pr-merge`.

The Jenkins `branch` status remains informational and
is not currently an additional required status check.

The required check does not currently enforce GitHub's
strict branch-up-to-date setting. Review changes to the
target branch before merging and revalidate when needed.

Repository-level merge settings permit merge commits
and disable squash and rebase integration.

The rulesets may expose additional merge-method options,
but the repository-level settings restrict the effective
integration choice to merge commits.

Recheck live GitHub settings before relying on this document:
repository settings are operational state, not files
controlled by Git.

## 5. Feature integration

Create each feature or ordinary maintenance branch
from an up-to-date `develop`.

Develop and verify the change on its own branch.

Open a PR targeting `develop`, inspect the final diff,
resolve review comments and wait for the required
Jenkins PR check.

Integrate using a merge commit.

Confirm the resulting `develop` commit and preserve
the history of the original feature branch.

Avoid adding unrelated work to an existing PR.

## 6. Release procedure

Create `release/*` from the intended `develop` release
baseline.

Limit the release branch to release preparation,
verification and necessary release-specific fixes.

Do not add unrelated features after the release
baseline has been selected.

Open a PR from the release branch into `main`.
Require the normal review and Jenkins checks.
Integrate using a merge commit.

Propagate any release-branch changes that are not already
present in `develop` through a separate PR into `develop`,
also using a merge commit.

If the release branch contains no changes beyond those
already in `develop`, do not invent an empty back-merge.

Reconcile existing branch differences by examining the
desired final content, not by blindly choosing one side
of a merge conflict.

Tag the verified release commit on `main` only when
the project's release requirements are satisfied.

Signed Android releases and automated publishing remain
separate, gated work. This document does not claim that
those capabilities are implemented.

## 7. Hotfix procedure

Create `hotfix/*` from the affected `main` baseline.

Apply the smallest verified correction required.

Integrate the hotfix through a PR into `main`,
using the required checks and a merge commit.

Then propagate the correction into `develop`
through a separately verified PR.

Preserve both histories and resolve any conflicts
against the intended behavior.

If another release line or active release branch
is affected, evaluate and propagate the correction
explicitly rather than assuming it is synchronized.

## 8. Maintenance and recovery

For routine maintenance, follow the normal branch
and PR workflow. A failing CI check blocks normal
integration; investigate or restore the check.

The current rulesets allow repository administrators
a PR-scoped bypass. This is an emergency recovery
capability, not an alternative everyday workflow.

Use it only when a genuinely urgent correction cannot
wait for normal infrastructure recovery.

Any exceptional bypass must have a recorded issue
or incident explanation, a reviewed scope, explicit
verification evidence and a follow-up to restore
normal checks.

Never expose secrets, private infrastructure or
credentials in that evidence.

If ruleset changes are temporarily required, minimize
their scope, record their original configuration and
restore protection immediately after recovery.

For incorrect merges, prepare a corrective commit
or revert through a new PR. Do not reset or rewrite
the history of protected branches.

## 9. Policy maintenance

Review protection settings whenever GitHub, Jenkins
or the contributor model changes.

Evaluate requiring the Jenkins `branch` check only
after demonstrating that it is consistently useful,
available and does not duplicate or obstruct the
required PR merge check.

Do not introduce mandatory independent approvals
until another maintainer can actually provide them.

Any future departure from this GitFlow policy needs
an explicit, documented decision.
