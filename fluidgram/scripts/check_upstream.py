#!/usr/bin/env python3
"""Check pinned public Telegram source compatibility with placeholder (invalid) credentials."""
import tempfile
import urllib.request
from pathlib import Path
from apply_fluidgram import UPSTREAM_SHA, PACKAGE, apply

FILES = (
    "gradle.properties",
    "TMessagesProj/build.gradle",
    "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java",
    "TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java",
    "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java",
    "TMessagesProj/src/main/assets/darkblue.attheme",
    "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java",
    "TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java",
    "TMessagesProj/src/main/java/org/telegram/messenger/camera/Camera2Session.java",
    "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ThemeColors.java",
    "TMessagesProj/src/main/res/values/strings.xml",
    "TMessagesProj/src/main/res/values/styles.xml",
    "TMessagesProj/config/release/AndroidManifest_standalone.xml",
)

def verify():
    with tempfile.TemporaryDirectory(prefix="fluidgram-compat-") as folder:
        root = Path(folder)
        for name in FILES:
            dest = root / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            url = f"https://raw.githubusercontent.com/DrKLO/Telegram/{UPSTREAM_SHA}/{name}"
            request = urllib.request.Request(url, headers={"User-Agent": "FluidGram-Compat/0.2"})
            with urllib.request.urlopen(request, timeout=45) as response:
                dest.write_bytes(response.read())
        (root / "TMessagesProj/src/main/res/drawable").mkdir(parents=True, exist_ok=True)
        apply(root, "123456", "0" * 32)  # invalid, not for login or distribution
        camera = (root / "TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java").read_text(encoding="utf8")
        fps = (root / "TMessagesProj/src/main/java/org/telegram/messenger/camera/Camera2Session.java").read_text(encoding="utf8")
        palette = (root / "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ThemeColors.java").read_text(encoding="utf8")
        props = (root / FILES[0]).read_text(encoding="utf8")
        assert "google-services plugin is applied by AppStandalone only" in (root / "TMessagesProj/build.gradle").read_text(encoding="utf8")
        assert "flipAnimationInProgress)" in camera
        assert "FluidMotion.videoEncoderFrameRate(videoEncoder, VIDEO_MIME_TYPE, videoWidth, videoHeight, fluidPrefer60Fps)" in camera
        assert "fluidSwitchingCamera" in camera
        assert "oldSession.destroy(true, openNewCamera)" in camera
        assert "camera_haptic" in camera
        settings = (root / "TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java").read_text(encoding="utf8")
        launcher = (root / "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java").read_text(encoding="utf8")
        manifest = (root / "TMessagesProj/config/release/AndroidManifest_standalone.xml").read_text(encoding="utf8")
        labs = (root / "TMessagesProj/src/main/java/org/telegram/ui/FluidLabsActivity.java").read_text(encoding="utf8")
        refresh = (root / "TMessagesProj/src/main/java/org/telegram/ui/FluidRefresh.java").read_text(encoding="utf8")
        assert "FluidGram Labs" in settings and "case 27:" in settings
        assert "FluidLabsActivity.class" in settings
        assert "FluidRefresh.resume(this)" in launcher
        assert "FluidRefresh.pause(this)" in launcher
        assert 'android:name="org.telegram.ui.FluidLabsActivity"' in manifest
        assert 'android:exported="false"' in manifest
        assert "Экран · частота HUD" in labs and "Glass highlights" in labs
        assert "FluidGlass.surface(this, true)" in labs
        assert "FluidGlass.setAmbientBlur(glassBackdrop, false)" in labs
        glass = (root / "TMessagesProj/src/main/java/org/telegram/ui/FluidGlass.java").read_text(encoding="utf8")
        assert "RenderEffect.createBlurEffect" in glass
        assert "ColorOS · Автоматически" in labs
        assert "preferredRefreshRate = 0f" in refresh
        assert "getSupportedModes()" in refresh
        assert "preferredDisplayModeId = best.getModeId()" in refresh
        assert "batteryPercent" in refresh
        assert "registerDisplayListener(displayListener, adaptiveHandler)" in refresh
        assert "unregisterDisplayListener(displayListener)" in refresh
        assert "postFrameCallback" not in refresh
        # Catch literal backslash-n leaked into Java by Python string escaping.
        assert r"setPrefer60Fps(fluidPrefer60Fps);\n" not in camera
        assert "camera2Sessions[a].setPrefer60Fps(fluidPrefer60Fps);\n" in camera
        assert "camera2SessionCurrent.setPrefer60Fps(fluidPrefer60Fps);\n" in camera
        assert "FluidGram: smoother video mode" in camera
        assert "lastFluidPreviewTapUptime" in camera
        assert "FluidMotion.isOnePlus15()" in camera
        assert "\"Dark Blue\"" in (root / "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java").read_text(encoding="utf8")
        assert "fluidglass_v03_applied" not in (root / "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java").read_text(encoding="utf8")
        assert "chat_outBubble=#ff1c4951" in (root / "TMessagesProj/src/main/assets/darkblue.attheme").read_text(encoding="utf8")
        assert "FluidMotion.cameraFlipDurationMillis()" in camera
        assert "FluidCameraFps.choose(" in fps
        assert "CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES" in fps
        assert "0xffDDF7F4" in palette
        assert f"APP_PACKAGE={PACKAGE}" in props
        assert "APP_VERSION_CODE=7244" in props
        assert "FluidGram (unofficial)" in (root / "TMessagesProj/src/main/res/values/strings.xml").read_text(encoding="utf8")
        print("OK: applied to pinned source; no APK compiled, no private credentials")

if __name__ == "__main__":
    verify()
