# Design: Reproducible Navidrome Integration

## Boundary

#35 is host/JVM integration testing.

The runtime topology is:

    JVM integration tests
            |
            | loopback HTTP
            v
    ephemeral Navidrome
            |
            +-- temporary data
            +-- synthetic music fixtures

It does not add an Android emulator. That remains #34.

## Image pinning

Do not use a floating `latest` image.

WU0 selects an explicit Navidrome version and records the resolved immutable
image digest before CI integration is considered reproducible.

Image upgrades are explicit reviewed changes.

## Runtime isolation

Every run owns:

- unique Compose project identity;
- temporary writable Navidrome data;
- isolated loopback endpoint and port;
- generated synthetic account password.

Do not use:

- fixed container names;
- persistent shared volumes;
- shared fixed host ports.

Parallel CI runs must not collide.

## Synthetic account

WU0 verifies a supported Navidrome test/development account bootstrap
mechanism before implementation.

The account is synthetic.

The password is generated independently for every run and is never committed
or printed.

Scripts must not enable shell tracing while secret material is in scope.

## Fixtures

Fixtures contain only original or synthetic media and metadata.

Minimum target:

- one album;
- at least two short tracks;
- deterministic album, artist and track metadata;
- at least one FLAC track for later First Sound reuse.

Navidrome-generated album and track identifiers remain opaque.

Tests must discover those IDs from responses rather than hardcoding them.

WU0 documents fixture provenance and decides whether deterministic binaries
are committed or generated.

## Readiness

Container-running state is not sufficient readiness evidence.

The lifecycle harness uses a bounded wait for:

1. HTTP availability;
2. authenticated OpenSubsonic success;
3. visibility of the expected synthetic library.

An arbitrary fixed sleep is not the source of truth.

Timeout diagnostics must remain sanitized.

## Integration tests

Normal `testDebugUnitTest` stays Docker-independent.

The Docker-backed suite uses a separate invocation and distinct JUnit report
path.

Tests exercise real application boundaries for:

- authenticated ping;
- recent albums;
- album details.

Required behavior:

- valid synthetic authentication succeeds;
- invalid synthetic credentials fail closed;
- recent albums expose expected synthetic content;
- album details use an opaque server-returned album ID;
- expected synthetic tracks are present;
- server-returned track order is preserved.

No integration test asserts fixed Navidrome-generated IDs.

## Lifecycle harness

Use a repository-owned harness exposing operations conceptually equivalent to:

    preflight
    up
    wait-ready
    test
    diagnostics
    down
    run

`run` owns cleanup through a shell trap.

Cleanup is idempotent.

A failed integration run must not leave its server or temporary data behind.

## Jenkins

WU0 first proves the Android Jenkins agent can use Docker and the
integrated Docker Compose plugin exposed through `docker compose`.

The eventual synthetic integration stage:

- requires no real Navidrome credentials;
- uses isolated runtime state;
- publishes distinct JUnit results;
- exposes sanitized diagnostics;
- performs unconditional cleanup.

Once synthetic integration coverage is proven, remove the old automated
trusted-server Navidrome hook.

This does not remove #17.

## Privacy

Never publish:

- personal endpoints;
- personal usernames;
- real credentials or tokens;
- personal music-library metadata;
- environment dumps containing transient secrets;
- private machine paths.

## Relationship to #17

#17 remains manual validation against a real user-provided Navidrome instance.

#35 provides deterministic synthetic automated integration.

Neither replaces the other.

## Relationship to #34

#34 owns Android instrumented CI.

#35 does not add emulator infrastructure merely to validate HTTP integration
that can be meaningfully exercised from JVM tests.

## Review strategy

Keep implementation slices small:

- WU0 — capability evidence;
- WU1 — lifecycle;
- WU2 — fixtures and integration tests;
- WU3 — Jenkins and docs;
- WU4 — final validation.

Normal target remains below approximately 400 changed lines per PR.
