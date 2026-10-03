# Device validation: minimal persistent playback queue

Status: WU4 real-device validation complete
Related issue: #7
Validation date: 2026-10-03
Source baseline: develop at 122047f139bc54f737292f9599c447a7cfd15259

## Validation boundary

Validation was performed on a physical Android device running API 36 against
a real authenticated BYON Navidrome/OpenSubsonic library.

Device model, serial, server endpoint, account identifiers, credentials,
authentication material and listening-history values are intentionally
omitted.

Raw app-local persistence, MediaSession, notification and process-log captures
were held only in temporary local storage during QA and deleted afterwards.

The validation runner derived comparison references from the application's own
private storage instead of requiring the tester to re-enter private values.
The encrypted credential was not decrypted for leak detection, and private
reference values were never printed.

## Results

| Requirement | Result |
| --- | --- |
| Selecting an album track replaces the queue with the album's ordered tracks and starts at the selected index | PASS |
| App Previous at the first entry and Next at the final entry do not wrap | PASS |
| Notification media controls navigate the same queue with Next/Previous | PASS |
| Lock-screen media controls navigate the same queue with Next/Previous | PASS |
| Queue/current index survive Activity recreation and ordinary backgrounding | PASS |
| Matching queue state restores after process recreation without autoplay | PASS |
| Explicit playback after process restoration continues over the restored queue | PASS |
| Sign-out clears active runtime playback/queue ownership | PASS |
| A different authenticated account does not restore or play another account's queue | PASS |
| Durable queue persistence contains no raw endpoint or username | PASS |
| Durable queue persistence contains no authentication material or signed stream URI | PASS |
| DevDigi Music MediaSession output contains no endpoint, account identity, auth material or signed stream URI | PASS |
| DevDigi Music notification/system-media output contains no endpoint, account identity, auth material or signed stream URI | PASS |
| DevDigi Music process logs contain no endpoint, account identity, auth material or signed stream URI | PASS |
| Manual notification and lock-screen privacy review | PASS |

## Privacy-scan clarification

Two intermediate automated notification checks produced false positives.

The first used broad context windows around package-name matches in
`dumpsys notification`. The second treated the remainder of the notification
dump as part of a matched record. Both approaches could include unrelated
applications and Android notification-service configuration.

The final check parsed the structural indentation boundary of each
`NotificationRecord` and inspected only the record belonging to DevDigi Music.
That isolated record passed.

Future reusable QA tooling should prefer structurally bounded parsing over
line-context windows when a system dump contains data for multiple
applications.

## Account isolation

The account-switch test used two distinct valid authenticated identities.

After sign-out, runtime playback state was cleared. The second identity did
not receive the first identity's queue or playback and did not begin playback
automatically.

No account names are retained in this evidence.

## Process restoration

A queue with an intermediate current entry was paused before the application
process was stopped.

After process recreation:

- the authenticated session restored;
- the matching safe queue restored;
- playback did not start automatically;
- the restored current entry remained associated with the matching account;
- explicit playback could resume;
- Next/Previous continued over the restored queue.

## Scope

This validation closes the minimal persistent queue behavior defined by #7.

It does not add:

- queue-editor or drag-and-drop UI;
- mini-player or broader navigation work tracked by #12;
- the broader First Sound real-instance closeout tracked by #17;
- smart shuffle, collaborative queues or cloud queue synchronization.

Required Jenkins pull-request checks are recorded only after the final WU4
pull request exists.
