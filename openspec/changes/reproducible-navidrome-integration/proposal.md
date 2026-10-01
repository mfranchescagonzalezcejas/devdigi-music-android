# Proposal: Reproducible Navidrome Integration

## Intent

Implement #35 with an ephemeral, synthetic Navidrome environment for automated
integration validation.

The environment exercises real DevDigi Music OpenSubsonic boundaries without
using a personal server, library or credential.

#17 remains the separate manual validation path against a real user-provided
Navidrome instance.

## Current state

The repository already has JVM coverage for authentication, recent albums,
album details, protocol parsing, transport hardening and account isolation.

Jenkins currently runs formatting, JVM unit tests, Android lint and debug
assembly.

It also contains an older optional trusted Navidrome stage using
Jenkins-managed real-server credentials.

There is currently no committed synthetic Docker Navidrome integration
environment.

## Scope

### In scope

- Explicitly pinned Navidrome Docker image.
- Ephemeral server and data per execution.
- Synthetic account with per-run generated password.
- Legally distributable synthetic music fixtures.
- Loopback-only host exposure.
- Deterministic readiness, diagnostics and cleanup.
- JVM integration tests for:
  - authenticated ping;
  - recent albums;
  - album details.
- Jenkins execution without personal Navidrome credentials.
- Privacy-safe CI documentation and evidence.

### Out of scope

- Android emulator/instrumented CI (#34).
- Playback implementation (#1).
- Background playback (#15).
- Queue implementation (#7).
- Personal libraries, endpoints or credentials.
- Persistent/shared test servers.
- Replacing #17.

## Security

Synthetic credentials remain secret material for the lifetime of a run.

They MUST NOT be:

- committed;
- printed;
- archived;
- stored in Jenkins Credentials;
- reused outside the current run.

No personal endpoint, account or media metadata may enter fixtures, logs,
reports or artifacts.

## Work units

1. WU0 — Docker/Navidrome capability and fixture probe.
2. WU1 — ephemeral Docker lifecycle.
3. WU2 — synthetic fixtures and JVM integration tests.
4. WU3 — Jenkins integration and documentation.
5. WU4 — reproducibility and cleanup validation.

Each implementation PR remains independently reviewable.

## Rollback

Before Jenkins integration the environment is additive and removable without
changing production application behavior.
