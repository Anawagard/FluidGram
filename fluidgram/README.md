# FluidGram v0.5 Alpha — Labs / ColorOS refresh profiles

Integrated a genuine **FluidGram Labs** entry in Telegram Settings with independent glass UI; profiles for the display, visual toggles and new diagnostic tools.

- **ColorOS Auto:** clears app refresh rate and display mode hints and lets the OS decide.
- **Adaptive+:** requests the highest advertised refresh rate for the app, with no guarantee ColorOS honors it.
- **Max up to 165Hz:** requests the fastest advertised display mode at the current resolution; only if advertised by Android. The OS may override it.
- **Smart battery:** requests high refresh but falls back to ColorOS control when battery <= 25% on activity resume.
- **VSync HUD:** display event sample readout, *not* actual app rendering or video encoding FPS.
- **Keep screen awake:** stays on while FluidGram activity is foreground.
- **Neon Glass UI:** distinctive cyan glowing options panel.
- **Camera haptics:** enables tap vibration when switching video-circle lenses.
- **Copy display modes:** local device refresh and resolution diagnostics, no user messages copied.

The OS decides actual panel refresh; if ColorOS auto is capped to 90Hz by firmware/app list, app cannot guarantee 165Hz through a public API. To prioritize higher FPS try Adaptive+ or Max instead.

v0.4 camera recovery and 60/LIGHT modes retained. Upgrades via versionCode 7241, same application ID `dev.fluidgram.oneplus15.web`, same signing keystore. GitHub Actions artifact `FluidGram-OnePlus15-v05-Labs-165Hz`.

GPL-2.0-or-later, based on official Telegram `3de37c8cfb039452be7398909461da6288babf15`. Never distribute binaries signed with test-only API credentials. Android API reference: https://developer.android.com/reference/android/view/WindowManager.LayoutParams.
