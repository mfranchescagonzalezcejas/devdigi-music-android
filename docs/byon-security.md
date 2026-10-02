# Bring Your Own Navidrome — Setup and Security

Status: Current implementation and planned First Sound behavior
Related issue: #18

## 1. Product model

DevDigi Music is a native Android client for music libraries
hosted on user-provided Navidrome/OpenSubsonic servers.

The application does not provide a central music catalog
or host its users' audio.

Each person is responsible for their own server access
and uses their own Navidrome account.

Server deployment and administration are outside this
Android repository's scope.

## 2. Current implementation

The current Android application provides:

- A Compose screen for server and account configuration.
- Endpoint validation and normalization.
- Local persistence of the selected server endpoint.
- OpenSubsonic token/salt authentication signing.
- Authenticated OkHttp connection verification.
- Defensive OpenSubsonic response parsing.
- Android Keystore-backed encrypted credential storage.
- Interactive sign-in and explicit sign-out.
- Validated session restoration after a fresh authenticated server check.
- Authenticated server metadata and account identity.
- Authenticated recent-album and album-details browsing.
- Service-backed Media3 playback with application and Android system controls.
- Unit and integration-focused tests for the connection and security code.

A real Navidrome validation under #14 also proved fail-closed restoration
during an outage, recovery without password re-entry, sign-out persistence
across a cold process restart, and rejection of invalid credentials.

Real-device playback validation under #15 proved background continuity,
system-control integration, account reconciliation and playback-surface
privacy without publishing private server or account data.

Saving a server URL persists the server profile but does not by itself
establish an authenticated session.

## 3. BYON configuration

The intended setup flow is:

1. Obtain access to a Navidrome/OpenSubsonic server.
2. Obtain an individual account on that server.
3. Configure a suitable HTTPS endpoint.
4. Enter the server URL in DevDigi Music.
5. Authenticate with the Navidrome account through the connection screen.
6. Browse and play the authorized library through the implemented native
   library and Media3 playback flow.

A documentation-only example endpoint is:

`https://music.example.com`

A documentation-only example username is:

`demo-user`

Do not substitute real credentials into public
documentation, issues, screenshots or test fixtures.

The app must not depend on a particular hosting provider,
network topology or maintainer-operated server.

If Navidrome is hosted under a URL path, that configured
base path must be preserved by future API requests.

## 4. Endpoint security

The existing endpoint validator normalizes supported URLs
and rejects malformed or ambiguous URL components.

Release builds accept HTTPS endpoints only.

Release validation also restricts explicit local/private
address forms. Consult the actual endpoint policy before
assuming a particular private-network configuration works.

Debug builds have narrowly scoped local HTTP exceptions
for development. These exceptions must not become a
general authentication transport policy.

A valid-looking URL is not proof of server ownership,
network reachability or TLS certificate validity.

The authenticated HTTP implementation requires HTTPS for release
authentication and rejects redirects rather than forwarding authentication
material to another destination.

## 5. Server and account identity

The canonical account identity consists of:

- The normalized server endpoint.
- The exact, case-sensitive username.

Usernames must not be silently trimmed, lowercased
or Unicode-normalized when constructing identity.

Two accounts on one server are distinct identities.
The same username on two different servers also
represents two distinct identities.

Future account-specific library state, preferences,
downloads, history and playback queues must not
be shared accidentally between identities.

Full account-scoped application state is a planned
capability. The current UI stores one server profile;
it is not a complete multi-account session manager.

## 6. Credential storage

The existing security implementation uses a dedicated
DataStore named `auth_secret`.

Passwords are encrypted with AES-GCM using a key
managed by Android Keystore.

Authenticated additional data binds encrypted secrets
to the normalized endpoint and exact username.

An encrypted credential for one identity must not
be accepted for another identity.

The username is stored separately as binding metadata.
The password is stored as encrypted data, not plaintext.

The non-secret server endpoint is persisted separately
in the `server_profile` DataStore.

These storage capabilities back the implemented interactive sign-in,
session-restoration and sign-out workflows.

