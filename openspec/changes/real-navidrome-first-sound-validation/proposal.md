# Proposal: Real Navidrome First Sound Validation

## Issue

GitHub #17 — validate the complete First Sound vertical slice against a real,
user-provided Navidrome/OpenSubsonic instance.

## Outcome

Provide one local, opt-in automated Android validation that drives First Sound
against a real instance without requiring routine screen interaction.

The user should only need to prepare a connected, unlocked Android device,
provide runtime-only connection credentials and launch the runner.

## Why

First Sound now has deterministic unit, contract and synthetic integration
coverage for authentication, library browsing, playback, persistent queue,
navigation and account isolation.

Issue #17 validates that those independently tested pieces also work together
against a real Navidrome instance and a real Android Media3 environment.

The real-instance path complements the synthetic Navidrome environment from
#35. It does not replace synthetic regression coverage.

## Automation instead of a manual checklist

The original #17 checklist was manual.

The approved execution model is now a local automated validation:

- Android instrumentation drives DevDigi Music through stable semantics.
- Narrow device/system commands exercise backgrounding and standard media
  transport controls where Compose cannot own the surface.
- No coordinate-based tapping is permitted when a stable semantic selector can
  be provided.
- The runner remains opt-in and local.

This does not move Android device testing into Jenkins.

## Dynamic media selection

No personal album, artist or track is hard-coded.

At execution time the validator:

1. inspects the same real Recent Albums source used by the app;
2. evaluates candidates in returned order;
3. selects the first album containing at least three tracks and at least one
   FLAC track;
4. drives the app to that album;
5. uses at least three tracks to validate queue navigation;
6. uses a FLAC track to prove real Media3 playback.

The selected title, artist, track names, media ids and server response content
must never be emitted as evidence.

If no qualifying candidate exists, the scenario is BLOCKED rather than
silently weakened.

## Privacy model

Real-instance values exist only at execution time.

The change must never commit or publish:

- real server URLs or DNS names;
- private or overlay IP addresses;
- usernames;
- passwords;
- tokens or salts;
- signed stream URLs;
- device serials;
- album, artist or track names from the private library;
- private media ids;
- listening history;
- screenshots containing private data;
- raw authenticated responses or diagnostic dumps containing that data.

Evidence uses fixed PASS, FAIL or BLOCKED labels only.

## Relationship to adjacent issues

### #34 — Android instrumented CI

#17 may create useful local instrumented coverage.

It MUST NOT add the real-instance path to Jenkins, create an emulator/device
farm, or inject real credentials into CI.

#34 remains responsible for deciding what device-level automation belongs in
CI.

### #104 — reusable QA / BDD harness

#17 may add the smallest feature-specific runner necessary for this validation.

It MUST NOT create the general smoke/sanity/regression framework, Gherkin
infrastructure or reusable QA dashboard tracked by #104.

### #35 — synthetic Navidrome integration

The existing reproducible synthetic environment remains the CI-safe integration
boundary.

#17 reuses its lessons and security constraints but validates a separate real
user-provided instance.

## Out of scope

- Jenkins/device-farm execution.
- Generic QA/BDD framework work.
- Public hosted test servers.
- Telemetry.
- Third-party providers.
- Search or Discover implementation.
- Recommendations.
- Queue editor UI.
- Cast or remote playback.
- Hard-coded personal media fixtures.
