# Navidrome Account Authentication Specification

## Purpose

#14 secure Subsonic/OpenSubsonic account auth over #13's endpoint boundary.

## ADDED Requirements

### Requirement: Subsonic Token Signing

`token = md5(password + salt)` as UTF-8 lowercase hex. Salt from `SecureRandom` bytes → lowercase hex, ≥ 6 chars, no `+`/`/`, per-request. Signer accepts transient `AuthCredentials`, returns `(salt, token)`, persists neither.

#### Scenario: Match the published Subsonic test vector

- GIVEN password `sesame` and salt `c19b2d`
- WHEN signer derives token
- THEN token MUST equal `26719a1196d2a940705a59634eb18eab`

#### Scenario: Produce a URL-safe per-request salt

- GIVEN fresh signing invocation
- WHEN salt generated
- THEN length ≥ 6 AND every char matches `[0-9a-f]`
- AND consecutive invocations yield different salts

### Requirement: Authenticated Ping Result Taxonomy

`AuthenticatedPingClient` returns `AuthResult` sealed to: `Authenticated(metadata)`, `InvalidCredentials`, `UnsupportedAuthentication`, `AuthProtocolError`, `IncompatibleServer`, `NetworkError`. Mapping: `#40` → `InvalidCredentials`; `#41`/`#42` → `UnsupportedAuthentication`; `#43` → `AuthProtocolError`; `#20`/`#30` + invalid protocol → `IncompatibleServer`; IO/timeout → `NetworkError`. `#44` unmapped.

On the `ConnectionFacts.authentication` axis: `Authenticated` → `AUTHENTICATED`; `InvalidCredentials` (`#40`) → `REJECTED`; `UnsupportedAuthentication` (`#41`/`#42`), `AuthProtocolError` (`#43`), `IncompatibleServer`, and `NetworkError` → `NOT_CHECKED`. `REJECTED` SHALL be used only when credentials were actually evaluated and rejected; an unsupported mechanism or protocol conflict MUST NOT imply invalid credentials.

#### Scenario: Successful authenticated ping

- GIVEN valid credentials for `https://music.example.com`, user `alice`
- WHEN server responds `ok`, `serverVersion=0.54.1`, `type=navidrome`, `openSubsonic=true`
- THEN result `Authenticated` with that metadata

#### Scenario: Invalid credentials

- GIVEN user `alice` with incorrect password
- WHEN server responds with error code `#40`
- THEN result MUST be `InvalidCredentials`

#### Scenario: Unsupported authentication scheme

- WHEN server responds with error code `#41` or `#42`
- THEN result MUST be `UnsupportedAuthentication`

#### Scenario: Auth protocol error

- WHEN server responds with error code `#43`
- THEN result MUST be `AuthProtocolError`

#### Scenario: Incompatible server or protocol

- WHEN server responds with error code `#20` or `#30` and invalid protocol response
- THEN result MUST be `IncompatibleServer`

#### Scenario: Network failure

- WHEN request times out or socket fails
- THEN result MUST be `NetworkError`

### Requirement: Fail-Closed Sign-In and Secret Persistence

Authenticated ping BEFORE persist. Secret persists ONLY after `Authenticated` into separate `auth_secret` Preferences DataStore (not `ServerProfile`'s). Secure-persist failure after valid ping → no durable `AUTHENTICATED`. `ServerProfile` unchanged by sign-in.
#### Scenario: Persist secret only after successful auth

- GIVEN successful authenticated ping for `alice` at `https://music.example.com`
- WHEN sign-in completes
- THEN secret in `auth_secret` AND `ServerProfile` endpoint-only AND `AUTHENTICATED` exposed
#### Scenario: Secure-store failure after valid ping fails closed

- GIVEN successful authenticated ping AND Keystore/DataStore write fails
- WHEN sign-in proceeds
- THEN no durable `AUTHENTICATED` or `ServerAccountIdentity` exposed

### Requirement: Sign-Out

Clear secret from `auth_secret` + clear authenticated state. Preserve `ServerProfile`.

#### Scenario: Sign out preserves the saved server

- GIVEN `AUTHENTICATED` session for `alice` at `https://music.example.com`
- WHEN user signs out
- THEN secret cleared from `auth_secret` AND auth state cleared AND `ServerProfile` remains
### Requirement: Session Restoration with Re-Authentication

Process restart: read `ServerProfile`, recover secret via `AuthSecretStore`, fresh authenticated ping BEFORE exposing durable `ServerAccountIdentity`/`AUTHENTICATED`. Revoked creds, missing/invalid key, or network failure → no false durable identity.
#### Scenario: Restore after process restart

- GIVEN stored profile and recoverable secret
- WHEN process restarts and authenticated ping succeeds
- THEN `ServerAccountIdentity` and `AUTHENTICATED` exposed
#### Scenario: Invalidated or missing Keystore key

- GIVEN stored ciphertext with missing/invalidated Keystore key
- WHEN process restarts
- THEN secret store clears ciphertext AND no durable `AUTHENTICATED`/identity exposed

### Requirement: Secret Boundary

`AuthCredentials.toString` redacts password. Password transient, masked in UI, never in logs/telemetry/`SavedState`/real fixtures/`ServerProfile` DataStore. Tests use synthetic identities only.

#### Scenario: No secret leakage in credentials representation

- GIVEN `AuthCredentials` for `alice`
- WHEN `toString` invoked
- THEN output contains neither password nor derived token

#### Scenario: No secret in persisted or logged artifacts

- GIVEN sign-in flow for `alice`
- WHEN logs, `SavedState`, `ServerProfile` DataStore inspected
- THEN neither password nor token present

### Requirement: Stable Account Identity

`ServerAccountIdentity` from normalized `ServerEndpoint` + normalized username ONLY. `ServerMetadata` (`serverType`, `serverVersion`, `openSubsonic`) separate, not part of identity.

#### Scenario: Identity stable across version changes

- GIVEN `ServerAccountIdentity(endpoint=https://music.example.com, username=alice)`
- WHEN server reports different `serverVersion` or `openSubsonic`
- THEN identity unchanged AND only `ServerMetadata` updated
