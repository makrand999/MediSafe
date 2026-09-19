# APK Hosting & Distribution Context

This document outlines the setup and instructions for hosting and serving Android APK files on this server.

---

## ⚠️ Important Build Requirement: Sharable Builds Only

> **Rule:** Only upload **sharable release builds** (e.g., signed release APKs). **DO NOT** post debug builds (`*-debug.apk`).

- **Why no debug builds?**
  - Debug builds contain developer certificates that may fail to install or update on external devices.
  - They include debug flags, verbose logging, and potential performance overhead.
  - Standard Android security settings often block installation of untrusted debug packages.
- **Recommended naming:** Use release or versioned names such as `app-release.apk`, `medac-v1.0.0.apk`, etc., rather than `app-debug.apk`.

---

## 1. Directory Paths

| Item | Path | Description |
|---|---|---|
| **Storage Directory** | `/var/www/apks/` | Physical directory where APK files are stored |
| **Alternate / Legacy Path** | `/var/www/apps/` | Previous apps download directory with custom `index.html` |
| **Nginx Config** | `/etc/nginx/sites-available/default` | Virtual host config defining routes |

---

## 2. Web Access URLs

- **Directory listing:** `http://<server-ip-or-domain>/apks/`
- **Direct file download:** `http://<server-ip-or-domain>/apks/<filename>.apk`

*(The existing route `http://<server-ip-or-domain>/apps/` also remains active).*

---

## 3. Nginx Configuration

The `/apks` route is configured in `/etc/nginx/sites-available/default`:

```nginx
# Android APK downloads on /apks
location /apks {
    alias /var/www/apks/;
    autoindex on;
    autoindex_exact_size off;
    autoindex_localtime on;

    # Serve .apk files as attachments with correct MIME type
    location ~ \.apk$ {
        default_type application/vnd.android.package-archive;
        add_header Content-Disposition "attachment";
    }
}
```

### Key Features:
- **`autoindex on`**: Automatically displays an index list of files when visiting `/apks/`.
- **MIME Type**: Serves APKs with `application/vnd.android.package-archive`.
- **Content-Disposition**: Forces browser download instead of attempting to open/preview.

---

## 4. How to Post / Upload New APKs

### Option A: Direct Copy on Server
If the APK is already on the server or generated locally:
```bash
cp /path/to/your-app.apk /var/www/apks/
chown www-data:www-data /var/www/apks/your-app.apk
chmod 644 /var/www/apks/your-app.apk
```

### Option B: From Remote Machine via SCP
```bash
scp ./app-release.apk root@<server-ip>:/var/www/apks/
```

### Option C: From Remote Machine via Rsync
```bash
rsync -avP ./app-release.apk root@<server-ip>:/var/www/apks/
```

---

## 5. Permissions & Maintenance

Ensure permissions are maintained so Nginx can read the files:
```bash
chown -R www-data:www-data /var/www/apks
chmod -R 755 /var/www/apks
chmod 644 /var/www/apks/*.apk
```

To test and reload Nginx after any configuration changes:
```bash
nginx -t && systemctl reload nginx
```
