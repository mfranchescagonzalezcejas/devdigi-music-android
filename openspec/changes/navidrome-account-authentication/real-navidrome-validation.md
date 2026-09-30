# WU5 — Real Navidrome Validation

Date: 2026-09-30

Canonical baseline:

`cb78d37b050f70e2c044f73323e0960858c6f61a`

Scope: gated real-device/runtime validation of the merged
WU1-WU4 Navidrome account-authentication implementation.

No password, authentication token, salt, username, device
serial, raw authenticated request URL, private host address,
or private network address is recorded in this evidence.

## Runtime validation

| Scenario | Result |
|---|---|
| Real Navidrome credentials authenticate | PASS |
| Navidrome/OpenSubsonic metadata is returned | PASS |
| Cold Android process restart restores session | PASS |
| Restore performs a fresh authenticated server round trip | PASS |
| Real Navidrome outage fails closed | PASS |
| Authenticated identity is hidden during outage | PASS |
| Recoverable outage retains the encrypted credential | PASS |
| Server recovery restores without password re-entry | PASS |
| Explicit sign-out survives cold process restart | PASS |
| Deliberately invalid password is rejected | PASS |
| Rejected password does not become a durable session | PASS |
| Final valid authentication succeeds | PASS |
| Final cold restoration succeeds | PASS |

## Secret-disclosure validation

| Check | Result |
|---|---|
| Plaintext real password absent from inspected DataStore bytes | PASS |
| Base64 real password absent from inspected DataStore bytes | PASS |
| Plaintext real password absent from captured logcat | PASS |
| Base64 real password absent from captured logcat | PASS |

## Regression validation

| Check | Result |
|---|---|
| Debug unit test suite | PASS |
| Android lintDebug | PASS |
| Debug APK assembly | PASS |
| Spotless | PASS |
| git diff --check | PASS |

## Security interpretation

A durable encrypted credential alone does not cause the UI
to expose an authenticated identity.

During a real server outage, a cold process start failed
closed and did not expose the saved account identity. This
provides integration evidence that session restoration
depends on a fresh authenticated Navidrome round trip.

The recoverable network/server failure did not destroy the
stored encrypted credential. Once the server returned, a
later cold start restored the session without password
re-entry.

Explicit sign-out removed the durable session credential:
a subsequent cold process start remained signed out.

A deliberately rejected password did not become a durable
restorable credential.

The real password was not observed in plaintext or base64
form in the inspected authentication persistence bytes or
captured Android logs.

## Result

WU5 real Navidrome validation: **PASS**.

WU1-WU5 implementation and validation for
`navidrome-account-authentication` are complete.

API-key authentication remains intentionally deferred until
the capability is available in a stable Navidrome release.
