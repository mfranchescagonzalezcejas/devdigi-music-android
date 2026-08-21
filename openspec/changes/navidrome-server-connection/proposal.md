# Proposal: Navidrome Server Connection

## Intent

Let a user keep a safe Navidrome/OpenSubsonic server endpoint across restarts without storing secrets or claiming a server is connected. Establish the server-only boundary that #14 will authenticate.

## Scope

### In Scope
- Validate and normalize endpoints while preserving reverse-proxy paths; reject user-info, query, fragments, and disallowed hosts.
- Persist only the normalized endpoint through a small `ServerProfileRepository`; restore, replace, and delete the profile. Reparse stored values and discard malformed data.
- Keep immutable, non-secret `ServerProfile` strictly separate from future credential-bearing `AccountProfile`.
- Enforce variants: release accepts HTTPS only; debug may accept HTTP only for `localhost`, `127.0.0.1`, and Android Emulator alias `10.0.2.2` where applicable—never arbitrary remote HTTP.
- Cover persistence and each variant policy with synthetic, testable cases.

### Out of Scope
- Credentials, token/salt generation, secure storage, identity, account persistence, library access, and playback.
- Authenticated `ping`, reachability, compatibility, and login; these remain #14.
- Docker/Compose Navidrome bootstrap or files; local dev infrastructure is a separate issue.

## Capabilities

### New Capabilities
- `navidrome-server-connection`: Configure a normalized non-secret server endpoint and express connection-verification facts safely.

### Modified Capabilities
None; `openspec/specs/` contains no existing capabilities.

## Approach

Keep parsing and normalization pure. Use one concrete endpoint-only repository, not generic storage; save after valid confirmation and expose restore/delete. Use mutually exclusive debug/release endpoint-policy implementations plus a debug-only narrow cleartext exception; release has no cleartext override. #14 supplies authenticated OpenSubsonic requests and may establish reachability, compatibility, and login.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `app/src/main/java/dev/devdigi/music/connection/` | Modified | Endpoint-only profile repository and restore/save/delete flow. |
| `app/src/debug/`, `app/src/release/` | New | Variant-specific endpoint policy implementations. |
| `app/src/debug/AndroidManifest.xml`, `app/src/debug/res/xml/` | New | Debug-only narrow cleartext policy. |
| `app/src/test/java/dev/devdigi/music/connection/` | Modified | Persistence, malformed-data, and variant-policy tests. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Debug HTTP policy broadens | Medium | Explicit host allowlist, variant-specific enforcement, and tests. |
| Secrets enter persisted data | Low | Store only normalized endpoint; keep `AccountProfile` deferred. |
| Native review binding is unavailable | High | Maintainer must provide approved lineage and expected revision; remain unbound. |

## Rollback Plan

Remove the repository and variant policy, delete the stored endpoint, and restore the prior in-memory HTTPS-only flow. No credentials or account data require migration.

## Dependencies

- #14 must provide credentials and authenticated request construction before `ping`, reachability, compatibility, or login can complete.
- Native review binding remains unresolved pending maintainer-provided approved lineage and revision.

## Success Criteria

- [ ] A normalized endpoint survives restart; restore, replacement, deletion, and malformed stored values are covered by synthetic tests.
- [ ] Release rejects all HTTP; debug admits HTTP only for the explicit local allowlist and rejects remote HTTP.
- [ ] #13 makes no authenticated `ping`, reachability, compatibility, or login claim; #14 owns those outcomes.
