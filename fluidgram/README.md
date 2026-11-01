# FluidGram v1.4 — thermal-aware glass and adaptive refresh / VPN audit / Energy Lab

Unofficial Telegram Android build for OnePlus 15 / ColorOS 17. Overlay is applied to pinned upstream Telegram at `3de37c8cfb039452be7398909461da6288babf15`.

## What is implemented

- **Thermal guard (v1.4):** on Android 10+ severe-or-higher system thermal status releases high-refresh requests and disables the decorative Labs blur. A foreground-only `PowerManager.OnThermalStatusChangedListener` restores the selected mode when the device cools; it is unregistered on pause. No temperature polling or per-frame callbacks. This is a protective policy, not a measured battery or temperature improvement. The Android thermal service may report different thresholds across devices.

- **Glass rendering optimization (v1.1):** glass-panel gradients, reflective paths, and ambient backdrop shaders are cached until bounds/size changes instead of allocated on every draw; redundant RenderEffect updates are suppressed. No per-frame loops. Battery savings still require device measurements.
- **Glass Labs:** translucent glass surfaces, static specular highlights, decorative ambient blur on Android 12+, fallback without blur. Blur is suspended when the Labs activity is paused, the battery is at or below 20%, or Android power saver is enabled.
- **Refresh hints:** ColorOS automatic, high-refresh request, highest advertised compatible mode, smart low-battery fallback, and scroll-only adaptive. Android and ColorOS decide the actual physical refresh. Neither 1 Hz nor 165 Hz is guaranteed.
- **Power guard:** scroll hints coalesced to at most ten updates/second, released when idle or when the activity is paused. Display HUD is event-driven, not a per-frame FPS monitor.
- **Energy Lab (v1.0):** battery charge counter snapshots in microamp-hours, foreground duration, and instantaneous current if the device exposes these sensors. Sampling occurs only on foreground lifecycle transitions and user-requested reports; no polling, wake locks, or background service. Reports can be copied and the comparison baseline reset. **Battery counters measure the whole device, not isolated FluidGram consumption.** Compare under similar conditions and never infer battery savings from a single reading.
- **Camera:** round-video 60/LIGHT toggle (subject to camera support), camera flip safeguards, optional haptics.
- **Privacy:** diagnostic clipboard reports contain device display/power readings, not chats, API keys or VPN secrets.

## VPN profile safety audit (v1.3) — NOT a working tunnel

- FluidGram Labs now offers a user-selected WireGuard `.conf` file picker and bounded, read-only validation (64 KiB maximum). The parser checks Interface/Peer essentials, validates DNS hostnames and numeric IPv4/IPv6 DNS entries, rejects duplicate default routes and malformed endpoint hostnames, and reports only the peer count, route coverage and interface address-family coverage, and does not persist credentials, endpoint addresses, URI permissions, or files. The raw input buffer is wiped after parsing; transient Java strings are garbage-collected. Syntax checks are not a cryptographic verification or a network connectivity test.
- A separate card opens Android system VPN settings to manage an independently installed VPN. No VPN service is started by FluidGram and no traffic is routed through FluidGram. The UI explicitly marks built-in connection unavailable.
- The import feature is foreground-only, with no polling, wake locks, background service, or network activity. This avoids introducing new idle battery drain.

**Energy and privacy:** While Labs is visible, the last Android battery broadcast is reused for glass policy and energy-card rendering; the cached percentage is cleared on pause. Profile inspection runs only after user file selection; no periodic network checks, no background VPN service, and no stored credentials. Route coverage and address-family flags are only static configuration hints, never proof of a leak-free connection.

## Planned, not yet implemented

A functional built-in VPN client remains under investigation. A real tunnel requires a vetted transport engine, Android `VpnService` user consent, secure profile/key storage, and a server or user-provided profile. No VPN connection is claimed by this version.

## Build and verification

GitHub Actions runs unit/regression checks and the pinned-upstream overlay check, then builds a signed APK when the required private build secrets are present. Artifact: `FluidGram-OnePlus15-v14-Thermal-Glass-Guard`, containing APK and `FluidGram-APK-SHA256.txt`. A passing source test is not proof of device-specific refresh rate or measured power reduction.

The package is `dev.fluidgram.oneplus15.web`; versionCode `7248`. GPL-2.0-or-later, based on official Telegram sources.
