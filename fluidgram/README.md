# FluidGram v0.4 Alpha — stable camera switch and custom controls

Targets the reported crash/black screen when switching round-video recording **front → rear** on OnePlus 15 (CPH2747). The precise crash origin is unverified without an Android logcat.

For OnePlus 15 only, this version disables the simultaneous dual Camera2 opening and waits for asynchronous release of the previous session before creating the next. A failed rear-camera initialization returns to front, and a 3.5-second no-frame condition attempts recovery. It preserves the prior camera preview during switching and fades the overlay on the first new frame.

**Distinctive round-recording controls:**
- **60 FPS / LIGHT** control switches between supported 60fps priority and a 30fps-friendly low-light mode; choice persists. Toggling while recording changes the camera's AE FPS request immediately, but the video encoder mode only applies on the next recording.
- **Double tap the round preview** to switch front/rear, retaining pinch-to-zoom.
- Automatic front-camera recovery when rear activation fails.

No guarantee of true 60fps in low light; device hardware tests are required. The previous v0.2 APK remains the rollback baseline.

APK package: `dev.fluidgram.oneplus15.web`; versionCode `7240`. CI artifact: `FluidGram-OnePlus15-v04-camera-hotfix`. Telegram original pinned commit `3de37c8cfb039452be7398909461da6288babf15`. GPL-2.0-or-later. Do not distribute development binaries with public test Telegram API credentials.
