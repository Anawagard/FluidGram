#!/usr/bin/env python3
"""Apply FluidGram v0.2 overlay to pinned Telegram Android checkout."""
import argparse
import os
import re
from pathlib import Path

PACKAGE = "dev.fluidgram.oneplus15"
UPSTREAM_SHA = "3de37c8cfb039452be7398909461da6288babf15"
MOTION_CLASS = """/* FluidGram. GPL-2.0-or-later. */
package org.telegram.ui.Components;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** Motion curve. Android ValueAnimator honours system animation scale. */
public final class FluidMotion {
    private FluidMotion() {}
    public static long cameraFlipDurationMillis() { return 440L; }
    public static Interpolator cameraFlipInterpolator() {
        return new PathInterpolator(0.18f, 0.00f, 0.16f, 1.00f);
    }
}
"""
ICON = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#0B1730" android:pathData="M0,0h108v108h-108z"/>
    <path android:fillColor="#40DCCF" android:pathData="M54,14 a40,40 0,1 0,0 80 a40,40 0,1 0,0 -80"/>
    <path android:fillColor="#122C50" android:pathData="M54,28 a26,26 0,1 0,0 52 a26,26 0,1 0,0 -52"/>
    <path android:fillColor="#BDFBF3" android:pathData="M54,42 a12,12 0,1 0,0 24 a12,12 0,1 0,0 -24"/>
    <path android:fillColor="#E7FDFB" android:pathData="M51,53h18v5h-18z"/>
