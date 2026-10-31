# FluidGram v0.3.1 Alpha — FluidGlass Dark startup hotfix

This patch prioritizes startup compatibility on OnePlus 15 / ColorOS 17 after reports that v0.3 closed on launch. There is no device-side crash log yet, so the exact cause is **unconfirmed**.

## Startup change

The initial v0.3 changed Telegram's internal "Dark Blue" theme identifiers across Theme.java and forced a one-time SharedPreferences migration at startup. v0.3.1 **removes both changes** to reduce boot-time risk and preserve the native theme-selection logic.

The FluidGlass colors remain in the app's modified **Dark Blue** preset. After the app opens, go to **Settings → Chat Settings / Appearance → Themes → Dark Blue** to apply FluidGlass. Do not expect it to be forced on first launch; this is a conservative recovery build.

The fast camera flip transition and conditional supported-60fps Camera2/AVC settings remain. 60fps cannot be guaranteed in poor light and still needs hardware testing. App versionCode increases from 7238 to 7239, preserving application ID and release signing keystore for an in-place update.

## Build & safety

GitHub Actions artifact: `FluidGram-OnePlus15-alpha3-1-safe-start`.

All changes are open source and based on official pinned Telegram Android source `3de37c8cfb039452be7398909461da6288babf15` (GPL-2.0-or-later). Signing keys and API credentials are GitHub Secrets, never committed. Do not distribute APKs with public test-only Telegram API credentials.

If startup still fails, collect a **redacted adb logcat** of the crash. Avoid uninstalling or clearing application data; clearing cache is less destructive. Android generally blocks downgrading to v0.2 while preserving app data, so an in-place hotfix with a higher versionCode is preferred.
