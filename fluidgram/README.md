# FluidGram v1.0 — Energy Lab / ColorOS 17 glass

Unofficial Telegram Android build for OnePlus 15 / ColorOS 17. Overlay is applied to pinned upstream Telegram at `3de37c8cfb039452be7398909461da6288babf15`.

## What is implemented

- **Glass Labs:** translucent glass surfaces, static specular highlights, decorative ambient blur on Android 12+, fallback without blur. Blur is suspended when the Labs activity is paused, the battery is at or below 20%, or Android power saver is enabled.
- **Refresh hints:** ColorOS automatic, high-refresh request, highest advertised compatible mode, smart low-battery fallback, and scroll-only adaptive. Android and ColorOS decide the actual physical refresh. Neither 1 Hz nor 165 Hz is guaranteed.
- **Power guard:** scroll hints coalesced to at most ten updates/second, released when idle or when the activity is paused. Display HUD is event-driven, not a per-frame FPS monitor.
- **Energy Lab (v1.0):** battery charge counter snapshots in microamp-hours, foreground duration, and instantaneous current if the device exposes these sensors. Sampling occurs only on foreground lifecycle transitions and user-requested reports; no polling, wake locks, or background service. Reports can be copied and the comparison baseline reset. **Battery counters measure the whole device, not isolated FluidGram consumption.** Compare under similar conditions and never infer battery savings from a single reading.
- **Camera:** round-video 60/LIGHT toggle (subject to camera support), camera flip safeguards, optional haptics.
- **Privacy:** diagnostic clipboard reports contain device display/power readings, not chats, API keys or VPN secrets.

## Planned, not yet implemented

A built-in VPN client is under investigation. A real tunnel requires a vetted transport engine, Android `VpnService` user consent, secure profile/key storage, and a server or user-provided profile. No VPN connection is claimed by this version.

## Build and verification

GitHub Actions runs unit/regression checks and the pinned-upstream overlay check, then builds a signed APK when the required private build secrets are present. Artifact: `FluidGram-OnePlus15-v10-Energy-Lab`, containing APK and `FluidGram-APK-SHA256.txt`. A passing source test is not proof of device-specific refresh rate or measured power reduction.

The package is `dev.fluidgram.oneplus15.web`; versionCode `7245`. GPL-2.0-or-later, based on official Telegram sources.
