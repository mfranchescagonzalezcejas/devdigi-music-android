# Tasks: Navidrome Account Authentication

## Review Workload Forecast

Estimated changed lines for this generation: ~150-250 (one focused production change + planning/docs corrections).
400-line budget risk: Low (single parser guard + two unit tests; remaining work is documentation-only).

Hard planning ceiling: 800 — respected; forecast is within ceiling.
Delivery strategy: chained-prs (canonical, approved)
(superseded — earlier single-pr / size:exception statements are historical audit context only; canonical strategy is chained PRs: A/#48 planning+WU1, B/#49 WU2, C WU3, D WU4, WU5 gated. 400 lines is the normal review-budget decision threshold, not a hard limit; PR A/#48 has an approved cohesive size exception and is intentionally not split.)
Chain strategy: stacked-to-main
PR A / #48: planning + WU1 auth core
PR B / #49: WU2 secure-secret-storage
PR C: WU3 authenticated-network-boundary
PR D: WU4 session/UI
WU5: gated real-Navidrome validation after WU1-WU4 integration

Decision needed before apply: No (canonical chained PR strategy approved)
Chained PRs recommended: Yes
400-line budget risk: Low (this generation is one focused parser guard + docs corrections)

| Unit | Goal | Test command | Rollback boundary |
|------|------|--------------|-------------------|
| 1 | Auth types + signer + response parser + result/facts mapping + identity/metadata | `testDebugUnitTest --tests "*.connection.*Auth*"` | Remove new files + deps |
| 2 | Ping client + session + UI | `testDebugUnitTest --tests "*.connection.*"` | Remove OkHttp/SessionRestorer, revert VM/Screen |

## Phase 1: Domain Types + Signer (WU1)

### RED

- [x] 1.1 Add `kotlinx-serialization-json` 1.9.0 runtime dependency to `libs.versions.toml` + `implementation` in `app/build.gradle.kts`; remove `org.json`. OkHttp 5.4.0 + MockWebServer deferred to WU3 (boundary decision)
- [x] 1.2 Create `SubsonicAuthSignerTest.kt` — Subsonic vector (sesame+c19b2d->26719a...); salt >=6 hex
- [x] 1.3 Create `AuthCredentialsTest.kt` — toString redacts password
- [x] 1.4 Create `ReduceAuthResultTest.kt` — all AuthResult variants map to ConnectionFacts
- [x] 1.5 Create `ServerAccountIdentityTest.kt` — identity stable across metadata changes
- [x] 1.6 Create `SubsonicResponseParserTest.kt` — JSON `subsonic-response` → AuthResult mapping using strict `kotlinx-serialization-json`; rejects single quotes, unquoted keys, trailing commas, comments, trailing tokens

### GREEN

- [x] 1.7 Add to `ServerConnection.kt`: ServerAccountIdentity, ServerMetadata, AuthCredentials, AuthResult, AuthenticatedPingClient, reduceAuthResult, SubsonicResponseParser
- [x] 1.8 Create `SubsonicAuthSigner.kt` — interface+AuthSignature+impl: md5(password+salt) lowercase hex, SecureRandom salt >=6 hex

### Verify

- [x] 1.9 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.*Auth*"`
- [x] 1.10 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.ReduceAuthResultTest"`
- [x] 1.11 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.ServerAccountIdentityTest"`
- [x] 1.12 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.SubsonicResponseParserTest"`

### Remediation (PR #48 late review — WU1)

- [x] 1.13 RED tests for strict standard JSON: single quotes, unquoted keys, trailing commas, comments, trailing tokens all map to `AuthProtocolError`
- [x] 1.14 GREEN: rewrite `SubsonicResponseParser.parse` with `Json { isLenient = false }`, safe `JsonPrimitive` extraction, no `org.json`

### Remediation (PR #48 late review — planning/docs)

- [x] 1.15 Backup-path documentation corrected across openspec: `auth_secret.preferences_pb` → `datastore/auth_secret.preferences_pb`
- [x] 1.16 WU2 endpoint/username binding acceptance expanded in Phase 2 (AAD binds normalized endpoint + exact opaque username; no trim/lowercase/NFC)
- [x] 1.17 WU3 redirect policy + request-inspection acceptance expanded in Phase 3 (`followRedirects(false)`, no signed-param forwarding, 3xx → `AuthProtocolError`, exact query-param assertions)
- [x] 1.18 WU4 stale in-flight auth vs profile-change contract added to Phase 4 (generation/revision backstop, cancellation, no Mutex across network ping)

### Generation 13 planning/docs remediation (review 4997606540 on 0f85153)

- [x] 1.19 Finding 1 (3833865653, P1): Preserve endpoint base paths in WU3 planning + design; add deterministic MockWebServer acceptance cases for endpoint root `/rest/ping.view`, endpoint with base path `/navidrome/rest/ping.view`, trailing-slash normalization avoiding `//rest/...`, encoded path segments preserved, query/auth construction preserving endpoint path.
- [x] 1.20 Finding 2 (3833865657, P1): Clarify in design.md that username is persisted OUTSIDE the ciphertext as non-secret binding metadata; username participates in AES-GCM AAD authentication; `read(expectedEndpoint)` reads stored username first, builds AAD from normalized endpoint + stored exact username, then decrypts; stored username remains exact/opaque/case-sensitive/Unicode-preserving/no trim/no lowercase/no NFC.
- [x] 1.21 Finding 3 (3833865663, P1): Strengthen WU4 contract in design.md + tasks.md Phase 4 for atomic revision check + auth commit (TOCTOU): final generation/profile validation MUST be atomic with each security-relevant state transition using a SHORT shared critical section / orchestration mutex; network request remains OUTSIDE the mutex; deterministic planned tests expanded to cover profile change immediately after ping completion, after final pre-persist check, while persistence suspended, after final pre-publish check, stale identity not visible, stale credentials not durable snapshot.
- [x] 1.22 Finding 4 (3833865670, P2): Make delivery strategy consistent across all planning artifacts with canonical CHAINED PR strategy (A/#48 planning+WU1, B/#49 WU2, C WU3, D WU4, WU5 gated); mark stale single-pr / PR-A=WU1+WU2+WU3 / PR-B=WU4+WU5 statements as superseded.
- [x] 1.23 Finding 5 (3833865674, P1): Document WON'T FIX AS MANDATORY POST rationale in tasks.md + design.md: baseline authenticated ping uses query-param mechanism (u/t/s/v/c/f) required before extension discovery; HTTPS mandatory; HTTP rejected; redirects disabled; no logging interceptor; `AuthSignature.toString` redacted; fresh salt per request; no token/salt persistence; formPost only after capability discovery.
- [x] 1.24 Finding 6 (3833865679, P1): Strengthen WU4 fail-closed sign-out contract in design.md + tasks.md Phase 4: sign-out MUST NOT report success if recoverable durable credential still exists; prefer `clear()` must succeed; surface non-secret error/retry state; planned tests for success/failure/restoration/cancellation/retry.
- [x] 1.25 Finding 7 (3833865681, P1): Scope verify-report.md to implemented work: WU1 / PR #48 verification = PASS; overall `navidrome-account-authentication` change = INCOMPLETE / NOT READY TO ARCHIVE; machine-readable counts scoped to WU1-implemented requirements (4/4) and scenarios (11/11).
- [x] 1.26 Finding 8 (3833865684, P1): Add WU3 planning/tests requirements in tasks.md Phase 3 + design.md: only HTTP 2xx responses eligible for OpenSubsonic JSON parsing; non-2xx response with valid success envelope MUST NOT yield Authenticated; non-2xx -> AuthProtocolError BEFORE body interpretation; preserve timeout/IOException -> NetworkError and 3xx -> AuthProtocolError.

## Phase 2: Secure Secret Storage (WU2)

### RED

- [ ] 2.1 Create `AuthSecretCipherTest.kt` — round-trip; wrong key->exception; tampered->fail
- [ ] 2.2 Create `AuthSecretStoreTest.kt` — round-trip; empty->null; clear; failure->no durable state
- [ ] 2.2a Endpoint/username binding acceptance: secret saved for endpoint A cannot be read under endpoint B; same snapshot with changed exact username fails authentication; endpoint mismatch returns no credentials; username/AAD mismatch returns no credentials; invalid rejected snapshot is conditionally cleared; stale cleanup must not erase a newer valid replacement snapshot. `read(expectedEndpoint)` reads the stored username first, builds AAD from normalized `expectedEndpoint` + stored exact username, then decrypts; stored username is non-secret binding metadata, remains exact/opaque/case-sensitive/Unicode-preserving/no trim/no lowercase/no NFC.
- [ ] 2.2b Fresh IV acceptance: every AES-GCM encryption generates a fresh random IV; IV size = 12 bytes / 96 bits; two encryptions under the same key and same plaintext must produce distinct IVs and distinct ciphertexts; IV must never be constant/reused; IV uniqueness is security-critical because AES-GCM nonce reuse is forbidden.
- [ ] 2.2c Key permanently invalidated acceptance: on `KeyPermanentlyInvalidatedException`/equivalent `GeneralSecurityException`, fail the current crypto op closed; delete the invalidated Android Keystore alias; clear or conditionally invalidate ciphertext bound to the old key; do NOT retry `getOrCreateKey` within the same failed op; a later user/auth op MAY create a fresh replacement key; a newly-entered credential encrypts successfully under the fresh key. Reference PR #49 tests `keyPermanentlyInvalidatedDeletesAliasAndFailsClosed` and `keyPermanentlyInvalidatedDuringEncryptDeletesAliasAndFailsClosedAndLaterSucceeds`.

### GREEN

- [ ] 2.3 Create `AuthKeyProvider.kt` — interface + AndroidKeystore impl (AES/GCM)
- [ ] 2.4 Create `AuthSecretCipher.kt` — interface + EncryptedSecret + Android impl; AAD binds normalized `ServerEndpoint.value` + exact opaque username (case-sensitive, Unicode-preserving, no trim/lowercase/NFC)
- [ ] 2.5 Create `AuthSecretStore.kt` — interface + DataStore impl, separate auth_secret
- [ ] 2.6 Create `res/xml/backup_rules.xml` — exclude `datastore/auth_secret.preferences_pb`
- [ ] 2.7 Create `res/xml/data_extraction_rules.xml` — exclude `datastore/auth_secret.preferences_pb`
- [ ] 2.8 Update `AndroidManifest.xml` — fullBackupContent, dataExtractionRules, disableIfNoEncryption

### Verify

- [ ] 2.9 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.AuthSecretCipherTest"`
- [ ] 2.10 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.AuthSecretStoreTest"`

## Phase 3: Authenticated Network Boundary (WU3)

### RED

- [ ] 3.1 Create `OkHttpAuthenticatedPingClientTest.kt` — MockWebServer: success->Authenticated; #40->InvalidCredentials; #41/#42->Unsupported; #43->AuthProtocolError; #20/#30->IncompatibleServer; malformed/protocol-invalid response->AuthProtocolError; timeout/IOException->NetworkError; #44 unmapped. Request inspection: path `/rest/ping.view` when endpoint has no base path; path preserves configured endpoint base path (e.g. `/navidrome/rest/ping.view`); trailing-slash normalization must not create `//rest/...`; encoded path segments must not be decoded/re-encoded incorrectly; query/auth construction must never discard the existing endpoint path; decoded query params exactly `u`=opaque username, `t`=MD5(password+captured salt) lowercase 32 hex, `s`=fresh salt, `v`=1.13.0, `c`=devdigi-music, `f`=json; `p` absent; plaintext password absent; no duplicate auth params; username NOT trimmed/lowercased/NFC-normalized; salt satisfies format/length; successive requests use different salts. Redirect tests: cross-origin 302/307/308 — configured server receives exactly one request, redirect target receives zero, result != Authenticated, username/salt/token never reach redirect target.
- [ ] 3.1a Endpoint base path preservation acceptance: deterministic MockWebServer cases for endpoint root `/rest/ping.view`, endpoint with base path `/navidrome/rest/ping.view`, trailing-slash normalization avoiding `//rest/...`, encoded path segments preserved, query/auth construction preserving endpoint path.
- [ ] 3.1b Non-success HTTP response rejection: only HTTP 2xx responses are eligible for OpenSubsonic JSON parsing; non-2xx (400/401/404/500/502/503) with an otherwise valid `status: ok` / `openSubsonic: true` body MUST NOT yield Authenticated; non-2xx -> AuthProtocolError BEFORE body interpretation. Preserve: timeout/IOException -> NetworkError; 3xx -> AuthProtocolError (redirects disabled).
- [ ] 3.1c Authenticated ping protocol/security rationale (WON'T FIX AS MANDATORY POST): baseline authenticated ping uses query-parameter mechanism (u/t/s/v/c/f) required before extension discovery; HTTPS mandatory; HTTP endpoints rejected; redirects disabled (`followRedirects(false)`); no logging interceptor; application code never logs full authenticated request URLs; `AuthSignature.toString` remains redacted (salt=***, token=***); fresh random salt on every request; no persistence of token/salt. Optional formPost extension MAY be considered separately only after server capability discovery confirms support.

### GREEN

- [ ] 3.2 Create `OkHttpAuthenticatedPingClient.kt` — OkHttp impl, SubsonicAuthSigner, strict `kotlinx-serialization-json` parsing
- [ ] 3.3 OkHttp client factory — no logging-interceptor; `followRedirects(false)`; `followSslRedirects(false)` if applicable; any 3xx rejected locally as `AuthProtocolError`

### Verify

- [ ] 3.4 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.OkHttpAuthenticatedPingClientTest"`

## Phase 4: Session + ViewModel + UI (WU4)

### RED

- [ ] 4.1 Create `SessionRestorerTest.kt` — valid->success; missing profile/secret/ping->fail closed
- [ ] 4.2 Update `ServerConnectionViewModelTest.kt` — sign-in Restoring->AUTHENTICATED; failure->no durable; sign-out clears auth; restore re-authenticates
- [ ] 4.2a Stale in-flight auth vs profile change (TOCTOU): each attempt captures current `ServerProfile` generation/revision; profile save/replace/delete cancels the active job AND increments/invalidates prior attempts; the final generation/profile validation MUST be atomic with each security-relevant commit of authentication state, using a SHORT shared critical section / orchestration mutex covering (check captured profile generation + check captured endpoint/profile + commit the state transition). The network request MUST remain OUTSIDE this mutex; never hold a mutex while waiting for network ping or long unrelated I/O. Cancellation remains defense-in-depth, NOT sole correctness. Before exposing `AUTHENTICATED`/identity, verify generation still matches and target profile still current; stale attempt loses, current profile wins. Deterministic tests for (A) profile change immediately after ping completion; (B) immediately after final pre-persist check; (C) while persistence is suspended; (D) immediately after final pre-publish check; (E) stale identity cannot become visible; (F) stale credentials cannot become the current durable snapshot — using CompletableDeferred/controlled fakes/coroutines-test, no Thread.sleep.
- [ ] 4.2b Fail-closed sign-out: a user MUST NOT be told sign-out succeeded if a recoverable durable credential still exists for the active profile. Successful sign-out requires EITHER (A) secret store `clear()` succeeds, OR (B) an explicitly-designed durable cryptographic invalidation mechanism succeeds such that restoration cannot recover the credential. For current scope prefer (A): `clear()` must succeed before sign-out is committed as successful. If clear fails: do NOT report successful sign-out; do NOT silently transition to a state that can restore as authenticated later; surface a non-secret error/retry state; keep fail-closed semantics; no secret material in errors/logs. Planned tests: successful clear -> signed out; clear failure -> sign-out not reported successful; subsequent restoration cannot be incorrectly treated as a successful prior logout; cancellation propagates correctly; retry can eventually complete logout.
- [ ] 4.2c Restoration credential-retention policy: `Authenticated` -> expose identity/metadata and RETAIN credential; `InvalidCredentials` -> clear credential (forced re-entry); `NetworkError`/`AuthProtocolError`/`UnsupportedAuthentication`/`IncompatibleServer` -> RETAIN encrypted credential; unrecoverable key/ciphertext/GCM failure or explicit sign-out -> clear invalid snapshot. `AUTHENTICATED`/identity exposed only after successful ping; never expose identity merely because a secret is retained. Planned tests: NetworkError retains secret and exposes no identity; retry authenticates without re-entering password; AuthProtocolError/Unsupported/Incompatible retain; InvalidCredentials clears; crypto-unrecoverable clears; sign-out clears.

### GREEN

- [ ] 4.3 Create `SessionRestorer.kt` — read profile + recover secret + fresh ping; fail-closed; generation/revision check backstop (not Mutex across network ping)
- [ ] 4.4 Update `ServerConnectionViewModel.kt` — sign-in/sign-out/restore flows; wire deps
- [ ] 4.5 Update `ServerConnectionScreen.kt` — masked password, sign-in/out buttons, status
- [ ] 4.6 Update `MainActivity.kt` — DI wiring
- [ ] 4.7 Update `AndroidManifest.xml` — INTERNET permission

### Verify

- [ ] 4.8 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.SessionRestorerTest"`
- [ ] 4.9 `./gradlew testDebugUnitTest --tests "dev.devdigi.music.connection.ServerConnectionViewModelTest"`
- [ ] 4.10 `./gradlew testDebugUnitTest` + `./gradlew assembleDebug`

## Phase 5: Real Navidrome Validation (WU5 gated)

- [ ] 5.1 Gated, separate. Injection TBD. After WU1-WU4 merged.

## Generation 14 planning/docs remediation (review 4997606541 on 7824f8e)

Codex reviewed exact HEAD `7824f8e` and reported five fresh findings for work unit `WU1-late-review-remediation-3`. All are planning/documentation updates only; no production Kotlin, no dependencies, no tests added.

- [x] 1.27 Finding 1 (3834204845, P1): Correct exploration.md WU2 storage-model wording to the canonical layout: separate `auth_secret` Preferences DataStore persists exact username metadata + IV + ciphertext; username is non-secret binding metadata used for AAD reconstruction; password never persisted in plaintext; username stays outside ciphertext; username authenticated by AES-GCM AAD; `read(expectedEndpoint)` reads stored exact username, constructs AAD from normalized endpoint + stored username, decrypts, returns credentials only after successful GCM authentication; username remains exact/opaque/case-sensitive/Unicode-preserving/no trim/lowercase/NFC. Align design.md/tasks.md where stale.
- [x] 1.28 Finding 2 (3834204850, P1): Correct exploration.md WU3 integration matrix and all stale `#10` phrasing: `status = "ok"` is eligible for `Authenticated` only after metadata validation succeeds; `failed + error.code = 10` → `AuthProtocolError` (code 10 = required parameter missing; MUST NEVER produce `Authenticated`). Preserve: #40 → `InvalidCredentials`; #41/#42 → `UnsupportedAuthentication`; #43 → `AuthProtocolError`; #20/#30 → `IncompatibleServer`; #44 unmapped → `AuthProtocolError`; unknown failure code → `AuthProtocolError`; malformed protocol → `AuthProtocolError`; timeout/IOException → `NetworkError`.
- [x] 1.29 Finding 3 (3834204857, P2): Correct verify-report.md and apply-progress.md to report the actual executed `./gradlew testDebugUnitTest` count as **70 executed / 70 passed (0 failures, 0 errors, 0 skipped)** derived from Gradle/JUnit XML; keep WU1-scoped requirement/scenario counts (4/4, 11/11) and `implemented_scope=WU1`, `overall_change_complete=false`. The "30" figure remains only when explicitly labeled as `SubsonicResponseParserTest` focal count. (superseded by Generation 15 Finding 7: explicit scenario-to-evidence matrix shows 2/4 requirements and 9/11 scenarios executable in WU1; the 4/4 11/11 count was normative scope, not executable evidence.)
- [x] 1.30 Finding 4 (3834204860, P2): Document WON'T FIX — protocol compliance: official OpenSubsonic schema requires `error.code` (int) and makes `error.message` optional; a failed response with a valid envelope and integer `error.code` (e.g. `{"status":"failed","version":"1.16.1","error":{"code":40}}`) is protocol-valid and maps code 40 → `InvalidCredentials`; `error.message` is not required. Add protocol-rationale note to design.md.
- [x] 1.31 Finding 5 (3834204864, P1): Add Phase 2 (WU2) RED/acceptance requirement for fresh IV: every AES-GCM encryption generates a fresh random IV; IV size = 12 bytes / 96 bits; two encryptions under the same key and same plaintext produce distinct IVs and distinct ciphertexts; IV must never be constant/reused; IV uniqueness is security-critical because AES-GCM nonce reuse is forbidden. Align design.md cipher contract.

### Generation 15 remediation (review 4997606542 on 289cc63)

Codex reviewed exact HEAD `289cc63` and reported seven fresh findings. One finding (Finding 4) requires a focused WU1 production change with RED→GREEN unit tests; the remaining six are planning/documentation/spec/evidence corrections.

- [x] Finding 1 (3834346833, P2): Document WON'T FIX — OpenSubsonic protocol compliance: for a response claiming `openSubsonic: true`, the fields `type` and `serverVersion` are MANDATORY per the official subsonic-response schema; the parser correctly requires actual nonblank strings for them before yielding `Authenticated`; no defaults for missing descriptive metadata; `ServerMetadata` stays non-nullable. Added protocol-rationale note to design.md.
- [x] Finding 2 (3834346836, P2): Reconcile invalid-protocol taxonomy across spec.md, proposal.md, and exploration.md: `#20`/`#30` → `IncompatibleServer`; malformed JSON/envelope, missing/wrong-typed required protocol fields, contradictory payloads, and unknown/unmapped failure codes → `AuthProtocolError`; `#43` → `AuthProtocolError`; never describe generic "invalid protocol" as `IncompatibleServer`.
- [x] Finding 3 (3834346838, P2): Correct 400-line review-guard truthfulness across proposal.md/exploration.md/tasks.md: 400 is the normal review-budget decision threshold, not a hard repository limit; PR A/#48 has an approved cohesive size exception and is intentionally not split; later PRs should stay within the normal budget where practical or obtain their own explicit exception.
- [x] Finding 4 (3834346843, P1): RED→GREEN fail-closed parser change: a `status="ok"` envelope that also contains an `error` member is contradictory and MUST map to `AuthProtocolError`. Added `okEnvelopeWithErrorMapsToAuthProtocolError` and `okEnvelopeWithAnyErrorMemberMapsToAuthProtocolError` to `SubsonicResponseParserTest.kt`; added `root.containsKey("error")` guard in `SubsonicResponseParser.parse`.
- [x] Finding 5 (3834346845, P1): Correct exploration.md restore-policy wording and add canonical restoration behavior to design.md + tasks.md Phase 4: `Authenticated` retains credential; `InvalidCredentials` clears; `NetworkError`/`AuthProtocolError`/`UnsupportedAuthentication`/`IncompatibleServer` retain; unrecoverable crypto failure or explicit sign-out clears; `AUTHENTICATED`/identity exposed only after successful ping. Added planned WU4 tests for each case.
- [x] Finding 6 (3834346848, P1): Align PR #48 WU2 planning with PR #49 `KeyPermanentlyInvalidatedException` contract in design.md + tasks.md Phase 2: fail current op closed; delete invalidated Keystore alias; clear/conditionally invalidate ciphertext bound to old key; do NOT retry `getOrCreateKey` within the same failed op; later op may create fresh key; newly-entered credential encrypts successfully. Reference existing PR #49 tests.
- [x] Finding 7 (3834346852, P2): Build explicit WU1 scenario-to-evidence matrix and correct verify-report.md + apply-progress.md machine-readable counts: requirements 2/4, scenarios 9/11 (Req 1 2/2, Req 2 5/6 pending "Network failure" for WU3, Req 6 1/2 pending "No secret in persisted/logged artifacts" for WU4, Req 7 1/1). Preserve real Gradle evidence: testDebugUnitTest = 70 executed / 70 passed baseline; updated after Finding-4 tests.

## Review Truthfulness (PR #48 late review)

Codex reviewed exact HEAD `98c4801` and reported six fresh findings for work unit `WU1-late-review-remediation`:

- [x] Finding 1 (3833373946, P1): strict standard JSON parser — production + tests switched from `org.json` to `kotlinx-serialization-json` 1.9.0 runtime with `isLenient = false`.
- [x] Finding 2 (3833373954, P1): WU3 redirect policy added to design/tasks — no automatic redirects, signed query params must not be forwarded to another origin, 3xx → `AuthProtocolError`.
- [x] Finding 3 (3833373957, P1): WU3 request-inspection acceptance expanded in tasks — exact path, decoded query params, absence of plaintext password, fresh salts, no normalization.
- [x] Finding 4 (3833373964, P2): backup-path documentation corrected from `auth_secret.preferences_pb` to `datastore/auth_secret.preferences_pb`.
- [x] Finding 5 (3833373969, P1): WU2 endpoint/username binding acceptance expanded in tasks to match the AES-GCM AAD contract already implemented in PR #49.
- [x] Finding 6 (3833373974, P1): WU4 stale in-flight auth vs profile-change contract added to design/tasks — generation/revision check backstop, cancellation, no Mutex across network ping.
