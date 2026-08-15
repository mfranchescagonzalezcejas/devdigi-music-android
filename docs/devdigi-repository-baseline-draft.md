# Repository Engineering Baseline (Draft)

Use this as a portable adoption guide, not a claim that every control already
exists. Replace every placeholder with repository evidence; do not copy another
repository's stack, provider settings, identities, paths, endpoints, or secrets.

## Purpose

Create a small, truthful engineering contract: a contributor can discover how
to work safely, verify a change, and roll it back without guessing.

## When to use

- Bootstrapping a repository or improving its contribution flow.
- Auditing a reference repository for principles rather than configuration.
- Planning documentation or backlog work before implementing automation.

## Guidance classification

| Class | Use it for | Rule |
| --- | --- | --- |
| **Universal** | Principles that apply to every repository | Reuse the principle when evidence supports it. |
| **Technology-dependent** | Commands, dependency ecosystems, test layers, and release topology | Adapt from the target stack and maturity; never mandate a vendor. |
| **Project-dependent** | Current facts, ownership, trackers, and constraints | Record evidence and do not generalize it. |

Classify every practice as **reuse**, **adapt**, **do not copy**, **obsolete**,
or **defer**. Independently choose a backlog action: **existing covers**,
**edit**, **create**, **document**, or **defer**.

## Universal

### Project discovery questions

Before proposing work, answer from repository evidence:

1. What is the repository's purpose, supported contribution path, and license state?
2. What commands currently prove formatting, analysis, tests, and builds?
3. What is the single CI source of truth and which work is trusted-only?
4. Which files or systems may contain local, private, or generated material?
5. Which issue labels, templates, milestones, and existing trackers govern work?
6. What test signal, dependency ecosystem, release path, and review budget exist?

### Repository bootstrap

- Start with a concise README, an explicit verification path, and truthful status.
- Keep local material ignored; use semantic placeholders such as `<repo>`,
  `<command>`, `<secret-name>`, and `<supported-branch>` in public examples.
- Add policies only when their owner, enforcement point, and rollback are known.

### Git strategy

- Use a documented integration-branch model and small, independently reversible changes.
- Keep branch-protection rollout proportional to contributor and review needs.
- Do not create, delete, rewrite, or protect branches during a documentation audit.

### GitHub governance

- Inspect repository policy, templates, labels, milestones, and all issue states before writing.
- Reuse an authoritative tracker rather than duplicating it; use only existing labels.
- Each new issue states Objective/Outcome, Context, Scope, Out of scope,
  acceptance criteria, dependencies, privacy/security impact, reuse result,
  verification, and rollback.

### Documentation

- Prefer short entry points, checklists, and links to source-of-truth evidence.
- Keep operational facts current; mark proposals and deferrals as such.
- Keep specification artifacts portable and repository-relative.

### Security

- Treat documentation, issue bodies, logs, and examples as public-facing.
- Never publish credentials, tokens, private identities, hostnames, endpoints,
  machine paths, or copied configuration values.
- Keep secrets at runtime in the approved secret store; audit history and assets
  before public release.

### Local quality gates

- Document the smallest local commands that correspond to CI evidence.
- Defer hooks, formatters, analysis, or coverage gates until a recurring problem
  and a meaningful signal justify their maintenance cost.

### Testing strategy

- Match the highest useful test layer to the behavior under change.
- Keep one focused check per work unit and record its exact result.
- Do not represent generated or structural tests as meaningful behavior coverage.

### AI-assisted workflow

- AI may summarize evidence and draft artifacts; humans own decisions and review.
- Require privacy-safe inputs, explicit scope, duplicate review, and a rollback boundary.
- Defer mandatory AI tooling until the repository has a proven need and owner.

### CI strategy

- Keep one primary CI source of truth; document its commands, evidence, and trust boundary.
- Do not add a second CI system merely to imitate a reference repository.

### Release strategy

- Require reproducible build, signing/credential boundaries, verification, and rollback evidence.
- Defer release automation until prerequisites are implemented and owned.

### Public-readiness

- Confirm license, contribution clarity, privacy-safe examples, local-file exclusions,
  and repository-history review before inviting reuse or external contribution.

### Definition of Ready

- Scope, owner, evidence, dependencies, acceptance criteria, and rollback are known.
- Existing trackers and taxonomy were inspected read-only.
- The 400-line review-budget decision is recorded before implementation.

### Definition of Done

- The stated acceptance criteria and focused evidence are complete.
- Documentation is truthful, scannable, privacy-safe, and links to owners.
- Duplicates were reused; any new tracker has verification and rollback guidance.

## Technology-dependent

### Dependency management

- Identify supported ecosystems and their authoritative dependency files.
- Propose update pull requests on a documented cadence with the same relevant
  verification commands as normal changes.
