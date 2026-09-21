# AGENTS.md — Instructions for Coding Agents

## Project

`medac` — Android app (Kotlin + Jetpack Compose), built with Gradle.
Project root is the directory containing this file and `gradlew`.

## Rule: a build is not done until the signed APK is published for testing

Whenever you build the app — whether the user asks for a build, a test build,
an APK, or you build as part of implementing/fixing something — you MUST finish
by publishing a **signed release APK** for testing. A build that is not published
is incomplete. Do not ask for confirmation; publishing is part of the build.

- **NEVER** publish `*-debug.apk`. Only signed release builds.
- **ALWAYS** rebuild from current source (`./gradlew assembleRelease`).
  Never re-upload a stale APK from a previous build.
- **ALWAYS** report the download URL when done.

## Publish steps (run from project root)

```bash
chmod +x gradlew
./gradlew assembleRelease
ls -lh app/build/outputs/apk/release/app-release.apk
cp app/build/outputs/apk/release/app-release.apk /var/www/apks/medisafe.apk
chown www-data:www-data /var/www/apks/medisafe.apk
chmod 644 /var/www/apks/medisafe.apk
curl -sI http://localhost/apks/medisafe.apk | head -n 10
```

- Stable public file: `/var/www/apks/medisafe.apk` — always overwrite it.
- Full details, troubleshooting, and Nginx reference: see [apk.md](./apk.md).
- If the build fails: report the last ~50 lines of the error and stop.
- If permission is denied on copy/chown: retry with `sudo` and report that.

## Report format (always end a build with this)

- Local build: path + size + timestamp
- Posted: `/var/www/apks/medisafe.apk`
- Download: `http://<server-ip-or-domain>/apks/medisafe.apk`
- Listing: `http://<server-ip-or-domain>/apks/`
- Build result: `BUILD SUCCESSFUL` or the failure excerpt
