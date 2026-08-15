# DevDigi Music Android

Minimal native Android bootstrap for DevDigi Music. It intentionally contains
no server, account, library, or playback implementation yet.

## First Sound

The planned first vertical slice lets each person bring their own
Navidrome/OpenSubsonic server (BYON), authenticate safely, browse recent
albums and tracks, and play FLAC through Media3 with Android system playback
integration. The app must not hardcode a server or assume a Tailnet, LAN, or
other deployment topology.

## Verify

Requires Android SDK platform 35. Gradle runs on JDK 25; Android
compilation remains on Java 17 bytecode.

For Android Studio sync, set **Settings/Preferences > Build, Execution,
Deployment > Build Tools > Gradle > Gradle JDK** to an installed **JDK 25**.
Current Android Studio versions support this Gradle runtime. Use JDK 17 only
as a fallback where JDK 25 is unavailable; the project keeps Java 17 for
Android compilation.

```sh
./gradlew lint testDebugUnitTest
```

## Privacy

Do not commit real server URLs, private DNS names, credentials, tokens, salts,
user identifiers, or listening data. Use `https://music.example.com`,
`demo-user`, `<password>`, and `<token>` in public examples.

## License

No license has been selected. See GitHub issue #19 before reusing or contributing code.

## Branch protection

`main` is intentionally unprotected while this is a sole-maintainer bootstrap. Require checks and pull-request reviews when another maintainer or external contribution flow exists.