- Keep update automation policy-only until its credentials, ownership, CI
  compatibility, and rollback are evidenced.

### Local quality gates and testing inputs

Adapt commands, test layers, generated artifacts, platform tooling, and CI
reports to `<stack-and-maturity>`. Preserve the target repository's primary CI
and test signal; do not import provider, toolchain, hook, coverage, or release
configuration from a reference repository.

## Project-dependent

### Current constraints and tracker ownership

| Practice | Current evidence | Classification | Action | Owner / prerequisite | Deferral trigger |
| --- | --- | --- | --- | --- | --- |
| Privacy and public-readiness documentation | Existing tracker | reuse | existing covers | #18 | Evidence requires an adjacent edit. |
| License and contribution eligibility | Existing tracker | reuse | existing covers | #19 | License decision is complete. |
| Trusted automation boundary | Completed tracker | reuse | existing covers | #32 (completed) | Revisit only if the trigger or security boundary regresses. |
| Duplicate quality-gate work | Closed tracker | obsolete | existing covers | #33 (preserve closed) | Never reopen for duplicated checks. |
| Test and release maturity | Existing trackers | defer | existing covers | #34–#37 | Behavior-focused tests and release prerequisites exist. |
| Portable specification artifacts | Existing tracker | reuse | existing covers | #39 | A portable-documentation gap is evidenced. |
| Integration-branch governance | Existing tracker | adapt | existing covers | #40 | Contributor/review needs justify rollout. |
| Contributor workflow and intake | No authoritative tracker after fresh audit | adapt | create | #19 | Candidate fails duplicate or privacy gate. |
| Dependency update proposals | No authoritative tracker after fresh audit | adapt | create | Supported ecosystems and verification are evidenced. |
| Hooks, mandatory AI review, coverage gates, release automation | No justified signal | defer | defer | Meaningful behavior tests and ownership | Recurring need with a measurable benefit. |

The current project keeps its established primary CI as the single source of
truth, retains portable OpenSpec artifacts, and follows its documented GitFlow
integration-branch constraints. This draft does not create product, CI,
infrastructure, security/legal-policy, endpoint, branch, pull-request, or
release-automation work.

### Project-specific exceptions

- Preserve the authenticated interactive Jenkins boundary and the least-privilege machine webhook exception; never expose credentials, origin details, or privileged integration data.
- Use the existing tracker set above instead of creating a generic duplicate.
- Create at most the two gated workflow candidates described here.

## Bootstrap checklist

- [ ] Answer the discovery questions from current repository evidence.
- [ ] Name the single primary CI and its trust boundary without publishing sensitive details.
- [ ] Document the smallest verified local commands.
- [ ] Audit templates, taxonomy, milestones, and open/closed duplicates before issue writes.
- [ ] Classify each practice and record a backlog action, owner, prerequisite, and deferral trigger.
- [ ] Use placeholders for all sensitive, local, or provider-specific values.
- [ ] Record the 400-line review-budget decision and chain work only when required.
- [ ] Verify scope and rollback before publishing documentation or a tracker.

## Reusable AI prompt

> Audit `<repo>` using `<stack-and-maturity>` and artifact-store mode
> `<artifact-store-mode>`. Treat `<primary-ci-source>` as the single CI source
> of truth. First read-only inspect repository identity, contribution policy,
> documentation, primary CI evidence, dependency entry points, templates,
> labels, milestones, branch model, and all open/closed issues. Reuse
> `<existing-trackers>` and preserve closed decisions. Classify every observed
> practice as `reuse`, `adapt`, `do not copy`, `obsolete`, or `defer`; do not
> copy reference stacks, providers, paths, identities, endpoints, credentials,
> secrets, or configuration values. Produce a concise Universal,
> Technology-dependent, and Project-dependent baseline plus an
> `existing covers|edit|create|document|defer` gap matrix with evidence, owner,
> prerequisite, deferral trigger, verification, and rollback. Keep one CI
> source of truth; defer hooks, mandatory AI tooling, coverage, integration,
> and release automation unless evidence shows readiness. Before every GitHub
> write, refresh the all-state duplicate, taxonomy, template, milestone, and
> privacy audit; use only existing labels and no unproven milestone. Create no
> more than the approved candidate types, and include Objective/Outcome,
> Context, Scope, Out of scope, acceptance criteria, Dependencies,
> Security/Privacy, reuse note, verification, and rollback. Do not change
> product, CI, infrastructure, settings, branches, pull requests, or public
> endpoints. Decide whether the forecast exceeds the 400-line review budget;
> record `single PR`, a chain strategy, or an explicit size exception before
> implementation. Replace any sensitive value with `<secret-name>`,
> `<identity>`, `<endpoint>`, `<machine-path>`, `<command>`, or
> `<supported-branch>`.
