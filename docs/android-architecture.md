# DevDigi Music Android — Architecture

Status: Proposed architecture baseline
Scope: Native Android client
Related issue: #18

## 1. Purpose

DevDigi Music is a native Android client for personal music
libraries. Navidrome/OpenSubsonic is the initial server and
the authoritative source of library data.

Each user supplies their own server and account. The app must
not depend on the maintainer's infrastructure or expose private
configuration.

This document distinguishes the current implementation from
the intended architecture. A proposed component must not be
presented as implemented.

## 2. Architectural decisions

The project adopts:

- Kotlin and Jetpack Compose for native Android development.
- Screaming Architecture for business-oriented feature grouping.
- Clean Architecture for dependencies and responsibilities.
- Explicit repository and integration boundaries where useful.
- Incremental evolution inside the existing Gradle app module.
- Jenkins as the single primary CI system.

Additional Gradle modules, dependency-injection frameworks,
databases and architectural abstractions require demonstrated
benefits before adoption.

## 3. Current implementation

The current application has one Gradle application module.

Its existing connection package contains:

- Server endpoint validation and connection models.
- Protocol parsing and authentication contracts.
- Authentication signing.
- Android Keystore-backed encrypted credential storage.
- DataStore-backed server profile persistence.
- A server connection ViewModel and Compose screen.

MainActivity currently composes the server connection screen
and manually supplies its repository.

The connection package contains several architectural
responsibilities together. It is not yet organized into
separate domain, data and presentation packages.

The current implementation is therefore not yet fully
compliant with the target dependency rules. In particular,
the existing connection source combines pure contracts with
protocol parsing, while other files combine interfaces
with Android-specific implementations.

The authenticated OkHttp transport, complete session UI,
music library and Media3 playback are not yet implemented.

Existing connection contracts and tests must be preserved
during architectural evolution.

## 4. Target package organization

The initial target is package-level organization within the
existing Gradle app module.


```text
app/src/main/java/dev/devdigi/music/
  MainActivity.kt
  AppGraph.kt                  # Proposed composition root

  core/
    # Only genuinely shared components.

  features/
    connection/
      domain/
        model/
        repository/
      data/
        remote/
        local/
        repository/
        security/
      presentation/
        screen/
        state/

    library/                   # Future First Sound feature
      domain/
      data/
      presentation/

  playback/                    # Future Media3 integration
    service/
    controller/
```

This tree is conceptual, not a claim that these directories
or classes currently exist.

Create directories and abstractions only when the corresponding
implementation needs them.

## 5. Dependency rules

The dependency direction is:

Presentation -> Domain <- Data

The application composition root connects the layers.

Domain:
- Contains pure models, policies and business contracts.
- Does not depend on Android, Compose, OkHttp or DataStore.
- Expresses transport-independent outcomes.
- Contains no persistence or framework implementation.

Data:
- Implements domain repository and integration contracts.
- Owns remote API, local persistence and security adapters.
- Maps protocol and persistence representations to domain types.
- Does not expose transport-specific objects to presentation.

Presentation:
- Contains Compose screens, UI states and ViewModels.
- Depends on domain contracts rather than concrete data adapters.
- Does not directly perform HTTP requests or cryptographic work.
- Delegates persistence and business operations.

Composition root:
- Creates and wires concrete implementations.
- Owns Android-specific initialization.
- Is the permitted place for cross-layer assembly.

Core:
- Contains functionality genuinely shared across features.
- Must not become a collection of unrelated abstractions.
- Must not create hidden dependencies between features.

## 6. Connection and authentication boundaries

Preserve the existing endpoint-validation and authentication
contracts during the ongoing authentication work.

ServerAccountIdentity consists of the normalized server
endpoint and the exact, opaque username.

Credentials are bound to the corresponding server account.
Secrets must never be included in logs or public artifacts.

Account isolation also applies to future local library data,
preferences, downloads, playback history and queue state.
Switching accounts must not expose another account's data.

The existing encrypted credential-store guarantees remain
mandatory throughout refactoring.

For the upcoming authenticated network implementation:

- HTTPS is required when transmitting credentials.
- Authenticated requests must not follow redirects.
- Sensitive request URLs must never be logged.
- A fresh authentication salt is generated per request.
- Only successful HTTP responses are eligible for protocol
  success interpretation.
- Response bytes must be bounded before string conversion.
- Existing parser bounds remain additional protections.

The detailed authentication specification remains authoritative
for WU3 and WU4 behavior.

## 7. Playback boundary

Media3 is the planned playback engine.

The playback integration will manage Android-specific services,
MediaSession integration and player lifecycle.

Queue rules, playback intentions and account ownership should
remain separate from Android service implementation details.

Playback must not silently mix state belonging to different
server accounts.

This boundary is planned and is not yet implemented.

## 8. Future provider boundaries

Navidrome remains canonical for First Sound.

Provider-neutral music identity, source selection and external
audio/video integrations are research subjects under #55,
with dependencies on #53 and #54.

This architecture does not approve external-provider contracts
or introduce premature provider abstractions.

## 9. Testing strategy

Domain:
- Pure JVM unit tests for models, policies and behavior.

Data:
- JVM tests using fakes where appropriate.
- MockWebServer tests for authenticated HTTP behavior.
- Focused persistence and cryptographic boundary tests.

Presentation:
- ViewModel state-transition tests.
- Compose UI tests when meaningful UI behavior exists.

Playback:
- Focused playback and queue tests when implemented.
- Instrumented tests for Android-specific behavior where
  JVM tests cannot provide sufficient evidence.

Do not add coverage thresholds without a meaningful baseline.

Jenkins remains the authoritative automated verification system.
The actual commands and configured gates are documented in
docs/ci.md.

## 10. Migration policy

Architecture changes must be incremental and reviewable.

1. Agree on this architecture baseline.
2. Preserve existing public contracts and regression tests.
3. Confirm Android SDK 36 is available locally and on the
   Jenkins agent before beginning WU3.
4. Implement the WU3 HTTP adapter behind the existing
   AuthenticatedPingClient contract. Keep networking and
   cryptographic operations out of presentation code.
5. Avoid unrelated package moves during WU3 and WU4.
6. Extract existing mixed packages in separate, test-backed
   refactors without changing their established behavior.
7. Document architectural exceptions and their rationale.

Avoid mixing broad file moves with security-sensitive behavior
changes unless there is an explicit, reviewed reason.

Do not add Hilt, Koin, Room or extra Gradle modules solely to
match a proposed directory diagram.

## 11. Engineering rules

Each substantial change should:

- Have a clear issue or specification.
- Respect the established feature and dependency boundaries.
- Include relevant behavior-focused verification.
- Keep private data out of source, tests and documentation.
- Pass the applicable Jenkins checks.
- Remain reviewable and independently reversible.
- Document important architectural tradeoffs.

The normal changed-line review budget is 400 lines.
A cohesive exception requires an explicit rationale.

## 12. Deferred decisions

The following require separate decisions or implementation:

- Physical multi-module architecture.
- Dependency-injection framework adoption.
- Database and offline persistence design.
- Media3 service and queue implementation.
- External music-provider integration.
- Release automation and additional quality tooling.

Related existing trackers should be reused rather than
creating duplicate engineering work.
