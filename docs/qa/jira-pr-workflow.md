# Jira QA and pull-request workflow

Status: Active operating model  
Jira: MUSIC-7  
Related GitHub issue: #104  
Scope: DevDigi Music Android

## 1. Purpose

This document defines the operational handoff between Jira, GitHub, Jenkins,
and QA.

Use it together with:

- [QA strategy](strategy.md) for testing layers and suite semantics;
- [GitFlow and branch governance](../gitflow-governance.md) for branch routing,
  protected branches, and merge policy;
- [CI](../ci.md) for Jenkins behavior and trust boundaries.

Jira owns planning and workflow state. GitHub owns code, PRs, versioned test
specifications, and executable evidence. Jenkins is the required CI authority.

## 2. Jira workflow

The normal delivery path is:

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

`Bloqueado` may be entered whenever progress cannot continue for a material
dependency or environment reason.

Not every low-risk change must visit every QA status. A documentation-only or
similarly low-risk change may move from `En Revision` to `Listo` after merge
when QA is explicitly recorded as N/A with a reason. Product behavior changes
should use the full QA handoff when acceptance risk justifies it.

### Por hacer

Use for backlog work that is known but not yet ready to start.

The issue may still need scope, acceptance, dependencies, priority, or an
estimate.

### Necesita Revision

Use for intake that requires triage/refinement before it can be considered
Ready.

Typical examples:

- newly imported GitHub work;
- ambiguous reports;
- duplicate candidates;
- unclear product/technical ownership;
- issues whose acceptance criteria no longer match the current product.

### Ready

An item is Ready when:

- scope and out-of-scope are understandable;
- acceptance is observable;
- dependencies/blockers are known;
- relevant security/privacy risk is considered;
- the issue is estimated when sprint planning needs an estimate;
- Jira metadata is usable;
- the work can start without another discovery pass.

Being in a sprint does not by itself make an issue Ready.

### En curso

Move the Jira item to `En curso` when implementation or substantive
documentation work actually starts.

At that point:

- branch from the correct current baseline;
- include the Jira key in the branch name when practical;
- keep the work limited to the Jira scope;
- update the Jira item if scope, risk, dependency, or estimate materially
  changes.

Do not leave active work in Ready.

### En Revision

Move to `En Revision` when the pull request is open and ready for review.

The PR must identify the Jira item and record:

- purpose and scope;
- out-of-scope;
- QA impact;
- affected Gherkin scenarios/tags when applicable;
- automated verification evidence;
- manual/device evidence or why it is not required;
- known risks/dependencies;
- security/privacy impact;
- rollback;
- changed-line budget.

The repository review budget is normally **400 changed lines**. A larger
cohesive change needs an explicit justification.

### Listo para QA

Use when review/CI evidence is sufficient for the planned QA execution and the
candidate should no longer be changing except to address findings.

Before this state, confirm as applicable:

- relevant local checks passed or exceptions are documented;
- required Jenkins evidence is green for the candidate;
- review conversations are resolved or understood;
- acceptance/Gherkin scope is identified;
- the Test Execution or equivalent QA record is ready;
- the candidate build/commit can be identified.

If QA is intentionally N/A, record why instead of manufacturing empty QA work.

### En QA

Use while the planned acceptance, smoke, sanity, regression, or device
validation is actively being executed.

Record results using:

- PASS;
- FAIL;
- BLOCKED;
- NOT RUN;
- N/A.

The Test Execution/equivalent record must identify the build/commit and retain
only privacy-safe evidence.

### Listo

An item is Done only when:

- implementation/documentation is integrated through the required PR path;
- required Jenkins status passed;
- required QA is PASS, or QA is explicitly N/A with a justified reason;
- actionable QA failures have linked Bugs;
- required retests passed;
- acceptance criteria are satisfied;
- residual risks are recorded;
- no unresolved blocker remains.

`Listo` means potentially releasable for the issue's scope. It does not mean
the whole product release has been published.

## 3. Branch and commit contract

Ordinary work starts from current `develop`.

Preferred examples:

```text
feature/MUSIC-123-album-search
fix/MUSIC-124-session-restore
docs/MUSIC-7-jira-pr-workflow
test/MUSIC-9-authentication-validation
ci/MUSIC-49-release-signing
```

Commit messages use Conventional Commits. Including the Jira key is encouraged
for traceability when it remains readable:

```text
docs(qa): define Jira QA handoff (MUSIC-7)
```

Do not create artificial commits merely to change Jira state or trigger CI.
Use repository/Jenkins operational controls when a build needs rediscovery.

## 4. Pull-request contract

All normal integration into protected branches uses a PR.

A PR targeting `develop` is merge-ready only when:

