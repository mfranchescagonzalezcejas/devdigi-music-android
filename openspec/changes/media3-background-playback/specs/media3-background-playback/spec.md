# Media3 Background Playback Specification

## Requirement: service-owned playback

The application MUST host active Media3 playback in one
`MediaLibraryService`-owned player.

### Scenario: playback service starts

**Given** no playback service instance exists

**When** Media3 creates the playback service

**Then** the service MUST create exactly one player and one
MediaLibrarySession for that service lifetime.

### Scenario: service is destroyed

**Given** the service owns an active session and player

**When** the service is destroyed

**Then** it MUST release the session and player without leaking or duplicating
either object.

## Requirement: background continuity

Playback MUST remain independent from the Activity lifetime while Android
considers playback eligible for media foreground-service execution.

### Scenario: Activity goes to background

**Given** a selected track is playing

**When** the Activity is backgrounded

**Then** the service-owned player SHALL continue the same playback.

### Scenario: user returns to the app

**Given** service-owned playback remains active

**When** the Activity is recreated or returns to foreground

**Then** application controls MUST reconnect to the existing session

**And** MUST NOT create a second player.

## Requirement: common player controls

Application and Android system controls MUST operate the same service-owned
player.

### Scenario: notification pauses playback

**Given** a track is playing

**When** the user pauses from an Android media notification or supported
system control

**Then** the service-owned player MUST pause

**And** application-visible playback state MUST become paused.

### Scenario: application resumes playback

**Given** the same player is paused

**When** the application sends resume through its MediaController

**Then** the same service-owned player MUST resume.

## Requirement: authenticated selected-track playback

Authenticated stream resolution MUST remain service-side.

### Scenario: own application requests a track

**Given** the application's controller is connected

**And** it supplies a valid current account identity and opaque track id

**When** it requests selected-track playback

**Then** the service MUST resolve credentials for that exact account

**And** MUST create fresh OpenSubsonic authentication material

**And** MUST resolve the playable source only inside the playback service
boundary.

### Scenario: external controller attempts an authenticated play command

**Given** a controller other than this application is connected

**When** it attempts an account-bearing or authenticated selected-track custom
command

**Then** the service MUST reject that command

**And** MUST NOT resolve credentials or a stream URI for it.

### Scenario: trusted controller is not the application controller

**Given** Media3 considers a system or external controller trusted

**But** that controller is not this application's own controller

**When** session commands are granted

**Then** trusted status alone MUST NOT grant authenticated selected-track or
account-reconciliation commands.

### Scenario: media notification controller connects

**Given** Media3's media-notification controller requests a connection

**When** the service grants its commands

**Then** the service MUST allow the connection required for normal media
notification behavior

**And** MUST NOT grant account-bearing authenticated custom commands.

### Scenario: external controller attempts media-item injection

**Given** a system or external controller can operate supported transport
controls

**When** it attempts to replace or add an arbitrary media item

**Then** the service MUST deny that media-item mutation

**And** MUST NOT treat the supplied media item or URI as an authenticated
Navidrome playback source.

## Requirement: safe system metadata

Media3 metadata published outside the playback service MUST contain only
minimum now-playing information.

### Scenario: active item is published

**Given** an authenticated stream has been resolved

**When** Media3 publishes the current item to Android system surfaces

**Then** title and optional artist MAY be visible

**And** an opaque track id MAY be visible

**But** server endpoint, username, password, token, salt and signed stream URI
MUST NOT be published as metadata or session extras.

## Requirement: account isolation

Playback MUST remain owned by the authenticated server-plus-user identity that
started it.

### Scenario: account changes

**Given** playback belongs to account A

**When** the application authenticates as account B

**Then** playback for account A MUST stop

**And** its active media item MUST be cleared before account B can play.

### Scenario: user signs out

**Given** an authenticated account owns active playback

**When** the application has no authenticated account

**Then** active playback MUST stop

**And** the media item MUST be cleared.

### Scenario: matching account reconnects

**Given** playback remains active in the service

**And** the application reconnects with the same account identity

**When** account reconciliation completes

**Then** the application MAY reconstruct safe playback state from the session

**And** MUST continue controlling the same player.

## Requirement: foreground-service declaration

The application MUST declare Android media playback foreground-service
requirements.

### Scenario: manifest configuration

**Given** the application includes the playback service

**Then** the manifest MUST declare the foreground-service permissions required
for media playback

**And** the service MUST declare a `mediaPlayback` foreground service type

**And** the service MUST declare `android:exported="true"`

**And** exporting the service MUST NOT grant authenticated custom commands
without the separate own-application controller authorization

**And** the manifest MUST declare the
`androidx.media3.session.MediaLibraryService` service action

**And** platform media-service compatibility MAY be exposed through
`android.media.browse.MediaBrowserService` without adding Navidrome library
browsing behavior to this issue.

## Requirement: no queue expansion

Issue #15 MUST NOT implement issue #7 queue semantics.

### Scenario: background playback is added

**Given** one selected track can play

**When** service/session integration is implemented

**Then** persistent queue replacement, append, next, previous, removal and
queue restoration MUST remain outside this change.

## Requirement: safe failure state

A terminal service/player error MUST remain recoverable and privacy safe.

### Scenario: background playback fails

**Given** a selected track was preparing or playing

**When** Media3 reports a terminal playback failure

**Then** presentation MUST transition away from playing

**And** MUST expose only a safe failure category

**And** MUST NOT expose the signed URI, raw exception, endpoint or credentials.
