# Navidrome Account Isolation Specification

## Requirement: canonical account identity

All account-owned First Sound state MUST use the exact authenticated
server-plus-user identity as its ownership boundary.

The identity MUST consist of the normalized server endpoint plus the exact
opaque username.

### Scenario: two users on one server

**Given** `alice` and `bob` authenticate against the same normalized server

**Then** they MUST resolve to different account identities

**And** state owned by one MUST NOT be accepted for the other.

### Scenario: same username on two servers

**Given** `alice` authenticates against two different normalized servers

**Then** those sessions MUST resolve to different account identities

**And** state owned on one server MUST NOT be accepted on the other.

## Requirement: isolation without multi-account retention

First Sound MUST isolate account-owned state without requiring a
multi-account manager.

### Scenario: active profile changes

**Given** the app persists one selected server profile

**When** that profile changes

**Then** stale work from the previous profile MUST NOT become current
authenticated state

**And** the implementation MAY replace the previously selected profile.

## Requirement: authentication isolation

Authentication and restoration work MUST fail closed when its profile or
generation becomes stale.

### Scenario: profile changes during authentication

**Given** authentication for account A is in progress

**When** the active profile changes before A publishes authenticated state

**Then** A MUST NOT become the current authenticated identity

**And** stale A credentials MUST NOT become the current durable credential.

## Requirement: recent-albums isolation

Recent-album presentation state MUST accept results only for the current
account.

### Scenario: stale result after account switch

**Given** a recent-albums request for account A is pending

**When** account B becomes current

**And** the account-A result completes later

**Then** account-A albums MUST NOT replace or augment account-B state.

### Scenario: sign-out during request

**Given** a recent-albums request is pending

**When** the session becomes unauthenticated

**Then** account-bound recent-album state MUST be cleared

**And** the old result MUST NOT repopulate it later.

## Requirement: album-details isolation

Album details MUST belong to both the active account and current album target.

### Scenario: stale album result after account switch

**Given** account A is loading album X

**When** account B becomes current

**And** account A's result completes later

**Then** album X from account A MUST NOT become visible to account B.

## Requirement: navigation isolation

Navigation state that references account-owned content MUST reset when the
exact authenticated identity changes or the session becomes unauthenticated.

### Scenario: switch account from album details

**Given** account A has an album-detail route open

**When** account B becomes current

**Then** account A's album route MUST be cleared

**And** account B MUST NOT inherit account A's secondary navigation state.

### Scenario: sign out

**Given** authenticated account-owned navigation state exists

**When** sign-out succeeds

**Then** that account-owned navigation state MUST no longer be visible.

## Requirement: runtime playback isolation

`PlaybackService` MUST remain the sole runtime player and queue authority.

Playback ownership MUST match the current authenticated account.

### Scenario: account switch during playback

**Given** account A owns active playback and runtime queue state

**When** account B becomes current

**Then** account A's runtime ownership MUST be invalidated

**And** account B MUST NOT observe or control account A's queue state.

### Scenario: stale stream resolution

**Given** stream resolution for account A is pending

**When** account ownership changes before resolution completes

**Then** the stale result MUST NOT become the active Media3 item.

## Requirement: durable queue ownership

Persisted queue state MUST restore only for the account identity that owns it.

A single physical queue snapshot is permitted.

### Scenario: matching owner

**Given** a safe queue snapshot belongs to account A

**When** account A reconciles

**Then** that queue MAY restore without autoplay.

### Scenario: different owner

**Given** a safe queue snapshot belongs to account A

**When** account B reconciles

**Then** account A's queue MUST NOT restore

**And** MUST NOT play

**And** MUST NOT become account B's runtime queue.

### Scenario: sign-out preserves safe snapshot

**Given** account A has a safe persisted queue snapshot

**When** account A signs out

**Then** runtime playback ownership MUST be removed

**And** the safe snapshot MAY remain

**But** another account MUST NOT restore it.

## Requirement: credentials remain inside secure storage

Credentials MUST NOT become ordinary account-scoping keys or ordinary
persistent application state.

### Scenario: account ownership key

**Given** local state requires account ownership information

**Then** passwords, credentials, tokens and salts MUST NOT be used as the
ownership key

**And** MUST NOT be copied into ordinary persistence.

## Requirement: public regression data is synthetic

Account-isolation tests and documentation MUST use synthetic identities and
media.

### Scenario: committed regression coverage

**Given** account-isolation regression tests are committed

**Then** they MUST NOT contain real endpoints, private IP addresses, private
DNS names, real usernames, credentials, tokens, salts, signed stream URIs,
device serials, private library metadata or listening history.
