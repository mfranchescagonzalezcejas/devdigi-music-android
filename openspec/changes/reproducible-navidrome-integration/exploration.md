# Exploration: Reproducible Navidrome Integration

## WU0 result

PASS

WU0 established the capabilities and implementation decisions required before
building the ephemeral integration environment.

No production application code, Jenkins configuration, Docker Compose
definition, personal Navidrome instance or repository fixture was changed
during these probes.

## Local Docker capability

Observed locally:

- Docker daemon available.
- Docker server version: 29.8.1.
- Integrated `docker compose` command available.
- Docker Compose version: 5.5.1.
- Legacy standalone `docker-compose` is not required.

The original task wording used "Docker Compose v2". That wording was too
specific to an older Compose major version. The required capability is the
integrated Docker Compose plugin exposed through `docker compose`.

## Jenkins agent capability

A live Jenkins agent process was found on the Android build host.

The running agent uses the same operating-system user as the successful local
Docker capability probe.

That user successfully accessed:

- the Docker daemon;
- the integrated `docker compose` command.

This proves the current agent OS user has the permissions needed for the
planned Docker-backed integration environment.

This is an OS-user capability proof. WU3 will still validate the final
repository-owned integration command inside an actual Jenkins build.

## Navidrome image

Selected test image:

    deluan/navidrome:0.64.2

Resolved immutable repository digest:

    deluan/navidrome@sha256:38dc2727bfcfd5ede290f8ada114fc90368146f265ae4701ddddbcbe2a44ee52

Observed platform:

    linux/amd64

The integration environment must use the explicit version and immutable digest
rather than a floating `latest` tag.

## Synthetic account bootstrap

A temporary Navidrome container was started with an independently generated
runtime password and the supported development auto-admin bootstrap
configuration.

Observed behavior:

- synthetic `admin` account created successfully;
- authenticated OpenSubsonic ping succeeded;
- protocol version reported as 1.16.1;
- server type reported as Navidrome;
- server version reported as 0.64.2;
- OpenSubsonic support reported as enabled;
- deliberately invalid synthetic credentials returned Subsonic error code 40.

The generated password was not printed, committed or left in repository files.

## Runtime isolation

The temporary server was published on an automatically allocated host port
bound only to loopback.

Validated:

- loopback-only binding;
- dynamic host port allocation;
- container cleanup;
- temporary data cleanup;
- unchanged repository worktree.

No personal endpoint, account or library was used.

## Fixture decision

The test library will use two tiny original synthetic FLAC files.

Prototype generation used FFmpeg's synthetic sine source with deterministic
metadata:

- artist: `DevDigi Synthetic Artist`;
- album: `DevDigi Synthetic Album`;
- track 1 title: `Synthetic Track A`;
- track 2 title: `Synthetic Track B`;
- one second per track;
- mono 44.1 kHz PCM source encoded as FLAC;
- no third-party media or cover art.

Prototype sizes:

- track A: 23,586 bytes;
- track B: 24,814 bytes;
- total: 48,400 bytes.

Prototype SHA-256 values:

    cd06e0ff28d4d162a703d083d15e2d240b2863f91bed2b6b05bfb3ba37891ac7
    82cdb3c79e6880649c7a45e2f8adf95ac10b4cd1ba74682317b322f8f3c39114

The same generation command produced identical bytes in two independent runs.

## Fixture implementation decision

WU2A will commit the tiny generated FLAC fixtures together with:

- provenance;
- exact generation command;
- SHA-256 checksums.

FFmpeg is therefore a development/provenance tool only.

Jenkins and normal integration execution will not require FFmpeg to regenerate
the fixtures.

## Security and privacy

WU0 used only synthetic values and temporary local runtime state.

The evidence intentionally excludes:

- generated passwords;
- personal server URLs;
- personal usernames;
- personal library metadata;
- private filesystem paths;
- Docker environment dumps.

## WU0 completion

Completed:

- 0.1 local Docker and integrated Compose capability;
- 0.2 Jenkins agent OS-user Docker/Compose capability;
- 0.3 explicit Navidrome version and immutable digest;
- 0.4 synthetic admin bootstrap;
- 0.5 isolated loopback port allocation;
- 0.6 synthetic FLAC fixture strategy;
- 0.7 sanitized capability evidence.

Next implementation slice:

WU1 — ephemeral Navidrome lifecycle.