- scope matches the linked Jira item;
- final diff was reviewed;
- required review conversations are resolved;
- `continuous-integration/jenkins/pr-merge` is PASS;
- QA requirements for the change are satisfied or explicitly deferred to the
  defined pre-merge QA handoff;
- privacy/security review is complete;
- rollback is understood;
- branch divergence from the target has been evaluated.

Use **Create a merge commit** only. Do not squash, rebase, force-push, or
directly push protected branches.

A skipped automated reviewer is not a review and is not a substitute for
Jenkins or human inspection.

## 5. QA impact in a PR

Every PR declares one of the following:

- **N/A** — no executable QA is relevant; explain why;
- **Sanity** — changed behavior and immediate dependencies;
- **Smoke** — critical product path confidence;
- **Regression** — broader established behavior;
- **Device** — Android hardware/runtime or real system-surface validation.

Multiple suites may apply.

When Gherkin exists, reference the affected feature/scenario or tags instead of
copying the whole specification into the PR.

When automation exists, record the actual command/check/result. Do not write
"tests pass" without identifying the evidence source.

## 6. Review to QA handoff

For behavior changes that need explicit QA:

1. Open the PR and move Jira to `En Revision`.
2. Complete code/document review and required automated checks.
3. Identify the candidate commit/build and QA scope.
4. Move Jira to `Listo para QA`.
5. Start the Test Execution and move Jira to `En QA`.
6. Record scenario results and evidence.
7. If QA passes, merge through the protected-branch workflow and move Jira to
   `Listo`.
8. If QA fails, create/link Bug(s) and return the implementation item to
   `En curso` when rework is required.

If new commits materially change the candidate after QA, reassess which QA
must be repeated.

## 7. QA failure and defect loop

A failed scenario does not become a vague PR note.

For an actionable product defect:

- create/link a Jira Bug;
- record expected vs actual;
- record safe environment/build context;
- link the failed Test Execution/scenario and affected requirement;
- classify priority separately from severity;
- move implementation back to `En curso` when rework starts;
- fix via branch/PR/Jenkins;
- retest the failed scenario;
- run proportional regression;
- record the retest outcome before closing the Bug.

A BLOCKED test is not automatically a product defect. Record the blocking
dependency/environment and next action.

## 8. Jenkins evidence

The protected `develop` ruleset requires:

```text
continuous-integration/jenkins/pr-merge
```

Do not merge while that required status is missing, pending, or failing.

If Jenkins was unavailable when the PR event occurred, rediscover/rebuild the
PR through Jenkins rather than bypassing protection or adding meaningless
source changes.

Administrator PR bypass is emergency recovery only and follows the exceptional
procedure in GitFlow governance.

## 9. Intake and needs-review

GitHub issue forms may label new reports with `status:needs-review`. That is
intake metadata, not the canonical delivery state.

For work that will be acted on:

- locate/create the corresponding Jira item;
- normalize scope, acceptance, dependencies, risk, labels, and priority;
- use `Necesita Revision` while refinement is still required;
- move to `Ready` only when the Ready criteria in this document are met.

Jira status wins if GitHub labels and Jira disagree.

## 10. Migration-on-touch for historical GitHub issues

Do not rewrite historical GitHub issues merely to make them look like new Jira
work.

When an existing GitHub issue is touched for new implementation:

1. Confirm its Jira counterpart exists.
2. Review the current GitHub source and historical context.
3. Update Jira with current scope, acceptance, dependencies, QA impact, and
   estimate as needed.
4. Link Jira and the relevant GitHub issue/PR.
5. Use Jira for current status, sprint, priority, blockers, and QA state.
6. Keep GitHub as the source for repository history, code discussion, PRs, and
   versioned executable evidence.

Avoid maintaining two competing status systems by hand.

## 11. Dependency, risk, privacy, and rollback

Before merge, record material dependencies and risks rather than relying on
tribal knowledge.

Security/privacy-sensitive changes must specifically evaluate:

- credential handling;
- account/server isolation;
- persistence/backups;
- logs and diagnostics;
- public artifacts/screenshots;
- authenticated URLs or media metadata.

Rollback must explain how the change can be safely reversed. If rollback is
not simple, state why and identify recovery constraints.

## 12. Definition of Done

The repository-level Definition of Done for a normal Jira item is:

- Jira scope and acceptance are current;
- implementation matches that scope;
- tests are at the lowest useful layer;
- required QA evidence is recorded;
- PR template is complete;
- review threads are resolved;
- required Jenkins PR status passes;
- privacy/security review is complete;
- defects/retests are linked when applicable;
- rollback is documented;
- merge uses the approved merge-commit path;
- Jira reflects the real final state.

Release-level readiness remains a separate decision from issue-level Done.