</vector>
"""

FPS_CLASS = "/* FluidGram camera support. GPL-2.0-or-later. */\npackage org.telegram.messenger.camera;\nimport android.util.Range;\n/** Never set an unadvertised Camera2 frame-rate range. */\npublic final class FluidCameraFps {\n    private FluidCameraFps() {}\n    public static Range<Integer> choose(Range<Integer>[] advertised) {\n        if (advertised == null || advertised.length == 0) return null;\n        Range<Integer> best30 = null;\n        Range<Integer> closest = null;\n        for (Range<Integer> range : advertised) {\n            if (range == null) continue;\n            int low = range.getLower(), high = range.getUpper();\n            if (low <= 0 || high < low) continue;\n            if (low == 30 && high == 30) return range;\n            if (low <= 30 && high >= 30 && (best30 == null ||\n                    high - low < best30.getUpper() - best30.getLower())) best30 = range;\n            if (closest == null ||\n                    Math.abs(high - 30) < Math.abs(closest.getUpper() - 30)) closest = range;\n        }\n        return best30 != null ? best30 : closest;\n    }\n}\n"
THEME_CHANGES = {
    "public static final int TELEGRAM_COLOR = 0xFF229AF0;": "public static final int TELEGRAM_COLOR = 0xFF16A6A2;",
    "public static final int TELEGRAM_COLOR_TEXT = 0xFF298ACF;": "public static final int TELEGRAM_COLOR_TEXT = 0xFF087C85;",
    "defaultColors[key_actionBarDefault] = 0xffffffff;": "defaultColors[key_actionBarDefault] = 0xffF4F8FC;",
    "defaultColors[key_windowBackgroundGray] = 0xffF1F1F3;": "defaultColors[key_windowBackgroundGray] = 0xffEDF3F8;",
    "defaultColors[key_dialogSearchBackground] = 0xfff2f4f5;": "defaultColors[key_dialogSearchBackground] = 0xffF4F8FC;",
    "defaultColors[key_chat_outBubble] = 0xffefffde;": "defaultColors[key_chat_outBubble] = 0xffDDF7F4;",
    "defaultColors[key_chat_outBubbleSelected] = 0xffd9f7c5;": "defaultColors[key_chat_outBubbleSelected] = 0xffCAEEE9;",
    "defaultColors[key_chat_outMediaIcon] = 0xffefffde;": "defaultColors[key_chat_outMediaIcon] = 0xffDDF7F4;",
    "defaultColors[key_chat_outMediaIconSelected] = 0xffe1f8cf;": "defaultColors[key_chat_outMediaIconSelected] = 0xffCAEEE9;"
}

def replace_exact(source, old, new, path):
    n = source.count(old)
    if n != 1:
        raise RuntimeError(f"{path}: expected one anchor, found {n}: {old[:65]!r}")
    return source.replace(old, new, 1)

def rewrite(path, fn):
    if not path.is_file():
        raise RuntimeError(f"Missing upstream source: {path}")
    before = path.read_text(encoding="utf8")
    after = fn(before)
    if before == after:
        raise RuntimeError(f"Unchanged source: {path}")
    path.write_text(after, encoding="utf8")
    print(f"Patched {path}")

def apply(root, api_id, api_hash):
    if not re.fullmatch(r"[0-9]{4,12}", api_id):
        raise ValueError("Missing or invalid FLUIDGRAM_API_ID")
    if not re.fullmatch(r"[0-9a-fA-F]{32}", api_hash):
        raise ValueError("Missing or invalid FLUIDGRAM_API_HASH")
    props = root / "gradle.properties"
    rewrite(props, lambda s: replace_exact(s, "APP_PACKAGE=org.telegram.messenger", "APP_PACKAGE=" + PACKAGE, props))

    buildvars = root / "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java"
    def patch_vars(s):
        s = re.sub(r"public static int APP_ID = \d+;", f"public static int APP_ID = {api_id};", s, count=1)
        s = re.sub(r'public static String APP_HASH = "[0-9a-fA-F]+";',
                   f'public static String APP_HASH = "{api_hash}";', s, count=1)
        s = replace_exact(s, "public static boolean SUPPORTS_PASSKEYS = true;",
                          "public static boolean SUPPORTS_PASSKEYS = false;", buildvars)
        return replace_exact(s, "public static boolean CHECK_UPDATES = true;",
                             "public static boolean CHECK_UPDATES = false;", buildvars)
    rewrite(buildvars, patch_vars)

    camera = root / "TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java"
    guard = """            if (!cameraReady || !isCameraSessionInitiated() || cameraThread == null || flipAnimationInProgress) {
                return;
            }
            final long fluidNow = SystemClock.uptimeMillis();
            if (fluidNow - lastFluidSwitchUptime < 560L) {
                return;
            }
            lastFluidSwitchUptime = fluidNow;"""
    def patch_camera(s):
        s = replace_exact(s, "    private volatile boolean cameraReady;",
                          "    private volatile boolean cameraReady;\n    private long lastFluidSwitchUptime;", camera)
        s = replace_exact(s, """            if (!cameraReady || !isCameraSessionInitiated() || cameraThread == null) {
                return;
            }""", guard, camera)
        s = replace_exact(s, "            valueAnimator.setDuration(580);",
                          "            valueAnimator.setDuration(FluidMotion.cameraFlipDurationMillis());", camera)
        return replace_exact(s, "            valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);",
                             "            valueAnimator.setInterpolator(FluidMotion.cameraFlipInterpolator());", camera)
    rewrite(camera, patch_camera)
    motion = camera.parent / "FluidMotion.java"
    if motion.exists(): raise RuntimeError("FluidMotion exists already; apply only once")
    motion.write_text(MOTION_CLASS, encoding="utf8")


    session = root / "TMessagesProj/src/main/java/org/telegram/messenger/camera/Camera2Session.java"
    def patch_session(s):
        return replace_exact(s,
            "                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(30, 60));",
            """                final Range<Integer> fluidFpsRange = FluidCameraFps.choose(
                        cameraCharacteristics == null ? null : cameraCharacteristics.get(
                                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES));
                if (fluidFpsRange != null) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fluidFpsRange);
                }""", session)
    rewrite(session, patch_session)
    fps_file = session.parent / "FluidCameraFps.java"
    if fps_file.exists(): raise RuntimeError("FluidCameraFps exists already; apply once")
    fps_file.write_text(FPS_CLASS, encoding="utf8")
    palette = root / "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ThemeColors.java"
    def patch_palette(s):
        for before, after in THEME_CHANGES.items():
            s = replace_exact(s, before, after, palette)
        return s
    rewrite(palette, patch_palette)

    strings = root / "TMessagesProj/src/main/res/values/strings.xml"
    def patch_strings(s):
        s = replace_exact(s, '<string name="AppName">Telegram</string>',
                          '<string name="AppName">FluidGram (unofficial)</string>', strings)
        return replace_exact(s, '<string name="AppNameBeta">Telegram Beta</string>',
                             '<string name="AppNameBeta">FluidGram Beta</string>', strings)
    rewrite(strings, patch_strings)

    manifest = root / "TMessagesProj/config/release/AndroidManifest_standalone.xml"
    rewrite(manifest, lambda s: replace_exact(
        s, 'android:icon="@mipmap/ic_launcher_sa"\n        android:roundIcon="@mipmap/ic_launcher_sa"',
        'android:icon="@drawable/fluidgram_icon"\n        android:roundIcon="@drawable/fluidgram_icon"', manifest))
    icon = root / "TMessagesProj/src/main/res/drawable/fluidgram_icon.xml"
    if icon.exists(): raise RuntimeError("FluidGram icon already exists")
    icon.write_text(ICON, encoding="utf8")

    styles = root / "TMessagesProj/src/main/res/values/styles.xml"
    def patch_styles(s):
        start_tag = '<style name="Theme.TMessages.Start" parent="Theme.AppCompat.Light">'
        head, rest = s.split(start_tag, 1)
        section, tail = rest.split("</style>", 1)
        section = replace_exact(section, '<item name="android:windowBackground">@android:color/white</item>',
                                '<item name="android:windowBackground">#F4F8FC</item>', styles)
        return head + start_tag + section + "</style>" + tail
    rewrite(styles, patch_styles)
    print("FluidGram v0.2 applied. Install package: " + PACKAGE + ".web")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--telegram", required=True, type=Path)
    args = parser.parse_args()
    apply(args.telegram, os.environ.get("FLUIDGRAM_API_ID", ""), os.environ.get("FLUIDGRAM_API_HASH", ""))
