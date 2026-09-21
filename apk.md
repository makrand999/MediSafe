# APK Rebuild & Post — Agent Instruction

> **TRIGGER:** Whenever the user references this file (`@apk.md`, `apk.md`,
> "rebuild the app", "post the apk", "rebuild and post") — **DO IT, don't explain it.**
> Rebuild the release APK from current source and post it. No confirmation needed.
> No debug builds. No skipping the build and re-uploading a stale APK.

## Task (execute in order)

Run from the **project root** (the directory containing this file and `gradlew`).

### 1. Build release APK

```bash
chmod +x gradlew
./gradlew assembleRelease
```

- Must succeed with `BUILD SUCCESSFUL`.
- Expected output: `app/build/outputs/apk/release/app-release.apk`
- If the build fails: show the last ~50 lines of the error and stop. Check:
  - `local.properties` → `sdk.dir` (current: `/home/dev/android-sdk`)
  - `release.keystore` exists in project root (alias `medisafe`)
  - Java toolchain available

### 2. Verify the fresh build

```bash
ls -lh app/build/outputs/apk/release/app-release.apk
```

- File must exist, be freshly timestamped, and be **release** (`app-release.apk`).
- **NEVER** post `*-debug.apk`: debug certs fail on external devices, carry debug
  flags/logging, and are often blocked by Android security settings.

### 3. Post it (stable filename)

The public stable file is **`medisafe.apk`**. Always overwrite it so the URL never changes:

```bash
cp app/build/outputs/apk/release/app-release.apk /var/www/apks/medisafe.apk
chown www-data:www-data /var/www/apks/medisafe.apk
chmod 644 /var/www/apks/medisafe.apk
```

- If permission denied, retry with `sudo` and report that.
- Optional versioned copy only if the user explicitly asks
  (e.g. `medac-v1.0.0.apk` alongside `medisafe.apk`).

### 4. Verify the post

```bash
ls -lh /var/www/apks/
curl -sI http://localhost/apks/medisafe.apk | head -n 10
```

- `Content-Type` should be `application/vnd.android.package-archive`.
- Fix perms if Nginx can't read it:
  ```bash
  chown -R www-data:www-data /var/www/apks
  chmod 755 /var/www/apks
  chmod 644 /var/www/apks/*.apk
  ```

### 5. Report back

Always end with:

- Local build path + size + timestamp
- Posted path: `/var/www/apks/medisafe.apk`
- Download URL: `http://<server-ip-or-domain>/apks/medisafe.apk`
- Listing URL: `http://<server-ip-or-domain>/apks/`
- Build result (`BUILD SUCCESSFUL` or the failure excerpt)

Get the host for the URL from context, or `hostname -I` if unknown.

---

## Reference

### Paths

| Item | Path | Description |
|---|---|---|
| Storage directory | `/var/www/apks/` | Live APK dir (autoindex listing) |
| Stable file | `/var/www/apks/medisafe.apk` | **Always overwrite this** |
| Legacy path | `/var/www/apps/` | Old dir with custom `index.html` |
| Live Nginx config | `/etc/nginx/sites-enabled/default` | Defines `/apks` and `/apps` routes |
| Build output | `app/build/outputs/apk/release/app-release.apk` | Fresh Gradle output |

### Web access

- Listing: `http://<server-ip-or-domain>/apks/`
- Direct download: `http://<server-ip-or-domain>/apks/medisafe.apk`
- Legacy route `http://<server-ip-or-domain>/apps/` also active.

### Nginx (`/apks` block in `/etc/nginx/sites-enabled/default`)

```nginx
location /apks {
    alias /var/www/apks/;
    autoindex on;
    autoindex_exact_size off;
    autoindex_localtime on;

    location ~ \.apk$ {
        default_type application/vnd.android.package-archive;
        add_header Content-Disposition "attachment";
    }
}
```

After any Nginx config change: `nginx -t && systemctl reload nginx`.

### Remote-upload alternatives (only if the agent is NOT on the server)

```bash
scp ./app-release.apk root@<server-ip>:/var/www/apks/medisafe.apk
rsync -avP ./app-release.apk root@<server-ip>:/var/www/apks/medisafe.apk
```
