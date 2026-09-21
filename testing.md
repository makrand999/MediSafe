# Testing on the Windows Emulator

The test emulator (`emulator-5554`, ASUS_I001D) runs on the Windows machine
and is accessed from this server through a reverse SSH tunnel on the
default adb port 5037. The server's own local adb server is kept stopped
so plain `adb` talks straight to the emulator — no env vars needed.

```
Windows (emulator + adb server :5037)
    --SSH reverse tunnel-->  this server 127.0.0.1:5037
    --plain adb / Gradle-->  install / shell / logcat / connectedTest
```

## 1. Start the tunnel (Windows PowerShell)

On the Windows machine, with the emulator running:

```powershell
# must show the emulator locally first
adb devices

# open a second PowerShell window, leave it running
ssh -N -v -R 127.0.0.1:5037:127.0.0.1:5037 root@169.58.196.107
```

- Replace `root` with your SSH user if different.
- Keep this window open for the whole test session. Closing it
  disconnects the emulator.
- No inbound firewall rule needed on Windows (outbound SSH only).
- adb versions should match both sides (server: 34.0.4). Mismatches
  cause `adb server version doesn't match`.
- If you had the old 6037 tunnel running, close that PowerShell window
  first, then start this one.

## 2. Verify (this server)

All commands run from the project root.

```bash
# 5037 must be owned by sshd (the tunnel), NOT by adb
ss -tlnp | grep 5037

# emulator must appear here directly
adb devices -l
# expected: emulator-5554  device  model:ASUS_I001D
```

Important: do NOT run plain `adb` before the tunnel is up — the adb
client auto-starts a local server on 5037, which then blocks the tunnel
with `remote port forwarding failed`. If that happens:

```bash
adb kill-server   # free 5037 on the server
# then re-run the ssh -R command on Windows
```

## 3. Install and launch the app

```bash
# fast path for iteration (debug build, emulator only)
./gradlew installDebug
adb shell am start -n com.example.medac/.MainActivity
```

Notes:

- To test the exact signed release binary instead:
  `./gradlew assembleRelease`, then
  `adb install -r app/build/outputs/apk/release/app-release.apk`.
- Per [AGENTS.md](./AGENTS.md), only signed release APKs are published
  to `/var/www/apks/`. Debug builds are for this emulator only.

## 4. Useful adb commands

```bash
adb devices -l            # list (expect emulator-5554)
adb shell                 # shell on the emulator
adb logcat                # live logs (Ctrl-C to stop)
adb logcat -d | tail -n 100            # last 100 log lines
adb logcat -d | grep -i medac          # app logs only
adb shell pm clear com.example.medac   # reset app data
adb uninstall com.example.medac        # remove app
adb shell screencap -p /sdcard/shot.png && adb pull /sdcard/shot.png
```

## 5. Automated tests

Unit tests run locally, no emulator needed:

```bash
./gradlew test
```

Instrumented tests need the emulator via the tunnel:

```bash
adb devices   # must show emulator-5554 first
./gradlew connectedDebugAndroidTest
```

Reports: `app/build/reports/androidTests/connected/`.

Existing suites:

- `app/src/test/` — unit tests (`./gradlew test`)
- `app/src/androidTest/` — instrumented tests (`connectedDebugAndroidTest`)

## 6. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `remote port forwarding failed` on Windows | Something owns 5037 on the server (usually a respawned local adb). Run `adb kill-server` on the server, then re-run the `ssh -R` command. Check owner with `ss -tlnp \| grep 5037` — it must be `sshd`, not `adb`. |
| `adb devices` empty on server | Tunnel is down. Re-run the `ssh -R` command on Windows and keep it open. |
| `adb server version doesn't match` | Platform-tools differ. Align Windows adb with server 34.0.4. |
| `device unauthorized` / offline | Accept the RSA debugging prompt on the emulator screen. |
| `emulator-5554` missing on Windows too | Emulator not running or adb server stale: `adb kill-server && adb start-server` on Windows, then `adb devices`. |
| `ss -tlnp \| grep 5037` empty | No tunnel and no local adb. Nothing on the server can fix it; restart step 1 on Windows. |
| SSH asks for password every time | Set up key auth (`ssh-copy-id`) or use `autossh` for persistence. |

## 7. Quick reference

```bash
# Windows (keep running):
ssh -N -R 127.0.0.1:5037:127.0.0.1:5037 root@169.58.196.107

# Server:
adb devices -l
./gradlew installDebug
./gradlew connectedDebugAndroidTest
```
