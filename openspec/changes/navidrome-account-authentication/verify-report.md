```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:c61804fce721d1fe7591ee86f670a379c12f4ea34cfeb3044e4d466f046106dc
verdict: fail
blockers: 0
critical_findings: 0
requirements: 2/4
scenarios: 9/11
test_command: ./gradlew clean testDebugUnitTest --no-build-cache
test_exit_code: 0
test_output_hash: sha256:49413a1ffd80637418b3af5d41008be568398c06f8b55e99d39561df6b4654a7
build_command: ./gradlew assembleDebug
build_exit_code: 0
build_output_hash: sha256:585355836107977cb4894b28ebe4a2255a72b62a9733a43b4a36d34bea3f9232
```

## Verification Report

**Change**: navidrome-account-authentication
**Version**: N/A
**Mode**: Strict TDD
**Implemented scope**: WU1 / PR #48 only (planning + auth core)
**Overall change complete**: false — `navidrome-account-authentication` remains INCOMPLETE and is NOT READY TO ARCHIVE

### Completeness

| Metric | Value |
|--------|-------|
| Scope of this verification | WU1 / PR #48: Subsonic Token Signing, Authenticated Ping Result Taxonomy, Secret Boundary, Stable Account Identity |
| Requirements verified (WU1) | 2/4 |
| Scenarios verified (WU1) | 9/11 |
| Requirements remaining (WU2–WU5) | 4+ (Cryptographic Endpoint Binding, Fail-Closed Sign-In/Sign-Out, Session Restoration, Secure Secret Storage, Authenticated Network Boundary, etc.) |
| Scenarios remaining (WU2–WU5) | 7+ |

### Build & Tests Execution

**Build**: ✅ Passed
```text
./gradlew assembleDebug -> BUILD SUCCESSFUL
./gradlew lint -> BUILD SUCCESSFUL
```

**Tests**: ✅ passed
```text
./gradlew testDebugUnitTest -> BUILD SUCCESSFUL (92 executed / 92 passed; 0 failures, 0 errors, 0 skipped)
SubsonicResponseParserTest focal count: 52/52
Fresh derivation: `./gradlew clean testDebugUnitTest --no-build-cache` regenerated `app/build/test-results/testDebugUnitTest/TEST-*.xml` from scratch (10 classes; includes EndpointPolicyTest from the `app/src/testDebug/java` source set — a tracked current class, not stale XML). The 92 executed / 92 passed count is reproducible from a clean build.
```

**Coverage**: ➖ Not available

### WU1 / PR #48 Scenario-to-Evidence Matrix

The previous report claimed 4/4 requirements and 11/11 scenarios for WU1, overstating executable evidence. The matrix below maps every WU1-facing scenario to its actual executable evidence in PR #48.

