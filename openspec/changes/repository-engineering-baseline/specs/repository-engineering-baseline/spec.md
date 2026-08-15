# Repository Engineering Baseline Specification

## Purpose

Define a portable, evidence-based baseline draft and conditional issue-audit output without changing product, CI, infrastructure, GitHub settings, branches, or pull requests.

## Requirements

### Requirement: Classified Baseline Draft

The baseline draft MUST separate **Universal**, **Technology-dependent**, and **Project-dependent** guidance. It MUST state the current evidence and classify each practice as `reuse`, `adapt`, `do not copy`, `obsolete`, or `defer`; unimplemented controls MUST NOT be presented as current facts. Technology-dependent guidance MUST describe adaptation inputs rather than mandate a vendor, tool, CI provider, or reference-stack configuration.

#### Scenario: Evidence-backed baseline section

- GIVEN audited repository facts and reference practices
- WHEN the baseline draft is prepared
- THEN each practice appears in its applicable guidance section with a disposition and evidence status

#### Scenario: Unsupported reference practice

- GIVEN a practice depends on an unadopted provider, release topology, or toolchain
- WHEN it is evaluated
- THEN the draft marks it `adapt`, `do not copy`, `obsolete`, or `defer` rather than mandating it

### Requirement: Reusable and Privacy-Safe Prompt

The draft MUST include a reusable prompt that requests the target repository, stack, single CI source of truth, artifact-store mode, read-only taxonomy and duplicate review, existing trackers to reuse, classification, gap matrix, and 400-line review-budget decision. Examples MUST use placeholders such as `<repo>`, `<command>`, `<secret-name>`, and `<supported-branch>`; they MUST NOT contain identities, credentials, secrets, endpoints, machine paths, or provider-specific values.

#### Scenario: Prompt reuse in another repository

- GIVEN a maintainer supplies repository and stack context
- WHEN the prompt is used
- THEN it requests evidence-backed recommendations and an `existing covers|edit|create|document|defer` gap matrix

#### Scenario: Sensitive or copied value

- GIVEN source material contains an endpoint, identity, credential, or repository-specific path
- WHEN an example is included
- THEN the value is replaced with a semantic placeholder or omitted

### Requirement: Evidence-Based Backlog Audit

The issue-audit output MUST record current evidence, disposition, and owner tracker for every gap. It MUST reuse #18, #19, #32, #34–#37, #39, and #40 where they already cover privacy, license, trusted automation, maturity, portable OpenSpec, or GitFlow/branch protection; it MUST preserve closed #33. The output MUST NOT create a public-Jenkins-endpoint, product, CI, infrastructure, security/legal-policy, or release-automation backlog item.

#### Scenario: Existing tracker covers a gap

- GIVEN a gap is already owned by an authoritative issue
- WHEN the audit is published
- THEN it links that tracker with `existing covers` or `edit` and creates no duplicate

#### Scenario: Deferred maturity work

- GIVEN hooks, AI review, coverage, integration testing, or release automation lacks prerequisites
- WHEN the audit classifies the gap
- THEN it records `defer` with its prerequisite instead of proposing implementation

### Requirement: Conditional Workflow Issue Creation

Before each candidate issue, the process MUST freshly inspect repository policy, templates, labels, milestones, and all open and closed issues. It MUST reuse the established GitHub taxonomy when applicable and create a candidate only if no equivalent tracker exists, the description is privacy-safe, and evidence supports it. At most two candidates MAY be created: contributor workflow/intake templates and Gradle dependency-update policy; neither MAY mandate a vendor or operational change.

#### Scenario: Unique, supported workflow gap

- GIVEN the fresh audit finds no equivalent issue and confirms taxonomy and privacy requirements
- WHEN a supported candidate is filed
- THEN it uses applicable existing labels and contains scope, evidence, prerequisites, and rollback guidance

#### Scenario: Duplicate or unsafe candidate

- GIVEN the fresh audit finds an equivalent tracker or private information
- WHEN the candidate is evaluated
- THEN no new issue is created and the audit records the reused tracker or redaction reason
