# Design: Navidrome Server Connection

## Technical Approach

Amend the existing `:app` vertical slice to persist only a normalized `ServerEndpoint`. `ServerEndpoint.parse` remains pure but receives the build-variant `EndpointPolicy`. A concrete AndroidX Preferences DataStore repository exposes one `Flow<ServerProfile?>` behind the small interface injected into `ServerConnectionViewModel`; no module, DI framework, generic storage layer, secret model, or network client is added. This follows the amended proposal and delta spec, which require endpoint persistence and explicit debug/release transport-admission policies.

## Architecture Decisions

| Topic | Choice | Rejected / tradeoff | Rationale |
|---|---|---|---|
| Persistence | `DataStore<Preferences>` with one `stringPreferencesKey("server_endpoint")` | SharedPreferences, database, generic key/value repository | DataStore already provides serialized `Flow` reads and atomic `edit`; the endpoint is non-secret and needs no schema. |
| Test seam | `ServerProfileRepository` with observe/save/delete only | Mocking DataStore or adding storage/domain layers | A `MutableStateFlow` fake gives deterministic ViewModel tests while production keeps one concrete endpoint-only implementation. |
| Validation | Reparse every emitted stored string with the active `EndpointPolicy`; invalid values restore as `null` | Trust stored bytes or persist serialized models | Stored values can be stale or malformed; reparsing preserves current variant rules without migration machinery. |
| Variants | Shared policy contract; same `BuildVariantEndpointPolicy` symbol implemented in `debug` and `release` source sets | `BuildConfig.DEBUG` branches | Mutually exclusive source sets prevent debug allowances from entering release logic. |

## Data Flow

```text
DataStore.data ──> reparse ──> Flow<ServerProfile?> ──> ViewModel state ──> Screen
Screen confirm ──> parse ──> repository.save ──> atomic key replacement ──┘
Screen delete ─────────────────> repository.delete ──> atomic key removal ──┘
```

The ViewModel collects restoration in `viewModelScope`. A valid confirmation saves the normalized endpoint; the resulting Flow emission restores or replaces the visible profile. Deletion removes the key and the `null` emission clears profile/input state. Malformed stored text is discarded from restored state. Repository write failures propagate to the ViewModel and must not produce a false saved/deleted state.

## File Changes

| File | Action | Description |
|---|---|---|
| `app/src/main/java/dev/devdigi/music/connection/ServerConnection.kt` | Modify | Accept `EndpointPolicy`; keep structural parsing/profile pure. |
| `app/src/main/java/dev/devdigi/music/connection/ServerProfileRepository.kt` | Create | Interface, one-key DataStore implementation, and application-context DataStore delegate. |
| `app/src/main/java/dev/devdigi/music/connection/ServerConnectionViewModel.kt` | Modify | Inject repository; collect restoration and launch save/delete. |
| `app/src/main/java/dev/devdigi/music/connection/ServerConnectionScreen.kt` | Modify | Expose delete action and Flow-restored state. |
| `app/src/main/java/dev/devdigi/music/MainActivity.kt` | Modify | Construct repository and minimal ViewModel factory. |
| `app/src/debug/java/dev/devdigi/music/connection/BuildVariantEndpointPolicy.kt` | Create | Permit HTTPS public hosts and HTTP only for `localhost`, `127.0.0.1`, `10.0.2.2`. |
| `app/src/release/java/dev/devdigi/music/connection/BuildVariantEndpointPolicy.kt` | Create | Permit safe HTTPS only. |
| `app/src/debug/AndroidManifest.xml` | Create | Reference debug network security config. |
| `app/src/debug/res/xml/network_security_config.xml` | Create | Deny base cleartext; allow exact local hosts only, without subdomains. |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modify | Add Preferences DataStore and coroutine-test aliases. |
| `app/src/test/java/dev/devdigi/music/connection/ServerProfileRepositoryTest.kt` | Create | Temporary-file DataStore tests. |
| `app/src/test/java/dev/devdigi/music/connection/ServerConnectionViewModelTest.kt` | Modify | Flow restoration, replacement, deletion, and write-failure tests with a fake. |
| `app/src/testDebug/java/dev/devdigi/music/connection/EndpointPolicyTest.kt` | Create | Debug allowlist/rejection cases. |
| `app/src/testRelease/java/dev/devdigi/music/connection/EndpointPolicyTest.kt` | Create | Release HTTP rejection cases. |

## Interfaces / Contracts

```kotlin
interface ServerProfileRepository {
    val profile: Flow<ServerProfile?>
    suspend fun save(profile: ServerProfile)
    suspend fun delete()
}
```

`save` assigns the single string key inside `DataStore.edit`; repeated saves replace it atomically. `delete` removes that key in `edit`. Nothing credential-bearing is accepted or stored.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Parsing and both policy implementations | Synthetic URLs; run `testDebugUnitTest` and `testReleaseUnitTest`; no sockets. |
| Ping state | Synthetic `PingClient` outcomes and independent verification facts | Use a fake `PingClient` to cover reachable, transport-error, unexpected, and unauthenticated outcomes; compatibility and authentication remain `NOT_CHECKED`. |
| Repository | Restore, replace, delete, malformed values, one-key payload | `PreferenceDataStoreFactory`, temporary file, `runTest`/`backgroundScope`. |
| ViewModel | Initial restoration and Flow-driven replacement/deletion; failures | `MutableStateFlow` fake repository and coroutine test dispatcher. |
| E2E/network | None in #13 | No `INTERNET` permission, HTTP client, or requests. |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable classification, or process-integration boundary.

## Migration / Rollout

No migration required. The debug-only manifest overlay is absent from release; release retains target-SDK cleartext denial and its HTTPS-only policy. The narrow config only prepares debug transport policy—it adds neither permission nor client, so it does not enable #13 networking. Local Navidrome Docker/Compose remains a separate dev-infrastructure issue.

## Open Questions

- [ ] Review binding remains blocked until a maintainer supplies approved lineage and expected revision; implementation must remain unbound.
