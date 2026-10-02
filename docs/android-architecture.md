# DevDigi Music Android — Architecture

Status: Incremental implemented architecture baseline
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
- OpenSubsonic token/salt authentication signing.
- An authenticated OkHttp ping boundary with defensive response handling.
- Android Keystore-backed encrypted credential storage.
- DataStore-backed server profile persistence.
- Interactive sign-in, validated session restoration and explicit sign-out.
- Authenticated server metadata and session state.
- A server connection ViewModel and adaptive Compose screen.

MainActivity is the explicit composition root. It manually wires the
server-profile repository, secure secret store, authentication signer/client,
library repositories and ViewModels, and the foreground playback engine
factory.

The connection package contains several architectural
responsibilities together. It is not yet organized into
separate domain, data and presentation packages.

The current implementation is therefore not yet fully
compliant with the target dependency rules. In particular,
the existing connection source combines pure contracts with
protocol parsing, while other files combine interfaces
with Android-specific implementations.

The authenticated OkHttp connection boundary, session UI, recent-album and
album-details browsing, and foreground one-track Media3 playback are
implemented. Background/system playback remains #15 scope, and queue behavior
remains #7 scope.

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

    library/
      domain/
      data/
      presentation/

    playback/
      domain/
      data/
      presentation/
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

The endpoint-validation and authentication contracts completed under #14
are current behavior and must be preserved during future feature work and
refactoring.

ServerAccountIdentity consists of the normalized server
endpoint and the exact, opaque username.

Credentials are bound to the corresponding server account.
Secrets must never be included in logs or public artifacts.

Account isolation also applies to future local library data,
preferences, downloads, playback history and queue state.
Switching accounts must not expose another account's data.

The existing encrypted credential-store guarantees remain
mandatory throughout refactoring.

The implemented authenticated connection boundary enforces:

- HTTPS is required when transmitting credentials.
- Authenticated requests must not follow redirects.
- Sensitive request URLs must never be logged.
- A fresh authentication salt is generated per request.
- Only successful HTTP responses are eligible for protocol
  success interpretation.
- Response bytes must be bounded before string conversion.
- Existing parser bounds remain additional protections.

The completed authentication OpenSpec remains authoritative for the
security and session behavior validated through #14 WU1-WU5.

## 7. Playback boundary

Foreground one-track Media3 playback is implemented under the playback
feature. Presentation depends on domain playback contracts; the data layer
owns authenticated stream resolution, redirect-disabled transport and the
Media3 ExoPlayer adapter. MainActivity wires the concrete engine through the
explicit composition root.

Playback state contains only safe selected-track metadata and coarse outcomes.
Account changes and sign-out clear current playback, and stale track/account
events are rejected by the presentation controller.

Android service/MediaSession/background playback remains explicitly deferred
to #15. Queue behavior remains explicitly deferred to #7. Neither concern is
part of the #1 foreground playback boundary.

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
- Pure JVM state-reducer, stream-resolution and presentation-controller tests.
- Focused Media3 adapter tests where framework-independent behavior can be
  isolated.
- Real-device validation for Android codec/player behavior that JVM tests
  cannot prove.
- Instrumented CI remains tracked separately by #34.

Do not add coverage thresholds without a meaningful baseline.

Jenkins remains the authoritative automated verification system.
The actual commands and configured gates are documented in
docs/ci.md.

## 10. Migration policy

Architecture changes must be incremental and reviewable.

1. Preserve the implemented #14 authentication, credential and session
   contracts together with their regression tests.
2. Keep the current single-module composition explicit; extract mixed
   packages only in separate, test-backed refactors with demonstrated value.
3. Add the First Sound library flow behind account-scoped domain and data
   boundaries without leaking transport details into presentation.
4. Extend foreground Media3 playback through #15 service/session behavior
   and #7 queue behavior in their separate scopes while preserving account
   ownership and domain boundaries.
5. Introduce database or offline persistence structures only when their
   product milestones require them.
6. Finalize external-provider contracts only after #53, #54 and #55 provide
   the required viability and architecture decisions.
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

The normal hard changed-line review budget is 1000 lines.
Larger changes require an explicit rationale and split after measurement.

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
