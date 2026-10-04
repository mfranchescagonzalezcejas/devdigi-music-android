# Real Navidrome First Sound Validation Specification

## Requirement: local automated real-instance execution

The #17 validation MUST be runnable locally against a real user-provided
Navidrome/OpenSubsonic instance without routine manual screen interaction.

### Scenario: automated execution

**Given** a compatible Android device is connected and unlocked

**And** valid runtime-only server credentials are supplied

**When** the #17 runner executes

**Then** it MUST drive the required First Sound scenarios automatically

**And** it MUST NOT require coordinate-based manual tapping.

## Requirement: real credentials remain runtime-only

Real-instance connection and authentication material MUST NOT become committed
configuration or public evidence.

### Scenario: runtime input

**Given** real endpoint and account credentials are required

**When** the runner receives them

**Then** they MUST NOT be printed

**And** they MUST NOT be committed

**And** any temporary app-private representation MUST be deleted after use.

## Requirement: device identity remains private

Device serials MUST NOT become validation evidence.

### Scenario: connected device

**Given** the runner selects an Android device

**Then** the serial MAY be used internally for ADB routing

**But** it MUST NOT be printed or committed.

## Requirement: dynamic private-library selection

Validation MUST select media by capability rather than by hard-coded private
metadata.

### Scenario: qualifying recent album

**Given** Recent Albums contains one or more albums

**When** candidate discovery runs

**Then** it MUST choose the first candidate with at least three tracks and at
least one FLAC track

**And** it MUST NOT publish the candidate album, artist, track names or ids.

### Scenario: no qualifying album

**Given** no Recent Albums entry satisfies the candidate properties

**Then** FLAC/queue validation MUST be BLOCKED

**And** it MUST NOT silently select a weaker hard-coded fixture.

## Requirement: privacy-safe UI selectors

Automation selectors MUST NOT encode real account or media metadata.

### Scenario: positional selector

**Given** a Recent Album or track requires a stable automation selector

**Then** a role/position tag MAY be used

**And** server, account, album and track identifiers MUST NOT be embedded in
the tag.

## Requirement: real FLAC playback

The validator MUST prove that a FLAC track selected from a qualifying recent
album reaches active Media3 playback.

### Scenario: FLAC selection

**Given** the privacy-safe probe identifies the FLAC track position

**When** the corresponding app track is selected

**Then** Media3 playback MUST become active

**And** no stream URL or private media metadata may be emitted as evidence.

## Requirement: queue behavior

The validator MUST exercise the service-owned queue with at least three album
tracks.

### Scenario: queue navigation

**Given** a qualifying album has populated the playback queue

**When** the validator uses Next and Previous

**Then** queue selection MUST advance and retreat consistently

**And** assertions MUST NOT depend on publishing private track names.

## Requirement: background playback

Active playback MUST survive Activity backgrounding as defined by the current
Media3 service contract.

### Scenario: background

**Given** a real track is actively playing

**When** the app Activity is backgrounded

**Then** service-owned playback MUST remain active.

## Requirement: Android system media controls

Standard Android media transport commands MUST control the same service-owned
player.

### Scenario: system pause and resume

**Given** playback continues in background

**When** Android system media transport issues Pause and Play

**Then** the app MUST reflect the corresponding paused and playing states.

### Scenario: system queue navigation

**Given** a multi-track queue is active

**When** Android system transport issues Next or Previous

**Then** the same service-owned queue MUST respond consistently.

## Requirement: sign-out isolation

Sign-out MUST remove account-bound runtime and presentation state.

### Scenario: sign out after playback

**Given** an authenticated identity owns loaded library, navigation, playback
and runtime queue state

**When** sign-out succeeds

**Then** prior account-bound library/navigation state MUST no longer be
visible

**And** prior runtime playback/queue ownership MUST no longer be exposed.

## Requirement: second-identity validation

When a second real identity is supplied, the validator MUST exercise an actual
account switch.

### Scenario: account A to account B

**Given** identity A has loaded library and playback state

**When** A signs out and identity B signs in

**Then** B MUST NOT inherit A library state

**And** B MUST NOT inherit A runtime queue

**And** B MUST NOT resume A playback.

## Requirement: sanitized evidence

Committed validation results MUST contain only safe status information.

### Scenario: successful validation

**When** the complete real-instance run succeeds

**Then** evidence MUST contain fixed PASS labels

**And** MUST confirm private metadata was not recorded

**And** MUST NOT contain real endpoint, account, device or library data.

## Requirement: #34 and #104 remain separate

Issue #17 MUST NOT absorb unrelated testing infrastructure.

### Scenario: local instrumentation

**Given** #17 requires Android instrumentation locally

**Then** it MAY add the minimum feature-specific test support

**But** Jenkins instrumented execution remains #34

**And** the reusable QA/BDD framework remains #104.
