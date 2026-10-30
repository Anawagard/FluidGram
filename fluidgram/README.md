# FluidGram v0.2 — Telegram for OnePlus 15 / ColorOS 17

**Unofficial Telegram client (GPL-2.0-or-later).** Reviewable source overlay for a pinned official Android Telegram source revision: `3de37c8cfb039452be7398909461da6288babf15` (13.1.1 at project start). This project is a development prototype, **not yet a tested or distributable working messenger APK**.

## Completed in v0.2

- Independent Android app ID `dev.fluidgram.oneplus15.web`, original teal circle icon and clear unofficial branding.
- FluidMotion: 440-ms camera-flip transition with custom ColorOS-inspired easing, respecting Android system animation scale.
- Prevents overlapping camera-switch animations; adds a 560-ms button debounce.
- FluidCameraFps: reads supported Camera2 auto-exposure FPS ranges, prefers fixed **30 FPS if advertised**, otherwise the narrowest range containing 30 FPS and then the closest supported fallback. It does **not** force 60 FPS or alter the encoder; actual recording FPS remains subject to the device and encoder.
- Initial ColorOS-inspired light palette: cool background, teal accent and sent bubbles (not a complete ColorOS theme).
- CI: unit tests and actual patch-compatibility check against the upstream SHA, with no personal secrets.
- A separate gated Android build that refuses to produce a login-capable APK until private API, Firebase, and signing material are configured.

## Get a working APK

Create a dedicated GitHub repository for FluidGram; keep the source public to meet upstream GPL requirements. On GitHub, add these repository-level **Actions secrets** (do not paste them into a chat or source file):

| Secret | Contents |
| --- | --- |
| `FLUIDGRAM_API_ID` | Your own Telegram application ID from https://my.telegram.org |
| `FLUIDGRAM_API_HASH` | The matching hex API hash |
| `FLUIDGRAM_GOOGLE_SERVICES_JSON_B64` | Base64 encoded Firebase `google-services.json` including `dev.fluidgram.oneplus15.web` |
| `FLUIDGRAM_KEYSTORE_B64` | Base64 encoded dedicated APK signing keystore |
| `FLUIDGRAM_KEY_ALIAS` | Keystore signing alias |
| `FLUIDGRAM_STORE_PASSWORD` | Keystore store password |
| `FLUIDGRAM_KEY_PASSWORD` | Key password |

**Never use Telegram's public sample API credentials or release signing key for distributing FluidGram.** Back up your own keystore in a safe private place; loss means signed updates cannot use the same certificate. Do not publish APKs built with test-only credentials. Use a valid Firebase project for the final app; if Firebase registration is unavailable, the Android build configuration must be revised and tested first.

Go to **GitHub Actions → FluidGram Android APK (OnePlus 15) → Run workflow**. When secrets are missing, the validation jobs can pass but the APK stage is skipped. After a real signed build, the downloadable artifact `FluidGram-OnePlus15-alpha2` contains `app.apk` and a SHA-256 checksum. The first build may take substantial CI resources due to Telegram's native libraries.

## Local source validation

```bash
python3 -m unittest discover -s fluidgram/tests -v
python3 fluidgram/scripts/check_upstream.py
```

Integration test downloads only **public upstream source files** pinned to a SHA, injects **invalid placeholders only inside temporary files**, applies the patch, and checks expected results. It neither produces nor publishes a usable APK.

## Limits & next steps

- No OnePlus 15 hardware test, no proven improvement to sound synchronization, video fps, preview freezes or camera crashes yet.
- The original recording and codec pipeline remains unchanged; only Camera2 advertised FPS choice and switch-button UI guard were modified.
- Non-default Telegram themes can override these palette defaults; comprehensive ColorOS dark/light themes and chat animation polish remain later milestones.
- All patches should be verified on the real device for startup, login, 2FA, notifications, camera, calls, privacy, and security. See `docs/ONEPLUS15_TEST_PLAN.md`.

Official Telegram source: https://github.com/DrKLO/Telegram | License: GPL-2.0-or-later. No affiliation or endorsement by Telegram or OnePlus.