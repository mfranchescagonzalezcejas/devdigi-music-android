# Acceptance features

Version-controlled Gherkin acceptance specifications live in this directory.

These files are the canonical business-readable acceptance behavior for DevDigi
Music. They are not automatically executable merely because they use Gherkin.

Current rules:

- use observable behavior rather than implementation steps;
- reuse the suite/domain tags defined in `docs/qa/strategy.md`;
- `@manual` means the scenario currently participates in human/Test Execution
  validation;
- `@automation-candidate` means later automation may be valuable; it does not
  claim Cucumber automation exists;
- existing JVM/integration tests remain separate automated evidence at their
  appropriate lower layer;
- use `@real-instance` only when a user-provided compatible server or Android
  runtime is part of the acceptance signal;
- never include real endpoints, usernames, credentials, device serials,
  listening/library data, signed stream URLs, or raw authenticated logs.

Test Execution results belong in Jira/equivalent execution evidence. Do not
edit a feature file to record PASS/FAIL for a particular run.
