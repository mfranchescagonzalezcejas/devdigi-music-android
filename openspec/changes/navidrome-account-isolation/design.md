# Design: Navidrome Account Isolation

## 1. Canonical account identity

The isolation key remains the existing `ServerAccountIdentity`.

It is composed from:

- the normalized server endpoint;
- the exact server username.

The username remains opaque, case-sensitive and Unicode-preserving.

Therefore:

- server A + alice differs from server A + bob;
- server A + alice differs from server B + alice;
- server A + alice equals the same normalized server A + exact alice identity.

Server metadata, credentials and authentication mechanism are not part of
account identity.

## 2. Isolation model

Issue #16 requires account isolation, not multi-account retention.

Different state holders may enforce ownership by:

- storing or verifying the current account identity;
- using generation and target checks;
- clearing transient state on identity change;
- rejecting stale asynchronous results;
- using a non-reversible account fingerprint for durable ownership.

The governing invariant is:

State owned by identity A must never be accepted as current state for
identity B.

A store does not need to retain simultaneous values for every historical
account unless another issue explicitly requires that behavior.

## 3. State ownership matrix

| State | Authority | Lifetime | Isolation rule |
| --- | --- | --- | --- |
| Server profile | `ServerProfileRepository` | Durable | Profile changes invalidate stale authentication work. |
| Credential | `AuthSecretStore` | Durable encrypted | Bound to normalized endpoint plus exact username. |
| Authenticated identity | Connection/session layer | Runtime | Exposed only after current successful authentication or restoration. |
| Recent albums | Repository and `RecentAlbumsViewModel` | Runtime | Result owner must match current account. |
| Album details | Repository and `AlbumDetailsViewModel` | Runtime | Owner and requested album must both match. |
| Navigation | First Sound shell | Runtime/saveable | Account-owned routes reset on identity change or sign-out. |
| Playback | `PlaybackService` | Runtime | Runtime ownership must match current account. |
| Queue runtime | `PlaybackService` | Runtime | Different-account mutation is rejected. |
| Queue snapshot | Queue persistence | Durable safe metadata | Restore requires matching account fingerprint. |

## 4. Authentication and session transitions

Existing #14 authentication guarantees remain authoritative.

Authentication or restoration work that becomes stale because the selected
profile or generation changed must not:

- publish `AUTHENTICATED`;
- expose a stale `ServerAccountIdentity`;
- persist stale credentials as the current credential;
- revive presentation state owned by the prior account.

Explicit sign-out remains fail-closed.

Issue #16 does not weaken or replace the existing secure credential boundary.

## 5. Asynchronous stale-result policy

Cancellation alone is not sufficient as an isolation guarantee.

Where asynchronous work can complete after an account transition, acceptance
must also depend on the current generation, target or account ownership.

Example sequence:

1. request for account A starts;
2. active identity changes to B;
3. request for B starts;
4. the older result for A completes;
5. the A result is ignored.

This pattern applies wherever relevant to authentication, library loading,
playback restoration and stream resolution.

## 6. Library state

Recent albums and album details already carry account ownership through their
domain results.

The required rule is that data owned by one identity cannot publish into
presentation state after another identity becomes current.

Album details additionally require the requested album target to remain
current.

No new durable library cache is introduced by #16.

Any future durable library persistence must use the same server-plus-user
ownership boundary.

## 7. Navigation state

Navigation state that can reference account-owned content must not cross an
identity transition.

This includes selected album state and secondary content routes.

When the exact authenticated identity changes, or the session becomes
unauthenticated, account-owned navigation state resets instead of being
transferred to the next account.

Navigation remains presentation state and does not become an account store.

## 8. Playback and runtime queue

`PlaybackService` remains the sole runtime player and queue authority.

If account A owns runtime playback and account B becomes current:

1. stale A work must no longer be applicable;
2. A runtime ownership must be invalidated;
3. B must not observe or control A-owned queue state;
4. only B-owned or safely B-restorable state may become current.

Signed stream URIs remain ephemeral and must never enter durable queue state.

## 9. Durable queue semantics

The existing #7 persistent-queue contract remains authoritative.

A queue snapshot may remain one physical durable snapshot.

Required behavior is:

- a snapshot owned by A may restore when A reconciles;
- a snapshot owned by A must not restore or play when B reconciles.

Issue #16 does not require separate durable queues for every historical
account.

The existing rule that a safe queue snapshot does not need to be deleted on
sign-out remains valid.

A later account may replace the single durable snapshot with its own queue.

## 10. Identity regression dimensions

Tests must independently cover both identity dimensions.

### Same server, different users

Synthetic example:

- `https://music.example.com` + `alice`;
- `https://music.example.com` + `bob`.

These are different identities.

### Same username, different servers

Synthetic example:

- `https://music.example.com` + `alice`;
- `https://music-alt.example.com` + `alice`.

These are different identities.

No real infrastructure or account information may appear in committed tests.

## 11. Implementation strategy

Working subsystems should not be rewritten merely to satisfy #16.

Follow-up WUs add focused regression coverage first.

Production changes are justified only where those tests expose an actual gap.

Planned boundaries:

- WU1: identity, session, library and navigation isolation;
- WU2: playback and queue isolation;
- WU3: documentation, OpenSpec reconciliation and closeout.

Every WU remains below 1000 changed lines and should preferably remain below
approximately 800 to 850 lines.

## 12. Relationship to #17

Issue #16 establishes deterministic local account isolation first.

Issue #17 then validates the complete First Sound vertical slice against the
user-provided real Navidrome instance.

Private endpoint, account, credential, media and device information from that
validation must not be committed.
