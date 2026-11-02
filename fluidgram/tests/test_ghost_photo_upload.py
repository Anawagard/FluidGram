"""Regression checks for optional photo-upload activity suppression."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class PhotoUploadGhostTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.privacy = (ROOT / "android/FluidPrivacy.java").read_text(encoding="utf-8")
        cls.patch = (ROOT / "scripts/apply_fluidgram.py").read_text(encoding="utf-8")
        cls.labs = (ROOT / "android/FluidLabsActivity.java").read_text(encoding="utf-8")

    def test_only_photo_activity_rpc_and_opt_in(self):
        method = self.privacy.split(
            "public static boolean shouldSuppressPhotoUploadIndicator(", 1
        )[1].split("    /**", 1)[0]
        self.assertIn("instanceof TLRPC.TL_messages_setTyping", method)
        self.assertIn("instanceof TLRPC.TL_sendMessageUploadPhotoAction", method)
        self.assertIn('enabled("ghost_hide_photo_upload_indicator")', method)
        self.assertIn('getBoolean(key, false)', self.privacy)
        self.assertNotIn("TL_messages_sendMedia", method)
        self.assertNotIn("TL_messages_setEncryptedTyping", self.privacy)
        self.assertNotIn("TL_messages_readHistory", self.privacy)
        self.assertNotIn("TL_account_updateStatus", self.privacy)

    def test_hook_before_native_dispatch(self):
        hook = self.patch.split("if (FluidPrivacy.shouldSuppressTyping(object)", 1)[1]
        hook = hook.split("if (BuildVars.LOGS_ENABLED)", 1)[0]
        self.assertIn("FluidPrivacy.shouldSuppressPhotoUploadIndicator(object)", hook)
        self.assertIn("No native_sendRequest", hook)
        self.assertIn("return;", hook)
        self.assertIn("onComplete.run(new TLRPC.TL_boolTrue(), null)", hook)
        self.assertIn("onCompleteTimestamp.run(new TLRPC.TL_boolTrue(), null, 0L)", hook)

    def test_independent_disabled_by_default_setting(self):
        self.assertIn('toggle("Не показывать «отправляет фото…»"', self.labs)
        self.assertIn('"ghost_hide_photo_upload_indicator", false', self.labs)
        self.assertIn('"ghost_hide_typing", false', self.labs)
        self.assertIn('"ghost_hide_recording_audio", false', self.labs)
        self.assertIn('"ghost_hide_recording_video", false', self.labs)
        self.assertIn("не саму загрузку, отправку", self.labs)
        self.assertIn("других клиентов Telegram", self.labs)


if __name__ == "__main__":
    unittest.main()
