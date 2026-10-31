# FluidGram v0.3 Alpha — FluidGlass Dark + Camera Motion

Based on the pinned upstream Telegram Android source `3de37c8cfb039452be7398909461da6288babf15`. GPL-2.0-or-later; unofficial.

## New in v0.3

- Full bespoke **FluidGlass Dark** palette for menus, chat list, sent/received bubbles, dialogs, toolbar, and input surface. Dark slate surfaces and cyan/teal accents. This replaces the shipped Dark Blue preset.
- On first start of v0.3 only, migrate to FluidGlass Dark; the user can subsequently switch themes manually. Dark startup background.
- A shorter 330ms camera-flip animation, with 390ms cooldown and overlapping-flip protection. Existing Camera2 dual camera preopening remains.
- Camera2 requests a supported fixed 60 FPS range when available, otherwise the best advertised range containing 60, or a 30 FPS fallback.
- AVC encoder requests 60 FPS only if the selected encoder advertises support at the current video size; otherwise chooses 30 FPS.
- `APP_VERSION_CODE=7238` for upgrade from v0.2 (7237), keeping the same Android package and signing key.

**Device limits:** In low light, a camera can lower FPS when using variable ranges. A fixed 60fps exposure sacrifices image brightness/quality and is not guaranteed in all camera configurations. Encoder 60fps support alone does not confirm camera throughput, actual video fps, or A/V sync. Measure output video timestamps on the real OnePlus 15 before claiming smooth 60fps.

## Build

From the dedicated repo's GitHub Actions, `FluidGram-OnePlus15-alpha3` contains the signed APK and checksum if all GitHub Secrets are configured. Use your **own Telegram API credentials** for any public release. Test-only credentials are not appropriate for distribution or real-user login. Do not share signing secrets or Firebase config in source control.

Run `python -m unittest discover -s fluidgram/tests -v` and `python fluidgram/scripts/check_upstream.py` for source checks. The CI also performs a complete Gradle/native build. Keep the working v0.2 APK for rollback.

Telegram source: https://github.com/DrKLO/Telegram; no endorsement by Telegram or OnePlus.