| Requirement | Scenario | Executable in PR #48? | Current Test / Pending WU |
|---|---|---|---|
| **Req 1 Subsonic Token Signing** | Match the published Subsonic test vector | yes | `SubsonicAuthSignerTest` |
| **Req 1 Subsonic Token Signing** | Produce a URL-safe per-request salt | yes | `SubsonicAuthSignerTest` |
| **Req 2 Authenticated Ping Result Taxonomy** | Successful authenticated ping | yes | `SubsonicResponseParserTest.okResponseMapsToAuthenticatedWithMetadata` |
| **Req 2 Authenticated Ping Result Taxonomy** | Invalid credentials | yes | `SubsonicResponseParserTest.errorCode40MapsToInvalidCredentials` |
| **Req 2 Authenticated Ping Result Taxonomy** | Unsupported authentication scheme | yes | `SubsonicResponseParserTest.errorCode41MapsToUnsupportedAuthentication`, `errorCode42MapsToUnsupportedAuthentication` |
| **Req 2 Authenticated Ping Result Taxonomy** | Auth protocol error | yes | `SubsonicResponseParserTest.errorCode43MapsToAuthProtocolError`, strict-syntax cases |
| **Req 2 Authenticated Ping Result Taxonomy** | Incompatible server or protocol | yes | `SubsonicResponseParserTest.errorCode20MapsToIncompatibleServer`, `errorCode30MapsToIncompatibleServer`, `okWithoutOpenSubsonicMapsToIncompatibleServer`, `okWithOpenSubsonicFalseMapsToIncompatibleServer` |
| **Req 2 Authenticated Ping Result Taxonomy** | Network failure | **no** | pending WU3 (no production network client in PR #48) |
| **Req 6 Secret Boundary** | No secret leakage in credentials representation | yes | `AuthCredentialsTest` |
| **Req 6 Secret Boundary** | No secret in persisted or logged artifacts | **no** | pending WU4 (persistence/UI/logging not implemented in PR #48) |
| **Req 7 Stable Account Identity** | Identity stable across version changes | yes | `ServerAccountIdentityTest` |

**Totals**: requirements fully verified 2 of 4 (Req 1, Req 7); requirements partially verified 2 of 4 (Req 2 5/6, Req 6 1/2); scenarios executable 9 of 11; scenarios pending 2 of 11 (Network failure → WU3, No secret in persisted/logged artifacts → WU4).

### WU1 / PR #48 Requirements Partially Summarized

1. **Subsonic Token Signing** — `SubsonicAuthSigner` produces `md5(password + salt)` UTF-8 lowercase hex; per-request `SecureRandom` salt ≥ 6 hex chars; matches published Subsonic vector. (2/2 scenarios executable)
2. **Authenticated Ping Result Taxonomy** — `AuthResult` sealed variants map correctly from OpenSubsonic error codes; `reduceAuthResult` sets `ConnectionFacts` axes consistently. (5/6 scenarios executable; "Network failure" pending WU3)
3. **Secret Boundary** — `AuthCredentials.toString` redacts password; no password/token in logs/fixtures/`ServerProfile`. (1/2 scenarios executable; "No secret in persisted/logged artifacts" pending WU4)
4. **Stable Account Identity** — `ServerAccountIdentity` uses normalized endpoint + exact opaque username; `ServerMetadata` is separate and not part of identity. (1/1 scenario executable)

### Generation 15 remediation (review 4997606542 on 289cc63)

Seven fresh Codex findings: one focused WU1 production change with RED→GREEN tests, six planning/documentation/spec/evidence corrections.

1. **Finding 1 (3834346833, P2)**: Documented WON'T FIX — OpenSubsonic protocol compliance: `type` and `serverVersion` are MANDATORY for `openSubsonic: true`; `ServerMetadata` stays non-nullable.
2. **Finding 2 (3834346836, P2)**: Reconciled invalid-protocol taxonomy across spec.md/proposal.md/exploration.md; `#20`/`#30` → `IncompatibleServer`; malformed/contradictory/unmapped → `AuthProtocolError`.
3. **Finding 3 (3834346838, P2)**: Corrected 400-line review-guard truthfulness: 400 is the normal review-budget threshold, not a hard limit; PR A/#48 has an approved cohesive size exception.
4. **Finding 4 (3834346843, P1)**: RED→GREEN fail-closed parser change: `status="ok"` + explicit `error` member → `AuthProtocolError`.
5. **Finding 5 (3834346845, P1)**: Corrected restore-policy wording; documented canonical credential retention/clear behavior per `AuthResult` outcome.
6. **Finding 6 (3834346848, P1)**: Aligned WU2 planning with PR #49 `KeyPermanentlyInvalidatedException` contract.
7. **Finding 7 (3834346852, P2)**: Built explicit scenario-to-evidence matrix; corrected machine-readable counts to requirements 2/4, scenarios 9/11.

### Issues Found

**CRITICAL**: None
**WARNING**: None
**SUGGESTION**: None

### Generation 18 remediation (review 5000298986 on 12e601e)
- Finding A: failed-envelope present-but-malformed OpenSubsonic metadata fails closed — `openSubsonic` present must be actual Boolean, `type`/`serverVersion` present must be actual nonblank Strings, else AuthProtocolError; absence stays compatible with legacy failed responses; failed + code 40 without malformed metadata remains InvalidCredentials. RED: failedWithWrongTypedOpenSubsonic/Type/ServerVersionMapsToAuthProtocolError (failed pre-change), GREEN.
- Finding B size: MAX_AUTH_RESPONSE_CHARS = 65_536 (64 KiB, power of two), checked BEFORE parseToJsonElement; oversized -> AuthProtocolError (RED oversizedResponseMapsToAuthProtocolError, GREEN).
- Finding B depth: controlled probe of kotlinx-serialization-json 1.9.0 demonstrated an unhandled StackOverflowError for ~20k/30k nested arrays (~40-60 KiB, INSIDE the size bound). MAX_AUTH_RESPONSE_DEPTH = 128 with a pre-parse O(n)/O(1) structural scan (ignores braces/brackets inside strings, handles escaped quotes/backslashes); depth > 128 -> AuthProtocolError. No StackOverflowError/Error catch was added. RED: deeplyNestedResponseMapsToAuthProtocolError (failed pre-guard), GREEN; regression: highDepthRegressionMapsToAuthProtocolError (10k nesting) returns AuthProtocolError safely; boundary/string/escape tests pass.
- WU3 planning (tasks.md 3.3b): transport byte-bound before String materialization; layered defenses (transport byte bound; parser char bound 64 KiB + depth bound 128).


### Generation 20 remediation (review 5000298986 follow-up on cb6a911)
- Finding A: duplicate JSON object member names rejected BEFORE parseToJsonElement via a security lexical pre-scan (per-object seen-key sets on a frame stack; strings/escapes ignored; escape-equivalent keys decoded through strictJson so `status` vs `sta\u0074us` collide). Duplicate status/openSubsonic/error.code and escape-equivalent duplicates -> AuthProtocolError (RED: duplicateStatusKeysMapToAuthProtocolError, duplicateOpenSubsonicKeysMapToAuthProtocolError, duplicateErrorCodeKeysMapToAuthProtocolError, escapeEquivalentDuplicateKeysMapToAuthProtocolError — all failed pre-change, GREEN). Controls: same key in different objects allowed (sameKeyInDifferentObjectsIsAllowed), key-like text inside strings ignored (keyLikeTextInsideStringIsIgnored). Ordering: size -> depth -> duplicate keys -> strict parse -> semantic.
- Finding B: error.message remains OPTIONAL but when PRESENT must be an actual JSON String (blank allowed). Non-string/null message -> AuthProtocolError (RED: failedWithNumeric/Boolean/Object/NullErrorMessageMapsToAuthProtocolError — all failed pre-change, GREEN). Blank message remains InvalidCredentials (blankErrorMessageRemainsInvalidCredentials); absent message and String message remain InvalidCredentials.
- Fresh clean evidence: 92 executed / 92 passed / 10 classes; parser focal 52/52.


### Verdict

PASS for WU1 / PR #48 (merged into develop) and WU2 secure-secret-storage (implemented in PR #49). Normative executable scope: 2/4 requirements fully verified, 9/11 scenarios executable.

The overall `navidrome-account-authentication` change is **INCOMPLETE**: WU3 (authenticated network boundary), WU4 (session/UI), and WU5 (gated real-Navidrome validation) remain unimplemented. Do NOT archive this OpenSpec change until WU1–WU5 are verified.

### WU2 implementation evidence (PR #49, after ancestry integration)

### Gen 10 remediation evidence (this attempt)
ONE new Codex finding (WU2-review-remediation-4, evidence_goal `verified-wu2-initial-read-stale-credential-finding`) implemented RED -> GREEN:
- `DataStoreAuthSecretStore.save()` now distinguishes a successfully-read snapshot from an unknown snapshot (initial read failure) via a `snapshotRead` flag.
- If the snapshot WAS read: the existing conditional cleanup (`clearIfSnapshotStillMatches`) is preserved (clear only if current stored username/payload still match the captured snapshot).
- If the initial snapshot read FAILED: best-effort UNCONDITIONAL credential clear runs under the existing process-wide shared coroutine Mutex (already held by save). A private no-lock helper `clearCredentialsNoLock()` is shared by both the public `clear()` and the unknown-snapshot save path — no second lock, no reentrant non-reentrant-Mutex call (no deadlock).
- Error semantics: the ORIGINAL initial-read/storage failure remains the externally observed `Result.failure`; an ordinary unconditional-cleanup exception is attached as suppressed; `CancellationException` from cleanup still propagates.

Preserved guarantees: process-wide shared Mutex serialization across wrappers; conditional snapshot cleanup when snapshot IS known; original failure preserved when cleanup fails; cleanup CancellationException propagates; encrypt/decrypt KeyPermanentlyInvalidatedException deletes alias + fail-closed + no same-operation retry; AES-GCM endpoint+exact username AAD binding; opaque case-sensitive Unicode-preserving username; ProviderException fail-closed; corruption recovery; backup exclusions; password/token redaction; no stale previous credential after failed replacement.

### Issues Found
**CRITICAL**: None
**WARNING**: None
**SUGGESTION**: None




### Preserved invariants

- Strict standard JSON parsing (`kotlinx-serialization-json`, `isLenient = false`)
- Exact opaque case-sensitive Unicode-preserving username
- Normalized endpoint + endpoint base path preservation
- `MD5(password + salt)` lowercase hex
- Fresh `SecureRandom` salt
- `AuthCredentials` / `AuthSignature` redaction
- No token/salt persistence
- Fail-closed taxonomy
- AES-GCM AAD endpoint+username (planned for WU2)
- Backup exclusion `datastore/auth_secret.preferences_pb`
- `server_profile` not excluded
- `compileSdk` / `targetSdk` 35
- No OkHttp / MockWebServer / INTERNET added in PR #48
