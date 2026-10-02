# Design: Minimal Persistent Playback Queue

## 1. Runtime ownership

`PlaybackService` remains the single runtime authority for:

- queue contents;
- current queue index;
- ExoPlayer;
- MediaLibrarySession;
- Media3 playlist synchronization.

Activity, Compose and ViewModel code are clients only.

## 2. Pure queue domain

The intended domain shape is conceptually:

    PlaybackQueue
      entries: List<PlaybackTrack>
      currentIndex: Int?

Invariants:

- an empty queue has no current index;
- a non-empty queue has an index inside `entries.indices`;
- queue entries contain only safe `PlaybackTrack` metadata.

## 3. Replace

Replacing with a non-empty list requires a valid selected index.

Album-track selection replaces the queue with the album's ordered track list
and selects the tapped track.

Replacing with an empty list clears the queue.

## 4. Append

Appending preserves the current selection.

Appending to an empty queue makes the first appended entry current.

## 5. Next and previous

Navigation does not wrap.

- next at the final entry is a no-op;
- previous at index zero is a no-op.

## 6. Remove

Removing an entry before the current entry decrements the current index.

Removing an entry after the current entry leaves the current index unchanged.

Removing the current entry selects:

1. the successor now occupying the same index;
2. otherwise the previous final entry;
3. otherwise an empty queue.

## 7. Clear

Clear removes all entries and the current selection.

## 8. Durable queue snapshot

Use a dedicated Preferences DataStore.

Persist only:

- schema version;
- account fingerprint;
- current index;
- track id;
- track title;
- optional artist.

Never persist:

- endpoint;
- username;
- credentials;
- token;
- salt;
- signed stream URL;
- HTTP headers;
- Media3 objects.

v0.1 keeps one durable snapshot.

## 9. Account fingerprint

The fingerprint is deterministic and versioned.

Its input is:

- normalized endpoint UTF-8 bytes;
- exact username UTF-8 bytes.

Use an unambiguous domain/version prefix and length-prefixed fields before
SHA-256.

Only the digest representation is persisted.

The fingerprint is an isolation key, not an authentication secret.

## 10. Persistence hardening

Malformed, unsupported or oversized snapshots fail closed.

Implementation must bound:

- serialized snapshot size;
- queue entry count;
- required id/title fields.

Cancellation must not be converted into a successful write.

Fingerprint mismatch behaves as no restorable queue.

## 11. Media3 translation

Only `PlaybackService` translates queue entries into Media3 MediaItems.

Public Media3 information remains:

- opaque track id;
- title;
- optional artist.

Fresh signed stream URIs remain private local playback configuration.

They never enter durable queue state or public metadata.

## 12. Session commands

Own-application-only queue operations may include:

- replace queue;
- append entries;
- remove entry;
- clear queue.

Account context exists only transiently inside private session commands.

External/system controllers cannot perform account-bearing queue mutation.

## 13. System controls

Once a valid queue exists, trusted/system controllers may receive supported
next and previous commands.

They still do not receive:

- `COMMAND_SET_MEDIA_ITEM`;
- `COMMAND_CHANGE_MEDIA_ITEMS`.

## 14. Restoration

When an authenticated account reconciles:

1. preserve matching active runtime playback when present;
2. otherwise inspect the durable snapshot;
3. require matching account fingerprint;
4. reject malformed state;
5. rebuild the safe service queue;
6. restore current index;
7. remain non-playing until an authorized playback action.

## 15. Account changes

Sign-out or account mismatch clears runtime player/queue ownership before a
different identity can control playback.

The safe persisted snapshot does not need to be deleted on sign-out.

## 16. Presentation

`PlaybackViewModel` must not become a second queue store.

The application client sends queue operations to the service.

Album details already provide the ordered tracks required for queue
replacement.

No queue editor UI is introduced.

## 17. Testing

Domain JVM tests cover:

- invariants;
- replacement;
- append;
- next/previous boundaries;
- removal;
- clear.

Persistence tests cover:

- round trip;
- matching identity;
- identity mismatch;
- malformed snapshot;
- bounds;
- absence of raw identity and authenticated stream material.

Service/session tests cover:

- own-app authorization;
- account ownership;
- safe system command grants;
- Media3 metadata safety;
- current-index synchronization.

Presentation tests preserve:

- stale-event rejection;
- reconnect behavior;
- account-switch behavior.

Real-device validation covers:

- ordered album queue;
- app next/previous;
- system next/previous;
- background playback;
- Activity recreation;
- process restoration without autoplay;
- account switching;
- privacy.

## 18. Review split

Planned WUs:

- WU1 — queue domain + persistence.
- WU2 — service/session backend.
- WU3 — application wiring.
- WU4 — Android validation and closeout.

Any WU exceeding 1000 changed lines is split before commit.
