```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:66e4043136ac9d4a74a9a6e7e838477dfdb090d3bd00260efd128941e8d48abb
verdict: fail
blockers: 0
critical_findings: 0
requirements: 2/4
scenarios: 9/11
test_command: ./gradlew testDebugUnitTest
test_exit_code: 0
test_output_hash: sha256:8efeca9f21b0dbf0a75b06fa70c7fe3e308b18dc1d6100e7141eb4fca03ad343
build_command: ./gradlew assembleDebug
build_exit_code: 0
build_output_hash: sha256:21b8f9ad126060931a8b0b2f8bbc44f1f9a158edced6b111465d49a90537ca43
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
./gradlew testDebugUnitTest -> BUILD SUCCESSFUL (72 executed / 72 passed; 0 failures, 0 errors, 0 skipped)
SubsonicResponseParserTest focal count: 32/32
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

### Verdict

PASS for WU1 / PR #48 (2/4 requirements fully verified, 9/11 scenarios executable).

The overall `navidrome-account-authentication` change is **INCOMPLETE**: WU2, WU3, WU4, and WU5 remain unimplemented in PR #48. Do NOT archive this OpenSpec change until WU1–WU5 are verified.

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
