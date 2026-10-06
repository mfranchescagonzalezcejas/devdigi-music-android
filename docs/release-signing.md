# Android release signing

Status: Implemented pipeline contract; trusted runtime validation required before MUSIC-49 closes  
Jira: MUSIC-49  
Related GitHub issue: #36

## Trust boundary

Release signing is available only to Jenkins branch jobs for:

- `main`;
- `release/*`.

Pull-request jobs and all other branch names skip the signing stage before any signing credential is bound.

Fork PRs therefore never receive signing material. Ordinary `develop`, feature,
fix, docs, test, ci, chore, and refactor builds remain debug-only.

A `release/*` branch is a trusted ref. Create it only from an approved release
baseline and do not use it as a general-purpose development branch.

## Jenkins Credentials

Create these credentials in Jenkins. The IDs are repository configuration; the
values must never be committed or copied into issue/PR evidence.

| Credential ID | Jenkins type | Purpose |
| --- | --- | --- |
| `android-release-keystore` | Secret file | Android signing keystore |
| `android-release-store-password` | Secret text | Keystore password |
| `android-release-key-alias` | Secret text | Signing key alias |
| `android-release-key-password` | Secret text | Signing key password |

The pipeline binds them only inside the trusted release stage as:

- `DEVDIGI_RELEASE_STORE_FILE`;
- `DEVDIGI_RELEASE_STORE_PASSWORD`;
- `DEVDIGI_RELEASE_KEY_ALIAS`;
- `DEVDIGI_RELEASE_KEY_PASSWORD`.

Do not print these variables or pass their values as Gradle command-line
properties.

## Gradle behavior

Release tasks fail closed when any required signing value is unavailable.

The signing configuration reads only environment-provided values. No signing
password, alias, or keystore path is stored in Gradle properties or source
control.

Trusted Jenkins builds run:

```sh
./gradlew assembleRelease bundleRelease
```

Normal debug tasks do not require release credentials.

## Verification and artifacts

A trusted release build must produce:

- signed release APK;
- signed release AAB;
- `release-sha256.txt` containing only artifact filenames and SHA-256 hashes.

Jenkins verifies:

- APK signature with Android `apksigner`;
- AAB JAR signature with `jarsigner`.

The verification step emits only privacy-safe PASS/FAIL labels. It does not
print certificate details, aliases, passwords, keystore paths, or credential
values.

Only the APK, AAB, checksum manifest, and normal non-secret reports are
archived.

## Operational validation

MUSIC-49 is not complete merely because the code is merged.

Before closing it:

1. provision all four Jenkins credentials;
2. create/use a trusted `release/*` branch from the intended baseline;
3. confirm ordinary PR and `develop` jobs do not execute the signing stage;
4. confirm the trusted release job reports:
   - `RELEASE_APK_SIGNATURE=PASS`;
   - `RELEASE_AAB_SIGNATURE=PASS`;
   - `RELEASE_PROVENANCE_SHA256=PASS`;
   - `RELEASE_SIGNING=PASS`;
5. confirm signed APK/AAB and checksum manifest are archived;
6. retain only sanitized evidence in Jira/GitHub.

## Failure behavior

If credentials are absent or incomplete, release tasks fail before producing a
valid signed release.

If signature verification fails, Jenkins fails the release stage and the build
must not be treated as a releasable artifact.

A failing release-signing job must never be bypassed by copying signing material
into the workspace or repository.

## Rotation and recovery

If the keystore or password material is suspected to be compromised:

1. disable/remove the affected Jenkins credentials;
2. stop release signing until a replacement/recovery decision is made;
3. record the incident without publishing secret values;
4. rotate the Jenkins credential values;
5. re-run a trusted release build and signature verification.

Before the first published release, a lost signing key may be replaced with a
new release key because no installed production lineage exists yet.

After publication, key recovery/rotation must follow the distribution
platform's signing-key policy. Do not generate a replacement casually if it
would break application update continuity.

## Pipeline rollback

If the signing pipeline itself is defective:

- revert the signing implementation through a normal PR;
- leave signing credentials in Jenkins disabled or unused until the corrected
  pipeline is reviewed;
- restore the previous CI behavior without exposing the secret material;
- re-enable trusted signing only after the corrected path passes validation.

Never solve a pipeline rollback by committing a keystore or secret property
file.
