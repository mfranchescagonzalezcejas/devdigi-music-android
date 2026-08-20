## Exploration: navidrome-server-connection — #13 persistence and debug local policy amendment

### Current State
`navidrome-server-connection` is still an active OpenSpec change, not an archived one. Its completed artifacts and current uncommitted implementation deliberately keep `ServerProfile` in memory, reject local hosts, require HTTPS, and declare persistence out of scope. The application has one module, `BuildConfig` enabled, no DataStore dependency, no debug/release source-set content, and no network-security configuration. The main manifest also has no network permission.

The user-approved #13 extension changes two original boundaries: `ServerProfile` must now survive process restart without secrets, and a local endpoint is permitted only through an explicit debug-only policy. This remains server-connection configuration, not #16 account-scoped persistence: the persisted record is only the normalized endpoint. Current AndroidX documentation supports a context-owned Preferences DataStore delegate, `stringPreferencesKey`, atomic `edit`, and `data` as a `Flow`; Android build variants support debug/release source-set-specific manifests and code.

The current worktree contains the completed #13 implementation and all active-change artifacts. No Compose/Docker files are present in the repository. The native review authority is valid and has approved lineages, but the read-only status does not establish which approved lineage belongs to this change.

### Affected Areas
- `app/src/main/java/dev/devdigi/music/connection/ServerConnection.kt` — retain the immutable, non-secret profile and make endpoint validation invoke the build-variant endpoint policy.
- `app/src/main/java/dev/devdigi/music/connection/ServerProfileRepository.kt` — add one concrete Preferences DataStore-backed repository that persists only the normalized endpoint string and reparses it on read.
- `app/src/main/java/dev/devdigi/music/connection/ServerConnectionViewModel.kt` and `MainActivity.kt` — load the persisted profile and save only after successful confirmation, using a small factory for repository injection.
- `app/src/debug/` and `app/src/release/` — provide mutually exclusive endpoint-policy implementations; debug permits only the approved Android Emulator alias while release permits HTTPS only.
- `app/src/debug/AndroidManifest.xml` and `app/src/debug/res/xml/` — add a debug-only network security configuration that denies cleartext by default and permits it only for the approved Android Emulator alias. Release keeps no cleartext override.
- `gradle/libs.versions.toml` and `app/build.gradle.kts` — add the AndroidX Preferences DataStore dependency; `BuildConfig` is already enabled.
- `app/src/test/java/dev/devdigi/music/connection/` — add synthetic persistence/restart, malformed-stored-value, release-policy, and debug-allowlist tests. Use only `https://music.example.com` plus the approved Android Emulator alias where a debug policy test requires it.
- `openspec/changes/navidrome-server-connection/{proposal.md,specs/navidrome-server-connection/spec.md,design.md,tasks.md,verify-report.md}` — amend before reapplying and re-verifying the active change.

### Approaches
1. **Amend the active #13 change** — add durable endpoint-only persistence and the debug-only local-policy requirement to the existing proposal/spec/design/tasks, then reopen verification.
   - Pros: extends the exact `ServerProfile` and endpoint boundary already implemented; maintains the no-secret/#14 boundary; avoids splitting one user-visible configuration flow across changes.
   - Cons: supersedes the original no-persistence decision and requires renewed verification.
   - Effort: Medium.

2. **Create a separate persistence/debug-policy change** — leave #13 archived as originally specified and add a new cross-cutting change.
   - Pros: preserves the original artifact history unchanged.
   - Cons: artificially separates save/restore and the policy from the only profile that needs them; duplicates UI and validation hand-off work.
   - Effort: Medium.

### Recommendation
Amend `navidrome-server-connection`; do not archive it yet. Use one concrete `ServerProfileRepository` around `DataStore<Preferences>`, with one endpoint string key, `Flow<ServerProfile?>` for restore, and a suspend save after normalization. Do not introduce a generic storage interface or persist credentials, identity, authentication, facts, or raw input. Test it with a temporary Preferences DataStore: save a synthetic profile, reopen/read it, and verify malformed stored data yields no profile; retain the existing pure ViewModel/parser tests.

For endpoint policy, keep release HTTPS-only. Prefer variant-specific source-set policy code over a runtime flag: the release implementation never admits a local endpoint, while the debug implementation admits only the approved Android Emulator alias and only the explicit local form. Pair it with a debug-only network-security configuration whose base policy denies cleartext and whose sole exception is that alias. `BuildConfig` is already generated and needs no custom field. This is an implementation option to be checked against the target Android API behavior during apply; it must not broaden release cleartext.

A local Navidrome Compose/Docker environment is not #13 product scope. It introduces container lifecycle, media mounting, and real-instance validation; create a separate dev-environment issue, coordinated with #17 if it is used for end-to-end validation. Do not use `/path/to/your/music` until that issue explicitly authorizes environment setup.

The native review binding cannot be completed in this read-only exploration, but a documented safe resolution exists: bind this change only to the maintainer-identified, already-approved matching lineage using `gentle-ai review bind-sdd` with `--change`, `--lineage`, and the known `--expected-binding-revision`. The status command confirms valid authority and approved lineages but supplies no evidence that any one is this change; no binding, receipt, control change, or invented value is justified now.

### Risks
- A debug cleartext exception that is wider than the approved Android Emulator alias would weaken the local-only policy; release must retain both HTTPS validation and no cleartext override.
- Saving raw input, facts, or future credentials would violate the non-secret profile boundary; persist only the normalized endpoint and revalidate on load.
- The active worktree is already modified, so the amendment must be verified against the current implementation rather than assumed from the earlier pass report.
- A Docker/Compose setup would cross into private media and infrastructure operations and must remain a separately approved dev-environment change.

### Ready for Proposal
Yes — amend the existing #13 OpenSpec artifacts with the two approved requirements, then run design/tasks/apply/verify again. Create a separate issue for local Navidrome Docker/Compose infrastructure. Resolve the native review binding only after a maintainer identifies the matching approved lineage and revision.
