# Signed release APK

Debug APKs (from **Android APK**) are fine for personal testing. For a release build:

1. **Create a keystore** (keep it safe; losing it means you can't update the app):
   - From the phone: install **Termux** (F-Droid), then
     `pkg install openjdk-17` and
     `keytool -genkeypair -v -keystore swarai.jks -alias swarai -keyalg RSA -keysize 4096 -validity 10000`,
     then `base64 -w0 swarai.jks > swarai.b64`.
2. In GitHub: **Settings → Secrets and variables → Actions → New repository secret**, add:
   `SWARAI_KEYSTORE_BASE64` (contents of `swarai.b64`), `SWARAI_KEYSTORE_PASSWORD`, `SWARAI_KEY_ALIAS` (`swarai`),
   `SWARAI_KEY_PASSWORD`.
3. **Actions → Android signed release APK → Run workflow**, enter a version. Leave *create_draft_release* off unless you
   want a **draft** GitHub Release (nothing is published until you publish the draft yourself).

The keystore is decoded to the runner's temp dir, used, and deleted; it is never committed.
