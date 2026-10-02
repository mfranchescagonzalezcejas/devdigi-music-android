# Minimal Persistent Playback Queue Specification

## Requirement: pure queue domain

Queue behavior MUST remain independent from Android, Media3, DataStore and
networking types.

### Scenario: empty queue

**Given** an empty queue

**Then** it MUST have no current index.

### Scenario: non-empty queue

**Given** a valid non-empty queue

**Then** its current index MUST identify exactly one entry.

## Requirement: deterministic replacement

Replacing a queue MUST preserve supplied entry order and selected index.

### Scenario: selected album track

**Given** ordered album tracks A, B and C

**And** B is selected

**When** the queue is replaced

**Then** the queue MUST contain A, B and C in that order

**And** B MUST be current.

## Requirement: deterministic append

Appending MUST preserve current selection.

### Scenario: append to active queue

**Given** A and B with A current

**When** C is appended

**Then** the queue MUST contain A, B and C

**And** A MUST remain current.

### Scenario: append to empty queue

**Given** an empty queue

**When** A and B are appended

**Then** A MUST become current.

## Requirement: non-wrapping navigation

Next and previous MUST NOT wrap.

### Scenario: next at final entry

**Given** the final entry is current

**When** next is requested

**Then** the current entry MUST remain unchanged.

### Scenario: previous at first entry

**Given** the first entry is current

**When** previous is requested

**Then** the current entry MUST remain unchanged.

## Requirement: deterministic removal

Removal MUST preserve a valid current selection.

### Scenario: remove current with successor

**Given** A, B and C with B current

**When** B is removed

**Then** A and C MUST remain

**And** C MUST become current.

### Scenario: remove final current entry

**Given** A and B with B current

**When** B is removed

**Then** A MUST become current.

### Scenario: remove only entry

**Given** A is the only queue entry

**When** A is removed

**Then** the queue MUST become empty.

## Requirement: account-scoped persistence

A durable queue MUST restore only for the server-plus-user identity that owns
it.

Raw endpoint and username MUST NOT be stored in the durable snapshot.

### Scenario: matching identity

**Given** a queue persisted for account A

**When** account A reconciles

**Then** that queue MAY be restored.

### Scenario: different identity

**Given** a queue persisted for account A

**When** account B reconciles

**Then** account A's queue MUST NOT be restored or played.

## Requirement: secret-free persistence

Queue persistence MUST NOT contain credentials, tokens, salts, signed stream
URLs or private server endpoints.

### Scenario: persist authenticated playback queue

**Given** authenticated stream URLs have been resolved

**When** queue state is persisted

**Then** authenticated stream URLs and authentication material MUST NOT enter
the durable snapshot.

## Requirement: service-owned queue

`PlaybackService` MUST remain the single runtime Media3 queue authority.

### Scenario: Activity recreation

**Given** the service owns an active queue

**When** the Activity is recreated

**Then** the application MUST reconnect to that same queue authority

**And** MUST NOT create a duplicate player or queue authority.

## Requirement: safe system navigation

Trusted Android system controllers MAY use supported next/previous transport
commands.

They MUST NOT gain arbitrary MediaItem injection rights.

### Scenario: system next

**Given** A and B with A current

**When** a trusted system controller requests next

**Then** B MUST become current

**And** `PlaybackService` MUST remain queue authority.

## Requirement: private queue mutation

Account-bearing queue mutation MUST be accepted only from this application's
own UID/package boundary.

### Scenario: external mutation

**Given** an external controller

**When** it attempts account-bearing queue mutation

**Then** the service MUST reject that mutation.

## Requirement: restore without autoplay

Persisted queue restoration MUST NOT begin audible playback automatically.

### Scenario: process recreation

**Given** a valid persisted queue for account A

**And** no active service playlist exists

**When** account A reconnects

**Then** the queue MAY be restored

**But** playback MUST remain non-playing until an authorized playback action.

## Requirement: account-switch isolation

Changing authenticated identity or signing out MUST remove the previous
account's runtime queue ownership.

### Scenario: account switch

**Given** account A owns the runtime queue

**When** authentication changes to account B

**Then** account A's runtime queue MUST be cleared before B controls playback

**And** B MUST NOT observe or play account A's queue.
