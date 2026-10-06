# Jira QA and pull-request workflow

Status: Active operating model  
Jira: MUSIC-7  
Related GitHub issue: #104  
Scope: DevDigi Music Android

## 1. Purpose

This document defines the operational handoff between Jira, GitHub, Jenkins,
and QA.

Use it with:
- [QA strategy](strategy.md) for testing layers and suite semantics;
- [GitFlow and branch governance](../gitflow-governance.md) for branch routing,
  protected branches, and merge policy;
- [CI](../ci.md) for Jenkins behavior and trust boundaries.

Jira owns planning/workflow state. GitHub owns code, PRs, versioned test
specifications, and executable evidence. Jenkins is the required CI authority.

## 2. Jira workflow

Normal delivery:

```text
Por hacer / Necesita Revision
        ↓
      Ready
        ↓
    En curso
        ↓
   En Revision
        ↓
 Listo para QA
        ↓
      En QA
        ↓
      Listo
```

`Bloqueado` may be entered whenever a material dependency or environment
condition prevents progress.

Low-risk documentation/configuration may move from `En Revision` to `Listo`
after merge when executable QA is explicitly N/A with a reason. Product
behavior changes should use the QA handoff whenever acceptance risk requires it.

### Por hacer / Necesita Revision

`Por hacer` is known backlog work not yet ready to start.

`Necesita Revision` is intake requiring triage/refinement, for example:
- newly imported GitHub work;
- ambiguous/duplicate reports;
- unclear ownership or acceptance;
- historical scope that no longer matches the product.

### Ready

Ready means:
- scope/out-of-scope are understandable;
- acceptance is observable;
- dependencies/blockers are known;
- security/privacy risk is considered;
- estimate/metadata are usable when planning needs them;
- work can start without another discovery pass.

Being in a sprint does not by itself make an issue Ready.

### En curso

Move here when implementation or substantive documentation actually starts.

Then:
- branch from the correct current baseline;
- include the Jira key in the branch name when practical;
- keep work limited to Jira scope;
- update Jira when scope, risk, dependency, or estimate materially changes.

Do not leave active work in Ready.

### En Revision

Move here when the PR is open and ready for review.

The PR records:
- purpose, scope, and out-of-scope;
- QA impact;
- affected Gherkin scenarios/tags where applicable;
- automated and manual/device evidence;
- risks/dependencies;
- security/privacy impact;
- rollback;
- changed-line budget.

Normal review budget: **400 changed lines**. A larger cohesive change needs an
explicit justification.

### Listo para QA

Use when review/CI evidence is sufficient for planned QA and the candidate
should no longer change except to address findings.

Confirm as applicable:
- local checks passed or exceptions are documented;
- required Jenkins evidence is green;
- review conversations are resolved/understood;
- acceptance/Gherkin scope is identified;
- Test Execution/equivalent record is ready;
- candidate build/commit is identifiable.

If QA is N/A, record why instead of manufacturing empty QA work.

### En QA

Use while planned acceptance, smoke, sanity, regression, or device validation
is running.

Record PASS / FAIL / BLOCKED / NOT RUN / N/A and identify the build/commit.
Evidence must be privacy-safe.

### Listo

Done requires:
- integration through the required PR path;
- required Jenkins status PASS;
- required QA PASS, or justified N/A;
- linked Bugs for actionable QA failures;
- required retests PASS;
- acceptance satisfied;
- residual risks recorded;
- no unresolved blocker.

`Listo` means potentially releasable for the issue scope, not that the whole
product release has been published.

## 3. Branch and commit contract

Ordinary work starts from current `develop`.

Examples:
```text
feature/MUSIC-123-album-search
fix/MUSIC-124-session-restore
docs/MUSIC-7-jira-pr-workflow
test/MUSIC-9-authentication-validation
ci/MUSIC-49-release-signing
```

Use Conventional Commits. Including the Jira key is encouraged when readable:
```text
docs(qa): define Jira QA handoff (MUSIC-7)
```

Do not create artificial commits merely to change Jira state or trigger CI.

## 4. Pull-request contract

All normal protected-branch integration uses a PR.

