#!/usr/bin/env python3
"""Apply FluidGram v0.3 FluidGlass overlay to pinned Telegram Android checkout."""
import argparse
import os
import re
from pathlib import Path

PACKAGE = "dev.fluidgram.oneplus15"
UPSTREAM_SHA = "3de37c8cfb039452be7398909461da6288babf15"
MOTION_CLASS = "/* GPL-2.0-or-later. FluidGram v0.3 */\npackage org.telegram.ui.Components;\nimport android.media.MediaCodec;\nimport android.media.MediaCodecInfo;\nimport android.view.animation.Interpolator;\nimport android.view.animation.PathInterpolator;\npublic final class FluidMotion {\n    private FluidMotion() {}\n    public static long cameraFlipDurationMillis() { return 330L; }\n    public static Interpolator cameraFlipInterpolator() {\n        return new PathInterpolator(0.20f, 0f, 0.15f, 1.00f);\n    }\n    public static int videoEncoderFrameRate(MediaCodec encoder, String mime, int width, int height) {\n        if (encoder == null || width <= 0 || height <= 0) return 30;\n        try {\n            MediaCodecInfo.CodecCapabilities caps = encoder.getCodecInfo().getCapabilitiesForType(mime);\n            if (caps != null && caps.getVideoCapabilities() != null\n                    && caps.getVideoCapabilities().areSizeAndRateSupported(width, height, 60.0)) return 60;\n        } catch (Exception ignored) {}\n        return 30;\n    }\n}\n"
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

FPS_CLASS = "/* GPL-2.0-or-later. FluidGram v0.3 */\npackage org.telegram.messenger.camera;\nimport android.util.Range;\n/** Prefer an advertised 60fps camera AE range, otherwise a 30fps fallback. */\npublic final class FluidCameraFps {\n    private FluidCameraFps() {}\n    public static Range<Integer> choose(Range<Integer>[] advertised) {\n        if (advertised == null || advertised.length == 0) return null;\n        Range<Integer> best60 = null, best30 = null, closest = null;\n        for (Range<Integer> range : advertised) {\n            if (range == null) continue;\n            int low = range.getLower(), high = range.getUpper();\n            if (low <= 0 || high < low) continue;\n            if (low == 60 && high == 60) return range;\n            if (low <= 60 && high >= 60 &&\n                    (best60 == null || low > best60.getLower() ||\n                    low == best60.getLower() && high < best60.getUpper())) best60 = range;\n            if (low <= 30 && high >= 30 &&\n                    (best30 == null || high-low < best30.getUpper()-best30.getLower())) best30 = range;\n            if (closest == null || Math.abs(high-30) < Math.abs(closest.getUpper()-30)) closest = range;\n        }\n        return best60 != null ? best60 : best30 != null ? best30 : closest;\n    }\n}\n"
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

FLUIDGLASS_COLORS = {
    "actionBarDefault": "#ff0e1929",
    "actionBarDefaultTitle": "#fff0faff",
    "actionBarDefaultSubtitle": "#ff9fb2c6",
    "actionBarDefaultIcon": "#ffe1f4ff",
    "actionBarDefaultSubmenuBackground": "#ff17263a",
    "windowBackgroundWhite": "#ff101c2b",
    "windowBackgroundGray": "#ff0a1422",
    "windowBackgroundWhiteBlackText": "#fff0f6ff",
    "windowBackgroundWhiteGrayText": "#ffa5b8c8",
    "windowBackgroundWhiteBlueText": "#ff4de6d4",
    "windowBackgroundWhiteBlueButton": "#ff35d9cc",
    "dialogBackground": "#ff172538",
    "dialogSearchBackground": "#ff1c3043",
    "chats_name": "#fff0faff",
    "chats_message": "#ffa2b4c8",
    "chats_date": "#ff8d9fb5",
    "chats_unreadCounter": "#ff2ccfc0",
    "chats_unreadCounterMuted": "#ff45566c",
    "chats_menuBackground": "#ff102034",
    "chats_actionBackground": "#ff2cdac7",
    "chat_inBubble": "#ff1b2c40",
    "chat_outBubble": "#ff1c4951",
    "chat_inBubbleSelected": "#ff2b4059",
    "chat_outBubbleSelected": "#ff255966",
    "chat_messageTextIn": "#fff3f8ff",
    "chat_messageTextOut": "#fff3f8ff",
    "chat_messagePanelBackground": "#ff101e30",
    "chat_outTimeText": "#ffc3d6e2",
    "chat_outSentCheck": "#ff62f3dc",
    "chat_wallpaper": "#ff0b1524"
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
    def patch_properties(s):
        s = replace_exact(s, "APP_PACKAGE=org.telegram.messenger", "APP_PACKAGE=" + PACKAGE, props)
        return replace_exact(s, "APP_VERSION_CODE=7237", "APP_VERSION_CODE=7239", props)
    rewrite(props, patch_properties)

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
            if (fluidNow - lastFluidSwitchUptime < 390L) {
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
        s = replace_exact(s, "            valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);",
                          "            valueAnimator.setInterpolator(FluidMotion.cameraFlipInterpolator());", camera)
        return replace_exact(s, "                format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);",
                          "                format.setInteger(MediaFormat.KEY_FRAME_RATE, FluidMotion.videoEncoderFrameRate(videoEncoder, VIDEO_MIME_TYPE, videoWidth, videoHeight));", camera)
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


    # Repaint Telegram's shipped dark theme, preserving a fully defined
    # contrast-safe palette instead of trying to darken light defaults.
    dark_asset = root / "TMessagesProj/src/main/assets/darkblue.attheme"
    def patch_dark_asset(s):
        for key, color in FLUIDGLASS_COLORS.items():
            pattern = r"(?m)^" + re.escape(key) + r"=.*$"
            s, count = re.subn(pattern, key + "=" + color, s)
            if count == 0:
                # Upstream dark asset inherits some colors from defaults.
                # Add an explicit entry instead of rejecting the theme.
                s += "\n" + key + "=" + color + "\n"
            elif count != 1:
                raise RuntimeError(f"Dark theme key {key}: {count} matches")
        return s
    rewrite(dark_asset, patch_dark_asset)
    # Startup safety in v0.3.1: do NOT replace Telegram's built-in theme
    # identifiers or write theme preferences during ApplicationLoader startup.
    # The recolored "Dark Blue" preset is user-selectable under Appearance.
    # This avoids startup regressions from the aggressive v0.3 migration.

    # Firebase Google Services must run on the application module only.
    # Its Android library module retains the upstream namespace, not our app ID.
    library_gradle = root / "TMessagesProj/build.gradle"
    rewrite(library_gradle, lambda s: replace_exact(
        s, "apply plugin: 'com.google.gms.google-services'",
        "// FluidGram: google-services plugin is applied by AppStandalone only",
        library_gradle))

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
                                '<item name="android:windowBackground">#0B1524</item>', styles)
        return head + start_tag + section + "</style>" + tail
    rewrite(styles, patch_styles)
    print("FluidGram v0.3.1 FluidGlass safe-start applied. Install package: " + PACKAGE + ".web")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--telegram", required=True, type=Path)
    args = parser.parse_args()
    apply(args.telegram, os.environ.get("FLUIDGRAM_API_ID", ""), os.environ.get("FLUIDGRAM_API_HASH", ""))
