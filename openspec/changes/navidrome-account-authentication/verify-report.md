```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:c444fda47088911bbbf941b41c13b5ca6aa35beefb7ba01f4e2735b7e42f5741
verdict: pass
blockers: 0
critical_findings: 0
requirements: 4/4
scenarios: 11/11
test_command: ./gradlew testDebugUnitTest
test_exit_code: 0
test_output_hash: sha256:73a21cb1c6d866e188032d7333419c92f6210e6cb4c63539f3848d7e721ac695
build_command: ./gradlew assembleDebug
build_exit_code: 0
build_output_hash: sha256:71e8f7ad00dd3b7b6aea467cb27408072f94cc310e4f1d091f1aa4d3a5f9518e
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
| Requirements verified (WU1) | 4/4 |
| Scenarios verified (WU1) | 11/11 |
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
./gradlew testDebugUnitTest -> BUILD SUCCESSFUL (70 executed / 70 passed; 0 failures, 0 errors, 0 skipped)
SubsonicResponseParserTest focal count: 30/30
```

**Coverage**: ➖ Not available

### WU1 / PR #48 requirements verified

1. **Subsonic Token Signing** — `SubsonicAuthSigner` produces `md5(password + salt)` UTF-8 lowercase hex; per-request `SecureRandom` salt ≥ 6 hex chars; matches published Subsonic vector.
2. **Authenticated Ping Result Taxonomy** — `AuthResult` sealed variants map correctly from OpenSubsonic error codes; `reduceAuthResult` sets `ConnectionFacts` axes consistently.
3. **Secret Boundary** — `AuthCredentials.toString` redacts password; no password/token in logs/fixtures/`ServerProfile`.
4. **Stable Account Identity** — `ServerAccountIdentity` uses normalized endpoint + exact opaque username; `ServerMetadata` is separate and not part of identity.

### Generation 13 planning/docs remediation (review 4997606540 on 0f85153)

Eight fresh Codex findings were dispositioned as planning/documentation updates ONLY; no production code was modified:

1. **Finding 1 (3833865653, P1)**: Endpoint base path preservation added to WU3 planning (tasks.md Phase 3 + design.md); deterministic MockWebServer acceptance cases documented.
2. **Finding 2 (3833865657, P1)**: Username-before-AAD-decryption contract clarified in design.md: username is non-secret binding metadata, stored outside ciphertext, used in AAD, exact/opaque/case-sensitive/Unicode-preserving.
3. **Finding 3 (3833865663, P1)**: WU4 TOCTOU contract strengthened: short shared critical section / orchestration mutex covers final generation/profile validation + state commit; network request remains outside mutex.
4. **Finding 4 (3833865670, P2)**: Delivery strategy made consistent across tasks.md, design.md, exploration.md, proposal.md as canonical chained PRs (A/#48, B/#49, C, D, WU5 gated); stale single-pr / alternate split statements labeled superseded.
5. **Finding 5 (3833865674, P1)**: WON'T FIX AS MANDATORY POST rationale documented: baseline authenticated ping uses query params (u/t/s/v/c/f) required before extension discovery; HTTPS mandatory; redirects disabled; no logging interceptor; redacted `AuthSignature.toString`; fresh salt per request.
6. **Finding 6 (3833865679, P1)**: WU4 fail-closed sign-out contract strengthened: sign-out MUST NOT report success if durable recoverable credential remains; `clear()` must succeed; non-secret error/retry state on failure.
7. **Finding 7 (3833865681, P1)**: verify-report.md scoped to implemented WU1/PR #48 work only; overall change explicitly marked INCOMPLETE / NOT READY TO ARCHIVE.
8. **Finding 8 (3833865684, P1)**: WU3 planning requires rejecting non-2xx HTTP responses before OpenSubsonic JSON parsing; non-2xx with valid success envelope → `AuthProtocolError`; timeout/IOException → `NetworkError`; 3xx → `AuthProtocolError`.

### Issues Found

**CRITICAL**: None
**WARNING**: None
**SUGGESTION**: None

### Verdict

PASS for WU1 / PR #48 (4/4 requirements, 11/11 scenarios).

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
