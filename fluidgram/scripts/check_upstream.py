#!/usr/bin/env python3
"""Check pinned public Telegram source compatibility with placeholder (invalid) credentials."""
import tempfile
import urllib.request
from pathlib import Path
from apply_fluidgram import UPSTREAM_SHA, PACKAGE, apply

FILES = (
    "gradle.properties",
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
        camera = (root / FILES[2]).read_text(encoding="utf8")
        fps = (root / FILES[3]).read_text(encoding="utf8")
        palette = (root / FILES[4]).read_text(encoding="utf8")
        props = (root / FILES[0]).read_text(encoding="utf8")
        assert "flipAnimationInProgress)" in camera
        assert "FluidMotion.cameraFlipDurationMillis()" in camera
        assert "FluidCameraFps.choose(" in fps
        assert "CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES" in fps
        assert "0xffDDF7F4" in palette
        assert f"APP_PACKAGE={PACKAGE}" in props
        assert "FluidGram (unofficial)" in (root / FILES[5]).read_text(encoding="utf8")
        print("OK: applied to pinned source; no APK compiled, no private credentials")

if __name__ == "__main__":
    verify()
