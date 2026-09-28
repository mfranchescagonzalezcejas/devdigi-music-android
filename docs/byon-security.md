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

- A Compose screen for entering a server URL.
- Endpoint validation and normalization.
- Local persistence of the selected server endpoint.
- Contracts for authenticated connection results.
- An OpenSubsonic authentication signer.
- Defensive OpenSubsonic response parsing.
- An encrypted credential-storage implementation.
- Unit tests for the existing connection and security code.

The authenticated HTTP transport is not implemented yet.

The sign-in form, session restoration and complete sign-out
workflow are not connected to the current UI.

Music browsing and Media3 playback are also not implemented.

Saving a server URL does not establish a connection,
authenticate the user or verify server compatibility.

## 3. BYON configuration

The intended setup flow is:

1. Obtain access to a Navidrome/OpenSubsonic server.
2. Obtain an individual account on that server.
3. Configure a suitable HTTPS endpoint.
4. Enter the server URL in DevDigi Music.
5. Authenticate after the upcoming sign-in implementation
   becomes available.
6. Browse and play the authorized library once the
   corresponding First Sound features are implemented.

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

The upcoming authenticated HTTP implementation requires
HTTPS and must reject redirects rather than forwarding
authentication material to another destination.

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

These are implemented storage capabilities, not a
claim that interactive sign-in is already available.

## 7. Backup and restoration

Android backup and device-transfer rules explicitly
exclude the dedicated auth-secret DataStore.

This prevents the configured backup flows from
restoring encrypted credentials without their
corresponding Android Keystore key.

The non-secret server-profile DataStore is not
excluded by these rules.

The future session-restoration implementation must
authenticate successfully before presenting an
authenticated session.

Successful sign-out must not be reported while
recoverable durable credentials remain.

These session behaviors belong to the planned
authentication integration.

## 8. Authentication transport

The existing signer supports OpenSubsonic token/salt
authentication.

The upcoming authenticated transport will use
the protocol's token/salt request parameters
instead of transmitting the plaintext password.

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

These transport requirements are planned under WU3.
They are not claims about an already operational
network client.

## 9. Android playback architecture

The planned native playback engine is Media3.

MediaSession and MediaLibraryService will provide
the Android system playback integration.

Playback infrastructure must remain separate from
queue-domain rules and account-ownership policies.

The queue must preserve the identity of its owner.
Changing accounts must not expose another account's
playback state.

See `android-architecture.md` for the architectural
responsibilities and migration policy.

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

The current Android build uses compileSdk 35.
Android compilation targets Java 17 bytecode.

The Jenkins pipeline uses these verification commands:

```sh
./gradlew testDebugUnitTest
./gradlew lint
./gradlew assembleDebug
```

Jenkins additionally publishes available test and lint
reports and archives the debug APK.

The formatting/static-analysis pipeline stage is
currently diagnostic, not an implemented formatting
or static-analysis gate.

Authenticated HTTP integration requires the separately
planned Android SDK 36 preflight.

A real Navidrome integration test environment is
separate, gated future work.

## 12. Related documentation

- [Android architecture](android-architecture.md)
- [Continuous integration](ci.md)
- [GitFlow governance](gitflow-governance.md)

The authentication OpenSpec remains authoritative
for detailed WU3 and WU4 security behavior.

External music-provider integrations are not approved
capabilities within this documentation scope.
