# Design: Navidrome Recent Albums

## Dependency direction

The initial feature follows:

`presentation -> domain <- data`

within the existing single Android application module.

No dependency-injection framework, database, extra module or provider-neutral
abstraction is introduced.

## Account ownership

`ServerAccountIdentity` remains the canonical local account key.

A successful recent-albums result carries its owning identity explicitly.
Presentation code added in WU3 must publish a result only while that identity
is still the active authenticated identity.

The library layer does not derive account identity from album metadata,
credentials, server metadata or endpoint strings independently.

## Domain model

WU1 introduces only the fields required by the First Sound recent-albums
surface:

- opaque album id;
- album title;
- optional artist;
- optional cover-art id.

Transport-specific JSON objects and URLs do not enter the domain model.

## Protocol parsing

The OpenSubsonic response parser:

- accepts strict JSON only;
- requires a valid `subsonic-response` envelope;
- requires `status` and protocol `version` strings;
- accepts an empty album list;
- preserves server album order;
- requires non-blank album ids and titles;
- treats malformed optional field types as malformed protocol data;
- maps a well-formed Subsonic error code 40 to an authentication-required
  result;
- distinguishes other well-formed server failures from malformed responses;
- bounds input size and structural nesting before materializing the JSON tree.

WU2 additionally enforces a byte bound at the HTTP boundary.

## Network boundary

WU2 will use the existing authentication signer and secure secret store.
It will not persist request URLs, tokens, salts or credentials.

The request target is the authenticated OpenSubsonic `getAlbumList2` endpoint
with `type=recent` and a deliberately bounded result size.

## Presentation

WU3 will expose explicit loading, empty, authentication, network,
server/protocol and content states.

Layout decisions are based on available width rather than device type.
Large surfaces constrain catalogue content instead of stretching it
indefinitely.
