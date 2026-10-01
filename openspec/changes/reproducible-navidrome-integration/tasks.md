# Tasks: Reproducible Navidrome Integration

## WU0 — Capability probe

- [x] 0.1 Verify local Docker and the integrated Docker Compose plugin via `docker compose`.
- [x] 0.2 Verify Docker/Compose capability on the Jenkins Android agent.
- [x] 0.3 Select and pin an explicit Navidrome version and immutable digest.
- [x] 0.4 Verify a supported synthetic admin bootstrap mechanism.
- [x] 0.5 Verify isolated loopback port allocation.
- [x] 0.6 Decide and document synthetic FLAC fixture provenance/generation.
- [x] 0.7 Record sanitized capability evidence.

## WU1 — Ephemeral lifecycle

- [ ] 1.1 Add lifecycle acceptance/self-tests where meaningful.
- [x] 1.2 Add pinned Compose configuration.
- [x] 1.3 Add unique per-run project, data and port state.
- [x] 1.4 Generate a fresh synthetic password without logging it.
- [x] 1.5 Add bounded authenticated/library readiness.
- [x] 1.6 Add sanitized failure diagnostics.
- [ ] 1.7 Add idempotent cleanup for success and failure.
- [ ] 1.8 Verify repeated lifecycle runs leave no state behind.

## WU2 — Fixtures and integration tests

- [ ] 2.1 Add original/synthetic album fixtures with at least two tracks.
- [ ] 2.2 Include at least one short FLAC fixture.
- [ ] 2.3 Document provenance/generation and checksums.
- [ ] 2.4 Add a separate Docker-backed JVM integration invocation.
- [ ] 2.5 Exercise authenticated ping against synthetic Navidrome.
- [ ] 2.6 Exercise recent albums through the real application boundary.
- [ ] 2.7 Resolve and use an opaque album ID returned by Navidrome.
- [ ] 2.8 Exercise album details and expected synthetic tracks.
- [ ] 2.9 Prove server track order is preserved.
- [ ] 2.10 Prove invalid synthetic credentials fail closed.
- [ ] 2.11 Keep ordinary `testDebugUnitTest` Docker-independent.
- [ ] 2.12 Give integration tests distinct JUnit output.
- [ ] 2.13 Pass unit tests, Spotless, lint and debug assembly.

## WU3 — Jenkins

- [ ] 3.1 Add Docker/Compose preflight.
- [ ] 3.2 Run synthetic integration without Jenkins Navidrome credentials.
- [ ] 3.3 Isolate concurrent build runtime state.
- [ ] 3.4 Publish integration JUnit evidence separately.
- [ ] 3.5 Guarantee sanitized diagnostics and unconditional cleanup.
- [ ] 3.6 Remove the old automated trusted-Navidrome credential hook.
- [ ] 3.7 Preserve #17 as manual real-instance validation.
- [ ] 3.8 Update CI and README documentation.

## WU4 — Final validation

- [ ] 4.1 Run from a clean checkout.
- [ ] 4.2 Run two consecutive successful integrations without leaked state.
- [ ] 4.3 Force failure and prove cleanup.
- [ ] 4.4 Prove project, data and port allocation is collision-resistant.
- [ ] 4.5 Verify committed files and CI artifacts are privacy-safe.
- [ ] 4.6 Verify Jenkins branch and PR-merge checks.
- [ ] 4.7 Record privacy-safe verification evidence.
- [ ] 4.8 Mark #35 complete without changing #17 or #34 scope.
