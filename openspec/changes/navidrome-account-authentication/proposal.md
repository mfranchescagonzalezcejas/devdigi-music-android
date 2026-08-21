# Proposal: Navidrome Account Authentication

## Intent

Implement #14: secure, durable Navidrome account authentication on top of #13's endpoint boundary. Today `ConnectionFacts.authentication` is always `NOT_CHECKED` and no credentials can be stored or used. Users must be able to sign in against a Subsonic/OpenSubsonic server, persist only an encrypted secret, restore a session without trusting stale state, and sign out without losing their saved server.

## Scope

### In Scope

- `ServerAccountIdentity` (normalized `ServerEndpoint` + exact opaque username used by the successful authentication request) and separate `ServerMetadata` (`serverType`, `serverVersion`, `openSubsonic`). Username is case-sensitive and Unicode-preserving; no trim, case folding, or Unicode normalization at the identity layer.
- `AuthCredentials` secret boundary: redacted `toString`, no serialization/logging/telemetry, transient password only.
- Subsonic token/salt signing: `token = md5(password + salt)` UTF-8 lowercase hex; per-request `SecureRandom` salt (≥6 chars, URL-safe hex, never persisted).
- `AuthResult` taxonomy: `Authenticated`, `InvalidCredentials` (#40), `UnsupportedAuthentication` (#41/#42), `AuthProtocolError` (#43), `IncompatibleServer` (#20/#30 + invalid protocol), `NetworkError`. #44 unmapped (API-key out of scope).
- Android Keystore AES/GCM/NoPadding ciphertext in separate `auth_secret` Preferences DataStore, excluded from backup/restore; invalid/missing key → clear + forced re-login (NOT `EncryptedSharedPreferences`).
- Fail-closed sign-in; sign-out (clear secret + auth state, preserve `ServerProfile`); re-authenticated session restoration.
- Authenticated network boundary via OkHttp 5.4.0 (no logging-interceptor); `kotlinx-serialization-json` 1.9.0 runtime; `mockwebserver` testImplementation only.
- Work units WU1–WU5 (WU5 real-Navidrome validation gated).

### Out of Scope

- Catalog, recent albums, album detail, playback, Media3, queue, offline, ListenBrainz, FastAPI, Cast/Alexa, discovery, Tailscale logic.
- API-key auth (#44), #16 account-scoped persistence implementation (only prepare `ServerAccountIdentity`).

## Capabilities

### New Capabilities

- `navidrome-account-authentication`: secure Subsonic/OpenSubsonic sign-in, sign-out, session restoration, secret storage, and result taxonomy.

### Modified Capabilities

None. `navidrome-server-connection` (#13, archived) stays canonical for endpoint/profile/repository boundary; `PingClient`, `PingObservation`, `reducePingObservation` remain the synthetic seam unchanged.

## Approach

Seam-preserving (exploration Option 1 + 4a + 5 + 6a + 7): add a parallel `AuthenticatedPingClient` fun interface + `reduceAuthResult` alongside the untouched #13 `PingClient`. Production `OkHttpAuthenticatedPingClient` uses `SubsonicAuthSigner` for per-request salt/token; `SessionRestorer` reads `ServerProfile` + `AuthSecretStore`, decrypts transiently, performs an authenticated ping, and only exposes durable `AUTHENTICATED`/identity on success. Secrets stored via `AndroidKeystoreAuthSecretCipher` into a separate `auth_secret` DataStore excluded from backup.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `app/src/main/java/.../connection/ServerConnection.kt` | Modified | Add identity, metadata, credentials, `AuthResult`, `AuthenticatedPingClient`, `reduceAuthResult`. |
| `app/src/main/java/.../connection/` (new files) | New | `SubsonicAuthSigner`, `AuthSecretStore`, `AuthSecretCipher`, `AuthKeyProvider`, `OkHttpAuthenticatedPingClient`, `SessionRestorer`. |
| `app/src/main/java/.../connection/ServerConnectionViewModel.kt` | Modified | Sign-in/sign-out/restore state + flows. |
| `app/src/main/java/.../connection/ServerConnectionScreen.kt` | Modified | Masked password field, status messaging. |
| `app/src/main/java/.../MainActivity.kt` | Modified | DI wiring. |
| `app/src/main/AndroidManifest.xml` | Modified | `INTERNET` permission; backup/data-extraction rules. |
| `app/src/main/res/xml/` | New | `backup_rules.xml`, `data_extraction_rules.xml`. |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | Modified | OkHttp, `kotlinx-serialization-json`, `mockwebserver`. |
| `app/src/test/.../connection/` | New | RED tests + `MockWebServer` integration. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Keystore unavailable/invalidated → crash loop or false identity | Med | Catch `GeneralSecurityException`; clear ciphertext; fail closed; test. |
| Backup-rule path glob mismatch restores orphaned ciphertext | Med | Verify `datastore/auth_secret.preferences_pb` exclusion in WU2; `disableIfNoEncryptionCapabilities`. |
| Logging leaks password/token | Low | Forbid logging-interceptor; no credentials in request `toString`; review checklist. |
| JSON runtime dep leak | Low | `kotlinx-serialization-json` is the only JSON runtime; verify `debugRuntimeClasspath` in verify. |
| Username normalization collision | Low | Username is an opaque, case-sensitive, Unicode-preserving identifier by design; no trim/case-fold/NFC at the identity layer (collisions cannot be collapsed). |
| Restore race shows stale `AUTHENTICATED` UI | Med | `Restoring` intermediate state; restore completes before auth UI. |
| Result taxonomy drift (#41/#42 vs #43, #44) | Med | WU3 matrix enumerates each code → cell; assert #44 unmapped. |

## Rollback Plan

- Revert `feat/14-secure-navidrome-authentication`; #13 code paths (`PingClient`, `reducePingObservation`, `ServerProfileRepository`) are untouched, so reversion restores prior behavior with no migration.
- No schema change to `server_profile` DataStore; new `auth_secret` DataStore can be deleted on uninstall.
- Remove `INTERNET` permission and OkHttp/kotlinx-serialization-json deps alongside revert to avoid dangling runtime dependencies.
- Keystore keys are device-local and orphaned harmlessly if reverted (ciphertext clears fail-closed on missing key).

## Dependencies

- Canonical base: #13 `navidrome-server-connection` archived spec (endpoint/profile/repository boundary) — read-only reference, no runtime dependency on #13.
- OkHttp `5.4.0`, `kotlinx-serialization-json` 1.9.0, `mockwebserver` (test).

## Delivery Note

`delivery_strategy = single-pr` (user-approved, no pre-split). Forecast may exceed the 400-line review budget (~700 est.). The orchestrator will surface the split-vs-`size:exception` decision at tasks forecast time — do NOT pre-split, do NOT assume `size:exception` auto-approved.

## Success Criteria

- [ ] Sign-in: authenticated ping → secret persisted → `AUTHENTICATED`; any chain failure → fail closed, no durable identity.
- [ ] Sign-out clears secret + auth state and preserves `ServerProfile`.
- [ ] Session restore re-authenticates via ping; revoked/invalid/network failure yields no durable identity.
- [ ] `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` pass; `org.json` absent from `debugRuntimeClasspath`; `kotlinx-serialization-json` present as the only JSON runtime.
- [ ] Password/token never logged, serialized, telemetered, or present in real fixtures.
