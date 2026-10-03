# First Sound Navigation Shell Specification

## Primary navigation

The application MUST expose Home, Library, Search and Discover as the First
Sound primary destinations.

### Scenario: select a primary destination

Given an authenticated account is active
And the First Sound shell is visible
When the user selects one of the four primary destinations
Then that destination MUST become the active primary destination
And any secondary surface MUST NOT remain incorrectly over another primary
destination

## Home library flow

Home MUST provide access to the implemented recent-albums to album-details
flow.

### Scenario: open an album from Home

Given Home is active
And recent albums contain an album
When the user selects that album
Then its album-details surface MUST open using the opaque album id
And Back MUST return to Home

## Library flow

Library MUST provide access to the implemented recent-albums to album-details
flow.

### Scenario: open an album from Library

Given Library is active
And recent albums contain an album
When the user selects that album
Then its album-details surface MUST open using the opaque album id
And Back MUST return to Library

## Search placeholder

Search MUST be presented as an explicit non-functional First Sound
placeholder.

### Scenario: open Search

Given an authenticated account is active
When the user selects Search
Then the app MUST state that Search is not implemented
And it MUST NOT display fake results
And it MUST NOT perform external-provider work

## Discover placeholder

Discover MUST be presented as an explicit non-functional First Sound
placeholder.

### Scenario: open Discover

Given an authenticated account is active
When the user selects Discover
Then the app MUST state that Discover is not implemented
And it MUST NOT display fake recommendations
And it MUST NOT perform external-provider work

## Persistent mini-player

The shell MUST derive mini-player state from the existing playback
presentation state.

It MUST NOT create another player or queue authority.

### Scenario: move between destinations during playback

Given the current playback state contains a visible current track
When the user moves between primary destinations
Then the mini-player MUST remain coherent with that same playback state
And the current track metadata MUST remain consistent

### Scenario: no current playback context

Given playback state has no current track
When the shell is visible
Then the mini-player MUST be hidden

## Mini-player controls

The mini-player MUST expose an accessible direct play/pause/resume action when
that action is valid for the current phase.

### Scenario: pause from mini-player

Given the current track is playing
When the user activates the mini-player pause action
Then the existing playback client MUST receive the pause command
And no second playback state machine MUST be created

## Now Playing

Selecting the mini-player MUST open a secondary Now Playing surface rather
than guessing an album-details route.

### Scenario: restored queue without album route

Given an authenticated matching account restores safe queue state
And the current track has no source album id in playback presentation state
When the user selects the mini-player
Then Now Playing MUST open
And the app MUST NOT fabricate an album id

## Local playback target

Now Playing MUST communicate the active conceptual playback target as
`This device`.

### Scenario: show local target

Given Now Playing is visible
When playback target information is displayed
Then `This device` MUST be presented as local informational state
And the UI MUST NOT imply Cast, Music Connect or remote-device selection

## Account isolation

Navigation state that references account-specific content MUST be scoped to
the active exact server-plus-user identity.

### Scenario: account changes while album details is open

Given account A is authenticated
And an album from account A is open
When the authenticated identity changes to account B
Then the account-A album navigation state MUST be cleared
And account B MUST NOT observe account-A library state

## Playback authority

`PlaybackService` MUST remain the runtime player and queue authority.

### Scenario: shell controls playback

Given the mini-player or Now Playing surface is visible
When the user activates a playback command
Then the command MUST flow through the existing playback client/service path
And the shell MUST NOT maintain a duplicate queue

## Back behavior

Secondary surfaces MUST have deterministic Back behavior.

### Scenario: Back from Now Playing

Given Now Playing was opened from the shell
When Back is invoked
Then the previous shell/library context MUST be restored

### Scenario: Back from album details

Given an album was opened from Home or Library
When Back is invoked
Then the originating primary destination MUST be restored

## Privacy

The shell MUST NOT publish private account or authenticated stream material.

### Scenario: visible playback state

Given playback UI is visible
Then visible shell/player metadata MAY contain the opaque track id internally,
title and optional artist as already permitted
But it MUST NOT contain the server endpoint
And it MUST NOT contain the username
And it MUST NOT contain credentials, authentication salts/tokens or signed
stream URLs
