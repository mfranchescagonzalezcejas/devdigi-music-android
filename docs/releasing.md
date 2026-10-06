# Releasing DevDigi Music

Status: Active release contract  
Jira: MUSIC-59

## Production release flow

DevDigi Music follows GitFlow for production releases:

```text
develop
  ↓
release/X.Y.Z
  ↓
PR to main
  ↓
merge commit
  ↓
tag vX.Y.Z on the verified main commit
  ↓
GitHub Release + Google Play
```

Do not tag `develop` directly.

A release branch is created only after the intended `develop` baseline is
approved for release preparation. Keep that branch limited to release
metadata, release notes, verification and necessary release-specific fixes.

If release-only changes are not already present in `develop`, propagate them
back through a separate reviewed PR after the release.

## Version identity

Application release identity is source-controlled in `gradle.properties`:

```properties
devdigi.versionName=0.1.0
devdigi.versionCode=1
```

Gradle validates both values during configuration.

### versionName

`versionName` is the user-facing semantic product version.

Production format:

```text
MAJOR.MINOR.PATCH
```

The production tag must match it exactly with a leading `v`:

```text
0.1.0 → v0.1.0
```

### versionCode

`versionCode` is the Android production build/candidate identity.

Rules:

- it must be a positive integer;
- it must be monotonically increasing across production candidates that enter
  external distribution or store history;
- replacing an externally distributed RC requires a new versionCode;
- promoting an approved RC to production keeps the same versionCode because it
  is the same artifact.

Do not replace this value with a Jenkins branch-local build number.

### CI build identity

Jenkins job/build identity plus the exact Git commit SHA is provenance
metadata. It identifies the CI execution, but it is not the Android
`versionCode` and is not the product `versionName`.

## Distribution channels

The long-term channel implementation is tracked by MUSIC-61.

DEV / STG / RC / PROD are distribution channels, not necessarily four
independent Android flavors.

| Channel | Intended role |
| --- | --- |
| DEV | Developer/internal builds; future separate app identity and Firebase DEV distribution. |
| STG | Production-like staging; future separate app identity and Firebase STG distribution. |
| RC | Exact signed production artifact under release-candidate validation. |
| PROD | Promotion of the approved RC artifact to GitHub Release and Google Play. |

RC and PROD must use the same approved production artifact. Do not validate one
binary and rebuild another merely to publish it.

Future Firebase App Distribution and environment/flavor mechanics belong to
MUSIC-61. Tagged GitHub/Google Play automation belongs to MUSIC-50.

## Release candidate

Create `release/X.Y.Z` from the exact approved `develop` commit.

The trusted Jenkins release branch job builds and verifies signed release APK
and AAB artifacts. Record the exact:

- release branch;
- commit SHA;
- versionName;
- versionCode;
- Jenkins job/build;
- artifact checksum/provenance evidence.

Run the required release QA against this candidate.

If product code or release metadata affecting the binary changes after RC
validation begins, invalidate the previous candidate and validate the new one.

## Promotion to main

Open a PR:

```text
release/X.Y.Z → main
```

Normal repository governance still applies:

- PR required;
- review conversations resolved;
- Jenkins required check PASS;
- merge commit only;
- no routine admin bypass.

After merging, verify the resulting `main` commit before tagging it.

## Tagging

Create the production tag only after the release PR is merged and the exact
`main` commit is verified.

For version `X.Y.Z`:

```text
vX.Y.Z
```

The tag must resolve to the verified `main` release commit.

Never move or reuse an existing production tag.

## GitHub Release

For v0.1.0 the GitHub Release is created manually. MUSIC-50 owns future
automation.

Use the body structure in
[release-notes-template.md](release-notes-template.md).

For a production release, attach the already approved artifacts rather than
building a new production binary after QA:

- verified signed APK;
- verified signed AAB;
- checksum/provenance manifest when available.

GitHub Release and Google Play are both required publication targets for
v0.1.0. Upload the same approved production AAB to the Play track selected in
MUSIC-62. Firebase App Distribution remains deferred to MUSIC-61.

## Release notes

Release notes are user-facing, not a raw commit dump.

Preferred sections:

1. Summary
2. What's New
3. Bug Fixes, when relevant
4. Improvements
5. Known limitations / Known Issues
6. Verification, only at a safe high level

Conventional Commits may be used as source material, but internal refactors,
private infrastructure and sensitive operational detail must not leak into the
release body.

## v0.1.0 checklist

Before creating `release/0.1.0`:

- [ ] MUSIC-49 secure signing is Done.
- [ ] Required QA/BDD evidence is complete.
- [ ] MUSIC-10 First Sound Smoke has a release decision.
- [ ] MUSIC-11 First Sound Regression has a release decision.
- [ ] `devdigi.versionName=0.1.0`.
- [ ] `devdigi.versionCode=1` remains valid for the first production candidate.
- [ ] v0.1.0 release notes are reviewed.
- [ ] license state remains explicit.

Before merging to `main`:

- [ ] release branch scope contains only release preparation/fixes.
- [ ] trusted release build/signature verification passes.
- [ ] exact RC artifact has been validated.
- [ ] required Jenkins PR check passes.
- [ ] residual release risk is documented.

After merging to `main`:

- [ ] verify the exact main merge commit.
- [ ] tag it `v0.1.0`.
- [ ] create GitHub Release from that tag.
- [ ] publish the approved production AAB to the selected Google Play track.
- [ ] attach/record the approved signed artifacts and provenance.
- [ ] reconcile release-only changes back to `develop` if required.
- [ ] mark Jira Release `v0.1.0 — First Sound` released only after both publications succeed.

## Failure and rollback

Before publication, fix release blockers on the release branch and produce a
new candidate. If an already distributed RC is replaced, increment
`versionCode`.

After publication, never rewrite the published tag or protected branch history.
Prepare the smallest verified fix and ship a new patch release.

If a release attempt fails after a tag was created but before publication,
investigate before deleting anything. Preserve published history; do not move a
tag that has already been used as a public release identifier.

Signing-key recovery follows [release-signing.md](release-signing.md).
