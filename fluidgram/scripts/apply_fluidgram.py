#!/usr/bin/env python3
"""Apply FluidGram v1.4 thermal-aware glass and refresh guard, VPN profile audit and Energy Lab overlay to pinned Telegram Android checkout."""
import argparse
import os
import re
from pathlib import Path

PACKAGE = "dev.fluidgram.oneplus15"
UPSTREAM_SHA = "3de37c8cfb039452be7398909461da6288babf15"
MOTION_CLASS = "/* GPL-2.0-or-later. FluidGram v0.4 */\npackage org.telegram.ui.Components;\nimport android.media.MediaCodec;\nimport android.media.MediaCodecInfo;\nimport android.os.Build;\nimport android.view.animation.Interpolator;\nimport android.view.animation.PathInterpolator;\npublic final class FluidMotion {\n    private FluidMotion() {}\n    public static boolean isOnePlus15() {\n        return (\"OnePlus\".equalsIgnoreCase(Build.MANUFACTURER) &&\n                (Build.MODEL != null && (Build.MODEL.contains(\"15\") || Build.MODEL.contains(\"CPH2747\"))))\n                || \"CPH2747\".equalsIgnoreCase(Build.MODEL);\n    }\n    public static long cameraFlipDurationMillis() { return 330L; }\n    public static Interpolator cameraFlipInterpolator() {\n        return new PathInterpolator(0.20f, 0f, 0.15f, 1.0f);\n    }\n    /** The encoder may only request 60 fps if user and codec allow it. */\n    public static int videoEncoderFrameRate(MediaCodec encoder, String mime, int width, int height, boolean prefer60) {\n        if (!prefer60 || encoder == null || width <= 0 || height <= 0) return 30;\n        try {\n            MediaCodecInfo.CodecCapabilities caps = encoder.getCodecInfo().getCapabilitiesForType(mime);\n            if (caps != null && caps.getVideoCapabilities() != null &&\n                    caps.getVideoCapabilities().areSizeAndRateSupported(width, height, 60.0)) return 60;\n        } catch (Exception ignored) {}\n        return 30;\n    }\n}\n"
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

