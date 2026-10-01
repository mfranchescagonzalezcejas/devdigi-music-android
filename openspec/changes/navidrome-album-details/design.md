# Design: Navidrome Album Details

## Dependency direction

Album details follow:

`presentation -> domain <- data`

No new DI framework, database, Gradle module or provider-neutral abstraction
is introduced.

## Account ownership

`ServerAccountIdentity` remains the canonical ownership key.

Every successful album-details result carries the exact account that owns the
album state.

A response belonging to an old or different account MUST NOT be presented
after the authenticated identity changes.

## Album domain

The album model contains only data required by the First Sound vertical slice:

- opaque album id;
- title;
- optional artist;
- optional cover-art id;
- ordered tracks.

## Track domain

A track contains:

- opaque track id;
- title;
- optional artist;
- optional track number;
- optional disc number;
- optional duration in seconds;
- optional cover-art id.

The opaque track id is sufficient for playback #1 to later resolve a stream
request through an authenticated transport boundary.

Resolved stream URLs and authentication query parameters MUST NOT enter
domain or UI state.

## Protocol boundary

WU1B will parse the OpenSubsonic album response defensively.

The parser will:

- require a valid `subsonic-response` envelope;
- require protocol status and version;
- require an album object on successful responses;
- require non-blank album and track ids/titles;
- accept optional artist, artwork and numeric metadata only with valid types;
- preserve the server-provided song order exactly;
- allow an empty song collection;
- map protocol error code 40 to authentication required;
- distinguish other server failures from malformed responses;
- bound response characters and structural nesting.

## Credential and network boundary

WU2 will reuse the secure credential store and `SubsonicAuthSigner`.

The request will use the authenticated OpenSubsonic/Navidrome `getAlbum`
endpoint with an opaque album id.

Redirects remain disabled and request/response limits follow the established
recent-albums transport boundary.

## Presentation

WU3 will own album-detail presentation state separately from authentication
state.

Selecting a recent album navigates to its detail state using only the opaque
album id.

Layout remains responsive to available width rather than device type.

Missing artwork and optional metadata use safe accessible fallbacks.
