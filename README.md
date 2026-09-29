# DevDigi Music Android

DevDigi Music is a native Kotlin and Jetpack Compose Android client for
user-provided Navidrome/OpenSubsonic servers.

The current bootstrap implements server URL validation and persistence,
authentication contracts, protocol parsing and encrypted credential storage.
Interactive authentication, authenticated networking, music browsing and
Media3 playback are not yet available.

## First Sound

The planned first vertical slice lets each person bring their own
Navidrome/OpenSubsonic server (BYON), authenticate safely, browse recent
albums and tracks, and play FLAC through Media3 with Android system playback
integration. The app must not hardcode a server or assume a Tailnet, LAN, or
other deployment topology.

## BYON and security

Each user supplies their own server and account. The current screen can
save a server URL, but saving it does not authenticate or contact the server.

See [BYON setup and security](docs/byon-security.md) for implemented and
planned security boundaries, and [Android architecture](docs/android-architecture.md)
for the native architecture and migration policy.

## Verify

Requires Android SDK platform 35. Local verification succeeded
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