A PR targeting `develop` is merge-ready only when:
- scope matches the linked Jira item;
- final diff is reviewed;
- required conversations are resolved;
- `continuous-integration/jenkins/pr-merge` is PASS;
- QA requirements are satisfied or follow the defined pre-merge QA handoff;
- privacy/security review and rollback are complete;
- target-branch divergence has been evaluated.

Use **Create a merge commit** only. Do not squash, rebase, force-push, or
directly push protected branches.

A skipped automated reviewer is not a review and does not replace Jenkins or
human inspection.

## 5. QA impact in a PR

Every PR declares one or more:
- **N/A** — no executable QA applies; explain why;
- **Sanity** — changed behavior and immediate dependencies;
- **Smoke** — critical product path confidence;
- **Regression** — broader established behavior;
- **Device** — Android hardware/runtime or real system surfaces.

When Gherkin exists, reference affected scenarios/tags rather than copying the
specification. When automation exists, record the actual command/check/result.

## 6. Review → QA handoff

For behavior changes needing explicit QA:

1. Open PR; move Jira to `En Revision`.
2. Complete review and required automated checks.
3. Identify candidate commit/build and QA scope.
4. Move Jira to `Listo para QA`.
5. Start Test Execution; move Jira to `En QA`.
6. Record scenario results/evidence.
7. If PASS, merge through the protected path and move Jira to `Listo`.
8. If FAIL, link Bug(s) and return implementation to `En curso` when rework starts.

If new commits materially change a QA-tested candidate, reassess the QA that
must be repeated.

## 7. QA failure and defect loop

For an actionable failed scenario:
- create/link a Jira Bug;
- record expected vs actual plus safe environment/build context;
- link failed Test Execution/scenario and affected requirement;
- classify priority separately from severity;
- move implementation to `En curso` when rework starts;
- fix through branch/PR/Jenkins;
- retest the failure and run proportional regression;
- record retest outcome before closing the Bug.

A BLOCKED test is not automatically a product defect; record the blocker and
next action.

## 8. Jenkins evidence

The protected `develop` ruleset requires:
```text
continuous-integration/jenkins/pr-merge
```

Do not merge while it is missing, pending, or failing.

If Jenkins was unavailable when the PR event occurred, rediscover/rebuild the
PR through Jenkins rather than bypassing protection or adding meaningless
source changes.

Administrator bypass is emergency recovery only, per GitFlow governance.

## 9. Intake and migration-on-touch

GitHub issue-form `status:needs-review` is intake metadata, not canonical
delivery state.

For work that will be acted on:
- locate/create the Jira counterpart;
- normalize scope, acceptance, dependencies, risk, labels, priority, and
  estimate as needed;
- use `Necesita Revision` while refinement remains;
- move to `Ready` only when Ready criteria are met.

For an existing historical GitHub issue being touched:
1. confirm its Jira counterpart;
2. review current GitHub source/history;
3. update Jira with current scope, acceptance, dependencies, QA impact, and estimate;
4. link Jira with relevant GitHub issue/PR;
5. use Jira for current status, sprint, priority, blockers, and QA state;
6. keep GitHub for repository history, code discussion, PRs, and executable evidence.

Jira status wins if GitHub labels disagree. Avoid two competing status systems.

## 10. Dependency, risk, privacy, and rollback

Before merge, record material dependencies and risks.

Security/privacy-sensitive changes explicitly consider:
- credentials;
- account/server isolation;
- persistence/backups;
- logs/diagnostics;
- public artifacts/screenshots;
- authenticated URLs/media metadata.

Rollback explains how to reverse the change safely. If rollback is not simple,
state why and identify recovery constraints.

## 11. Definition of Done

Repository-level Done requires:
- current Jira scope/acceptance;
- implementation matching scope;
- tests at the lowest useful layer;
- required QA evidence;
- complete PR template;
- resolved review threads;
- required Jenkins PR status PASS;
- privacy/security review;
- linked defects/retests where applicable;
- documented rollback;
- approved merge-commit integration;
- Jira reflecting the real final state.

Release-level readiness remains separate from issue-level Done.
