"""Regression checks for opt-in video/round-video upload activity filtering.

These tests validate the source-level dispatch guard. On-device testing with a
second account is still required to verify what Telegram actually displays.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class VideoUploadGhostTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.privacy = (ROOT / "android/FluidPrivacy.java").read_text(encoding="utf-8")
        cls.patch = (ROOT / "scripts/apply_fluidgram.py").read_text(encoding="utf-8")
        cls.labs = (ROOT / "android/FluidLabsActivity.java").read_text(encoding="utf-8")

    def test_only_opted_in_video_activity_and_not_media_transfer(self):
        method = self.privacy.split(
            "public static boolean shouldSuppressVideoUploadIndicator(", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("instanceof TLRPC.TL_messages_setTyping", method)
        self.assertIn("instanceof TLRPC.TL_sendMessageUploadVideoAction", method)
        self.assertIn("instanceof TLRPC.TL_sendMessageUploadRoundAction", method)
        self.assertIn('enabled("ghost_hide_video_upload_indicator")', method)
        self.assertIn("getBoolean(key, false)", self.privacy)
        for forbidden in (
            "TL_messages_sendMedia", "TL_messages_setEncryptedTyping",
            "TL_messages_readHistory", "TL_account_updateStatus",
            "TL_sendMessageUploadPhotoAction",
        ):
            self.assertNotIn(forbidden, method)

    def test_interception_precedes_native_dispatch_and_returns_callback(self):
        guard = self.patch.split("if (FluidPrivacy.shouldSuppressTyping(object)", 1)[1]
        guard = guard.split("if (BuildVars.LOGS_ENABLED)", 1)[0]
        self.assertIn("|| FluidPrivacy.shouldSuppressVideoUploadIndicator(object)", guard)
        self.assertIn("No native_sendRequest", guard)
        self.assertIn("return;", guard)
        self.assertIn("onComplete.run(new TLRPC.TL_boolTrue(), null)", guard)
        self.assertIn("onCompleteTimestamp.run(new TLRPC.TL_boolTrue(), null, 0L)", guard)

    def test_independent_disabled_by_default_setting_with_limitations(self):
        self.assertIn('toggle("Не показывать «отправляет видео…»"', self.labs)
        self.assertIn('"ghost_hide_video_upload_indicator", false', self.labs)
        self.assertIn('"ghost_hide_photo_upload_indicator", false', self.labs)
        self.assertIn('"ghost_hide_recording_video", false', self.labs)
        self.assertIn("Сами файлы и сообщения продолжают отправляться", self.labs)
        self.assertIn("клиентов Telegram", self.labs)


if __name__ == "__main__":
    unittest.main()
