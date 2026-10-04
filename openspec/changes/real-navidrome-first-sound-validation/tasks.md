# Tasks: Real Navidrome First Sound Validation

## WU0 — contract and automation design

- [x] Confirm #17 follows completed account-isolation issue #16.
- [x] Confirm exact `develop` base.
- [x] Reframe the real-instance checklist as local opt-in automation.
- [x] Confirm `AndroidJUnitRunner` already exists.
- [x] Confirm no `app/src/androidTest` source set currently exists.
- [x] Confirm Compose UI/UIAutomator test dependencies are not configured.
- [x] Preserve #34 as the owner of instrumented CI.
- [x] Preserve #104 as the owner of the reusable QA/BDD harness.
- [x] Preserve #35 as the synthetic Navidrome integration environment.
- [x] Define privacy-safe runtime credential transport.
- [x] Define device-serial privacy constraints.
- [x] Define stable semantic-selector rules.
- [x] Define dynamic Recent Albums candidate selection.
- [x] Require at least 3 tracks for queue validation.
- [x] Require a FLAC track for the real playback proof.
- [x] Avoid expanding the product `AlbumTrack` model solely for test codec data.
- [x] Define background/system-media validation without metadata dumps.
- [x] Define one-identity sign-out validation.
- [x] Define optional second-identity account-switch validation.
- [x] Define sanitized PASS/FAIL/BLOCKED evidence.
- [x] Define implementation WU boundaries.
- [x] Keep WU0 documentation-only and below 1000 changed lines.

WU0 MUST NOT add Android test source, Gradle test dependencies, runner scripts,
Jenkins changes or production Kotlin changes.

## WU1 — local runner and instrumentation foundation

- [x] Add the minimum Android instrumentation dependencies.
- [x] Add a targeted real-instance `androidTest` entry point.
- [x] Add connected-device preflight without printing device serials.
- [x] Add privacy-safe runtime input prompting.
- [x] Transfer runtime data without embedding secrets in command arguments.
- [x] Delete temporary app-private runtime input on success and failure.
- [x] Drive app launch and BYON authentication automatically.
- [x] Emit only sanitized authentication/connection evidence.
- [x] Do not modify Jenkins.
- [x] Run applicable Android gates.
- [x] Keep WU1 below 1000 changed lines.

## WU2 — dynamic media, FLAC and queue validation

- [x] Add the narrow privacy-safe real-server media candidate probe.
- [x] Reuse production authentication/transport boundaries where practical.
- [x] Add only the semantic selectors required for deterministic UI driving.
- [x] Ensure selectors contain no private server/account/media identifiers.
- [x] Select the first Recent Album with at least 3 tracks and a FLAC track.
- [x] Return BLOCKED when no qualifying candidate exists.
- [x] Open the dynamically selected album through the UI.
- [x] Select the identified FLAC track through the UI.
- [x] Prove active Media3 playback.
- [x] Prove service-owned queue Next/Previous behavior across at least 3 tracks.
- [x] Emit no private album/artist/track/id/stream evidence.
- [x] Run applicable Android gates.
- [x] Keep WU2 below 1000 changed lines.

## WU3 — background, system controls and account isolation

- [ ] Background the Activity automatically.
- [ ] Prove playback continues in the service.
- [ ] Exercise Android system Pause/Play without publishing media metadata.
- [ ] Exercise Android system Next/Previous against the same queue.
- [ ] Return to the app and verify service/UI state reconciliation.
- [ ] Sign out automatically.
- [ ] Prove account-bound library/navigation state is cleared.
- [ ] Prove runtime playback/queue ownership is cleared.
- [ ] Support an optional second real identity.
- [ ] When supplied, prove identity B does not inherit A state.
- [ ] Keep missing optional second identity explicit rather than faking it.
- [ ] Run applicable Android gates.
- [ ] Keep WU3 below 1000 changed lines.

## WU4 — real execution and closeout

- [ ] Run the complete suite against the user-provided real instance.
- [ ] Record only sanitized PASS/FAIL/BLOCKED results.
- [ ] Confirm no endpoint/account/device/library metadata entered evidence.
- [ ] If a deterministic bug appears, add synthetic regression coverage first.
- [ ] Retry fixed scenarios against the real instance.
- [ ] Reconcile architecture/security/QA documentation if required.
- [ ] Verify #34 scope did not enter #17.
- [ ] Verify #104 general QA/BDD scope did not enter #17.
- [ ] Keep #17 below its approved product/testing scope.
- [ ] Run final applicable validation.
- [ ] Keep WU4 below 1000 changed lines.

## Follow-up

Useful local device scenarios discovered here may inform #34 and #104 later,
but #17 does not implement their broader infrastructure.
