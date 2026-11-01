# FluidGram v1.6 — experimental WireGuard VPN / ColorOS glass / Energy Lab

Unofficial Telegram Android build for OnePlus 15 / ColorOS 17. Overlay is applied to pinned upstream Telegram at `3de37c8cfb039452be7398909461da6288babf15`.

## What is implemented

- **Experimental WireGuard connection (v1.6):** official WireGuard Android tunnel library, Android VpnService consent, manual connect/disconnect, user-supplied single .conf profile held only in memory, and app-specific routing restricted to FluidGram. No bundled server, subscription, auto-connect, or key persistence. **Not yet proven on a physical device or real server.**
- **Idle energy guard (v1.6):** adaptive-scroll listener is not registered while battery, thermal or power-saver guard blocks high refresh. Guard cache resets on foreground return. This reduces observer work but does not establish measurable battery savings.

- **Thermal guard (v1.4):** on Android 10+ severe-or-higher system thermal status releases high-refresh requests and disables the decorative Labs blur. A foreground-only `PowerManager.OnThermalStatusChangedListener` restores the selected mode when the device cools; it is unregistered on pause. No temperature polling or per-frame callbacks. This is a protective policy, not a measured battery or temperature improvement. The Android thermal service may report different thresholds across devices.

- **Glass rendering optimization (v1.1):** glass-panel gradients, reflective paths, and ambient backdrop shaders are cached until bounds/size changes instead of allocated on every draw; redundant RenderEffect updates are suppressed. No per-frame loops. Battery savings still require device measurements.
- **Glass Labs:** translucent glass surfaces, static specular highlights, decorative ambient blur on Android 12+, fallback without blur. Blur is suspended when the Labs activity is paused, the battery is at or below 20%, or Android power saver is enabled.
- **Refresh hints:** ColorOS automatic, high-refresh request, highest advertised compatible mode, smart low-battery fallback, and scroll-only adaptive. Android and ColorOS decide the actual physical refresh. Neither 1 Hz nor 165 Hz is guaranteed.
- **Power guard:** scroll hints coalesced to at most ten updates/second, released when idle or when the activity is paused. Display HUD is event-driven, not a per-frame FPS monitor.
- **Energy Lab (v1.0):** battery charge counter snapshots in microamp-hours, foreground duration, and instantaneous current if the device exposes these sensors. Sampling occurs only on foreground lifecycle transitions and user-requested reports; no polling, wake locks, or background service. Reports can be copied and the comparison baseline reset. **Battery counters measure the whole device, not isolated FluidGram consumption.** Compare under similar conditions and never infer battery savings from a single reading.
- **Camera:** round-video 60/LIGHT toggle (subject to camera support), camera flip safeguards, optional haptics.
- **Privacy:** diagnostic clipboard reports contain device display/power readings, not chats, API keys or VPN secrets.

## Experimental WireGuard VPN (v1.6)

The official `com.wireguard.android:tunnel:1.0.20260102` library (Apache-2.0)
provides the WireGuard userspace transport and its Android `VpnService`.
FluidGram requests Android's VPN authorization and never silently connects.
The user selects a WireGuard `.conf` file (maximum 64 KiB); the existing
strict preflight validates its syntax, then the official parser builds the
tunnel configuration. A new interface configuration restricts Android VPN
routing to `getPackageName()` (FluidGram only), ignoring external app-routing
instructions. This is *app-specific VPN*, not a system-wide VPN.

- **Security:** No credentials, endpoint addresses or file URIs are persisted;
  raw input bytes are wiped. Parsed WireGuard key objects necessarily remain
  in process memory while needed and are subject to Java GC after release.
  No FluidGram logging of credentials. The third-party backend may emit its
  own diagnostic logs; do not assume all system or library logs are secret-free.
- **Energy:** No periodic keep-awake tasks or background polling added by
  FluidGram. A user-configured WireGuard PersistentKeepalive may consume
  network/battery; the VPN engine also requires ongoing networking when active.
- **Limitations:** A VPN status of UP means the local tunnel started, *not*
  that the remote server is reachable, DNS/IPv6 is leak-free, or Telegram is
  unblocked. No verified live server/device test yet. One profile at a time;
  profiles are not retained after Labs is destroyed. No server picker beyond
  importing another profile, no built-in network diagnostics or auto-reconnect.
  If the VPN drops, ordinary app networking may resume unless Android's
  always-on VPN lockdown is configured. **Caution:** lockdown combined with
  app-only VPN routing may block networking for other applications.
  This feature is not a kill switch.
  Android allows only one VPN provider at a time; enabling FluidGram VPN may
  replace another VPN.
- **Testing:** `test_vpn_tunnel.py` compiles the real controller with
  deterministic Android/WireGuard stubs and checks permission gating, import
  size, per-app scope and connect/disconnect state transitions. This does not
  prove native Android integration or network connectivity.

## Verification still required

Confirm Gradle compiles against the pinned official library and Telegram,
then install the signed APK on OnePlus 15 / ColorOS 17, approve the system VPN
dialog, import a known-good user-owned WireGuard profile and test both IPv4
and IPv6 routing, DNS, network loss, app-only routing, notifications, and
disconnect/reconnect. Measure foreground/background power and FPS before/after
under comparable conditions. **Do not claim battery savings, 165 Hz, or VPN
connectivity until those device measurements succeed.**

## Build and verification

GitHub Actions runs unit/regression checks and the pinned-upstream overlay check, then builds a signed APK when the required private build secrets are present. Artifact: `FluidGram-OnePlus15-v16-WireGuard-Glass`, containing APK and `FluidGram-APK-SHA256.txt`. A passing source test is not proof of device-specific refresh rate or measured power reduction.

The package is `dev.fluidgram.oneplus15.web`; versionCode `7248`; minSdk 24 (WireGuard library requirement). GPL-2.0-or-later, based on official Telegram sources.