FPS_CLASS = "/* GPL-2.0-or-later. FluidGram v0.4 */\npackage org.telegram.messenger.camera;\nimport android.util.Range;\n/** Only request ranges advertised by Camera2. */\npublic final class FluidCameraFps {\n    private FluidCameraFps() {}\n    public static Range<Integer> choose(Range<Integer>[] advertised) {\n        return choose(advertised, true);\n    }\n    public static Range<Integer> choose(Range<Integer>[] advertised, boolean smooth60) {\n        if (advertised == null || advertised.length == 0) return null;\n        Range<Integer> best60 = null, best30 = null, closest = null;\n        for (Range<Integer> range : advertised) {\n            if (range == null) continue;\n            int low = range.getLower(), high = range.getUpper();\n            if (low <= 0 || high < low) continue;\n            if (smooth60 && low == 60 && high == 60) return range;\n            if (!smooth60 && low == 30 && high == 30) return range;\n            if (low <= 60 && high >= 60 &&\n                    (best60 == null || low > best60.getLower() ||\n                    (low == best60.getLower() && high < best60.getUpper()))) best60 = range;\n            if (low <= 30 && high >= 30 &&\n                    (best30 == null || high-low < best30.getUpper()-best30.getLower())) best30 = range;\n            if (closest == null || Math.abs(high-30) < Math.abs(closest.getUpper()-30)) closest = range;\n        }\n        return smooth60 && best60 != null ? best60 : best30 != null ? best30 : best60 != null ? best60 : closest;\n    }\n}\n"
THEME_CHANGES = {
    "public static final int TELEGRAM_COLOR = 0xFF229AF0;": "public static final int TELEGRAM_COLOR = 0xFF16A6A2;",
    "public static final int TELEGRAM_COLOR_TEXT = 0xFF298ACF;": "public static final int TELEGRAM_COLOR_TEXT = 0xFF087C85;",
    "defaultColors[key_actionBarDefault] = 0xffffffff;": "defaultColors[key_actionBarDefault] = 0xffF4F8FC;",
    "defaultColors[key_windowBackgroundGray] = 0xffF1F1F3;": "defaultColors[key_windowBackgroundGray] = 0xffEDF3F8;",
    "defaultColors[key_dialogSearchBackground] = 0xfff2f4f5;": "defaultColors[key_dialogSearchBackground] = 0xffF4F8FC;",
    # ColorOS 17 chat-list polish: translucent selection, calm secondary text,
    # and teal unread/folder accents; these are actual Telegram theme keys.
    "defaultColors[key_dialogSearchHint] = 0xff98a0a7;": "defaultColors[key_dialogSearchHint] = 0xff778E9C;",
    "defaultColors[key_actionBarTabUnactiveText] = 0xff777c7f;": "defaultColors[key_actionBarTabUnactiveText] = 0xff708391;",
    "defaultColors[key_actionBarTabSelector] = 0x121a1d21;": "defaultColors[key_actionBarTabSelector] = 0x1426A7A4;",
    "defaultColors[key_chats_tabUnreadActiveBackground] = 0xFF66ade1;": "defaultColors[key_chats_tabUnreadActiveBackground] = 0xff16A6A2;",
    "defaultColors[key_chats_tabUnreadUnactiveBackground] = 0xffc5c9cc;": "defaultColors[key_chats_tabUnreadUnactiveBackground] = 0xffC6D3DB;",
    "defaultColors[key_chats_unreadCounterMuted] = 0xffBEC3C7;": "defaultColors[key_chats_unreadCounterMuted] = 0xffB7C8CF;",
    "defaultColors[key_chats_message] = 0xff75787A;": "defaultColors[key_chats_message] = 0xff647C89;",
    "defaultColors[key_chats_date] = 0xff848688;": "defaultColors[key_chats_date] = 0xff718492;",
    "defaultColors[key_chats_pinnedOverlay] = 0x08000000;": "defaultColors[key_chats_pinnedOverlay] = 0x0B16A6A2;",
    "defaultColors[key_chats_tabletSelectedOverlay] = 0x0f000000;": "defaultColors[key_chats_tabletSelectedOverlay] = 0x1816A6A2;",
    "defaultColors[key_chats_actionBackground] = 0xff65a9e0;": "defaultColors[key_chats_actionBackground] = 0xff16A6A2;",
    "defaultColors[key_chats_actionPressedBackground] = 0xff569dd6;": "defaultColors[key_chats_actionPressedBackground] = 0xff0B817E;",
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
    # Dark Blue preset: chat-list folders, search, archive and selection.
    # All values are static; no blur or frame callbacks are added to dialogs.
    "actionBarTabActiveText": "#ff62f3dc",
    "actionBarTabUnactiveText": "#ff9aafbf",
    "actionBarTabLine": "#ff2ccfc0",
    "chats_tabUnreadActiveBackground": "#ff2ccfc0",
    "chats_tabUnreadUnactiveBackground": "#ff465d6c",
    "chats_pinnedOverlay": "#1b2cdac7",
    "chats_tabletSelectedOverlay": "#302cdac7",
    "chats_archiveBackground": "#ff1f857f",
    "chats_actionPressedBackground": "#ff1c8f87",
    "chats_date_bold": "#ffa3b8c8",
    "chats_messageArchived": "#ff8da3b7",
    "dialogSearchHint": "#ff9bb0c1",
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


def patch_round_camera_extras(s, camera):
    """Adds a safe rear-camera switch, a 30/60 FPS toggle, and double-tap flip."""
    # Camera2 rear switch must close its old session before opening a new one.
    first = s.index("    private void switchCamera() {")
    last = s.index("    private void logLegacyCameraSwitchStage(", first)
    old = s[first:last]
    # Keep Telegram's original dual-camera and Camera1 paths; replace only
    # the one-device sequential Camera2 branch, after the outer safeguards.
    needle = """                if (camera2SessionCurrent != null) {
                    camera2SessionCurrent.destroy(false);
                    camera2SessionCurrent = null;
                    camera2Sessions[isFrontface ? 1 : 0] = null;
                }
                camera2SessionCurrent = camera2Sessions[isFrontface ? 0 : 1] = Camera2Session.create(isFrontface, MessagesController.getInstance(UserConfig.selectedAccount).roundVideoSize, MessagesController.getInstance(UserConfig.selectedAccount).roundVideoSize);
                if (camera2SessionCurrent == null) {
                    legacyCameraSwitchAwaitingFrame = false;
                    logLegacyCameraSwitchStage("failed: Camera2Session.create returned null");
                    return;
                }
                camera2SessionCurrent.setRecordingVideo(true);
                previewSize[0] = new Size(camera2SessionCurrent.getPreviewWidth(), camera2SessionCurrent.getPreviewHeight());
                cameraThread.setCurrentSession(camera2SessionCurrent);"""
    if old.count(needle) != 1:
        raise RuntimeError("Upstream sequential Camera2 switch changed")
    replacement = """                final Camera2Session oldSession = camera2SessionCurrent;
                final boolean targetFront = isFrontface;
                cameraReady = false;
                fluidSwitchingCamera = true;
                camera2SessionCurrent = null;
                camera2Sessions[targetFront ? 1 : 0] = null;
                Runnable openNewCamera = () -> {
                    if (cancelled || cameraThread == null || isFrontface != targetFront) {
                        fluidSwitchingCamera = false;
                        return;
                    }
                    try {
                        Camera2Session nextSession = Camera2Session.create(targetFront,
                                MessagesController.getInstance(UserConfig.selectedAccount).roundVideoSize,
                                MessagesController.getInstance(UserConfig.selectedAccount).roundVideoSize);
                        if (nextSession == null) {
                            fluidSwitchingCamera = false;
                            legacyCameraSwitchAwaitingFrame = false;
                            if (!targetFront) {
                                switchCamera(); // Recover the functioning front lens.
                            }
                            return;
                        }
                        camera2SessionCurrent = camera2Sessions[targetFront ? 0 : 1] = nextSession;
                        nextSession.setPrefer60Fps(fluidPrefer60Fps);
                        nextSession.setRecordingVideo(true);
                        previewSize[0] = new Size(nextSession.getPreviewWidth(), nextSession.getPreviewHeight());
                        cameraThread.setCurrentSession(nextSession);
                        cameraThread.reinitForNewCamera();
                        // A failed rear open should never leave the client permanently black.
                        AndroidUtilities.runOnUIThread(() -> {
                            if (!cancelled && cameraThread != null && !isFrontface
                                    && camera2SessionCurrent == nextSession && !nextSession.isInitiated()) {
                                logLegacyCameraSwitchStage("rear camera timeout; restoring front");
                                switchCamera();
                            }
                        }, 3500);
                    } catch (Exception e) {
                        FileLog.e(e);
                        if (!targetFront && cameraThread != null && !cancelled) {
                            fluidSwitchingCamera = false;
                            switchCamera();
                        }
                    } finally {
                        fluidSwitchingCamera = false;
                    }
                };
                if (oldSession != null) {
                    oldSession.destroy(true, openNewCamera);
                } else {
                    openNewCamera.run();
                }
                return;"""
    old = old.replace(needle, replacement)
    s = s[:first] + old + s[last:]
    # Add new mode UI alongside Telegram's ordinary switch and flash controls.
    anchor = "        flashButton = new FlashViews.ImageViewInvertable(context);"
    pill = """        // FluidGram-only feature: choose smoother footage or brighter night shots.
        fluidPrefer60Fps = getContext().getSharedPreferences("fluidgram_round_camera", Context.MODE_PRIVATE)
                .getBoolean("prefer_60", true);
        android.widget.TextView fluidFpsButton = new android.widget.TextView(context);
        fluidFpsButton.setGravity(Gravity.CENTER);
        fluidFpsButton.setTextSize(12);
        fluidFpsButton.setTextColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable fluidFpsBackground = new android.graphics.drawable.GradientDrawable();
        fluidFpsBackground.setColor(0xCC123040);
        fluidFpsBackground.setCornerRadius(dp(18));
        fluidFpsBackground.setStroke(dp(1), 0x9953D9D0);
        fluidFpsButton.setBackground(fluidFpsBackground);
        fluidFpsButton.setText(fluidPrefer60Fps ? "60 FPS" : "LIGHT");
        fluidFpsButton.setContentDescription("FluidGram camera mode: tap to switch smooth 60 FPS or brighter 30 FPS");
        buttonsLayout.addView(fluidFpsButton, LayoutHelper.createLinear(64, 44));
        fluidFpsButton.setOnClickListener(v -> {
            fluidPrefer60Fps = !fluidPrefer60Fps;
            getContext().getSharedPreferences("fluidgram_round_camera", Context.MODE_PRIVATE)
                    .edit().putBoolean("prefer_60", fluidPrefer60Fps).apply();
            fluidFpsButton.setText(fluidPrefer60Fps ? "60 FPS" : "LIGHT");
            fluidFpsButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            for (Camera2Session session : camera2Sessions) {
                if (session != null) session.setPrefer60Fps(fluidPrefer60Fps);
            }
            android.widget.Toast.makeText(getContext(),
                    fluidPrefer60Fps ? "FluidGram: smoother video mode (if supported)"
                                     : "FluidGram: brighter low-light mode",
                    android.widget.Toast.LENGTH_SHORT).show();
        });

"""
    s = replace_exact(s, anchor, pill + anchor, camera)
    # Double tap inside round preview: quick camera flip while recording,
    # preserving the pinch gesture and using the same guarded click handler.
    anchor_down = "                maybePinchToZoomTouchMode = AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY());"
    s = replace_exact(s, anchor_down, anchor_down + """
                if (!maybePinchToZoomTouchMode) lastFluidPreviewTapUptime = 0L;""", camera)
    anchor_end = """        return true;
    }

    ValueAnimator finishZoomTransition;"""
    flip = """        if (ev.getActionMasked() == MotionEvent.ACTION_UP && ev.getPointerCount() == 1
                && recording && videoPlayer == null && !isInPinchToZoomTouchMode
                && maybePinchToZoomTouchMode) {
            long now = SystemClock.uptimeMillis();
            if (now - lastFluidPreviewTapUptime < 320L) {
                lastFluidPreviewTapUptime = 0L;
                switchCameraButton.performClick();
            } else {
                lastFluidPreviewTapUptime = now;
            }
        }
"""
    s = replace_exact(s, anchor_end, flip + anchor_end, camera)
    oldFrame = '''                legacyCameraSwitchAwaitingFrame = false;
                logLegacyCameraSwitchStage("first target frame presented: surface="'''
    newFrame = '''                legacyCameraSwitchAwaitingFrame = false;
                AndroidUtilities.runOnUIThread(() -> {
                    cameraReady = true;
                    if (textureOverlayView != null) {
                        textureOverlayView.animate().alpha(0f).setDuration(120).start();
                    }
                });
                logLegacyCameraSwitchStage("first target frame presented: surface="'''
    return replace_exact(s, oldFrame, newFrame, camera)

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
        return replace_exact(s, "APP_VERSION_CODE=7237", "APP_VERSION_CODE=7248", props)
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
    guard = """            if (!cameraReady || !isCameraSessionInitiated() || cameraThread == null || flipAnimationInProgress || fluidSwitchingCamera) {
                return;
            }
            final long fluidNow = SystemClock.uptimeMillis();
            if (fluidNow - lastFluidSwitchUptime < 390L) {
                return;
            }
            lastFluidSwitchUptime = fluidNow;"""
    def patch_camera(s):
        s = replace_exact(s, "    private volatile boolean cameraReady;",
                          "    private volatile boolean cameraReady;\n    private long lastFluidSwitchUptime;\n    private boolean fluidSwitchingCamera;\n    private boolean fluidPrefer60Fps = true;\n    private long lastFluidPreviewTapUptime;", camera)
        s = replace_exact(s, """            if (!cameraReady || !isCameraSessionInitiated() || cameraThread == null) {
                return;
            }""", guard, camera)
        s = replace_exact(s, "            bothCameras = DualCameraView.roundDualAvailableStatic(getContext());",
                          "            bothCameras = !FluidMotion.isOnePlus15() && DualCameraView.roundDualAvailableStatic(getContext());", camera)
        s = replace_exact(s, "                            camera2Sessions[a].setRecordingVideo(true);",
                          "                            camera2Sessions[a].setPrefer60Fps(fluidPrefer60Fps);\n                            camera2Sessions[a].setRecordingVideo(true);", camera)
        # Upstream has two copies: initial open and sequential switch.
        # Change the first, then replace the switch method separately.
        recording_anchor = "                camera2SessionCurrent.setRecordingVideo(true);"
        if s.count(recording_anchor) != 2:
            raise RuntimeError("Upstream Camera2 setRecordingVideo layout changed")
        s = s.replace(recording_anchor,
                      "                camera2SessionCurrent.setPrefer60Fps(fluidPrefer60Fps);\n" + recording_anchor, 1)
        s = replace_exact(s, "            valueAnimator.setDuration(580);",
                          "            valueAnimator.setDuration(FluidMotion.cameraFlipDurationMillis());", camera)
        s = replace_exact(s, "            valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);",
                          "            valueAnimator.setInterpolator(FluidMotion.cameraFlipInterpolator());", camera)
        s = replace_exact(s, "                format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);",
                          "                format.setInteger(MediaFormat.KEY_FRAME_RATE, FluidMotion.videoEncoderFrameRate(videoEncoder, VIDEO_MIME_TYPE, videoWidth, videoHeight, fluidPrefer60Fps));", camera)
        s = patch_round_camera_extras(s, camera)
        needle = "            flipAnimationInProgress = true;"
        haptic = """            if (getContext().getSharedPreferences("fluidgram_labs", Context.MODE_PRIVATE)
                    .getBoolean("camera_haptic", true)) {
                switchCameraButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
"""
        return replace_exact(s, needle, haptic + needle, camera)
    rewrite(camera, patch_camera)
    motion = camera.parent / "FluidMotion.java"
    if motion.exists(): raise RuntimeError("FluidMotion exists already; apply only once")
    motion.write_text(MOTION_CLASS, encoding="utf8")


    session = root / "TMessagesProj/src/main/java/org/telegram/messenger/camera/Camera2Session.java"
    def patch_session(s):
        s = replace_exact(s,
            "                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(30, 60));",
            """                final Range<Integer> fluidFpsRange = FluidCameraFps.choose(
                        cameraCharacteristics == null ? null : cameraCharacteristics.get(
                                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES), prefer60Fps);
                if (fluidFpsRange != null) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fluidFpsRange);
                }""", session)
        s = replace_exact(s, "    private boolean recordingVideo;",
                  """    private boolean prefer60Fps = true;
    public void setPrefer60Fps(boolean enable) {
        if (prefer60Fps != enable) {
            prefer60Fps = enable;
            updateCaptureRequest();
        }
    }

    private boolean recordingVideo;""", session)
        return s
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


    labs_dir = Path(__file__).resolve().parents[1] / "android"
    for name in ("FluidRefresh.java", "FluidOplusRate.java", "FluidLabsActivity.java", "FluidGlass.java", "FluidEnergy.java", "FluidVpnProfile.java", "FluidVpnTunnel.java", "FluidVlessProfile.java"):
        original = labs_dir / name
        if not original.is_file():
            raise RuntimeError(f"FluidGram Labs source missing: {original}")
        dest = root / "TMessagesProj/src/main/java/org/telegram/ui" / name
        if dest.exists():
            raise RuntimeError(f"FluidGram Labs already installed: {dest}")
        dest.write_text(original.read_text(encoding="utf8"), encoding="utf8")

    settings = root / "TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java"
    def patch_settings(src):
        needle = "        items.add(SettingCell.Factory.of(9, IconBackgroundColors.ORANGE_DEEP.top"
        if src.count(needle) != 1:
            raise RuntimeError("Settings menu anchor changed")
        line = """        items.add(SettingCell.Factory.of(27, 0xFF4DDFCE, 0xFF176487,
                R.drawable.settings_power, "FluidGram Labs",
                "165 Гц · ColorOS · необычные функции"));
"""
        src = src.replace(needle, line + needle, 1)
        click = """            case 27:
                if (getParentActivity() != null) {
                    getParentActivity().startActivity(new Intent(getParentActivity(), FluidLabsActivity.class));
                }
                break;
"""
        return replace_exact(src, "            case 11:", click + "            case 11:", settings)
    rewrite(settings, patch_settings)

    launcher = root / "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"
    def patch_launcher(src):
        src = replace_exact(src,
            """    protected void onResume() {
        super.onResume();
        isResumed = true;""",
            """    protected void onResume() {
        super.onResume();
        FluidRefresh.resume(this);
        isResumed = true;""", launcher)
        src = replace_exact(src,
            """    protected void onPause() {
        super.onPause();
        isResumed = false;""",
            """    protected void onPause() {
        FluidRefresh.pause(this);
        super.onPause();
        isResumed = false;""", launcher)
        # Upstream Telegram already implements onUserInteraction(). Amend it instead
        # of inserting another override (which breaks javac).
        return replace_exact(src,
            """    public void onUserInteraction() {
        super.onUserInteraction();
        voipLaunchedInBackground = false;""",
            """    public void onUserInteraction() {
        super.onUserInteraction();
        FluidRefresh.onInteraction(this);
        voipLaunchedInBackground = false;""", launcher)
    rewrite(launcher, patch_launcher)

    privacy_source = labs_dir / "FluidPrivacy.java"
    privacy_dest = root / "TMessagesProj/src/main/java/org/telegram/tgnet/FluidPrivacy.java"
    if not privacy_source.is_file() or privacy_dest.exists():
        raise RuntimeError("FluidPrivacy source missing or destination already exists")
    privacy_dest.write_text(privacy_source.read_text(encoding="utf8"), encoding="utf8")

    # Intercept only opted-in activity RPCs (typing, recording, photo/video-upload indicators),
    # never actual file uploads, read receipts, online state or secret-chat traffic. This is the final Java RPC dispatch
    # before serialization, so all call sites share the same opt-in policy.
    connections = root / "TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java"
    rewrite(connections, lambda src: replace_exact(src,
        """    private void sendRequestInternal(TLObject object, RequestDelegate onComplete, RequestDelegateTimestamp onCompleteTimestamp, QuickAckDelegate onQuickAck, WriteToSocketDelegate onWriteToSocket, int flags, int datacenterId, int connectionType, boolean immediate, int requestToken) {
        if (BuildVars.LOGS_ENABLED) {""",
        """    private void sendRequestInternal(TLObject object, RequestDelegate onComplete, RequestDelegateTimestamp onCompleteTimestamp, QuickAckDelegate onQuickAck, WriteToSocketDelegate onWriteToSocket, int flags, int datacenterId, int connectionType, boolean immediate, int requestToken) {
        if (FluidPrivacy.shouldSuppressTyping(object)
                || FluidPrivacy.shouldSuppressRecordingAudio(object)
                || FluidPrivacy.shouldSuppressRecordingVideo(object)
                || FluidPrivacy.shouldSuppressPhotoUploadIndicator(object)
                || FluidPrivacy.shouldSuppressVideoUploadIndicator(object)) {
            // No native_sendRequest: this activity event never reaches Telegram.
            // Return a local success so activity throttles can clear normally.
            Utilities.stageQueue.postRunnable(() -> {
                if (onComplete != null) onComplete.run(new TLRPC.TL_boolTrue(), null);
                else if (onCompleteTimestamp != null)
                    onCompleteTimestamp.run(new TLRPC.TL_boolTrue(), null, 0L);
            });
            return;
        }
        if (BuildVars.LOGS_ENABLED) {""", connections))

    # The custom Labs screen is not exported outside FluidGram.
    manifest = root / "TMessagesProj/config/release/AndroidManifest_standalone.xml"
    rewrite(manifest, lambda src: replace_exact(
        src, 'tools:replace="android:supportsRtl">',
        '''tools:replace="android:supportsRtl">
        <activity android:name="org.telegram.ui.FluidLabsActivity"
                  android:exported="false"
                  android:theme="@style/Theme.TMessages.Start"
                  android:label="FluidGram Labs" />
        <service android:name="com.wireguard.android.backend.GoBackend$VpnService"
                 android:permission="android.permission.BIND_VPN_SERVICE"
                 android:exported="false">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>''', manifest))

    # Firebase Google Services must run on the application module only.
    # Its Android library module retains the upstream namespace, not our app ID.
    library_gradle = root / "TMessagesProj/build.gradle"
    def patch_library_gradle(s):
        s = replace_exact(s, "apply plugin: 'com.google.gms.google-services'",
            "// FluidGram: google-services plugin is applied by AppStandalone only",
            library_gradle)
        s = replace_exact(s, "    implementation 'androidx.core:core:1.16.0'",
            "    implementation 'androidx.core:core:1.16.0'\n"
            "    implementation 'com.wireguard.android:tunnel:1.0.20260102'",
            library_gradle)
        # Official WireGuard 2026 AAR requires Android API 24 minimum.
        return replace_exact(s, "minSdkVersion 23", "minSdkVersion 24", library_gradle)
    rewrite(library_gradle, patch_library_gradle)
    app_gradle = root / "TMessagesProj_AppStandalone/build.gradle"
    rewrite(app_gradle, lambda s: replace_exact(
        s, "minSdkVersion 23", "minSdkVersion 24", app_gradle))

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
    print("FluidGram v1.6 WireGuard opt-in VPN and glass power guard applied. Install package: " + PACKAGE + ".web")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--telegram", required=True, type=Path)
    args = parser.parse_args()
    apply(args.telegram, os.environ.get("FLUIDGRAM_API_ID", ""), os.environ.get("FLUIDGRAM_API_HASH", ""))
