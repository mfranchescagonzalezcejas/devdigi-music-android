# DevDigi Music Android

DevDigi Music is a native Kotlin and Jetpack Compose Android client for
user-provided Navidrome/OpenSubsonic servers.

The current app implements server URL validation and persistence,
secure interactive Navidrome authentication, authenticated OpenSubsonic
connection verification, encrypted credential storage, session restoration,
sign-out, authenticated server metadata, recent-album browsing, album details
with ordered tracks, and service-backed Media3 playback with a minimal
account-scoped persistent queue, ordered album replacement, app/system
next-previous controls and restoration without autoplay. The First Sound
Compose shell provides Home and Library navigation, explicit Search and
Discover placeholders, a persistent mini-player, a secondary Now Playing
surface and a read-only local `This device` playback target.

## First Sound

The First Sound vertical slice lets each person bring their own
Navidrome/OpenSubsonic server (BYON), authenticate safely, browse recent
albums and tracks, and play FLAC through service-backed Media3 playback with
an account-scoped persistent queue and Android system integration. Its native
shell exposes Home, Library, Search and Discover, keeps active playback
reachable through a persistent mini-player and Now Playing, and adapts primary
navigation to available Compose width. Search results, recommendations,
queue editing and remote playback targets remain outside this slice. The app
must not hardcode a server or assume a Tailnet, LAN, or other deployment
topology.

## Product roadmap

| Milestone | Direction |
| --- | --- |
| v0.1.0 — First Sound | Complete the first usable BYON vertical slice: authentication, minimal library flow, FLAC playback, queue, system playback, account isolation, and real-instance validation. |
| v0.2.0 — Library | Expand canonical Navidrome browsing, search, artists, albums, songs, genres, favorites, playlists, and collections. |
| v0.3.0 — Player | Mature the native Navidrome/Media3 player, queue, playback controls, metadata, and resilience. |
| v0.4.0 — Offline | Add account-scoped downloads and synchronization, including the contextual Download flow and optional server incorporation tracked by #56. |
| v0.5.0 — Import & Matching | Import external references or wishlists and match them against Navidrome without assuming every external item belongs to the playable library. |
| v0.6.0 — Discover | Add local/Navidrome discovery first; external discovery remains capability-gated. |

External-provider work is not an approved dependency of the native Navidrome
roadmap. #53 decides provider viability, #54 independently evaluates external
audio/video playback, and #55 defines provisional multi-provider identity and
source-selection architecture. A future multi-provider release is conditional
on those decisions.

Music Connect, remote playback targets, Cast and companion surfaces are future
directions rather than committed version milestones.

## BYON and security

Each user supplies their own server and account. The connection screen can
save a server, authenticate with OpenSubsonic, restore a saved session only
after fresh authenticated verification, and sign out. Saving a server alone
does not establish an authenticated session.

See [BYON setup and security](docs/byon-security.md) for implemented and
planned security boundaries, and [Android architecture](docs/android-architecture.md)
for the native architecture and migration policy.

## Verify

Requires Android SDK platform 36. Local verification succeeded
with Gradle 9.5.0 on JDK 17; Android compilation targets Java 17 bytecode.

For Android Studio sync, select a Gradle JDK compatible with the project's
Gradle and Android Gradle Plugin versions. Verify the Jenkins Gradle runtime
independently of the Jenkins agent JVM.

```sh
git fetch --no-tags origin \
    +refs/heads/develop:refs/remotes/origin/develop
./gradlew spotlessCheck
./gradlew lint testDebugUnitTest
```

When formatting is needed, run it manually:

```sh
./gradlew spotlessApply
git diff --check
git diff
```

Spotless Ratchet checks entire files modified relative to `origin/develop`,
not just the changed lines. Review every formatting change before staging.
Jenkins runs `spotlessCheck` only; it never applies formatting automatically.

For the deterministic Navidrome JVM integration suite, the host also needs a
working Docker daemon and the integrated `docker compose` command. The
repository preflight reports missing host capabilities before starting the
server:

    ./integration/navidrome/lifecycle.sh preflight
    ./integration/navidrome/run-integration.sh

This integration environment uses only committed synthetic FLAC fixtures, a
temporary loopback-only Navidrome server and a freshly generated synthetic
password. It does not require a personal server URL, account or media library.
Focused real-device playback validation for #1 records only privacy-safe
pass/fail evidence. Broader real-instance validation remains tracked by #17.

## Optional local hooks (Lefthook)

Install [Lefthook](https://github.com/evilmartians/lefthook) separately if you
want local fail-fast checks. Before installing, inspect `git config --get
core.hooksPath` and `git rev-parse --git-path hooks`; preserve any existing
hooks instead of allowing an installer to overwrite them. Linked Git worktrees
normally share the same hooks directory; test hook installation in an
independent clone, **not** a temporary worktree.

```sh
lefthook version
lefthook validate
lefthook install  # Explicit opt-in, only after checking existing hooks.
```

The `pre-commit` hook checks staged whitespace and runs existing Gradle
`spotlessCheck` when relevant files are staged. Lefthook hides tracked
unstaged edits during a normal `git commit`, but this behavior must not be
assumed for untracked files or manual hook runs. The `commit-msg` hook checks
Conventional Commits. Hooks never autoformat; there is no heavy `pre-push`
check. If a hook fails, fix the relevant staged content and retry. Use
`LEFTHOOK=0 git commit` only as a documented exceptional bypass; Jenkins still
must pass on the PR. To remove hooks, first inspect their contents and
ownership, then use Lefthook's uninstall command **only** for hooks you
know it installed; restore any pre-existing hooks from their backups.

## Privacy

Do not commit real server URLs, private DNS names, credentials, tokens, salts,
user identifiers, or listening data. Use `https://music.example.com`,
`demo-user`, `<password>`, and `<token>` in public examples.

## License

No license has been selected. See GitHub issue #19 before reusing or contributing code.

## Branch protection

Both `main` and `develop` are protected. Normal integration requires pull
requests, resolved review conversations and the Jenkins `pr-merge` check.
The repository uses merge commits and GitFlow. See
[GitFlow and branch governance](docs/gitflow-governance.md) for the full policy.
Production versioning, release-candidate promotion, tagging and GitHub Release
steps are defined in [Releasing DevDigi Music](docs/releasing.md).