## 7. Backup and restoration

Android backup and device-transfer rules explicitly
exclude the dedicated auth-secret DataStore.

This prevents the configured backup flows from
restoring encrypted credentials without their
corresponding Android Keystore key.

The non-secret server-profile DataStore is not
excluded by these rules.

Session restoration authenticates successfully against the configured
server before publishing an authenticated identity.

A recoverable server/network failure fails closed without exposing the
identity or deleting an otherwise valid encrypted credential. Rejected saved
credentials are not accepted as a restorable session.

Successful sign-out is published only after the durable credential has been
cleared. A clear failure remains retryable and must not falsely report a
successful sign-out.

## 8. Authentication transport

The existing signer supports OpenSubsonic token/salt
authentication.

The implemented authenticated transport uses the protocol's token/salt
request parameters instead of transmitting the plaintext password.

A fresh random salt is required for every request.

Authentication tokens, salts and sensitive request
URLs must never appear in application logs.

Automatic redirects must be disabled.

Only successful HTTP responses are eligible for
successful OpenSubsonic protocol interpretation.

Response bodies must be byte-bounded before complete
string materialization.

The existing protocol parser also enforces
character-length and structural-depth limits.

These transport requirements are implemented and were validated as part
of the completed #14 authentication work.

## 9. Android playback architecture

Native playback uses Media3.

A `MediaLibraryService` owns the active ExoPlayer and
`MediaLibrarySession`. The application UI controls that service through a
Media3 `MediaController`.

Authenticated stream resolution remains inside the service boundary. Signed
OpenSubsonic stream URLs are used only as player-local configuration and are
not published as Media3 metadata.

Now-playing system metadata is limited to the opaque track id, title and
optional artist. Server endpoints, account identity, credentials, salts,
tokens and signed stream URLs must not be exposed through notification,
lock-screen or system media surfaces.

Own-application account-bearing session commands are restricted to the
application identity. System media controllers receive supported transport
controls without authority to inject authenticated media items.

Sign-out and account changes reconcile service-owned playback before
credential/session ownership changes can expose stale playback state.

Real-device validation under #15 confirmed background playback, application
reconnection, notification and lock-screen controls, standard media-button
dispatch, Activity recreation, account switching and playback-surface
privacy.

Queue-domain behavior remains separate #7 scope.

See `android-architecture.md` for the architectural responsibilities and
migration policy.

## 10. Public repository privacy

Never publish:

- Real server endpoints or private DNS names.
- Credentials, passwords or authentication tokens.
- Authentication salts or authenticated request URLs.
- Personal account identifiers or listening history.
- Private network configuration.
- Signing keys or CI credentials.

Apply this policy to code, documentation, commits,
issues, pull requests, screenshots, tests and logs.

Use only synthetic identities and placeholders
in publicly shared examples.

Do not capture authenticated network traffic
in publicly distributed test evidence.

## 11. Verification

The current Android build uses compileSdk 36 and targetSdk 35.
Android compilation targets Java 17 bytecode.

The Jenkins pipeline uses these verification commands:

```sh
./gradlew spotlessCheck
./gradlew testDebugUnitTest
./gradlew lint
./gradlew assembleDebug
```

Jenkins additionally publishes available test and lint
reports and archives the debug APK.

The formatting/static-analysis pipeline stage enforces the existing
Spotless formatting gate. Detekt and coverage remain deferred.

Android SDK 36 support is part of the current build baseline and has been
validated by the required Jenkins pipeline.

The reproducible synthetic Navidrome integration environment tracked by #35
complements, but does not replace, real-account validation. Authentication
validation is recorded under #14 WU5, while privacy-safe real-device
background/system playback validation is recorded under #15.

## 12. Related documentation

- [Android architecture](android-architecture.md)
- [Continuous integration](ci.md)
- [GitFlow governance](gitflow-governance.md)

The authentication OpenSpec remains authoritative for the completed #14
WU1-WU5 security and session behavior.

External music-provider integrations are not approved
capabilities within this documentation scope.
