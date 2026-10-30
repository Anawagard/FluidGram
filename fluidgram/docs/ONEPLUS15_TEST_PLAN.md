# FluidGram v0.2 hardware acceptance — OnePlus 15 / ColorOS 17

1. Verify signed package `dev.fluidgram.oneplus15.web` installs alongside official Telegram. Check launcher icon, name, permissions and update signature.
2. Test account login and 2FA with own Telegram API credentials. Verify logout, multiple accounts, chats, sending photos and links.
3. Check circular video recording front/back: ten 10-second clips, ten 30-second clips and five 60-second clips. Inspect video and audio on a second phone.
4. While recording, switch front/back; attempt rapid double taps, 30 repeated switches. Verify no blank / green frames, stopped recording, audio cuts or leaked preview windows.
5. Compare Camera2 device-supported frame ranges with chosen 30-FPS preference; measure actual encoded FPS/bitrate (not simply UI rendering rate) and dropped frames.
6. Test recording with low light, bright light, other apps using microphone/camera, screen rotation and incoming calls. Observe ColorOS 17 camera/mic privacy indicators.
7. Test dark/light themes, accessibility reduce motion, display refresh rate, gesture navigation, status-bar cutout and back gestures.
8. Monitor temperature, RAM, battery, notifications and background app behavior with official Telegram as a baseline. Collect logs with personal contents redacted.

**Pass criteria:** no recorded-message loss, no unexpected camera switches, no camera permission regressions, 30 FPS when supported without visible instability, and no material increase in crashes/thermal drain over the official client. Numbers above are QA targets, not achieved results.